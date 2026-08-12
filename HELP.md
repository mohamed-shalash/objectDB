# Object3 — Usage Guide

Practical usage examples for both modules:

- **ObjectStoreDB** — via the official **AWS SDK v2 (Java)**
- **AdminAPI** — via **curl**

---

## 1. ObjectStoreDB — AWS SDK v2 (Java)

### 1.1 Maven Dependency

Add the AWS SDK S3 dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
</dependency>
```

### 1.2 Prerequisites

- ObjectStoreDB is running on `http://localhost:8080`
- You have a valid `Access Key` / `Secret Key` (default: `test` / `test`, or a user created via AdminAPI)
- The bucket `my-bucket` is authorized for your key (default user has `*` access)

### 1.3 Client Setup

The client must point to the local endpoint with **path-style** addressing:

```java
S3Client client = S3Client.builder()
        .endpointOverride(URI.create("http://localhost:8080"))
        .forcePathStyle(true)
        .region(Region.US_EAST_1)
        .credentialsProvider(
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                )
        )
        .build();
```

> **Important:** by default the SDK sends an `aws-chunked` content encoding. If you are not using chunked mode, disable it via `S3Configuration`:

```java
.serviceConfiguration(
        software.amazon.awssdk.services.s3.S3Configuration.builder()
                .chunkedEncodingEnabled(false)
                .build()
)
```

> **Note:** make sure the Access Key / Secret Key you pass here are exactly the ones the server knows. If you set up the client with one credential (e.g. `test`) but the server has a different user configured, the signature will be rejected.

---

### 1.4 Single-Shot Upload (small file)

```java
Path file = Paths.get("D:/file.mp4");

PutObjectResponse res = client.putObject(
        PutObjectRequest.builder()
                .bucket("my-bucket")
                .key("video.mp4")
                .build(),
        RequestBody.fromFile(file)
);

String versionId = res.versionId();
System.out.println("VersionId = " + versionId);
```

Every upload creates a new version, returned as `versionId`.

### 1.5 Upload with aws-chunked encoding

```java
S3Client client = S3Client.builder()
        .endpointOverride(URI.create("http://localhost:8080"))
        .forcePathStyle(true)
        .region(Region.US_EAST_1)
        .credentialsProvider(
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("pop", "test")
                )
        )
        .build();

Path file = Paths.get("D:/file.mp4");

PutObjectResponse res = client.putObject(
        PutObjectRequest.builder()
                .bucket("my-bucket")
                .key("video.mp4")
                .build(),
        RequestBody.fromFile(file)
);

String versionId = res.versionId();
System.out.println("VersionId = " + versionId);
```

### 1.6 Inspect Object Metadata (HEAD)

```java
HeadObjectResponse res2 = client.headObject(
        HeadObjectRequest.builder()
                .bucket("my-bucket")
                .key("video.mp4")
                .build()
);
System.out.println("Content-Type = " + res2.contentType());
System.out.println("Content-Length = " + res2.contentLength());
System.out.println("ETag = " + res2.eTag());
System.out.println("VersionId = " + res2.versionId());
```

### 1.7 Download a Specific Version

```java
GetObjectResponse res = client.getObject(
        GetObjectRequest.builder()
                .bucket("my-bucket")
                .key("video.mp4")
                .versionId(versionId)
                .build(),
        Paths.get("D:/downloaded.mp4")
);
System.out.println("Content-Type = " + res.contentType());
System.out.println("Content-Length = " + res.contentLength());
System.out.println("ETag = " + res.eTag());
System.out.println("VersionId = " + res.versionId());
```

### 1.8 Delete a Specific Version

```java
client.deleteObject(
        DeleteObjectRequest.builder()
                .bucket("my-bucket")
                .key("video.mp4")
                .versionId(versionId)
                .build()
);
```

---

### 1.9 Multipart Upload (large files)

Flow: **Create Multipart → Upload Parts → Complete Multipart**.

```java
String bucket = "my-bucket";
String key = "video.mp4";
Path file = Paths.get("D:/file.mp4");
String versionId = null;

try {

    // 1. Start
    var createRes = client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()
    );
    String uploadId = createRes.uploadId();
    System.out.println("UploadId = " + uploadId);

    // 2. Upload Parts
    long partSize = 5 * 1024 * 1024; // 5MB
    List<CompletedPart> completedParts = new ArrayList<>();

    try (var in = Files.newInputStream(file)) {
        byte[] buffer = new byte[(int) partSize];
        int partNumber = 1;
        int bytesRead;

        while ((bytesRead = in.read(buffer)) != -1) {
            byte[] actual = java.util.Arrays.copyOf(buffer, bytesRead);

            var uploadPartRes = client.uploadPart(
                    UploadPartRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) bytesRead)
                            .build(),
                    RequestBody.fromBytes(actual)
            );
            System.out.println("Uploaded part " + partNumber);

            completedParts.add(
                    CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(uploadPartRes.eTag())
                            .build()
            );
            partNumber++;
        }
    }

    // 3. Complete
    CompleteMultipartUploadResponse res = client.completeMultipartUpload(
            CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(
                            CompletedMultipartUpload.builder()
                                    .parts(completedParts)
                                    .build()
                    )
                    .build()
    );
    versionId = res.versionId();
    System.out.println("Upload completed successfully!");

} catch (Exception e) {
    e.printStackTrace();
} finally {
    client.close();
}

System.out.println("VersionId = " + versionId);
```

### 1.10 Multipart Upload with aws-chunked

Identical flow — the difference is only in the **client setup**: omit the `chunkedEncodingEnabled(false)` override so the SDK uses `aws-chunked` encoding, and make sure the credential you pass matches the configured user on the server:

```java
S3Client client = S3Client.builder()
        .endpointOverride(URI.create("http://localhost:8080"))
        .forcePathStyle(true)
        .region(Region.US_EAST_1)
        .credentialsProvider(
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                )
        )
        .build();
```

Then run the exact same multipart steps (1 → 2 → 3) from section 1.9.

---

## 2. AdminAPI — curl

### 2.1 Authentication

Every endpoint (except `/user/generateHmac`) requires this header:

```
Authorization: <accessKey>,<timestamp>,<hmac>
```

- `<timestamp>` — Unix time in milliseconds
- `<hmac>` — `HMAC-SHA256(secretKey, method + ":" + timestamp)`, hex encoded

**Step 1 — generate the HMAC** (this endpoint is unauthenticated):

```bash
curl --location 'http://localhost:8081/user/generateHmac' \
--header 'Content-Type: application/json' \
--data '{"accessKey":"admin","method":"GET"}'
```

Response:

```json
{
  "timestamp": "1786270122824",
  "hmac": "dc74c4248b2f408ca65da9193926de27537c70352fac52d79cf8ed90d6315336"
}
```

**Step 2 — use the values in the Authorization header:**

```bash
curl --location 'http://localhost:8081/user/admin' \
--header 'Authorization: admin,1786270122824,dc74c4248b2f408ca65da9193926de27537c70352fac52d79cf8ed90d6315336'
```

> The timestamp expires after **5 minutes** — generate a fresh HMAC for each new request.

---

### 2.2 Endpoint Examples

#### Get user info

```bash
curl --location 'http://localhost:8081/user/{accessKey}' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>'
```

#### Create a user

```bash
curl --location 'http://localhost:8081/user' \
--header 'Content-Type: application/json' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>' \
--data '{"accessKey":"dev-user"}'
```

Response returns the new user's `secretKey` — store it securely.

#### Rotate a user's secret key

```bash
curl --location --request PUT 'http://localhost:8081/user' \
--header 'Content-Type: application/json' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>' \
--data '{"accessKey":"dev-user"}'
```

#### Delete a user

```bash
curl --location --request DELETE 'http://localhost:8081/user/{accessKey}' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>'
```

#### Grant permissions

```bash
curl --location 'http://localhost:8081/permissions' \
--header 'Content-Type: application/json' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>' \
--data '{"accessKey":"dev-user","pattern":"*","permissions":["read","write"]}'
```

#### Revoke a permission

```bash
curl --location --request DELETE 'http://localhost:8081/permissions?accessKey=dev-user&pattern=*&permission=write' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>'
```

#### Add bucket authorization

```bash
curl --location 'http://localhost:8081/autherization' \
--header 'Content-Type: application/json' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>' \
--data '{"accessKey":"dev-user","pattern":"my-bucket/*"}'
```

#### Remove bucket authorization

```bash
curl --location --request DELETE 'http://localhost:8081/autherization?accessKey=dev-user&pattern=my-bucket/*' \
--header 'Authorization: <accessKey>,<timestamp>,<hmac>'
```
