# Object3 — A Small S3-Compatible Object Store

Object3 is a from-scratch, S3-compatible object store written in Java 17 with Spring Boot (Web MVC + JDBC) and SQLite as the metadata database. It speaks the AWS S3 REST protocol — the same HTTP API, the same `AWS4-HMAC-SHA256` (SigV4) authentication, multipart uploads, versioning and byte-range downloads — so any AWS SDK (Java SDK v2, boto3, aws-cli, …) can talk to it by simply overriding the endpoint URL.

> **Two projects share one codebase**: `ObjectStoreDB` (the S3 server) and `AdminAPI` (a small admin REST API that seeds users/permissions). They share a single SQLite database file placed **next to both projects** at `PROJECT_URL/Object3/data.db`.

---

## 1. How a request travels through the system

```
                  AWS SDK / aws-cli / boto3
                            │
                 SigV4 signed HTTP request
                            │
                            ▼
                 ┌──────────────────────────┐      ┌──────────────┐
                 │  AuthenticationFilter     │─────▶│   Filter     │
                 │  (checks & authz,        │      │ logFilter    │
                 │   every request)         │      └──────────────┘
                 └──────────────────────────┘
                            │ allowed?
                            ▼
                 ┌──────────────────────────┐
                 │  FileOperationController  │  REST endpoints
                 └──────────────────────────┘
                            │
                            ▼
                 ┌──────────────────────────┐      ┌──────────────────┐
                 │  FileOperationService     │─────▶│  data/ on disk   │
                 │  (objects + versions)     │      │  (buckets/…,     │
                 │                            │      │   multipart/…)  │
                 └──────────────────────────┘      └──────────────────┘
                            │
                            ▼
                 ┌──────────────────────────┐
                 │ Repositories + SQLite    │  versions, objects,
                 │ (JdbcTemplate)           │  credentials, authorization
                 └──────────────────────────┘
```

The filter runs **before** any controller: it authenticates the request signature, then authorises the operation against the caller's permissions.

---

## 2. Authentication (SigV4 / `AWS4-HMAC-SHA256`)

Every request must carry an `Authorization` header that proves it was signed with a valid **secret key**. Object3 re-computes the signature server-side and compares.

A real client header looks like this:

```
Authorization: AWS4-HMAC-SHA256 Credential=test/20260830/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=3f6c…
```

The `Credential=` value is split by `/` into the **scope**:

| Part            | Example          | Meaning                              |
|-----------------|------------------|--------------------------------------|
| `Credential=`   | `test`           | **Access key** — looks up the secret key |
| `20260830`      | date          | Date (used to derive the signing key)    |
| `us-east-1`     | region           | AWS region                              |
| `s3`            | service          | must be `s3`                           |
| `aws4_request`  | terminator       | must be `aws4_request`                 |

### 2.1 The canonical request — the 6 ingredients

To sign a request you build a **canonical (normalized) request**. It is just these six blocks joined by newlines:

```
HTTP Method
Canonical URI
Canonical Query String
Canonical Headers

Signed Headers
Hashed Payload
```

| Line | What it is in Object3 | Where it is built |
|------|-----------------------|-------------------|
| **HTTP Method** | `GET`, `PUT`, `POST`, `DELETE`, `HEAD` | `SignatureUtils.buildCanonicalRequest()` line 1 |
| **Canonical URI** | `request.getRequestURI()` with `//` collapsed to `/` | `canonicalUri()` |
| **Canonical Query String** | query params decoded, sorted by name, duplicate names sorted by value; `?uploads`, `?partNumber=…`, `?uploadId=…` are part of it | `canonicalQuery()` |
| **Canonical Headers** | each signed header lower-cased, trimmed, one-per-line `name:value` | rows 94–108 |
| **Signed Headers** | the `SignedHeaders=…` list, sorted, joined with `;` | row 121 |
| **Hashed Payload** | hex SHA-256 of the body, from `x-amz-content-sha256` header | rows 110–115 |

The exact build code:

```java
private String buildCanonicalRequest(HttpServletRequest request, String signedHeaders,
                                     boolean omitEmptyEquals) throws Exception {
    String method = request.getMethod();
    String canonicalUri = canonicalUri(request.getRequestURI());
    String canonicalQuery = canonicalQuery(request.getQueryString(), omitEmptyEquals);

    TreeMap<String, String> headers = new TreeMap<>();               // sorted header map
    for (String name : signedHeaders.split(";")) {
        String h = name.trim().toLowerCase(Locale.ROOT);
        if (!h.isEmpty()) {
            headers.put(h, headerValue(request, h));
        }
    }

    StringBuilder canonicalHeaders = new StringBuilder();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
        canonicalHeaders.append(entry.getKey())
                .append(':')
                .append(entry.getValue())
                .append('\n');                                        // name:value\n
    }

    String payloadHash = request.getHeader("x-amz-content-sha256");
    if (payloadHash == null || payloadHash.isEmpty()) {
        payloadHash = ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method))
                ? sha256Hex("")                                       // empty body
                : "UNSIGNED-PAYLOAD";                                 // fallback
    }

    return method + '\n'
            + canonicalUri + '\n'
            + canonicalQuery + '\n'
            + canonicalHeaders + '\n'
            + String.join(";", headers.keySet()) + '\n'               // SignedHeaders
            + payloadHash;                                            // Hashed Payload
}
```

Note the fallback at the end: for `GET`/`HEAD`/`DELETE` with **no** payload hash header it uses `sha256("")`; for others it uses the literal `UNSIGNED-PAYLOAD` (that is what the AWS SDK sends when `chunkedEncodingEnabled=true`).

### 2.2 From canonical request to signature

```java
private boolean matches(byte[] signingKey, String canonicalRequest, String amzDate,
                        String dateStamp, String region, String provided) throws Exception {
    String stringToSign = "AWS4-HMAC-SHA256\n"
            + amzDate + "\n"
            + dateStamp + "/" + region + "/" + SERVICE + "/" + TERMINATOR + "\n"
            + sha256Hex(canonicalRequest);
    byte[] signature = hmac(signingKey, stringToSign);

    return MessageDigest.isEqual(                    // constant-time comparison
            HexFormat.of().formatHex(signature).getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
}
```

The **signing key** is derived by folding the secret key through four successive HMAC-SHA256 layers:

```java
private byte[] signingKey(String secretKey, String dateStamp, String region) throws Exception {
    byte[] kDate    = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
    byte[] kRegion  = hmac(kDate, region);
    byte[] kService = hmac(kRegion, SERVICE);          // "s3"
    return hmac(kService, TERMINATOR);                 // "aws4_request"
}
```

`SignatureUtils.verify()` computes this once, then tries two canonical variants (`omitEmptyEquals` true/false) to stay compatible with the slightly different ways SDKs render a query string that ends with an empty value (`?uploads` vs `?uploads=`).

It also enforces a **15-minute clock-skew window** via `withinSkew()`:

```java
return Math.abs(System.currentTimeMillis() - requestTime) <= SKEW_MILLIS; // 15 * 60 * 1000
```

### 2.3 The filter — where it is wired in

`AuthenticationFilter` (a `OncePerRequestFilter`) is the gatekeeper for **every** request:

```java
String header = request.getHeader("Authorization");
if (header == null || !header.startsWith("AWS4-HMAC-SHA256")) {
    reject(response, "AccessDenied", "Missing or invalid Authorization header");
    return;
}

String credential    = extract(header, "Credential=", ",");
String signedHeaders = extract(header, "SignedHeaders=", ",");
String signature     = extract(header, "Signature=", null);

String[] parts = credential.split("/");                 // [accessKey, date, region, s3, aws4_request]
if (parts.length != 5 || !"s3".equals(parts[3]) || !"aws4_request".equals(parts[4])) {
    reject(response, "AccessDenied", "Invalid credential scope");
    return;
}

boolean ok = authService.authenticate(request, parts[0] /*accessKey*/, signature,
                                      signedHeaders, parts[1] /*date*/, parts[2] /*region*/);
if (!ok) {
    reject(response, "SignatureDoesNotMatch", "Signature verification failed");
    return;
}
```

If verification passes it moves on to **authorization** (section 3). On failure it answers with an S3-style XML error and HTTP 403:

```java
private void reject(HttpServletResponse response, String code, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/xml");
    response.getWriter().write("""
            <Error>
                <Code>%s</Code>
                <Message>%s</Message>
            </Error>
            """.formatted(code, message).trim());
}
```

### 2.4 AuthenticationService

Thin service that turns the access key into a secret key and delegates crypto to `SignatureUtils`:

```java
public boolean authenticate(HttpServletRequest request, String accessKey, String signature,
                            String signedHeaders, String dateStamp, String region) {
    Credential credential = repository.findByAccessKey(accessKey);
    if (credential == null) {
        return false;                                  // unknown access key
    }
    return signatureUtils.verify(request, credential.getSecretKey(),
                                 dateStamp, region, signedHeaders, signature);
}
```

---

## 3. Authorization (permissions per user)

Authentication answers *“who are you?”*; authorization answers *“are you allowed to do this on this bucket/key?”*

### 3.1 Data model (SQLite tables)

| Table | Purpose |
|-------|---------|
| `credentials` | `access_key`, `secret_key`, `active` — the users |
| `permissions` | the kinds of operations: `read`, `write`, `delete`, `admin` |
| `authorization` | a *pattern* per user, e.g. `*` (everything) or `pics/` (a prefix) |
| `authorization_permission` | many-to-many link between an authorization and its permissions |

The schema is auto-created in `AutoCreateTable` (`CREATE TABLE IF NOT EXISTS …`) and seeded with the default user `test/test` and `* -> read,write,delete`.

### 3.2 The query — `AuthoretiesRepository.getAuthoritiesByUser`

```java
SELECT c.access_key, a.pattern, p.authority
FROM authorization_permission ap
JOIN authorization a ON a.id = ap.authorization_id
JOIN permissions p    ON p.id = ap.permission_id
JOIN credentials c    ON c.id = a.user_id
WHERE c.access_key = ?
ORDER BY a.pattern
```

Rows are grouped into `Authorization` objects: one per pattern, each carrying its list of authorities:

```java
Authorization auth = map.computeIfAbsent(pattern, p -> { … });
auth.getAuthorities().add(rs.getString("authority"));
```

### 3.3 Decision — `AuthorizationService.isAuthorized`

```java
public boolean isAuthorized(String uri, String accessKey, String authority) {
    return repository.getAuthoritiesByUser(accessKey)
            .stream()
            .anyMatch(auth -> auth.getAuthorities().contains(authority)
                    && matches(auth.getPattern(), uri));
}

public boolean matches(String pattern, String uri) {
    if (pattern.endsWith("*")) {                       // "pics/*" → prefix match
        String prefix = pattern.substring(0, pattern.length() - 1);
        return uri.startsWith(prefix);
    }
    return uri.equals(pattern) || uri.startsWith(pattern + "/");
}
```

The filter derives the **authority** from the HTTP method and the **resource** from the URL path:

```java
String[] seg = uri.split("/");           // ["", "my-bucket", "video.mp4"]
String bucket = seg[1];
String key = FileUtils.extractKey(request, bucket);

String authority = switch (request.getMethod()) {
    case "GET", "HEAD"  -> "read";
    case "PUT", "POST"  -> "write";
    case "DELETE"       -> "delete";
    default             -> "";
};
String resource = bucket + (key.isEmpty() ? "" : "/" + key);

if (!authorizationService.isAuthorized(resource, accessKey, authority)) {
    reject(response, "AccessDenied", "Not authorized for this operation on " + key);
    return;
}
```

### 3.4 Where accounts come from — `AdminAPI` `AdminInitializer`

The admin project seeds the shared database at startup: it creates the four permission rows, an `admin/admin-secret` user if missing, an `authorization` with pattern `*`, and links all four permissions to it.

```java
Permissions read   = createPermissionIfNotExists("read");
Permissions write  = createPermissionIfNotExists("write");
Permissions delete = createPermissionIfNotExists("delete");
Permissions admin  = createPermissionIfNotExists("admin");
// … create "admin" user, create "*" authorization, link all 4 permissions
```

---

## 4. Storage layout on disk

```
data/
├─ buckets/                ← completed objects
│  └─ <bucket>/
│     └─ objects/
│        └─ <key>/
│           ├─ <versionId>      ← one file per version (UUID)
│           └─ …                ← another version file
└─ multipart/               ← in-progress multipart uploads
   └─ <bucket>/
      └─ <key>/
         └─ <uploadId>/
            ├─ part-1          ← raw bytes of part 1
            ├─ part-1.etag     ← stored MD5 hex of part 1
            ├─ part-2
            ├─ part-2.etag
            └─ …
```

Published objects are stored once per **version id** (a fresh UUID per write), and `versions` metadata in SQLite picks the latest one.

---

## 5. Uploading objects

### 5.1 Simple upload (`PUT /{bucket}/**`)

`FileOperationService.UploadFileInOneShot` reads the request body (raw or `aws-chunked`) straight to a new version file:

```java
Path versionFile = objectDir.resolve(versionId);
InputStream in = ("aws-chunked".equalsIgnoreCase(request.getHeader("Content-Encoding")))
        ? new AwsChunkedInputStream(request.getInputStream())
        : request.getInputStream();

try (var out = Files.newOutputStream(versionFile,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
    }
}
```

### 5.2 Multipart upload (large files) — 4 steps

S3 multipart is how big files are uploaded in chunks; each part is a separate signed request, so a dropped connection only costs you **one** part:

| Step | Method & query | Controller → Service | What happens |
|------|----------------|----------------------|--------------|
| Start | `POST /{bucket}/**?uploads` | `startMultipart` → `startMultipart()` | returns an XML `UploadId`; creates the parts folder |
| Upload part | `PUT /{bucket}/**?partNumber=N&uploadId=U` | `uploadPart` → `uploadPart()` | stores `part-N` + `part-N.etag`, returns an MD5 `ETag` |
| Complete | `POST /{bucket}/**?uploadId=U` | `completeMultipart` → `completeUpload()` | verifies every `ETag`, merges parts in order into a version file |
| Abort | `DELETE /{bucket}/**?uploadId=U` | `abortMultipart` → `AbortMultipartFileUpload()` | deletes the parts folder |

`completeUpload` merges only the parts listed in the CompleteMultipartUpload XML, sorted by part number, **validating each ETag against the stored `.etag` file first**:

```java
Map<Integer, String> parts = FileUtils.extractPartsFromXML(xml);
try (var out = Files.newOutputStream(finalFile)) {
    parts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String storedETag = Files.readString(etagPath).trim();
                if (!storedETag.equals(clientETag)) {
                    throw new RuntimeException("ETag mismatch for part " + partNumber);
                }
                try (InputStream in = Files.newInputStream(partPath)) {
                    in.transferTo(out);                    // append this part
                }
            });
}
```

### 5.3 `aws-chunked` bodies — `AwsChunkedInputStream`

The AWS SDK signs streaming bodies with `Content-Encoding: aws-chunked`. The body is a series of:

```
<hex-size>[;chunk-signature=<sig>]\r\n <payload bytes> \r\n …
0\r\n\r\n
```

`AwsChunkedInputStream` parses each chunk header (`readNextChunkHeader`), hands out the payload bytes, swallows the trailing `\r\n`, and signals EOF on the `0` chunk:

```java
private void readNextChunkHeader() throws IOException {
    String header = readLine();            // e.g. "10000;chunk-signature=abc123"
    String hexSize = header.split(";")[0];
    int size = Integer.parseInt(hexSize.trim(), 16);
    if (size == 0) { finished = true; readLine(); return; }   // last chunk
    remainingChunkBytes = size;
}
```

### 5.4 Resumable upload — “skip parts that already exist”

Each part is stored in its own folder alongside its ETag. If the network drops mid-upload, the **client simply re-sends the missing `partNumber`**. Because every part lives in its own file, the server can also short-circuit a request for a part it already has — it returns the stored ETag instead of writing again:

```java
Path partPath  = dir.resolve("part-" + partNumber);
Path etagPath  = dir.resolve("part-" + partNumber + ".etag");

if (Files.exists(partPath) && Files.exists(etagPath)) {
    String existingEtag = Files.readString(etagPath).trim();
    log.info("Part " + partNumber + " already exists → skip re-upload (ETag: " + existingEtag + ")");

    try (InputStream skip = request.getInputStream()) {
        skip.transferTo(java.io.OutputStream.nullOutputStream());  // drain body, keep conn clean
    }
    return existingEtag;                 // return the OLD etag, do not touch the file
}
```

What this block does, step by step:
1. Builds the paths for the part file and its `.etag` marker.
2. Checks **both** exist — the `.etag` file is only written *after* a full successful part write, so a half-written part (crash mid-stream) is treated as *not uploaded* and will be retried.
3. Drains the incoming body to `nullOutputStream()` so Tomcat doesn’t complain about an unread body and the keep-alive connection stays usable.
4. Returns the previously stored ETag — the client sees a successful `200` with the old ETag and completes normally.

That gives you exactly the "continue from the last part" behaviour: parts are idempotent, and re-sending any of them is harmless and fast.

---

## 6. Downloading objects

### 6.1 Full download (`GET /{bucket}/**`)

```java
String versionId = request.getParameter("versionId");     // optional: explicit version
if (versionId == null) {
    versionId = versionRepository.getLatest(bucket, key); // otherwise latest
}
Path path = objectDir.resolve(versionId);
if (!Files.exists(path)) {
    return ResponseEntity.notFound().build();
}

long fileSize = Files.size(path);
String range = request.getHeader("Range");
```

If no `Range` header → stream the whole file with its stored content-type:

```java
Map<String, Object> meta = metadataRepository.getMetadata(bucket, key, versionId);
return ResponseEntity.ok()
        .header("Content-Type", (String) meta.get("content_type"))
        .header("Content-Length", String.valueOf(fileSize))
        .body(new InputStreamResource(in));
```

### 6.2 Range / resume downloads (`GET /{bucket}/**` + `Range: bytes=from-to`)

This is the **resume-after-cut** feature for downloads. The client asks for the byte offset where it stopped:

```java
range = range.replace("bytes=", "").trim();
String[] parts = range.split("-");

long start = Long.parseLong(parts[0]);
long end = (parts.length > 1 && !parts[1].isEmpty())
        ? Long.parseLong(parts[1])        // bytes=0-1023
        : fileSize - 1;                   // bytes=1024-  → to the end

long contentLength = end - start + 1;

RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
raf.seek(start);                          // ← jump straight to the byte we stopped at
```

A byte-limited `InputStream` wraps the `RandomAccessFile` so exactly `contentLength` bytes are served, then the response says **206 Partial Content**:

```java
return ResponseEntity.status(206)
        .header("Accept-Ranges", "bytes")
        .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
        .header("Content-Length", String.valueOf(contentLength))
        .body(new InputStreamResource(limitedStream));
```

So a client that lost the connection can resume by remembering the byte count it wrote and asking again with `Range: bytes=<count>-`; the server seeks to that byte with `raf.seek(start)` and continues from exactly there.

---

## 7. Project tree

```
Object3/                                   ← git repository root
├─ .git/  .gitignore  HELP.md  README.md  .idea/
├─ data.db                                ← SHARED SQLite db (both projects)
│
├─ ObjectStoreDB/                         ← the S3-compatible server
│  ├─ pom.xml                             ← Spring Boot 4.0.5, java 17, sqlite-jdbc
│  ├─ mvnw / mvnw.cmd
│  ├─ data/
│  │  ├─ buckets/<bucket>/objects/<key>/<versionId>    ← object files on disk
│  │  └─ multipart/<bucket>/<key>/<uploadId>/…         ← in-progress parts
│  ├─ app.log  app-err.log  (runtime logs)
│  └─ src/main/java/org/shalash/objectstoredb/
│     ├─ ObjectStoreDbApplication.java    ← Spring Boot entry point (@SpringBootApplication)
│     ├─ controller/
│     │  └─ FileOperationController.java  ← REST endpoints (bucket/**): upload, multipart,
│     │                                       download, head, delete
│     ├─ service/
│     │  ├─ FileOperationService.java      ← all object I/O (one-shot, parts, merge, download,
│     │  │                                    delete, headers, resume logic)
│     │  ├─ AuthenticationService.java     ← access-key → secret-key → SignatureUtils.verify
│     │  └─ AuthorizationService.java      ← pattern + authority decision
│     ├─ repository/
│     │  ├─ CredentialRepository.java     ← SQL: credentials (findByAccessKey, getSecretKey)
│     │  ├─ AuthoretiesRepository.java    ← SQL: authorization join → per-user authorities
│     │  ├─ VersionRepository.java        ← SQL: versions (add/getLatest/list/delete)
│     │  └─ MetadataRepository.java       ← SQL: objects metadata (add/getMetadata)
│     ├─ dto/
│     │  ├─ Credential.java               ← Lombok DTO (id, accessKey, secretKey)
│     │  └─ Authorization.java            ← Lombok DTO (accessKey, pattern, authorities)
│     ├─ config/
│     │  ├─ AuthenticationFilter.java     ← OncePerRequestFilter: authn + authz for every call
│     │  ├─ SQLiteConfig.java             ← DataSource bean: jdbc:sqlite:../data.db
│     │  ├─ AutoCreateTable.java          ← creates SQLite tables on startup, seeds test/test
│     │  ├─ TomcatConfig.java             ← Tomcat tuning (10-min timeouts, 2GB post, log filter)
│     │  └─ … (auth tests exist in src/test)
│     ├─ utils/
│     │  ├─ SignatureUtils.java           ← SigV4: canonical request, signing key, verify, skew
│     │  ├─ AwsChunkedInputStream.java    ← decodes aws-chunked HTTP bodies
│     │  └─ FileUtils.java                ← extractKey(); extractPartsFromXML(); bytesToHex()
│     └─ handeler/
│        └─ S3ErrorHandler.java           ← @RestControllerAdvice → XML InternalError, HTTP 500
│
└─ AdminAPI/                              ← admin companion (seeds users & permissions)
   ├─ pom.xml                             ← Spring Boot 4.1.0 + JPA + sqlite-jdbc
   ├─ mvnw / mvnw.cmd
   └─ src/main/java/org/shalash/adminapi/
      ├─ AdminApiApplication.java
      ├─ config/
      │  └─ AdminInitializer.java         ← seeds read/write/delete/admin + admin user
      ├─ controller/
      │  ├─ UserController.java           ← create/list users
      │  └─ AutherizationController.java  ← grant patterns/permissions
      ├─ entity/ repo/ dto/ …            ← Hibernate entities & repositories
      └─ resources/application.properties ← jdbc:sqlite:../data.db, server.port 8081
```

---

## 8. Function-by-function reference — `service` package

### `AuthenticationService`

| Function | What it does |
|----------|--------------|
| `authenticate(request, accessKey, signature, signedHeaders, dateStamp, region)` | Looks up the secret key for `accessKey` via `CredentialRepository.findByAccessKey` (returns `false` if unknown), then delegates the full signature check to `SignatureUtils.verify(request, secretKey, dateStamp, region, signedHeaders, signature)`. Represents **step 2 of the filter**: *“is the signature valid?”* |

### `AuthorizationService`

| Function | What it does |
|----------|--------------|
| `isAuthorized(uri, accessKey, authority)` | Loads all `(pattern, authorities)` rows for the user from `AuthoretiesRepository.getAuthoritiesByUser`, then returns `true` if **any** pattern both (a) contains the required authority and (b) matches the resource path. |
| `matches(pattern, uri)` | `*`-suffixed pattern → prefix match (`pics/*` matches `pics/…`); otherwise exact match or path-prefix match (`pics` matches `pics` and `pics/a.txt`). |

### `FileOperationService`

| Function | What it does |
|----------|--------------|
| `init()` | `@PostConstruct`; resolves `root` from the `${data}` property (folder `data/`). |
| `startMultipart(request, bucket)` | Creates `data/multipart/<bucket>/<key>/<uploadId>` (random UUID) and returns the S3 `InitiateMultipartUploadResult` XML with `<UploadId>`. |
| `uploadPart(request, bucket, partNumber, uploadId)` | Stores one part: reads the body (raw or `aws-chunked`), writes `part-<n>`, computes MD5 → `part-<n>.etag`. **Resume guard:** if `part-<n>` **and** `.etag` already exist it drains the body and returns the stored ETag instead of re-writing. Returns ETag (also sent as the `ETag` response header). |
| `completeUpload(request, bucket, xml)` | Parses the `CompleteMultipartUpload` XML, merges the listed parts **in part-number order** after verifying each ETag, writes the final object under a new `versionId`, records it in `versions` + `objects` (metadata), and returns the `CompleteMultipartUploadResult` XML + version id. |
| `AbortMultipartFileUpload(request, bucket)` | If the multipart folder exists, walks it depth-first and deletes every file/folder (aborts the upload). |
| `UploadFileInOneShot(request, bucket)` | Simple `PUT`: reads the whole body to `objects/<key>/<versionId>`, records version + metadata, returns the version id. |
| `downloadFile(request, bucket)` | Serves `GET`: picks the requested `versionId` (or latest), returns `200` with full content, or for a `Range` header builds a bounded stream over a `RandomAccessFile` seeked to `start` and answers `206` with `Accept-Ranges`/`Content-Range`. |
| `deleteFile(request, bucket)` | Soft-deletes: marks the version `deleted=1` in `versions` and physically removes the object file. |
| `getObjectHeaders(request, bucket)` | Used by `HEAD`: reads stored metadata (content-type, size, version id) for the requested/latest version. |

### Companion files the services depend on

- `SignatureUtils.verify` → `buildCanonicalRequest`, `matches`, `signingKey`, `withinSkew`, `canonicalQuery`, `canonicalUri`, `headerValue`, `normalize`, `hmac`, `sha256Hex` — see section 2.
- `FileUtils.extractKey` strips `/bucket/` from the URI; `extractPartsFromXML` parses the complete-multipart body into `Map<partNumber, etag>`; `bytesToHex` formats MD5/SHA digests.
- `AwsChunkedInputStream` decodes chunked bodies (`read()`, `readNextChunkHeader()`, `readLine()`).

---

## 9. Controller endpoint reference (`FileOperationController`, prefix `/`)

| Method | Path | Query      | Handler | Service call |
|--------|------|-----------|---------|--------------|
| `POST` | `/{bucket}/**` | `uploads` | `startMultipart` | `startMultipart` |
| `PUT` | `/{bucket}/**` | `partNumber`, `uploadId` | `uploadPart` | `uploadPart` |
| `POST` | `/{bucket}/**` | `uploadId` | `completeMultipart` | `completeUpload` |
| `DELETE` | `/{bucket}/**` | `uploadId` | `abortMultipart` | `AbortMultipartFileUpload` |
| `PUT` | `/{bucket}/**` | — | `upload` | `UploadFileInOneShot` |
| `GET` | `/{bucket}/**` | `versionId` optional | `download` | `downloadFile` |
| `DELETE` | `/{bucket}/**` | `versionId` optional | `delete` | `deleteFile` |
| `HEAD` | `/{bucket}/**` | `versionId` optional | `head` | `getObjectHeaders` |

Versioning: every write creates a new version id; `GET`/`HEAD`/`DELETE` without `?versionId=` operate on the **latest** version (see `VersionRepository.getLatest`).

---

## 10. Configuration summary

| Concern | Where | Value |
|---------|-------|-------|
| Server port | `application.properties` | (default / configured in `server.port`, AdminAPI uses `8081`) |
| Object storage root | `application.properties` `data=` | `data/` |
| Database | `SQLiteConfig.dataSource()` | `jdbc:sqlite:../data.db` (shared, relative to working dir) |
| Tomcat | `TomcatConfig` | 10-minute timeouts, 2 GB max POST, unlimited swallow |
| Multipart limits | `application.properties` | `max-file-size`/`max-request-size` = 2 GB |
| Default user | `AutoCreateTable` | `access_key=test`, `secret_key=test`, pattern `*`, read/write/delete |

---

## 11. Testing it with the AWS Java SDK v2

Point the client at the server and disable/keep chunking to see both code paths:

```java
S3Client client = S3Client.builder()
        .endpointOverride(URI.create("http://localhost:8080"))
        .forcePathStyle(true)
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
        .build();

// one-shot PUT
client.putObject(PutObjectRequest.builder().bucket("my-bucket").key("video.mp4").build(),
                 RequestBody.fromFile(Paths.get("D:/file.mp4")));

// resume download: bytes 0..99 then 100..end
client.getObject(GetObjectRequest.builder().bucket("my-bucket").key("video.mp4")
        .range("bytes=100-").build(), Paths.get("D:/downloaded.mp4"));
```

---

## 12. Known behaviours & caveats

- `../data.db` is **relative to the working directory** — always start each app from its own project folder (or set a working directory in your IDE), otherwise SQLite answers `SQLITE_CANTOPEN`.
- Simple uploads are not byte-resumable; use **multipart** for big files (that is where per-part resume lives).
- `completeUpload`’s stored metadata uses `request.getContentLengthLong()` of the *completion* request, not the object size.
- `SignatureUtils` compares signatures in constant time (`MessageDigest.isEqual`) and enforces a 15-minute clock skew.