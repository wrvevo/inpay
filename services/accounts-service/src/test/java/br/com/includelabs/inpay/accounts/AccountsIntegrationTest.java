package br.com.includelabs.inpay.accounts;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.accounts.bootstrap.Application;
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
class AccountsIntegrationTest {
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
    void partnerUnavailableNeverReturnsFakeBalanceOrAccount() throws Exception {
        assertEquals(503, get("/accounts/v1/accounts/account-reference/balance").statusCode());
        assertEquals(
                503,
                post(
                                "/accounts/v1/accounts",
                                "open-1",
                                "{\"name\":\"Teste\",\"email\":\"test@example.com\",\"type\":\"individual\",\"document\":\"12345678901\",\"onboarding_reference\":\"kyc-ref\"}")
                        .statusCode());
        assertEquals(
                503,
                post(
                                "/accounts/v1/accounts/ref/transfers",
                                "transfer-1",
                                "{\"amount\":100,\"destination_key\":\"key\",\"description\":\"teste\"}")
                        .statusCode());
    }

    @Test
    void bankingSwaggerAndAuthenticationRemainSeparate() throws Exception {
        var doc = json.readTree(get("/v3/api-docs").body());
        assertTrue(doc.get("paths").has("/accounts/v1/accounts/{id}/pix-keys"));
        assertFalse(doc.get("paths").has("/gateway/v1/orders"));
        assertEquals(
                401,
                client.send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        base + "/accounts/v1/accounts/ref/balance"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode());
    }

    @Test
    void bankConfigurationRemainsDisabledAndIdempotent() throws Exception {
        var catalog = json.readTree(get("/accounts/v1/banks").body());
        assertEquals(13, catalog.size());
        var mockReady = Set.of("c6", "itau", "santander", "bradesco", "bb", "caixa", "banrisul");
        for (var bank : catalog) {
            assertFalse(bank.path("enabled").asBoolean());
            if (mockReady.contains(bank.path("id").asText())) {
                assertEquals("MOCK_READY", bank.path("status").asText());
                assertFalse(bank.path("implementedOperations").isEmpty());
            } else {
                assertEquals("NOT_IMPLEMENTED", bank.path("status").asText());
            }
        }
        String body =
                """
                {"bank":"c6","environment":"sandbox",
                 "credentialReference":"secrets/tenant/c6","certificateReference":"secrets/tenant/cert"}
                """;
        String key = UUID.randomUUID().toString();
        var first = post("/accounts/v1/connections", key, body);
        assertEquals(200, first.statusCode());
        var saved = json.readTree(first.body());
        assertEquals("PENDING_IMPLEMENTATION", saved.path("status").asText());
        assertFalse(saved.path("enabled").asBoolean());
        assertEquals(
                saved.path("id"),
                json.readTree(post("/accounts/v1/connections", key, body).body()).path("id"));
        assertEquals(
                409,
                post("/accounts/v1/connections", key, body.replace("sandbox", "production"))
                        .statusCode());
        assertEquals(
                400,
                post("/accounts/v1/connections", "invalid", body.replace("\"sandbox\"", "null"))
                        .statusCode());
    }
}
