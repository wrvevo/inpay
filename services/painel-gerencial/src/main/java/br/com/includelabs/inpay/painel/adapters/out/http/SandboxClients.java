package br.com.includelabs.inpay.painel.adapters.out.http;

import br.com.includelabs.inpay.contracts.SandboxBindings;
import br.com.includelabs.inpay.painel.domain.OnboardingCase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Local-only HTTP orchestration; no real partner data or financial credentials are transmitted. */
@Component
public class SandboxClients {
    private final boolean local;
    private final String authorization;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private static final Map<String, Integer> CARD_PORTS = Map.of(
            "cielo", 8101, "rede", 8102, "getnet", 8103, "stone", 8104, "bin", 8105,
            "global-payments", 8106, "pagbank", 8107, "safrapay", 8108);

    public SandboxClients(
            Environment environment, @Value("${inpay.local-password:}") String password) {
        this.local = environment.matchesProfiles("local");
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                ("inpay:" + password).getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, String> partners(UUID tenant, UUID caseId) {
        Map<String, String> evidence = new LinkedHashMap<>();
        for (String provider : new String[] {"clearsale", "serasa-experian"}) {
            int port = provider.equals("clearsale") ? 8128 : 8129;
            JsonNode reply = post(
                    "http://connector-" + provider + ":" + port
                            + "/internal/v1/customer-reviews/CUSTOMER_REVIEW",
                    tenant, caseId.toString(), Map.of(), true);
            if (!reply.path("mock").asBoolean()
                    || reply.path("productionApproved").asBoolean(true)
                    || !provider.equals(reply.path("provider").asText())
                    || !"CUSTOMER_REVIEW".equals(reply.path("stage").asText())) {
                throw new IllegalStateException("Partner review not confirmed");
            }
            evidence.put(provider, "MOCK_CUSTOMER_REVIEW");
        }
        return evidence;
    }

    public String provision(UUID tenant, OnboardingCase item) {
        JsonNode reply = post(
                "http://accounts-service:8111/accounts/v1/sandbox-configurations",
                tenant, "onboarding-" + item.id(),
                Map.of("storeId", item.storeId(), "bindings", item.bindings()), false);
        if (!"SANDBOX_CONFIGURED".equals(reply.path("status").asText())
                || reply.path("productionEnabled").asBoolean(true)
                || !item.storeId().toString().equals(reply.path("storeId").asText())
                || !json.readTree(json.writeValueAsString(item.bindings())).equals(reply.path("bindings"))
                || reply.path("id").asText().isBlank()) {
            throw new IllegalStateException("Accounts receipt not confirmed");
        }
        return reply.path("id").asText();
    }

    public Map<String, String> tests(UUID tenant, OnboardingCase item) {
        Map<String, String> results = new LinkedHashMap<>();
        item.bindings().forEach((method, provider) -> {
            if (SandboxBindings.CARDS.contains(provider)) {
                for (String operation : new String[] {"AUTHORIZE", "QUERY", "CAPTURE", "CANCEL", "REFUND"}) {
                    JsonNode reply = post(
                            "http://connector-" + provider + ":" + CARD_PORTS.get(provider)
                                    + "/internal/v1/provider-mocks/" + operation,
                            tenant, UUID.randomUUID().toString(), Map.of(), true);
                    if (!reply.path("mocked").asBoolean()
                            || reply.path("productionApproved").asBoolean(true)
                            || !operation.equals(reply.path("operation").asText())) {
                        throw new IllegalStateException("Card simulation failed");
                    }
                    results.put(method + ":" + provider + ":" + operation, "MOCK_PASS");
                }
            } else {
                String operation = SandboxBindings.bankOperation(method, provider);
                JsonNode reply = post(
                        "http://payment-core-service:8084/gateway/v1/bank-operations/"
                                + provider + "/" + operation,
                        tenant, UUID.randomUUID().toString(),
                        Map.of("amount", 1000, "txid", "synthetic"), true);
                if (!reply.path("mocked").asBoolean()
                        || reply.path("status").asInt() != 200
                        || !operation.equals(reply.path("operation").asText())) {
                    throw new IllegalStateException("Bank simulation failed");
                }
                results.put(method + ":" + provider + ":" + operation, "MOCK_PASS");
            }
        });
        return results;
    }

    private JsonNode post(
            String url, UUID tenant, String key, Object body, boolean mock) {
        if (!local) throw new UnsupportedOperationException("External partner contract not configured");
        try {
            var builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", authorization)
                    .header("X-Merchant-Id", tenant.toString())
                    .header("Idempotency-Key", key)
                    .header("Content-Type", "application/json");
            if (mock) builder.header("X-Mock", "true");
            var response = http.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Sandbox service failed");
            return json.readTree(response.body());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            // No fallback to a real transaction or local invented success.
        }
        throw new IllegalStateException("Sandbox service unavailable");
    }
}
