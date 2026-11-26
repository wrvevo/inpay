package br.com.includelabs.inpay.fraud.adapters.out.http;

import br.com.includelabs.inpay.contracts.AntifraudStubs;
import br.com.includelabs.inpay.contracts.FraudMode;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy;
import br.com.includelabs.inpay.fraud.domain.port.ExternalRiskPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Local-only HTTP calls; sends derived signals, never CPF or documents. */
@Component
@Profile("local")
public class LocalPartnerRiskAdapter implements ExternalRiskPort {
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private final String auth;
    private final Map<String, String> urls;

    public LocalPartnerRiskAdapter(
            @Value("${inpay.local-password}") String password,
            @Value("${INPAY_CLEARSALE_MOCK_URL:}") String clearSale,
            @Value("${INPAY_SERASA_MOCK_URL:}") String serasa) {
        auth =
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString(
                                        ("inpay:" + password).getBytes(StandardCharsets.UTF_8));
        urls = Map.of(FraudMode.CLEARSALE, clearSale, FraudMode.SERASA_EXPERIAN, serasa);
    }

    @Override
    public RiskPolicy.Assessment evaluate(String mode, AntifraudStubs.Probe probe) {
        try {
            String url = urls.get(mode);
            URI endpoint = URI.create(url + "/internal/v1/assessments");
            if (!"http".equals(endpoint.getScheme())
                    || endpoint.getUserInfo() != null
                    || endpoint.getHost() == null
                    || !List.of(
                                    "connector-clearsale",
                                    "connector-serasa-experian",
                                    "127.0.0.1",
                                    "localhost")
                            .contains(endpoint.getHost())) {
                throw new IllegalStateException("Partner mock endpoint unavailable");
            }
            var request =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(Duration.ofSeconds(5))
                            .header("Authorization", auth)
                            .header("Content-Type", "application/json")
                            .header("X-Mock", "true")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(probe)))
                            .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var body = json.readTree(response.body());
            String provider = mode.equals(FraudMode.CLEARSALE) ? "clearsale" : "serasa-experian";
            if (response.statusCode() != 200
                    || !body.path("mock").asBoolean()
                    || !provider.equals(body.path("provider").asText())
                    || !body.path("policyVersion").asText().endsWith("-wiremock-v1")) {
                throw new IllegalStateException("Unverified partner response");
            }
            int score = body.path("score").asInt(-1);
            if (score < 0 || score > 100) throw new IllegalStateException("Invalid score");
            return new RiskPolicy.Assessment(
                    score,
                    RiskPolicy.Decision.valueOf(body.path("decision").asText()),
                    body.path("policyVersion").asText(),
                    List.of("PARTNER_SYNTHETIC"),
                    mode,
                    0);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            // Never approve or fall back to an invented decision when the provider is unavailable.
        }
        return unavailable(mode);
    }

    public static RiskPolicy.Assessment unavailable(String mode) {
        return new RiskPolicy.Assessment(
                100,
                RiskPolicy.Decision.REVIEW,
                "partner-unavailable",
                List.of("PARTNER_UNAVAILABLE"),
                mode,
                0);
    }
}
