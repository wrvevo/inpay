package br.com.includelabs.inpay.cielo.adapters.out.http;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.connector.*;
import br.com.includelabs.inpay.connector.domain.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.ObjectMapper;

class CieloApiAdapterTest {
    private final CieloApiAdapter adapter = new CieloApiAdapter();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID operation = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String file) throws Exception {
        try (var input = getClass().getResourceAsStream("/provider-examples/" + file + ".json")) {
            return (Map<String, Object>) mapper.readValue(input, Map.class).get("payload");
        }
    }

    @Test
    void sendsNativeAuthorizationAndInterpretsFinancialStatus() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var seenMethod = new AtomicReference<String>();
        var seenPath = new AtomicReference<String>();
        var seenBody = new AtomicReference<Map>();
        byte[] response;
        try (var input = getClass().getResourceAsStream("/provider-response.json")) {
            response = input.readAllBytes();
        }
        server.createContext(
                "/",
                exchange -> {
                    seenMethod.set(exchange.getRequestMethod());
                    seenPath.set(exchange.getRequestURI().toString());
                    seenBody.set(
                            mapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            var config =
                    new Binder(
                                    new MapConfigurationPropertySource(
                                            Map.of(
                                                    "inpay.provider.connect-timeout",
                                                    "1s",
                                                    "inpay.provider.read-timeout",
                                                    "2s")))
                            .bind("inpay.provider", Bindable.of(ProviderSettings.class))
                            .get();
            var body = payload("authorize");
            body.put("optional_test_field", Map.of("preserved", true));
            var call = adapter.prepare(ProviderOperation.AUTHORIZE, null, operation, body);
            var reply =
                    new ProviderTransport(config)
                            .send(
                                    call.method(),
                                    "http://127.0.0.1:"
                                            + server.getAddress().getPort()
                                            + call.path(),
                                    call.headers(),
                                    call.body(),
                                    false);
            assertEquals("POST", seenMethod.get());
            assertEquals("/1/sales/", seenPath.get());
            assertEquals(Map.of("preserved", true), seenBody.get().get("optional_test_field"));
            var result =
                    adapter.interpret(
                            ProviderOperation.AUTHORIZE, null, reply.status(), reply.body());
            assertEquals("pay-100", result.providerId());
            assertEquals("1", result.status());
            assertFalse(result.uncertain());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesDocumentedCaptureAndCancelRoutes() throws Exception {
        var capture =
                adapter.prepare(
                        ProviderOperation.CAPTURE, "pay-100", operation, payload("capture"));
        assertEquals("PUT", capture.method());
        assertEquals("/1/sales/pay-100/capture?amount=1000", capture.path());
        var cancel =
                adapter.prepare(ProviderOperation.CANCEL, "pay-100", operation, payload("cancel"));
        assertEquals("PUT", cancel.method());
        assertEquals("/1/sales/pay-100/void?amount=1000", cancel.path());
    }

    @Test
    void rejectsMissingRequiredAuthorizationFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(ProviderOperation.AUTHORIZE, null, operation, Map.of()));
    }

    @Test
    void doesNotConvertEmptyHttpSuccessIntoFinancialApproval() {
        var result = adapter.interpret(ProviderOperation.AUTHORIZE, null, 200, Map.of());
        assertTrue(result.uncertain() || "PROVIDER_REJECTED".equals(result.status()));
    }
}
