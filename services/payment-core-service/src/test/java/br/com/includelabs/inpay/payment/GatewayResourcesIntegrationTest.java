package br.com.includelabs.inpay.payment;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.payment.bootstrap.Application;
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
class GatewayResourcesIntegrationTest {
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
    void orderAndChargesAreAtomicAndIdempotent() throws Exception {
        var customer =
                post(
                        "/gateway/v1/customers",
                        "customer-1",
                        "{\"name\":\"Teste\",\"email\":\"test@example.com\",\"type\":\"individual\",\"document\":\"12345678901\"}");
        assertEquals(201, customer.statusCode());
        String customerId = json.readTree(customer.body()).get("id").asText();
        String body =
                "{\"code\":\"order-test\",\"customer_id\":\""
                        + customerId
                        + "\",\"items\":[{\"code\":\"game\",\"description\":\"Game\",\"amount\":10000,\"quantity\":1}],\"payments\":[{\"payment_method\":\"pix\",\"amount\":10000,\"pix\":{\"expires_in\":300}}]}";
        var first = post("/gateway/v1/orders", "order-1", body);
        assertEquals(202, first.statusCode(), first.body());
        assertEquals(
                json.readTree(first.body()),
                json.readTree(post("/gateway/v1/orders", "order-1", body).body()));
        assertEquals(
                409,
                post("/gateway/v1/orders", "order-1", body.replace("order-test", "changed"))
                        .statusCode());
        var order = json.readTree(first.body());
        assertEquals("pending", order.get("status").asText());
        assertEquals(
                "not_dispatched", order.get("charges").get(0).get("processing_status").asText());
        assertEquals(
                422,
                post(
                                "/gateway/v1/orders",
                                "bad-sum",
                                body.replace("\"amount\":10000,\"pix\"", "\"amount\":9999,\"pix\""))
                        .statusCode());
        assertEquals(1, json.readTree(get("/gateway/v1/orders").body()).get("data").size());
        assertEquals(1, json.readTree(get("/gateway/v1/charges").body()).get("data").size());
        assertEquals(
                3,
                context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                        .queryForObject(
                                "SELECT count(*) FROM outbox WHERE partition_key='11111111-1111-1111-1111-111111111111'",
                                Integer.class));
    }

    @Test
    void swaggerDocumentsGatewayResourcesAndBusinessNeedsAuthentication() throws Exception {
        var doc = json.readTree(get("/v3/api-docs").body());
        assertTrue(doc.get("paths").has("/gateway/v1/orders"));
        assertTrue(doc.get("paths").has("/gateway/v1/charges/{id}/capture"));
        assertEquals(
                401,
                client.send(
                                HttpRequest.newBuilder(URI.create(base + "/gateway/v1/orders"))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString())
                        .statusCode());
        assertEquals(404, get("/gateway/v1/orders/or_missing").statusCode());
        assertEquals(422, get("/gateway/v1/orders?size=101").statusCode());
    }

    @Test
    void tenantMismatchIsDenied() throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base + "/gateway/v1/orders"))
                        .header("Authorization", auth)
                        .header("X-Merchant-Id", UUID.randomUUID().toString())
                        .GET()
                        .build();
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void nestedFailureRollsBackResourcesAndConcurrentReplaysCreateOnlyOne() throws Exception {
        var repository =
                context.getBean(br.com.includelabs.inpay.resources.ResourceRepository.class);
        UUID tenant = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        repository.create(
                                tenant,
                                "or",
                                "failed",
                                Map.of("amount", 100),
                                id -> {
                                    repository.create(
                                            tenant,
                                            "ch",
                                            "child",
                                            Map.of("amount", 100),
                                            child -> Map.of("id", child));
                                    throw new IllegalArgumentException(
                                            "Simulated failure before commit");
                                }));
        assertTrue(repository.list(tenant, "or", 1, 20).isEmpty());
        assertTrue(repository.list(tenant, "ch", 1, 20).isEmpty());
        var attempts = new ArrayList<java.util.concurrent.CompletableFuture<String>>();
        for (int i = 0; i < 6; i++)
            attempts.add(
                    java.util.concurrent.CompletableFuture.supplyAsync(
                            () ->
                                    repository
                                            .create(
                                                    tenant,
                                                    "cus",
                                                    "same-key",
                                                    Map.of("name", "test"),
                                                    id -> Map.of("id", id))
                                            .get("id")
                                            .asText()));
        Set<String> ids = new HashSet<>();
        for (var attempt : attempts) ids.add(attempt.get());
        assertEquals(1, ids.size());
        assertEquals(1, repository.list(tenant, "cus", 1, 20).size());
        assertThrows(
                br.com.includelabs.inpay.security.ApiErrors.NoSuchResource.class,
                () -> repository.get(UUID.randomUUID(), "cus", ids.iterator().next()));
        assertEquals(
                1,
                context.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                        .queryForObject(
                                "SELECT count(*) FROM outbox WHERE partition_key=?",
                                Integer.class,
                                tenant.toString()));
    }
}
