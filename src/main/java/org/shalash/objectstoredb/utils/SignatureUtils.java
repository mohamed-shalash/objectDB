package org.shalash.objectstoredb.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Component
public class SignatureUtils {

    //todo add to cash or constants file
    private static final String SERVICE = "s3";
    private static final String TERMINATOR = "aws4_request";
    private static final long SKEW_MILLIS = 15 * 60 * 1000L;

    public boolean verify(HttpServletRequest request,
                          String secretKey,
                          String dateStamp,
                          String region,
                          String signedHeaders,
                          String providedSignature) {
        try {
            String amzDate = request.getHeader("x-amz-date");
            if (amzDate == null || !amzDate.startsWith(dateStamp) || !withinSkew(amzDate)) {
                return false;
            }

            byte[] signingKey = signingKey(secretKey, dateStamp, region);

            return matches(signingKey, buildCanonicalRequest(request, signedHeaders, false),
                    amzDate, dateStamp, region, providedSignature)
                    || matches(signingKey, buildCanonicalRequest(request, signedHeaders, true),
                    amzDate, dateStamp, region, providedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matches(byte[] signingKey, String canonicalRequest, String amzDate,
                            String dateStamp, String region, String provided) throws Exception {
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + dateStamp + "/" + region + "/" + SERVICE + "/" + TERMINATOR + "\n"
                + sha256Hex(canonicalRequest);
        byte[] signature = hmac(signingKey, stringToSign);

        /***
         * String calculatedSignature = HexFormat.of().formatHex(signature);
         *
         * return MessageDigest.isEqual(
         *     calculatedSignature.getBytes(StandardCharsets.UTF_8),
         *     provided.getBytes(StandardCharsets.UTF_8)
         * );
         */
        return MessageDigest.isEqual(
                HexFormat.of().formatHex(signature).getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    //I add this to confirm that request time is within 15 minutes of the server current time
    //todo add option for ignore this  or use it
    private boolean withinSkew(String amzDate) {
        try {
            long requestTime = LocalDateTime
                    .parse(amzDate, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
            return Math.abs(System.currentTimeMillis() - requestTime) <= SKEW_MILLIS;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildCanonicalRequest(HttpServletRequest request, String signedHeaders,
                                         boolean omitEmptyEquals) throws Exception {
        String method = request.getMethod();
        String canonicalUri = canonicalUri(request.getRequestURI());
        String canonicalQuery = canonicalQuery(request.getQueryString(), omitEmptyEquals);

        TreeMap<String, String> headers = new TreeMap<>();
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
                    .append('\n');
        }

        String payloadHash = request.getHeader("x-amz-content-sha256");
        if (payloadHash == null || payloadHash.isEmpty()) {
            payloadHash = ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method))
                    ? sha256Hex("")
                    : "UNSIGNED-PAYLOAD";
        }

        return method + '\n'
                + canonicalUri + '\n'
                + canonicalQuery + '\n'
                + canonicalHeaders + '\n'
                + String.join(";", headers.keySet()) + '\n'
                + payloadHash;
    }

    private String canonicalQuery(String queryString, boolean omitEmptyEquals) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        TreeMap<String, List<String>> params = new TreeMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            List<String> values = new ArrayList<>(entry.getValue());
            Collections.sort(values);
            for (String value : values) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                if (omitEmptyEquals && value.isEmpty()) {
                    sb.append(entry.getKey());
                } else {
                    sb.append(entry.getKey()).append('=').append(value);
                }
            }
        }
        return sb.toString();
    }

    private String canonicalUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        String normalized = uri;
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private String headerValue(HttpServletRequest request, String name) {
        if ("host".equals(name)) {
            String host = request.getHeader("Host");
            if (host != null) {
                return normalize(host);
            }
            String hostname = request.getServerName();
            int port = request.getServerPort();
            return port == 80 ? hostname : hostname + ":" + port;
        }
        String value = request.getHeader(name);
        return value == null ? "" : normalize(value);
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private byte[] signingKey(String secretKey, String dateStamp, String region) throws Exception {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, SERVICE);
        return hmac(kService, TERMINATOR);
    }

    private byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data.getBytes(StandardCharsets.UTF_8)));
    }
}
