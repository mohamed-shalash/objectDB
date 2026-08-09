package org.shalash.objectstoredb.service;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.shalash.objectstoredb.repository.MetadataRepository;
import org.shalash.objectstoredb.repository.VersionRepository;
import org.shalash.objectstoredb.utils.AwsChunkedInputStream;
import org.shalash.objectstoredb.utils.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FileOperationService {

    @Autowired
    VersionRepository versionRepository;
    @Autowired
    MetadataRepository metadataRepository;

    //private final Path root = Paths.get({{data}});

    @Value("${data}")
    private String dataPath;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(dataPath);
    }

    public String startMultipart(HttpServletRequest request, String bucket) {

        String key = FileUtils.extractKey(request, bucket);
        String uploadId = UUID.randomUUID().toString();

        Path dir = root.resolve("multipart")
                .resolve(bucket)
                .resolve(key)
                .resolve(uploadId);

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String xml = """  
            <InitiateMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">                <Bucket>%s</Bucket>                <Key>%s</Key>                <UploadId>%s</UploadId>            </InitiateMultipartUploadResult>            """.formatted(bucket, key, uploadId);

        return xml;
    }

    public String uploadPart(HttpServletRequest request, String bucket, int partNumber, String uploadId) throws IOException, NoSuchAlgorithmException {
        String key = FileUtils.extractKey(request, bucket);

        log.info("✅ uploadPart → Part: " + partNumber + " | UploadId: " + uploadId + " | Key: " + key);

        Path dir = root.resolve("multipart")
                .resolve(bucket)
                .resolve(key)
                .resolve(uploadId);

        Files.createDirectories(dir);

        Path partPath = dir.resolve("part-" + partNumber);

        MessageDigest md = MessageDigest.getInstance("MD5");

        InputStream in;

        String encoding = request.getHeader("Content-Encoding");

        if ("aws-chunked".equalsIgnoreCase(encoding)) {

            in = new AwsChunkedInputStream(
                    request.getInputStream()
            );

        } else {

            in = request.getInputStream();
        }
        try (
             var out = Files.newOutputStream(partPath)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                md.update(buffer, 0, read);
            }
        }finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }

        String etag = FileUtils.bytesToHex(md.digest());

        log.info("✅ Part " + partNumber + " uploaded | ETag: " + etag);

        Path etagFile = dir.resolve("part-" + partNumber + ".etag");
        Files.writeString(etagFile, etag);

        return etag;
    }

    public String[] completeUpload(HttpServletRequest request, String bucket, String xml) throws IOException {
        log.info("✅ completeMultipart called");

        String key = FileUtils.extractKey(request, bucket);
        String uploadId = request.getParameter("uploadId");

        Path dir = root.resolve("multipart")
                .resolve(bucket)
                .resolve(key)
                .resolve(uploadId);

        Path path = root.resolve("buckets")
                .resolve(bucket)
                .resolve("objects")
                .resolve(key);

        String versionId = UUID.randomUUID().toString();
        Path finalFile = path.resolve(versionId);

        Files.createDirectories(finalFile.getParent());

        Map<Integer, String> parts = FileUtils.extractPartsFromXML(xml);

        try (var out = Files.newOutputStream(finalFile)) {
            parts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        int partNumber = entry.getKey();
                        //String clientETag = entry.getValue();
                        String clientETag = entry.getValue().replace("\"", "").trim();

                        Path partPath = dir.resolve("part-" + partNumber);
                        Path etagPath = dir.resolve("part-" + partNumber + ".etag");


                        try {
                            // String storedETag = Files.readString(etagPath).trim();


                            String storedETag = Files.readString(etagPath).trim();

                            if (!storedETag.equals(clientETag)) {
                                throw new RuntimeException("ETag mismatch for part " + partNumber);
                            }

                            try (InputStream in = Files.newInputStream(partPath)) {
                                in.transferTo(out);
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });


        }


        versionRepository.addVersion(bucket, key, versionId);
        String contentType = request.getContentType();
        long size = request.getContentLengthLong();
        metadataRepository.addMetadata(
                bucket,
                key,
                versionId,
                size,
                contentType != null ? contentType : "application/octet-stream"
        );

        String resultXml = """  
            <CompleteMultipartUploadResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">                <Location>http://localhost:8080/%s/%s</Location>                <Bucket>%s</Bucket>                <Key>%s</Key>                <ETag>"dummy-etag-for-now"</ETag>            </CompleteMultipartUploadResult>            """.formatted(bucket, key, bucket, key);

        return new String[]{resultXml, versionId};

    }

    public void AbortMultipartFileUpload(HttpServletRequest request, String bucket) throws IOException {
        String key = FileUtils.extractKey(request, bucket);
        String uploadId = request.getParameter("uploadId");

        Path dir = root.resolve("multipart")
                .resolve(bucket)
                .resolve(key)
                .resolve(uploadId);

        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    public String UploadFileInOneShot(HttpServletRequest request, String bucket) throws IOException {
        String key = FileUtils.extractKey(request, bucket);

        String versionId = UUID.randomUUID().toString();

        Path objectDir = root.resolve("buckets")
                .resolve(bucket)
                .resolve("objects")
                .resolve(key);

        Files.createDirectories(objectDir);

        Path versionFile = objectDir.resolve(versionId);

        InputStream in;

        String encoding = request.getHeader("Content-Encoding");

        if ("aws-chunked".equalsIgnoreCase(encoding)) {

            in = new AwsChunkedInputStream(
                    request.getInputStream()
            );

        } else {

            in = request.getInputStream();
        }

        try (
             var out = Files.newOutputStream(versionFile,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {


            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }
        versionRepository.addVersion(bucket, key, versionId);

        String contentType = request.getContentType();
        long size = request.getContentLengthLong();

        System.out.println("INSERT VERSION: " + bucket + " | " + key + " | " + versionId);
        metadataRepository.addMetadata(
                bucket,
                key,
                versionId,
                size,
                contentType != null ? contentType : "application/octet-stream"
        );
        return versionId;
    }


    public ResponseEntity<?> downloadFile(HttpServletRequest request, String bucket) throws IOException {
        String key = FileUtils.extractKey(request, bucket);


        Path objectDir = root.resolve("buckets")
                .resolve(bucket)
                .resolve("objects")
                .resolve(key);

        String versionId = request.getParameter("versionId");
        log.info(versionId);

        if (versionId == null) {
            versionId = versionRepository.getLatest(bucket, key);
            log.info("read latest: " + versionId);
        }
        Path path = objectDir.resolve(versionId);

        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        long fileSize = Files.size(path);

        String range = request.getHeader("Range");

        if (range == null) {
            InputStream in = Files.newInputStream(path);
            try {

                Map<String, Object> meta =
                        metadataRepository.getMetadata(bucket, key, versionId);

                System.out.println(meta);

            } catch (Exception e) {

                e.printStackTrace();

                throw e;
            }
            Map<String, Object> meta =
                    metadataRepository.getMetadata(bucket, key, versionId);

            String contentType = (String) meta.get("content_type");

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Length", String.valueOf(fileSize))
                    .body(new InputStreamResource(in));
        }


        range = range.replace("bytes=", "").trim();
        String[] parts = range.split("-");

        long start = Long.parseLong(parts[0]);
        long end = (parts.length > 1 && !parts[1].isEmpty())
                ? Long.parseLong(parts[1])
                : fileSize - 1;

        long contentLength = end - start + 1;

        RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
        raf.seek(start);

        InputStream limitedStream = new InputStream() {

            long remaining = contentLength;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) return -1;

                int data = raf.read();
                if (data != -1) remaining--;

                return data;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) return -1;

                int toRead = (int) Math.min(len, remaining);
                int read = raf.read(b, off, toRead);

                if (read > 0) remaining -= read;

                return read;
            }

            @Override
            public void close() throws IOException {
                raf.close();
            }
        };
        return ResponseEntity.status(206)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                .header("Content-Length", String.valueOf(contentLength))
                .body(new InputStreamResource(limitedStream));
    }


    public ResponseEntity<Object> deleteFile(HttpServletRequest request, String bucket) throws IOException {
        String key = FileUtils.extractKey(request, bucket);
        Path objectDir = root.resolve("buckets")
                .resolve(bucket)
                .resolve("objects")
                .resolve(key);

        String versionId = request.getParameter("versionId");

        if (versionId == null) {
            versionId = versionRepository.getLatest(bucket, key);
            log.info(" Deleting latest version: " + versionId);
        }

        if (versionId == null) {
            return ResponseEntity.notFound().build();
        }

        Path fileToDelete = objectDir.resolve(versionId);

        boolean fileDeleted = Files.deleteIfExists(fileToDelete);

        versionRepository.deleteVersion(versionId);

        log.info("️ Deleted: " + key + " | Version: " + versionId + " | File deleted: " + fileDeleted);
        return ResponseEntity.ok().build();
    }



    public Map<String, Object> getObjectHeaders(HttpServletRequest request, String bucket) {
        String key = FileUtils.extractKey(request, bucket);
        String versionId = request.getParameter("versionId");

        if (versionId == null) {
            versionId = versionRepository.getLatest(bucket, key);
        }

        Map<String, Object> meta =
                metadataRepository.getMetadata(bucket, key, versionId);
        return meta;
    }
}
