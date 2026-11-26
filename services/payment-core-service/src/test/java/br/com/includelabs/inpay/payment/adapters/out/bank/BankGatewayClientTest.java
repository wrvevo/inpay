package br.com.includelabs.inpay.payment.adapters.out.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BankGatewayClientTest {
    static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(
                WireMock.post(WireMock.urlPathEqualTo("/internal/v1/bank/operations/PIX_COB_PUT"))
                        .withHeader("X-Mock", WireMock.equalTo("true"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"mocked\":true}")));
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @Test
    void mockHeaderIsForwardedAndLiveTimeoutStaysInsideTheBudget() {
        BankGatewaySettings settings =
                new BankGatewaySettings(
                        Map.of("c6", "http://127.0.0.1:" + server.port()),
                        "developer",
                        "test-only");
        BankGatewayClient client = new BankGatewayClient(settings);
        BankGatewayClient.Downstream mocked = new BankGatewayClient.Downstream(503, "");
        for (int attempt = 0; attempt < 20; attempt++) {
            mocked = client.execute("c6", "PIX_COB_PUT", "true", "key-1", Map.of("txid", "abc123"));
            if (mocked.status() == 200) {
                break;
            }
        }
        assertEquals(200, mocked.status(), mocked.body());
        assertTrue(mocked.body().contains("\"mocked\":true"));
        long started = System.nanoTime();
        BankGatewayClient.Downstream live =
                client.execute("itau", "PIX_COB_PUT", "false", "key-2", Map.of("txid", "abc123"));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertEquals(503, live.status());
        assertTrue(elapsedMs < 500, "elapsed=" + elapsedMs);
    }
}
