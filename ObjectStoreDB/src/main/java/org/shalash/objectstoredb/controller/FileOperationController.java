package org.shalash.objectstoredb.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.shalash.objectstoredb.repository.VersionRepository;
import org.shalash.objectstoredb.service.FileOperationService;
import org.shalash.objectstoredb.utils.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@Slf4j
public class FileOperationController {

    @Autowired
    FileOperationService fileOperationService;


    //  Upload big file
    //  1. Start Multipart
    @PostMapping(value = "/{bucket}/**", params = "uploads")
    public ResponseEntity<String> startMultipart(HttpServletRequest request,
                                                 @PathVariable String bucket) {
        String xml = fileOperationService.startMultipart(request, bucket);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    // 2. Upload Part (multipart)
    @PutMapping(value = "/{bucket}/**", params = {"partNumber", "uploadId"})
    public ResponseEntity<?> uploadPart(HttpServletRequest request,
                                        @PathVariable String bucket,
                                        @RequestParam("partNumber") int partNumber,
                                        @RequestParam("uploadId") String uploadId) throws IOException, NoSuchAlgorithmException {

        String etag= fileOperationService.uploadPart(request, bucket, partNumber, uploadId);

        return ResponseEntity.ok()
                .header("ETag", "\"" + etag + "\"")
                .build();
    }


    //  3. Complete Multipart upload
    @PostMapping(value = "/{bucket}/**", params = "uploadId")
    public ResponseEntity<?> completeMultipart(HttpServletRequest request,
                                               @PathVariable String bucket,
                                               @RequestBody String xml) throws IOException {

        String[] resultXml =fileOperationService.completeUpload(request, bucket, xml);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header("x-amz-version-id", resultXml[1])
                .body(resultXml[0]);
    }

    // 4. Abort Multipart upload
    @DeleteMapping(value = "/{bucket}/**", params = "uploadId")
    public ResponseEntity<?> abortMultipart(HttpServletRequest request,
                                            @PathVariable String bucket) throws IOException {

        fileOperationService.AbortMultipartFileUpload(request, bucket);

        return ResponseEntity.ok().build();
    }

    //  Upload small file
    @PutMapping(value = "/{bucket}/**")//, params = {"!partNumber", "!uploadId"})
    public ResponseEntity<?> upload(HttpServletRequest request,
                                    @PathVariable String bucket) throws IOException {

        String versionId = null;

            versionId = fileOperationService.UploadFileInOneShot(request, bucket);


        return ResponseEntity.ok()
                .header("x-amz-version-id", versionId)
                .build();
    }

    // Download
    @GetMapping("/{bucket}/**")
    public ResponseEntity<?> download(HttpServletRequest request,
                                      @PathVariable String bucket) throws IOException {
        return fileOperationService.downloadFile(request, bucket);
    }

    //  Delete
    @DeleteMapping("/{bucket}/**")
    public ResponseEntity<?> delete(HttpServletRequest request,
                                    @PathVariable String bucket) throws IOException {

        return fileOperationService.deleteFile(request, bucket);

    }


    @RequestMapping(value = "/{bucket}/**", method = RequestMethod.HEAD)
    public ResponseEntity<?> head(HttpServletRequest request,
                                  @PathVariable String bucket) {

        Map<String, Object> meta = fileOperationService.getObjectHeaders(request, bucket);
        for (Map.Entry<String, Object> entry : meta.entrySet()) {
            log.info(entry.getKey() + ": " + entry.getValue());
        }
        return ResponseEntity.ok()
                .header("Content-Type", (String) meta.get("content_type"))
                .header("Content-Length", String.valueOf(meta.get("size")))
                .header("x-amz-version-id", String.valueOf(meta.get("version_id")))
                .build();
    }


}