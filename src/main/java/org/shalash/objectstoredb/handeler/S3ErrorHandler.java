package org.shalash.objectstoredb.handeler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class S3ErrorHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception e) {

        String xml = """
        <Error>
            <Code>InternalError</Code>
            <Message>%s</Message>
        </Error>
        """.formatted(e.getMessage());

        return ResponseEntity.status(500)
                .header("Content-Type", "application/xml")
                .body(xml);
    }
}