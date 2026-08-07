package org.shalash.objectstoredb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.shalash.objectstoredb.service.AuthenticationService;
import org.shalash.objectstoredb.service.AuthorizationService;
import org.shalash.objectstoredb.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(AuthenticationFilter.class);

    private final AuthenticationService authService;

    private final AuthorizationService authorizationService;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("AWS4-HMAC-SHA256")) {
            reject(response, "AccessDenied", "Missing or invalid Authorization header");
            return;
        }

        try {
            String credential = extract(header, "Credential=", ",");
            String signedHeaders = extract(header, "SignedHeaders=", ",");
            String signature = extract(header, "Signature=", null);

            String[] parts = credential.split("/");
            if (parts.length != 5
                    || !"s3".equals(parts[3])
                    || !"aws4_request".equals(parts[4])) {
                reject(response, "AccessDenied", "Invalid credential scope");
                return;
            }

            String accessKey = parts[0];
            String dateStamp = parts[1];
            String region = parts[2];

            boolean ok = authService.authenticate(
                    request,
                    accessKey,
                    signature,
                    signedHeaders,
                    dateStamp,
                    region);

            if (!ok) {
                reject(response, "SignatureDoesNotMatch", "Signature verification failed");
                return;
            }
            String uri = request.getRequestURI();
            String[] seg = uri.split("/");   // ["", "my-bucket", "video.mp4"]
            String bucket = seg[1];
            String key = FileUtils.extractKey(request, bucket);
            String authority = switch (request.getMethod()) {
                case "GET", "HEAD" -> "read";
                case "PUT", "POST" -> "write";
                case "DELETE" -> "delete";
                default -> "";
            };
            String resource = bucket + (key.isEmpty() ? "" : "/" + key);
            if (!authorizationService.isAuthorized(resource, accessKey, authority)) {
                reject(response, "AccessDenied", "Not authorized for this operation on " + key);
                log.error("Not authorized for this operation on {}", key);
                return;
            }

        } catch (Exception e) {
            log.error("Authentication failed", e);
            reject(response, "AccessDenied", "Authentication error");
            return;
        }

        chain.doFilter(request, response);
    }

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

    private String extract(String header, String key, String endDelimiter) {

        int start = header.indexOf(key);

        if (start == -1) {
            throw new IllegalArgumentException(key + " not found");
        }

        start += key.length();

        if (endDelimiter == null) {
            return header.substring(start).trim();
        }

        int end = header.indexOf(endDelimiter, start);

        if (end == -1) {
            return header.substring(start).trim();
        }

        return header.substring(start, end).trim();
    }
}
