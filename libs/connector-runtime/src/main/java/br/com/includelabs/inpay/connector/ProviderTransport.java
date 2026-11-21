package br.com.includelabs.inpay.connector;

import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Transporte HTTPS com timeout, sem redirecionamentos, retry limitado de consultas e sem log de
 * payload.
 */
public class ProviderTransport {

    private final CircuitGate circuit;
    private final ResilienceSettings resilience;
    private final java.util.concurrent.Semaphore bulkhead;
    private final HttpClient client;

    private final Duration timeout;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Cria cliente TLS; PKCS12 opcional habilita autenticação mútua do estabelecimento. */
    public ProviderTransport(ProviderSettings settings) {
        this(settings, ResilienceSettings.defaults());
    }

    /** Injeta políticas de circuito, concorrência e retry exclusivamente de consultas. */
    public ProviderTransport(ProviderSettings settings, ResilienceSettings resilience) {
        this.resilience = resilience;
        circuit = new CircuitGate(resilience.failureThreshold(), resilience.openDuration());
        bulkhead = new java.util.concurrent.Semaphore(resilience.maxConcurrent());
        timeout = settings.readTimeout();
        var builder =
                HttpClient.newBuilder()
                        .connectTimeout(settings.connectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER);
        if (settings.keyStore() != null && !settings.keyStore().isBlank()) {
            try (InputStream input = Files.newInputStream(Path.of(settings.keyStore()))) {
                char[] password =
                        ProviderSettings.required(settings.keyStorePassword(), "key-store-password")
                                .toCharArray();
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(input, password);
                var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keys.init(store, password);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(keys.getKeyManagers(), null, null);
                builder.sslContext(context);
                Arrays.fill(password, '\0');
            } catch (Exception exception) {
                throw new ProviderUnavailableException("Cannot load provider TLS identity");
            }
        }
        client = builder.build();
    }

    /** Retenta apenas GET. Comandos financeiros mantêm uma única tentativa e resultado incerto. */
    public Reply send(
            String method, String url, Map<String, String> headers, Object body, boolean form) {
        if (!bulkhead.tryAcquire()) return new Reply(503, Map.of(), true);
        try {
            int attempts = "GET".equals(method) ? resilience.queryAttempts() : 1;
            Reply reply = new Reply(0, Map.of(), true);
            for (int index = 0; index < attempts; index++) {
                CircuitGate.Lease lease = circuit.acquire();
                if (lease == null) return new Reply(503, Map.of(), true);
                reply = sendOnce(method, url, headers, body, form);
                boolean transientFailure =
                        reply.uncertain()
                                || reply.status() == 429
                                || reply.status() >= 500
                                || reply.status() == 408;
                circuit.complete(lease, !transientFailure);
                if (!transientFailure || index + 1 == attempts) return reply;
                try {
                    Thread.sleep(
                            resilience.retryDelay().toMillis() * (1L << index)
                                    + java.util.concurrent.ThreadLocalRandom.current()
                                            .nextLong(50));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return new Reply(0, Map.of(), true);
                }
            }
            return reply;
        } finally {
            bulkhead.release();
        }
    }

    /**
     * Envia exatamente uma chamada. Não retorna mensagens de erro que possam conter cartão ou
     * segredo.
     */
    @SuppressWarnings("unchecked")
    private Reply sendOnce(
            String method, String url, Map<String, String> headers, Object body, boolean form) {
        try {
            String data =
                    body == null ? "" : form ? body.toString() : mapper.writeValueAsString(body);
            var request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(timeout)
                            .header("Accept", "application/json")
                            .header(
                                    "Content-Type",
                                    form
                                            ? "application/x-www-form-urlencoded"
                                            : "application/json");
            headers.forEach(request::header);
            request.method(
                    method,
                    data.isEmpty()
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
            var future = client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<byte[]> response;
            try {
                response =
                        future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException exception) {
                future.cancel(true);
                return new Reply(0, Map.of(), true);
            }
            {
                byte[] bytes = response.body();
                if (bytes.length > 1_048_576) {
                    return new Reply(response.statusCode(), Map.of(), true);
                }
                if (bytes.length == 0) {
                    return new Reply(response.statusCode(), Map.of(), false);
                }
                Object decoded = mapper.readValue(bytes, Object.class);
                if (decoded instanceof List<?> list) {
                    return new Reply(response.statusCode(), Map.of("items", list), false);
                }
                return decoded instanceof Map<?, ?> map
                        ? new Reply(response.statusCode(), (Map<String, Object>) map, false)
                        : new Reply(response.statusCode(), Map.of(), true);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Reply(0, Map.of(), true);
        } catch (Exception exception) {
            return new Reply(0, Map.of(), true);
        }
    }

    /** Resposta transitória mantida somente em memória durante a interpretação. */
    public record Reply(int status, Map<String, Object> body, boolean uncertain) {

        @Override
        public String toString() {
            return "Reply[status=" + status + "]";
        }
    }
}
