package br.com.includelabs.inpay.bank;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Chamada HTTPS externa com orçamento máximo de 100 ms e circuit breaker. Payload não é logado. */
public final class BankTransport {
    public static final Duration MAX_BUDGET = Duration.ofMillis(100);

    private static final Logger LOG = LogManager.getLogger(BankTransport.class);

    private static final Duration MOCK_BUDGET = Duration.ofSeconds(2);

    private final BankCircuit circuit;
    private final BankCircuit mockCircuit;
    private final Duration mockBudget;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    /** Configura o transporte HTTP e a instrumentação do conector. */
    public BankTransport(BankSettings settings) {
        this(settings, null);
    }

    /** Configura o transporte HTTP e a instrumentação do conector. */
    public BankTransport(BankSettings settings, MeterRegistry meters) {
        this(settings, meters, MOCK_BUDGET);
    }

    /** Configura o transporte HTTP e a instrumentação do conector. */
    BankTransport(BankSettings settings, MeterRegistry meters, Duration mockBudget) {
        this.mockBudget = mockBudget;
        this.mockCircuit = new BankCircuit(settings.failureThreshold(), settings.openDuration());
        this.circuit = new BankCircuit(settings.failureThreshold(), settings.openDuration());
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(MAX_BUDGET)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        this.mapper = new ObjectMapper();
        this.meters = meters;
    }

    /** Encaminha a requisição ao ambiente configurado com limites de tempo. */
    public BankResult send(
            BankId bank, BankOperation operation, boolean mocked, URI uri, String body) {
        long started = System.nanoTime();
        LOG.info(
                "External bank call started bank={} operation={} mock={} host={}",
                bank.slug(),
                operation.name(),
                mocked,
                uri.getHost());
        BankCircuit activeCircuit = mocked ? mockCircuit : circuit;
        Duration budget = mocked ? mockBudget : MAX_BUDGET;
        BankCircuit.Lease lease = activeCircuit.acquire();
        if (lease == null) {
            return finish(
                    bank,
                    operation,
                    mocked,
                    started,
                    "open",
                    BankResult.unavailable(bank.slug(), operation.name(), mocked));
        }
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri)
                        .timeout(budget)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json");
        if (body == null || body.isBlank()) {
            request.method(operation.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(
                    operation.method(),
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        var future = client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        try {
            HttpResponse<byte[]> response = future.get(budget.toMillis(), TimeUnit.MILLISECONDS);
            boolean transientFailure =
                    response.statusCode() == 408
                            || response.statusCode() == 429
                            || response.statusCode() >= 500;
            activeCircuit.complete(lease, !transientFailure);
            BankResult result =
                    new BankResult(
                            bank.slug(),
                            operation.name(),
                            response.statusCode(),
                            transientFailure,
                            mocked,
                            decode(response.body()));
            return finish(
                    bank,
                    operation,
                    mocked,
                    started,
                    transientFailure ? "transient" : "completed",
                    result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            activeCircuit.complete(lease, false);
            LOG.error(
                    "External bank call interrupted bank={} operation={}",
                    bank.slug(),
                    operation.name(),
                    exception);
            return finish(
                    bank,
                    operation,
                    mocked,
                    started,
                    "interrupted",
                    BankResult.unavailable(bank.slug(), operation.name(), mocked));
        } catch (Exception exception) {
            future.cancel(true);
            activeCircuit.complete(lease, false);
            LOG.error(
                    "External bank call failed within configured budget bank={} operation={}",
                    bank.slug(),
                    operation.name(),
                    exception);
            return finish(
                    bank,
                    operation,
                    mocked,
                    started,
                    "timeout",
                    BankResult.unavailable(bank.slug(), operation.name(), mocked));
        }
    }

    /** Registra a conclusão e retorna o resultado normalizado. */
    private BankResult finish(
            BankId bank,
            BankOperation operation,
            boolean mocked,
            long started,
            String outcome,
            BankResult result) {
        long elapsed = System.nanoTime() - started;
        LOG.info(
                "External bank call finished bank={} operation={} mock={} status={} uncertain={} outcome={} elapsedMs={}",
                bank.slug(),
                operation.name(),
                mocked,
                result.status(),
                result.uncertain(),
                outcome,
                elapsed / 1_000_000);
        if (meters != null) {
            Timer.builder("inpay.bank.external.call")
                    .tag("bank", bank.slug())
                    .tag("operation", operation.name())
                    .tag("mock", Boolean.toString(mocked))
                    .tag("outcome", outcome)
                    .register(meters)
                    .record(elapsed, TimeUnit.NANOSECONDS);
        }
        return result;
    }

    /** Decodifica o corpo da resposta em uma estrutura de dados. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 1_048_576) {
            return Map.of();
        }
        try {
            Object decoded = mapper.readValue(bytes, Object.class);
            if (decoded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("items", decoded);
        } catch (Exception exception) {
            LOG.error("External bank response was not JSON", exception);
            return Map.of();
        }
    }
}
