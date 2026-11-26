package br.com.includelabs.inpay.c6.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "inpay.local-password=test-only",
            "inpay.c6.enabled=false",
            "inpay.c6.kafka-enabled=false",
            "inpay.c6.certificate=",
            "inpay.c6.private-key=",
            "spring.autoconfigure.exclude=org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"
        })
@ActiveProfiles("local")
class ConnectorHttpTest {
    @LocalServerPort int port;

    @Test
    void listsOperationsAndKeepsTheAcquirerDisabled() throws Exception {
        String auth =
                "Basic " + Base64.getEncoder().encodeToString("developer:test-only".getBytes());
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> catalog =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/internal/v1/bank/operations"))
                                .header("Authorization", auth)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, catalog.statusCode(), catalog.body());
        assertTrue(catalog.body().contains("PIX_COB_PUT"));
        assertTrue(catalog.body().contains("BANK_SLIP"));
        assertTrue(catalog.body().contains("CHECKOUT"));
        assertTrue(catalog.body().contains("PIX_AUTOMATIC"));
        assertTrue(catalog.body().contains("RECEIVABLES"));
        assertTrue(catalog.body().contains("SCHEDULE"));
        assertTrue(catalog.body().contains("c6"));
        HttpResponse<String> mocked =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/internal/v1/c6/operations/PIX_COB_PUT"))
                                .header("Authorization", auth)
                                .header("X-Mock", "true")
                                .header("Idempotency-Key", "pix-1")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"txid\":\"abc123\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, mocked.statusCode(), mocked.body());
        assertTrue(mocked.body().contains("pixCopiaECola"));
        HttpResponse<String> live =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/internal/v1/bank/operations/PIX_COB_GET"))
                                .header("Authorization", auth)
                                .header("X-Mock", "false")
                                .header("Idempotency-Key", "live-1")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"txid\":\"abc123\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(503, live.statusCode(), live.body());
    }
}
