package org.shalash.adminapi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class Utils {

    public static String generateHmac(
            String secretKey,
            String method,
            long timestamp
    ) {
        try {
            String message = method + ":" + timestamp;

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKeyspec = new SecretKeySpec(
                    secretKey
                            .getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            mac.init(secretKeyspec);

            byte[] hash = mac.doFinal(
                    message.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }
}
