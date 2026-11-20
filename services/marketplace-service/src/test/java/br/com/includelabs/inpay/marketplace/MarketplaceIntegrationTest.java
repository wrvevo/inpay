package br.com.includelabs.inpay.marketplace;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.marketplace.adapters.out.provider.UnavailableFulfillmentAdapter;
import br.com.includelabs.inpay.marketplace.application.MarketplaceService;
import br.com.includelabs.inpay.marketplace.bootstrap.Application;
import br.com.includelabs.inpay.marketplace.domain.*;
import br.com.includelabs.inpay.marketplace.domain.Order;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Testes com HTTP Spring e PostgreSQL reais; provedores financeiros exclusivamente simulados. */
class MarketplaceIntegrationTest {
    static EmbeddedPostgres postgres;
    static ConfigurableApplicationContext context;
    static MarketplaceService service;
    static String base;
    UUID tenant;
    String owner = "customer";

    @BeforeAll
    static void start() throws Exception {
        postgres =
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
                                        + postgres.getPort()
                                        + "/postgres",
                                "--spring.datasource.username=postgres",
                                "--spring.datasource.password=",
                                "--inpay.local-password=test-only",
                                "--spring.kafka.listener.auto-startup=false",
                                "--inpay.outbox.delay=3600000",
                                "--logging.level.root=WARN",
                                "--spring.main.banner-mode=off");
        service = context.getBean(MarketplaceService.class);
        base =
                "http://127.0.0.1:"
                        + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll
    static void stop() throws Exception {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void setup() {
        tenant = UUID.randomUUID();
        service.createAccount(
                tenant, UUID.randomUUID(), "operator", owner, "PlacarPlay demo", "contract-test");
        service.product(
                tenant,
                UUID.randomUUID(),
                "operator",
                new Product("GAME", "Gift card demonstrativo", 10000, "SIMULATED", true));
        fund("CREDIT_LIMIT", 20000);
        fund("SETTLED_REPASS", 10000);
    }

    void fund(String kind, long amount) {
        UUID id = UUID.randomUUID();
        service.requestAdjustment(
                tenant, id, "operator", owner, kind, amount, UUID.randomUUID().toString(), null);
        service.approve(tenant, UUID.randomUUID(), "reviewer", id);
    }

    UUID order(List<Tender> tenders) {
        UUID id = UUID.randomUUID();
        service.order(tenant, id, owner, owner, "GAME", tenders);
        return id;
    }

    Account account() {
        return service.view(tenant).account(owner);
    }

    @Test
    void mixedPaymentReservesConsumesAndRefundsOriginalSources() {
        UUID id = order(List.of(new Tender(Source.CREDIT, 6000), new Tender(Source.BALANCE, 4000)));
        assertEquals(14000, account().available(Source.CREDIT));
        assertEquals(6000, account().available(Source.BALANCE));
        assertEquals(0, account().creditUsed);
        service.process(tenant, UUID.randomUUID(), "operator", id, false);
        assertEquals(OrderStatus.DELIVERED, service.view(tenant).order(id).status);
        assertEquals(6000, account().creditUsed);
        assertEquals(6000, account().balance);
        assertEquals(0, account().creditReserved);
        service.refund(tenant, UUID.randomUUID(), "operator", id);
        assertEquals(0, account().creditUsed);
        assertEquals(10000, account().balance);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.refund(tenant, UUID.randomUUID(), "operator", id));
    }

    @Test
    void reducingLimitRecordsOnlyDeltaAndAllowsZeroWithoutChangingBalance() {
        fund("CREDIT_LIMIT", 15000);
        fund("CREDIT_LIMIT", 0);
        assertEquals(0, account().creditLimit);
        assertEquals(10000, account().balance);
        var reductions =
                service.journal(tenant, owner).stream()
                        .filter(
                                entry ->
                                        entry.debitBook().equals("CREDIT_AVAILABLE")
                                                && entry.creditBook().equals("CREDIT_CAPACITY"))
                        .map(JournalEntry::amount)
                        .sorted()
                        .toList();
        assertEquals(List.of(5000L, 15000L), reductions);
        assertThrows(
                IllegalArgumentException.class,
                () -> order(List.of(new Tender(Source.CREDIT, 10000))));
        UUID balanceOrder = order(List.of(new Tender(Source.BALANCE, 10000)));
        assertEquals(OrderStatus.RESERVED, service.view(tenant).order(balanceOrder).status);
    }

    @Test
    void invalidSecondSourceRollsBackFirstReserve() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        order(
                                List.of(
                                        new Tender(Source.CREDIT, 6000),
                                        new Tender(Source.VOUCHER, 4000))));
        assertEquals(0, account().creditReserved);
        assertTrue(service.view(tenant).orders.isEmpty());
    }

    @Test
    void rejectsInvalidSumDuplicateSourcesAndThreeSources() {
        assertThrows(
                IllegalArgumentException.class,
                () -> order(List.of(new Tender(Source.CREDIT, 9000))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        order(
                                List.of(
                                        new Tender(Source.CREDIT, 5000),
                                        new Tender(Source.CREDIT, 5000))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        order(
                                List.of(
                                        new Tender(Source.CREDIT, 5000),
                                        new Tender(Source.BALANCE, 4000),
                                        new Tender(Source.EXTERNAL, 1000))));
    }

    @Test
    void idempotencyReplaysWithoutDoubleReserveAndRejectsChangedBody() {
        UUID id = order(List.of(new Tender(Source.CREDIT, 10000)));
        service.order(tenant, id, owner, owner, "GAME", List.of(new Tender(Source.CREDIT, 10000)));
        assertEquals(10000, account().creditReserved);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.order(
                                tenant,
                                id,
                                owner,
                                owner,
                                "GAME",
                                List.of(new Tender(Source.BALANCE, 10000))));
    }

    @Test
    void unknownRetainsReserveAndReconciliationDoesNotIssueTwice() {
        service.product(
                tenant,
                UUID.randomUUID(),
                "operator",
                new Product("GAME", "Timeout demo", 10000, "SIMULATED_TIMEOUT", true));
        UUID id =
                order(List.of(new Tender(Source.CREDIT, 7000), new Tender(Source.EXTERNAL, 3000)));
        service.process(tenant, UUID.randomUUID(), "operator", id, false);
        assertEquals(OrderStatus.PENDING_RECONCILIATION, service.view(tenant).order(id).status);
        assertEquals(7000, account().creditReserved);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancel(tenant, UUID.randomUUID(), owner, owner, id));
        service.process(tenant, UUID.randomUUID(), "operator", id, true);
        assertEquals(7000, account().creditUsed);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(tenant, UUID.randomUUID(), "operator", id, true));
    }

    @Test
    void captureFailureKeepsAllSourcesPendingCompensation() {
        service.product(
                tenant,
                UUID.randomUUID(),
                "operator",
                new Product("GAME", "Capture failure", 10000, "SIMULATED_CAPTURE_FAILURE", true));
        UUID id =
                order(List.of(new Tender(Source.BALANCE, 5000), new Tender(Source.EXTERNAL, 5000)));
        service.process(tenant, UUID.randomUUID(), "operator", id, false);
        assertEquals(OrderStatus.COMPENSATION_REQUIRED, service.view(tenant).order(id).status);
        assertEquals(5000, account().balanceReserved);
    }

    @Test
    void definitiveRejectionReleasesBothSources() {
        service.product(
                tenant,
                UUID.randomUUID(),
                "operator",
                new Product("GAME", "Reject", 10000, "SIMULATED_REJECT", true));
        UUID id = order(List.of(new Tender(Source.CREDIT, 5000), new Tender(Source.BALANCE, 5000)));
        service.process(tenant, UUID.randomUUID(), "operator", id, false);
        assertEquals(OrderStatus.CANCELLED, service.view(tenant).order(id).status);
        assertEquals(0, account().creditReserved);
        assertEquals(0, account().balanceReserved);
    }

    @Test
    void independentApprovalAndDuplicateEvidenceAreEnforced() {
        UUID id = UUID.randomUUID();
        service.requestAdjustment(
                tenant, id, "operator", owner, "SETTLED_REPASS", 1000, "receipt-unique", null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.approve(tenant, UUID.randomUUID(), "operator", id));
        service.approve(tenant, UUID.randomUUID(), "reviewer", id);
        UUID duplicate = UUID.randomUUID();
        service.requestAdjustment(
                tenant,
                duplicate,
                "operator",
                owner,
                "SETTLED_REPASS",
                1000,
                "receipt-unique",
                null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.approve(tenant, UUID.randomUUID(), "reviewer", duplicate));
        assertEquals(11000, account().balance);
    }

    @Test
    void concurrentOrdersCannotOverspend() throws Exception {
        service.product(
                tenant,
                UUID.randomUUID(),
                "operator",
                new Product("GAME", "Concurrent", 7000, "SIMULATED", true));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task =
                    (Callable<Boolean>)
                            () -> {
                                try {
                                    order(List.of(new Tender(Source.BALANCE, 7000)));
                                    return true;
                                } catch (IllegalArgumentException e) {
                                    return false;
                                }
                            };
            var results = executor.invokeAll(List.of(task, task));
            int succeeded = 0;
            for (var result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertEquals(1, succeeded);
            assertEquals(7000, account().balanceReserved);
        }
    }

    @Test
    void repaymentsAreAllocatedToOrderAndNeverConvertedToCash() {
        UUID id = order(List.of(new Tender(Source.CREDIT, 10000)));
        service.process(tenant, UUID.randomUUID(), "operator", id, false);
        UUID repayment = UUID.randomUUID();
        service.requestAdjustment(
                tenant, repayment, "operator", owner, "CREDIT_REPAYMENT", 4000, "repayment-1", id);
        service.approve(tenant, UUID.randomUUID(), "reviewer", repayment);
        assertEquals(6000, account().creditUsed);
        assertEquals(10000, account().balance);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.refund(tenant, UUID.randomUUID(), "operator", id));
    }

    @Test
    void blockTenantAndOwnerBoundariesPreserveIsolation() {
        UUID id = order(List.of(new Tender(Source.CREDIT, 10000)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.ownedOrder(tenant, "another-user", id));
        assertTrue(service.view(UUID.randomUUID()).accounts.isEmpty());
        service.block(tenant, UUID.randomUUID(), "operator", owner, true);
        assertThrows(
                IllegalArgumentException.class,
                () -> order(List.of(new Tender(Source.CREDIT, 10000))));
    }

    @Test
    void journalIsImmutableAndOutboxCommitsTogether() {
        UUID id = order(List.of(new Tender(Source.CREDIT, 10000)));
        var jdbc = context.getBean(JdbcTemplate.class);
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM marketplace_journal WHERE tenant=?", tenant));
        long journals =
                jdbc.queryForObject(
                        "SELECT count(*) FROM marketplace_journal WHERE tenant=?",
                        Long.class,
                        tenant);
        long events =
                jdbc.queryForObject(
                        "SELECT count(*) FROM outbox WHERE partition_key LIKE ?",
                        Long.class,
                        tenant + ":%");
        assertEquals(journals, events);
        assertTrue(
                service.journal(tenant, owner).stream()
                        .anyMatch(e -> e.reference().equals(id.toString())));
    }

    @Test
    void httpScopesAndTenantValidationAndSwagger() throws Exception {
        assertEquals(403, get("customer", "/marketplace/v1/admin", null));
        assertEquals(403, get("developer", "/marketplace/v1/me", null));
        assertEquals(403, get("developer", "/marketplace/v1/admin", UUID.randomUUID().toString()));
        assertEquals(200, get("developer", "/marketplace/v1/admin", null));
        assertEquals(200, get("developer", "/v3/api-docs", null));
    }

    static int get(String user, String path, String tenant) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString((user + ":test-only").getBytes()));
        if (tenant != null) {
            request.header("X-Merchant-Id", tenant);
        }
        return HttpClient.newHttpClient()
                .send(request.GET().build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    @Test
    void completeCustomerJourneyOverAuthenticatedHttp() throws Exception {
        var json = new tools.jackson.databind.json.JsonMapper();
        String account =
                json.writeValueAsString(
                        Map.of(
                                "operationId",
                                UUID.randomUUID(),
                                "owner",
                                "customer",
                                "creditor",
                                "PlacarPlay demo",
                                "contract",
                                "HTTP-TEST"));
        assertEquals(200, post("developer", "/marketplace/v1/admin/accounts", account));
        for (String kind : List.of("CREDIT_LIMIT", "SETTLED_REPASS")) {
            UUID change = UUID.randomUUID();
            assertEquals(
                    200,
                    post(
                            "developer",
                            "/marketplace/v1/admin/adjustments",
                            json.writeValueAsString(
                                    Map.of(
                                            "operationId",
                                            change,
                                            "owner",
                                            "customer",
                                            "kind",
                                            kind,
                                            "amount",
                                            20000,
                                            "evidence",
                                            "HTTP-" + change))));
            assertEquals(
                    422,
                    post(
                            "developer",
                            "/marketplace/v1/admin/adjustments/" + change + "/approve",
                            json.writeValueAsString(Map.of("operationId", UUID.randomUUID()))));
            assertEquals(
                    200,
                    post(
                            "reviewer",
                            "/marketplace/v1/admin/adjustments/" + change + "/approve",
                            json.writeValueAsString(Map.of("operationId", UUID.randomUUID()))));
        }
        assertEquals(
                200,
                post(
                        "developer",
                        "/marketplace/v1/admin/products",
                        json.writeValueAsString(
                                Map.of(
                                        "operationId",
                                        UUID.randomUUID(),
                                        "product",
                                        new Product(
                                                "HTTP-GAME",
                                                "Gift card test",
                                                10000,
                                                "SIMULATED",
                                                true)))));
        UUID order = UUID.randomUUID();
        assertEquals(
                200,
                post(
                        "customer",
                        "/marketplace/v1/me/orders",
                        json.writeValueAsString(
                                Map.of(
                                        "operationId",
                                        order,
                                        "sku",
                                        "HTTP-GAME",
                                        "tenders",
                                        List.of(
                                                new Tender(Source.CREDIT, 7000),
                                                new Tender(Source.BALANCE, 3000))))));
        assertEquals(
                200,
                post(
                        "developer",
                        "/marketplace/v1/admin/orders/" + order + "/process",
                        json.writeValueAsString(Map.of("operationId", UUID.randomUUID()))));
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals(7000, service.view(tenantId).account("customer").creditUsed);
        assertEquals(17000, service.view(tenantId).account("customer").balance);
        assertEquals(200, get("customer", "/marketplace/v1/me", null));
    }

    static int post(String user, String path, String body) throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header(
                                "Authorization",
                                "Basic "
                                        + Base64.getEncoder()
                                                .encodeToString((user + ":test-only").getBytes()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    @Test
    void oauthScopeAloneDoesNotGrantStaffPrivileges() {
        var identity =
                context.getBean(
                        br.com.includelabs.inpay.marketplace.adapters.in.rest.MarketplaceIdentity
                                .class);
        var authorities =
                List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "SCOPE_marketplace/admin"));
        try {
            for (String group :
                    List.of(
                            "unrelated",
                            "marketplace-admin:" + UUID.randomUUID(),
                            "marketplace-admin:11111111-1111-1111-1111-111111111111")) {
                var jwt =
                        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                                .header("alg", "RS256")
                                .subject("user")
                                .claim("username", "user")
                                .claim("cognito:groups", List.of(group))
                                .build();
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .setAuthentication(
                                new org.springframework.security.oauth2.server.resource
                                        .authentication.JwtAuthenticationToken(jwt, authorities));
                assertEquals(
                        group.endsWith("11111111-1111-1111-1111-111111111111"),
                        identity.hasRole("admin"));
            }
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void realFulfillmentFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> new UnavailableFulfillmentAdapter().fulfill(tenant, new Order()));
    }
}
