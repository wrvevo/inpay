package br.com.includelabs.inpay.itau.adapters.out.http;

import br.com.includelabs.inpay.itau.bootstrap.ItauSettings;
import br.com.includelabs.inpay.itau.domain.ItauOperation;
import br.com.includelabs.inpay.itau.domain.ItauReply;
import br.com.includelabs.inpay.itau.domain.port.ItauBankPort;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** OAuth client_credentials e mTLS. Sandbox do Itaú não exige certificado; produção exige. */
public final class ItauHttpAdapter implements ItauBankPort {
    private static final Logger LOG = LogManager.getLogger(ItauHttpAdapter.class);
    private static final Duration BUDGET = Duration.ofSeconds(20);

    private final ItauSettings settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private volatile String token = "";
    private volatile Instant tokenUntil = Instant.EPOCH;
    private int failures;

    public ItauHttpAdapter(ItauSettings settings) {
        this.settings = settings;
        this.client =
                HttpClient.newBuilder().connectTimeout(BUDGET).sslContext(ssl(settings)).build();
    }

    @Override
    public ItauReply send(
            ItauOperation operation, String path, Map<String, Object> body, boolean form) {
        if (failures >= 5) {
            LOG.warn("Itau circuit open operation={}", operation.name());
            return new ItauReply(
                    operation.name(), 503, true, false, Map.of("error", "CIRCUIT_OPEN"));
        }
        try {
            String url = settings.baseUrl(operation.product()).replaceAll("/+$", "") + path;
            String access = operation == ItauOperation.AUTH_TOKEN ? "" : accessToken();
            String payload =
                    form
                            ? String.valueOf(body.getOrDefault("form", ""))
                            : mapper.writeValueAsString(body);
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(BUDGET)
                            .header("Accept", "application/json")
                            .header(
                                    "Content-Type",
                                    form
                                            ? "application/x-www-form-urlencoded"
                                            : "application/json");
            if (!access.isBlank()) {
                request.header("Authorization", "Bearer " + access);
            }
            if (!settings.clientId().isBlank()) {
                request.header("x-itau-apikey", settings.clientId());
            }
            if (payload.isBlank() || "{}".equals(payload)) {
                request.method(operation.method(), HttpRequest.BodyPublishers.noBody());
            } else {
                request.method(operation.method(), HttpRequest.BodyPublishers.ofString(payload));
            }
            LOG.info(
                    "Itau external call started operation={} host={}",
                    operation.name(),
                    URI.create(url).getHost());
            HttpResponse<byte[]> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            boolean failure = response.statusCode() >= 500 || response.statusCode() == 429;
            failures = failure ? failures + 1 : 0;
            LOG.info(
                    "Itau external call finished operation={} status={}",
                    operation.name(),
                    response.statusCode());
            return new ItauReply(
                    operation.name(),
                    response.statusCode(),
                    failure,
                    false,
                    decode(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures++;
            LOG.error("Itau external call interrupted operation={}", operation.name(), exception);
            return new ItauReply(
                    operation.name(), 503, true, false, Map.of("error", "INTERRUPTED"));
        } catch (Exception exception) {
            failures++;
            LOG.error("Itau external call failed operation={}", operation.name(), exception);
            return new ItauReply(
                    operation.name(), 503, true, false, Map.of("error", "PROVIDER_UNAVAILABLE"));
        }
    }

    private String accessToken() throws Exception {
        if (tokenUntil.isAfter(Instant.now().plusSeconds(5)) && !token.isBlank()) {
            return token;
        }
        String form =
                "grant_type=client_credentials&client_id="
                        + URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8)
                        + "&client_secret="
                        + URLEncoder.encode(settings.clientSecret(), StandardCharsets.UTF_8);
        ItauReply reply = send(ItauOperation.AUTH_TOKEN, "", Map.of("form", form), true);
        Object value = reply.body().get("access_token");
        if (value == null) {
            throw new IllegalStateException("Itau authentication failed");
        }
        token = value.toString();
        tokenUntil = Instant.now().plusSeconds(240);
        return token;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Map.of();
        }
        try {
            Object value = mapper.readValue(bytes, Object.class);
            if (value instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("items", value);
        } catch (Exception exception) {
            LOG.error("Itau response was not JSON", exception);
            return Map.of();
        }
    }

    private static SSLContext ssl(ItauSettings settings) {
        try {
            if (settings.certificate().isBlank() || settings.privateKey().isBlank()) {
                return SSLContext.getDefault();
            }
            Certificate certificate =
                    CertificateFactory.getInstance("X.509")
                            .generateCertificate(
                                    new ByteArrayInputStream(
                                            Files.readAllBytes(Path.of(settings.certificate()))));
            PrivateKey key = privateKey(Files.readString(Path.of(settings.privateKey())));
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry("itau", key, new char[0], new Certificate[] {certificate});
            KeyManagerFactory keys =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, new char[0]);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        } catch (Exception exception) {
            LOG.error("Cannot load Itau TLS identity", exception);
            throw new IllegalStateException("Cannot load Itau TLS identity");
        }
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String base64 =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception rsa) {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
    }
}
