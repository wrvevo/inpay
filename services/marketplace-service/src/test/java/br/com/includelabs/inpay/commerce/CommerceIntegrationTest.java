package br.com.includelabs.inpay.commerce;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.commerce.bootstrap.Application;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercita contrato HTTP, autorização e persistência PostgreSQL reais; não chama provedores
 * externos.
 */
class CommerceIntegrationTest {
    static EmbeddedPostgres pg;
    static ConfigurableApplicationContext context;
    static String base;
    static final HttpClient client = HttpClient.newHttpClient();
    static final JsonMapper json = JsonMapper.builder().build();
    static final String auth =
            "Basic " + Base64.getEncoder().encodeToString("developer:test-only".getBytes());

    @BeforeAll
    static void start() throws Exception {
        pg =
                EmbeddedPostgres.builder()
                        .setPort(0)
                        .setServerConfig("unix_socket_directories", "")
                        .start();
        context =
                new SpringApplicationBuilder(Application.class)
                        .run(
                                "--spring.profiles.active=local",
                                "--server.port=0",
                                "--spring.datasource.url=jdbc:postgresql://localhost:"
                                        + pg.getPort()
                                        + "/postgres",
                                "--spring.datasource.username=postgres",
                                "--spring.datasource.password=",
                                "--inpay.local-password=test-only",
                                "--spring.kafka.listener.auto-startup=false",
                                "--inpay.outbox.delay=3600000",
                                "--logging.level.root=WARN",
                                "--spring.main.banner-mode=off");
        base =
                "http://127.0.0.1:"
                        + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() throws Exception {
        if (context != null) context.close();
        if (pg != null) pg.close();
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Authorization", auth)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String key, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Authorization", auth)
                        .header("Idempotency-Key", key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void splitsConserveAmountAndCannotPayUnsettledMoney() throws Exception {
        String recipient =
                json.readTree(
                                post(
                                                "/marketplace/v1/recipients",
                                                "seller-1",
                                                "{\"name\":\"Loja\",\"document\":\"12345678901\",\"provider_recipient_id\":\"external-seller\"}")
                                        .body())
                        .get("id")
                        .asText();
        String body =
                "{\"order_id\":\"or_reference\",\"amount\":10000,\"allocations\":[{\"recipient_id\":\""
                        + recipient
                        + "\",\"amount\":10000}]}";
        var response = post("/marketplace/v1/split-plans", "split-1", body);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(
                json.readTree(response.body()),
                json.readTree(post("/marketplace/v1/split-plans", "split-1", body).body()));
        String split = json.readTree(response.body()).get("id").asText();
        String settlement =
                json.readTree(
                                post(
                                                "/marketplace/v1/settlements",
                                                "settlement-1",
                                                "{\"split_id\":\""
                                                        + split
                                                        + "\",\"evidence_reference\":\"evidence\"}")
                                        .body())
                        .get("id")
                        .asText();
        assertEquals(
                501,
                post("/marketplace/v1/settlements/" + settlement + "/payouts", "payout-1", "{}")
                        .statusCode());
        assertEquals(
                422,
                post("/marketplace/v1/split-plans", "bad-split", body.replaceFirst("10000", "9999"))
                        .statusCode());
    }

    @Test
    void creditEndpointsAreNotExposedInMarketplace() throws Exception {
        assertEquals(404, get("/marketplace/v1/me").statusCode());
        var doc = json.readTree(get("/v3/api-docs").body());
        assertTrue(doc.get("paths").has("/marketplace/v1/split-plans"));
        assertFalse(doc.get("paths").has("/credits/v1/me"));
    }
}
