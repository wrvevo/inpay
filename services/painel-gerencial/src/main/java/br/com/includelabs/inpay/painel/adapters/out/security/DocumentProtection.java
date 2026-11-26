package br.com.includelabs.inpay.painel.adapters.out.security;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Encrypts file contents before storage. An actual scanner is mandatory outside the synthetic local
 * fixture. Keys and retention policy are deployment configuration, never browser data.
 */
@Component
public class DocumentProtection {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final byte[] FIXTURE =
            "%PDF-1.4\n% INPAY-SYNTHETIC-DOCUMENT\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private final SecureRandom random = new SecureRandom();
    private final String key;
    private final String keyId;
    private final String scanner;
    private final int scannerPort;
    private final int retentionDays;
    private final boolean local;
    private final boolean fixtureEnabled;

    public DocumentProtection(
            Environment environment,
            @Value("${INPAY_DOCUMENT_KEY:}") String key,
            @Value("${INPAY_DOCUMENT_KEY_ID:}") String keyId,
            @Value("${INPAY_DOCUMENT_SCANNER_HOST:}") String scanner,
            @Value("${INPAY_DOCUMENT_SCANNER_PORT:3310}") int scannerPort,
            @Value("${INPAY_DOCUMENT_RETENTION_DAYS:0}") int retentionDays,
            @Value("${INPAY_DOCUMENT_SYNTHETIC_FIXTURE:false}") boolean fixtureEnabled) {
        this.local = environment.matchesProfiles("local");
        this.key = key;
        this.keyId = keyId;
        this.scanner = scanner;
        this.scannerPort = scannerPort;
        this.retentionDays = retentionDays;
        this.fixtureEnabled = fixtureEnabled;
    }

    public record Protected(
            String mime,
            String sha256,
            String keyId,
            byte[] nonce,
            byte[] ciphertext,
            String scanSource,
            Instant expiresAt) {}

    public Protected protect(UUID tenant, UUID id, String kind, byte[] data) {
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Document size invalid");
        }
        String mime = mime(data);
        String source = scan(data);
        if (retentionDays < 1 || retentionDays > 3650 || !keyId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalStateException("Document retention or key policy unavailable");
        }
        try {
            String hash =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, tenant, id, kind, hash, nonce, data);
            return new Protected(
                    mime,
                    hash,
                    keyId,
                    nonce,
                    encrypted,
                    source,
                    Instant.now().plus(retentionDays, ChronoUnit.DAYS));
        } catch (Exception failure) {
            throw new IllegalStateException("Document encryption unavailable");
        }
    }

    public byte[] decrypt(
            UUID tenant,
            UUID id,
            String kind,
            String hash,
            String storedKeyId,
            byte[] nonce,
            byte[] ciphertext) {
        if (!keyId.equals(storedKeyId)) throw new IllegalStateException("Document key unavailable");
        try {
            return crypt(Cipher.DECRYPT_MODE, tenant, id, kind, hash, nonce, ciphertext);
        } catch (Exception failure) {
            throw new IllegalStateException("Document integrity verification failed");
        }
    }

    private byte[] crypt(
            int mode, UUID tenant, UUID id, String kind, String hash, byte[] nonce, byte[] bytes)
            throws Exception {
        byte[] material = Base64.getDecoder().decode(key);
        try {
            if (material.length != 32) throw new IllegalStateException("AES-256 key required");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(material, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(
                    (tenant + "|" + id + "|" + kind + "|" + hash + "|" + keyId)
                            .getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(bytes);
        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    private String scan(byte[] bytes) {
        if (local && fixtureEnabled && Arrays.equals(bytes, FIXTURE)) return "SYNTHETIC_FIXTURE";
        if (scanner.isBlank()) throw new IllegalStateException("Malware scanner unavailable");
        if (local && fixtureEnabled) {
            throw new IllegalArgumentException("Only the synthetic fixture is allowed in this lab");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(scanner, scannerPort), 2000);
            socket.setSoTimeout(10000);
            var output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            output.writeInt(bytes.length);
            output.write(bytes);
            output.writeInt(0);
            output.flush();
            var input = socket.getInputStream();
            var result = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < 4096; i++) {
                int b = input.read();
                if (b == 0 || b == -1) break;
                result.write(b);
            }
            String verdict = result.toString(StandardCharsets.US_ASCII);
            if ("stream: OK".equals(verdict.trim())) return "CLAMAV";
            throw new IllegalArgumentException("Document did not pass malware scanning");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Malware scanner unavailable");
        }
    }

    private static String mime(byte[] bytes) {
        if (starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (starts(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        if (starts(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
            return "image/png";
        throw new IllegalArgumentException("Unsupported document type");
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }
}
