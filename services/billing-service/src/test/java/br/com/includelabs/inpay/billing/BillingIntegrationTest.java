package br.com.includelabs.inpay.billing;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.billing.bootstrap.Application;
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
class BillingIntegrationTest {
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
    void settlementIsAtomicIdempotentAndChargesOnlyAfterPayment() throws Exception {
        var operations =
                context.getBean(
                        br.com.includelabs.inpay.billing.application.BillingOperations.class);
        var db = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var merchantBody =
                new br.com.includelabs.inpay.billing.application.BillingCommands.Merchant(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Teste financeiro",
                        "START-2026-11",
                        "contract-ref",
                        null);
        UUID merchant = (UUID) operations.register("operator", merchantBody).get("id");
        var terms =
                new br.com.includelabs.inpay.billing.domain.BillingPolicy.Conditions(
                        0, 0, 0, null, 0);
        var invoiceBody =
                new br.com.includelabs.inpay.billing.application.BillingCommands.Invoice(
                        UUID.randomUUID(),
                        merchant,
                        "customer",
                        10000,
                        "BOLETO",
                        java.time.LocalDate.now(),
                        terms,
                        "contract-ref");
        assertThrows(
                IllegalStateException.class,
                () -> operations.createInvoice("operator", invoiceBody));
        assertThrows(
                IllegalStateException.class,
                () ->
                        operations.approve(
                                "operator",
                                merchant,
                                new br.com.includelabs.inpay.billing.application.BillingCommands
                                        .Command(UUID.randomUUID())));
        operations.approve(
                "reviewer",
                merchant,
                new br.com.includelabs.inpay.billing.application.BillingCommands.Command(
                        UUID.randomUUID()));
        UUID invoice = (UUID) operations.createInvoice("operator", invoiceBody).get("id");
        assertEquals(
                invoice.toString(),
                operations.createInvoice("operator", invoiceBody).get("id").toString());
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT count(*) FROM billing_costs WHERE invoice_id=?",
                        Integer.class,
                        invoice));
        var event =
                new br.com.includelabs.inpay.billing.application.BillingCommands.Settlement(
                        "test-provider",
                        UUID.randomUUID().toString(),
                        invoice,
                        UUID.randomUUID().toString(),
                        10000,
                        199,
                        java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")),
                        java.time.Instant.now(),
                        "deposit-reference");
        assertEquals(
                403,
                post("/billing/v1/internal/settlements", "unused", json.writeValueAsString(event))
                        .statusCode());
        assertEquals("PAID", operations.settle("trusted-worker", event).get("status"));
        assertEquals("REPLAY", operations.settle("trusted-worker", event).get("status"));
        assertEquals(
                49,
                db.queryForObject(
                        "SELECT gateway_fee FROM billing_costs WHERE invoice_id=?",
                        Integer.class,
                        invoice));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () ->
                        db.update(
                                "UPDATE billing_costs SET gateway_fee=1 WHERE invoice_id=?",
                                invoice));
        assertEquals(200, get("/billing/v1/dashboard").statusCode());
        var swagger = json.readTree(get("/v3/api-docs").body());
        assertTrue(swagger.get("paths").has("/billing/v1/invoices"));
    }

    @Test
    void cyclesRemainUniqueAndCancellationStopsFutureInvoices() {
        var ops =
                context.getBean(
                        br.com.includelabs.inpay.billing.application.BillingOperations.class);
        UUID merchant =
                (UUID)
                        ops.register(
                                        "operator",
                                        new br.com.includelabs.inpay.billing.application
                                                .BillingCommands.Merchant(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "Cycles",
                                                "GROW-2026-11",
                                                "contract",
                                                "CLEARSALE"))
                                .get("id");
        ops.approve(
                "reviewer",
                merchant,
                new br.com.includelabs.inpay.billing.application.BillingCommands.Command(
                        UUID.randomUUID()));
        var due = java.time.LocalDate.of(2030, 1, 31);
        UUID subscription =
                (UUID)
                        ops.subscribe(
                                        "operator",
                                        new br.com.includelabs.inpay.billing.application
                                                .BillingCommands.Subscription(
                                                UUID.randomUUID(),
                                                merchant,
                                                "client",
                                                1000,
                                                "PIX",
                                                due,
                                                31,
                                                new br.com.includelabs.inpay.billing.domain
                                                        .BillingPolicy.Conditions(0, 0, 0, null, 0),
                                                "mandate"))
                                .get("id");
        assertEquals(1, ops.generateCycles(due, 100));
        assertEquals(0, ops.generateCycles(due, 100));
        assertEquals(1, ops.generateCycles(due.plusMonths(1), 100));
        ops.cancelSubscription(
                "operator",
                subscription,
                new br.com.includelabs.inpay.billing.application.BillingCommands.Command(
                        UUID.randomUUID()));
        assertEquals(0, ops.generateCycles(due.plusMonths(2), 100));
    }
}
