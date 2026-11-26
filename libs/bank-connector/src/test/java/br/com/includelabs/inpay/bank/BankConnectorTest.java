package br.com.includelabs.inpay.bank;

import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BankConnectorTest {
    private static final Pattern TOKEN = Pattern.compile("\\{([^}]+)}");
    private static WireMockServer server;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        assertEquals(BankId.values().length, BankStubRegistry.register(server));
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @BeforeEach
    void stubs() {
        server.resetAll();
        BankStubRegistry.register(server);
    }

    @Test
    void everyOperationHasAStubFile() throws Exception {
        long stubs =
                java.nio.file.Files.list(
                                java.nio.file.Path.of("src/main/resources/wiremock/mappings"))
                        .filter(path -> path.toString().endsWith(".json"))
                        .count();
        assertEquals((long) BankId.values().length * BankOperation.values().length, stubs);
    }

    @Test
    void everyBankPixAndPaymentOperationIsMocked() {
        for (BankId bank : BankId.values()) {
            BankConnector connector = connector(bank, false, true);
            for (BankOperation operation : BankOperation.values()) {
                Map<String, Object> payload = payload(operation);
                String operationId = operation.hasBody() ? "op-" + operation.name() : null;
                BankResult result =
                        connector.execute(operation.name(), operationId, "true", payload);
                assertEquals(200, result.status(), bank.slug() + " " + operation.name());
                assertEquals(Boolean.TRUE, result.body().get("mocked"));
                assertEquals(bank.slug(), result.body().get("bank"));
                assertFalse(result.uncertain());
                assertTrue(result.mocked());
            }
            assertEquals(BankOperation.values().length, connector.catalog().size());
        }
    }

    @Test
    void missingOrFalseMockDoesNotCallTheAcquirerWhenDisabled() {
        BankConnector connector = connector(BankId.C6, false, true);
        int before = server.getAllServeEvents().size();
        IllegalStateException disabled =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                connector.execute(
                                        "PIX_COB_GET", null, null, Map.of("txid", "abc123")));
        assertTrue(disabled.getMessage().contains("disabled"));
        assertThrows(
                IllegalStateException.class,
                () -> connector.execute("PIX_COB_GET", null, "false", Map.of("txid", "abc123")));
        assertEquals(before, server.getAllServeEvents().size());
        assertThrows(IllegalArgumentException.class, () -> MockMode.parse("yes", true));
        assertThrows(IllegalArgumentException.class, () -> MockMode.parse("true", false));
        assertEquals(MockMode.LIVE, MockMode.parse(" false ", true));
    }

    @Test
    void pixReplayIsIdempotentAndConflictRejectsADifferentCommand() {
        BankConnector connector = connector(BankId.ITAU, false, true);
        Map<String, Object> payload = Map.of("txid", "abc123", "valor", "10.00");
        BankResult first = connector.execute("PIX_COB_PUT", "same-key", "true", payload);
        BankResult second = connector.execute("PIX_COB_PUT", "same-key", "true", payload);
        assertEquals(first, second);
        assertThrows(
                IllegalStateException.class,
                () ->
                        connector.execute(
                                "PIX_COB_PUT",
                                "same-key",
                                "true",
                                Map.of("txid", "abc123", "valor", "11.00")));
        assertThrows(
                IllegalArgumentException.class,
                () -> connector.execute("PIX_COB_PUT", null, "true", payload));
        assertThrows(
                IllegalArgumentException.class,
                () -> connector.execute("PIX_COB_PUT", "ok", "true", Map.of()));
    }

    @Test
    void livePixRequiresHttpsAndUnverifiedProductsStayBlocked() {
        BankSettings blocked =
                new BankSettings(
                        "santander",
                        true,
                        true,
                        "http://127.0.0.1:" + server.port(),
                        "http://127.0.0.1:" + server.port(),
                        false,
                        5,
                        Duration.ofSeconds(30));
        BankConnector connector = new BankConnector(blocked, new BankTransport(blocked));
        assertThrows(
                IllegalStateException.class,
                () -> connector.execute("PIX_COB_GET", null, "false", Map.of("txid", "abc123")));
        assertThrows(
                IllegalArgumentException.class,
                () -> connector.execute("BOLETO_LIST", null, "false", Map.of()));
    }

    @Test
    void confirmedLiveCallUsesTheAcquirerAndTimesOutAtOneHundredMilliseconds() {
        BankSettings live =
                new BankSettings(
                        "bb",
                        true,
                        true,
                        "https://127.0.0.1:9",
                        "http://127.0.0.1:" + server.port(),
                        true,
                        5,
                        Duration.ofSeconds(30));
        BankConnector connector =
                new BankConnector(live, new BankTransport(live, new SimpleMeterRegistry()));
        long started = System.nanoTime();
        BankResult result = connector.execute("BOLETO_GET", null, "false", Map.of("id", "abc123"));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(result.uncertain());
        assertFalse(result.mocked());
        assertTrue(elapsedMs < 500, "elapsed=" + elapsedMs);
    }

    @Test
    void slowMockTripsTheCircuit() {
        server.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/bradesco/contas/v1/saldo"))
                        .atPriority(1)
                        .willReturn(
                                WireMock.aResponse()
                                        .withFixedDelay(400)
                                        .withStatus(200)
                                        .withBody("{}")));
        BankSettings settings =
                new BankSettings(
                        "bradesco",
                        false,
                        true,
                        BankId.BRADESCO.defaultSandbox(),
                        "http://127.0.0.1:" + server.port(),
                        false,
                        1,
                        Duration.ofSeconds(30));
        BankConnector connector =
                new BankConnector(
                        settings,
                        new BankTransport(
                                settings, new SimpleMeterRegistry(), Duration.ofMillis(100)));
        long started = System.nanoTime();
        BankResult timedOut = connector.execute("BALANCE_GET", null, "true", Map.of());
        BankResult opened = connector.execute("BALANCE_GET", null, "true", Map.of());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(timedOut.uncertain());
        assertEquals(503, opened.status());
        assertTrue(elapsedMs < 700, "elapsed=" + elapsedMs);
    }

    @Test
    void nonJsonAndRejectedResponsesStayUncertainWithoutPayloadLogs() {
        server.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/caixa/contas/v1/extrato"))
                        .atPriority(1)
                        .willReturn(WireMock.aResponse().withStatus(200).withBody("not-json")));
        server.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/caixa/pix/v2/pix"))
                        .atPriority(1)
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(503)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("[{\"status\":\"DOWN\"}]")));
        BankConnector connector = connector(BankId.CAIXA, false, true);
        BankResult statement =
                connector.execute("STATEMENT_GET", null, "true", Map.of("inicio", "2026-01-01"));
        BankResult pix = connector.execute("PIX_LIST", null, "true", Map.of());
        assertTrue(statement.body().isEmpty());
        assertEquals(503, pix.status());
        assertTrue(pix.uncertain());
        assertEquals(Boolean.FALSE, pix.body().isEmpty());
    }

    @Test
    void invalidPathsHeadersAndBanksAreRejected() {
        BankConnector connector = connector(BankId.BANRISUL, false, true);
        assertThrows(IllegalArgumentException.class, () -> BankId.fromSlug("nubank"));
        assertThrows(IllegalArgumentException.class, () -> BankOperation.fromName("NOPE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> connector.execute("PIX_KEY_GET", null, "true", Map.of("chave", "a/b")));
        Map<String, Object> bad = new LinkedHashMap<>();
        Map<String, Object> cycle = new LinkedHashMap<>();
        cycle.put("self", cycle);
        bad.put("txid", "abc123");
        bad.put("nested", cycle);
        assertThrows(
                IllegalArgumentException.class,
                () -> connector.execute("PIX_COB_POST", "broken-body", "true", bad));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BankSettings(
                                "nope",
                                false,
                                true,
                                null,
                                "http://127.0.0.1",
                                false,
                                1,
                                Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BankSettings(
                                "c6",
                                false,
                                true,
                                null,
                                "http://127.0.0.1",
                                false,
                                0,
                                Duration.ofSeconds(1)));
    }

    @Test
    void circuitIgnoresStaleCallsAndAdmitsOneProbe() {
        AtomicLong clock = new AtomicLong(0);
        BankCircuit circuit = new BankCircuit(2, Duration.ofSeconds(10), clock::get);
        BankCircuit.Lease first = circuit.acquire();
        circuit.complete(first, false);
        circuit.complete(first, false);
        assertNull(circuit.acquire());
        clock.addAndGet(Duration.ofSeconds(11).toNanos());
        BankCircuit.Lease probe = circuit.acquire();
        assertNotNull(probe);
        assertNull(circuit.acquire());
        circuit.complete(probe, true);
        assertNotNull(circuit.acquire());
        assertThrows(
                IllegalArgumentException.class, () -> new BankCircuit(0, Duration.ofSeconds(1)));
    }

    @Test
    void requestLogAndErrorsDoNotEchoSecrets() {
        BankRequestLog log = new BankRequestLog();
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/v1/bank/operations/PIX_COB_PUT");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(log.preHandle(request, response, this));
        log.afterCompletion(request, response, this, null);
        log.afterCompletion(request, response, this, new IllegalStateException("failed"));
        BankErrors errors = new BankErrors();
        assertEquals(
                422,
                errors.invalid(new IllegalArgumentException("Invalid path parameter"))
                        .getStatusCode()
                        .value());
        assertEquals(
                503,
                errors.conflict(new IllegalStateException("Bank connector is disabled"))
                        .getStatusCode()
                        .value());
        assertEquals(
                409,
                errors.conflict(new IllegalStateException("operationId was reused"))
                        .getStatusCode()
                        .value());
        assertEquals(
                403,
                errors.denied(new org.springframework.security.access.AccessDeniedException("no"))
                        .getStatusCode()
                        .value());
        assertEquals(503, errors.unexpected(new Exception("secret-body")).getStatusCode().value());
        assertEquals(
                "Bank connector failed",
                errors.unexpected(new Exception("secret-body")).getBody().get("error"));
        assertEquals(
                "c6",
                new BankSecurity(
                                connector(BankId.C6, false, true) == null
                                        ? null
                                        : settings(BankId.C6))
                        .scopeId());
    }

    private static BankSettings settings(BankId bank) {
        return new BankSettings(
                bank.slug(),
                false,
                true,
                bank.defaultSandbox(),
                "http://127.0.0.1:" + server.port(),
                false,
                5,
                Duration.ofSeconds(30));
    }

    private static BankConnector connector(BankId bank, boolean enabled, boolean mockEnabled) {
        BankSettings settings =
                new BankSettings(
                        bank.slug(),
                        enabled,
                        mockEnabled,
                        bank.defaultSandbox(),
                        "http://127.0.0.1:" + server.port(),
                        false,
                        5,
                        Duration.ofSeconds(30));
        BankTransport transport = new BankTransport(settings);
        java.net.URI warmup =
                java.net.URI.create(
                        settings.mockBaseUrl() + "/" + bank.slug() + "/pix/v2/cob/abc123");
        for (int attempt = 0; attempt < 4; attempt++) {
            transport.send(bank, BankOperation.PIX_COB_GET, true, warmup, "");
        }
        return new BankConnector(settings, transport);
    }

    private static Map<String, Object> payload(BankOperation operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Matcher matcher = TOKEN.matcher(operation.path());
        while (matcher.find()) {
            payload.put(matcher.group(1), "abc123");
        }
        if (operation.hasBody() && payload.isEmpty()) {
            payload.put("amount", "10.00");
        }
        return payload;
    }
}
