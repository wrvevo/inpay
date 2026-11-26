package br.com.includelabs.inpay.payment.adapters.out.bank;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Encaminha a jornada do gateway ao conector do banco com orçamento de 100 ms. */
@Component
public class BankGatewayClient {
    static final Duration BUDGET = Duration.ofMillis(100);
    private static final Duration MOCK_BUDGET = Duration.ofSeconds(5);
    private static final Logger LOG = LogManager.getLogger(BankGatewayClient.class);

    private final BankGatewaySettings settings;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public BankGatewayClient(BankGatewaySettings settings) {
        this(
                settings,
                HttpClient.newBuilder()
                        .connectTimeout(BUDGET)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    private BankGatewayClient(BankGatewaySettings settings, HttpClient client) {
        this.settings = settings;
        this.client = client;
    }

    public Downstream execute(
            String bank,
            String operation,
            String mock,
            String operationId,
            Map<String, Object> payload) {
        String base = settings.url(bank);
        Duration budget =
                "true".equalsIgnoreCase(mock == null ? "" : mock.trim()) ? MOCK_BUDGET : BUDGET;
        LOG.info(
                "Gateway bank dispatch started bank={} operation={} mock={} ",
                bank,
                operation,
                mock == null ? "false" : mock.trim());
        try {
            String body = mapper.writeValueAsString(payload == null ? Map.of() : payload);
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(
                                    URI.create(base + "/internal/v1/bank/operations/" + operation))
                            .timeout(budget)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", operationId)
                            .header(
                                    "Authorization",
                                    "Basic "
                                            + Base64.getEncoder()
                                                    .encodeToString(
                                                            (settings.username()
                                                                            + ":"
                                                                            + settings.password())
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)));
            if (mock != null && !mock.isBlank()) {
                request.header("X-Mock", mock.trim());
            }
            request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            var future = client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response =
                    future.get(budget.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            LOG.info(
                    "Gateway bank dispatch finished bank={} operation={} status={}",
                    bank,
                    operation,
                    response.statusCode());
            return new Downstream(response.statusCode(), response.body());
        } catch (Exception exception) {
            LOG.error(
                    "Gateway bank dispatch failed bank={} operation={}",
                    bank,
                    operation,
                    exception);
            return new Downstream(503, "{\"error\":\"PROVIDER_UNAVAILABLE\"}");
        }
    }

    public record Downstream(int status, String body) {}
}
