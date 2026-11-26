package br.com.includelabs.inpay.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Token local HMAC para as APIs de laboratório. Fora do perfil local, o acesso é o JWT do Cognito.
 */
public final class LocalApiToken {
    private LocalApiToken() {}

    public record Claims(
            String subject, String role, String realm, long expiresAt, List<String> scopes) {}

    public static String issue(
            String secret,
            String subject,
            String role,
            String realm,
            List<String> scopes,
            long expiresAtEpochSeconds) {
        reject(subject);
        reject(role);
        reject(realm);
        String payload =
                b64(
                        ("{\"sub\":\""
                                        + subject
                                        + "\",\"role\":\""
                                        + role
                                        + "\",\"realm\":\""
                                        + realm
                                        + "\",\"exp\":"
                                        + expiresAtEpochSeconds
                                        + ",\"scopes\":\""
                                        + String.join(" ", scopes)
                                        + "\"}")
                                .getBytes(StandardCharsets.UTF_8));
        return payload + "." + b64(hmac(secret, payload));
    }

    public static Claims verify(String secret, String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2
                || !MessageDigest.isEqual(hmac(secret, parts[0]), b64decode(parts[1]))) {
            throw new IllegalArgumentException("Invalid token");
        }
        String json = new String(b64decode(parts[0]), StandardCharsets.UTF_8);
        long exp = Long.parseLong(field(json, "exp"));
        if (exp < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Expired token");
        }
        String scopes = field(json, "scopes");
        return new Claims(
                field(json, "sub"),
                field(json, "role"),
                field(json, "realm"),
                exp,
                scopes.isBlank() ? List.of() : Arrays.asList(scopes.split(" ")));
    }

    private static void reject(String value) {
        if (value == null
                || value.isBlank()
                || value.indexOf('"') >= 0
                || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid token claim");
        }
    }

    private static String field(String json, String name) {
        String key = "\"" + name + "\":";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid token");
        }
        start += key.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return json.substring(start, end);
    }

    private static byte[] hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Token unavailable");
        }
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] b64decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
