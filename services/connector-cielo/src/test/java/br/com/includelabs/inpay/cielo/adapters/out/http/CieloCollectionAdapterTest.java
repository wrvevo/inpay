package br.com.includelabs.inpay.cielo.adapters.out.http;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.cielo.application.CieloCollectionService;
import br.com.includelabs.inpay.cielo.domain.*;
import br.com.includelabs.inpay.connector.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.ObjectMapper;

class CieloCollectionAdapterTest {
    private final CieloCollectionAdapter adapter = new CieloCollectionAdapter();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String id = "11111111-1111-1111-1111-111111111111";

    @Test
    @SuppressWarnings("unchecked")
    void allFortyPostmanRequestsMapToImplementedRoutes() throws Exception {
        Map<String, Object> catalog =
                mapper.readValue(
                        getClass().getResourceAsStream("/cielo-collection/catalog.json"),
                        Map.class);
        List<Map<String, Object>> requests = (List<Map<String, Object>>) catalog.get("requests");
        assertEquals(40, requests.size());
        Set<CieloOperation> covered = new HashSet<>();
        for (Map<String, Object> entry : requests) {
            CieloOperation operation = CieloOperation.valueOf(entry.get("operation").toString());
            covered.add(operation);
            Map<String, Object> example = (Map<String, Object>) entry.get("example");
            String resource =
                    example.containsKey("resourceId")
                            ? operation == CieloOperation.QUERY_BIN ? "402400" : id
                            : null;
            Long amount =
                    example.get("amount") == null
                            ? null
                            : ((Number) example.get("amount")).longValue();
            CieloRequestPlan call =
                    adapter.prepare(operation, resource, amount, example.get("payload"));
            assertEquals(entry.get("externalMethod"), call.method(), entry.get("name").toString());
            String expected =
                    entry.get("externalPath")
                            .toString()
                            .replace("{resourceId}", resource == null ? "" : resource)
                            .replace("{amount}", Objects.toString(amount, ""));
            assertEquals(expected, call.path(), entry.get("name").toString());
            assertEquals("query".equals(entry.get("host")), call.queryHost());
        }
        assertEquals(EnumSet.allOf(CieloOperation.class), covered);
    }

    @ParameterizedTest
    @EnumSource(CieloOperation.class)
    void rejectsInjectedOrUnexpectedResourceIdentifier(CieloOperation operation) {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(operation, "../sales?evil=true", null, Map.of()));
    }

    @Test
    void captureAndVoidAllowTotalAndPartialAmountsButRejectUnexpectedBody() {
        assertEquals(
                "/1/sales/" + id + "/capture",
                adapter.prepare(CieloOperation.CAPTURE_PAYMENT, id, null, null).path());
        assertEquals(
                "/1/sales/" + id + "/void?amount=400",
                adapter.prepare(CieloOperation.VOID_PAYMENT, id, 400L, null).path());
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(CieloOperation.VOID_PAYMENT, id, 0L, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        adapter.prepare(
                                CieloOperation.CAPTURE_PAYMENT, id, null, Map.of("Amount", 10)));
    }

    @Test
    void validatesScalarContractsWithoutTruncatingDecimals() {
        assertEquals(
                20L, adapter.prepare(CieloOperation.UPDATE_RECURRENT_DAY, id, null, 20).body());
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(CieloOperation.UPDATE_RECURRENT_AMOUNT, id, null, 12.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(CieloOperation.UPDATE_RECURRENT_DAY, id, null, 32));
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(CieloOperation.UPDATE_RECURRENT_INTERVAL, id, null, 7));
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.prepare(CieloOperation.UPDATE_RECURRENT_END_DATE, id, null, "bad"));
    }

    @Test
    void serializesScalarAndObjectBodiesOverHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> sent = new AtomicReference<>();
        server.createContext(
                "/",
                exchange -> {
                    sent.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                });
        server.start();
        try {
            ProviderSettings settings =
                    new Binder(
                                    new MapConfigurationPropertySource(
                                            Map.of(
                                                    "inpay.provider.connect-timeout",
                                                    "2s",
                                                    "inpay.provider.read-timeout",
                                                    "2s")))
                            .bind("inpay.provider", Bindable.of(ProviderSettings.class))
                            .get();
            ProviderTransport transport = new ProviderTransport(settings);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertEquals(204, transport.send("PUT", url, Map.of(), "2030-12-01", false).status());
            assertEquals("\"2030-12-01\"", sent.get());
            transport.send("PUT", url, Map.of(), 1500L, false);
            assertEquals("1500", sent.get());
            transport.send("PUT", url, Map.of(), null, false);
            assertEquals("", sent.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void responsesRemoveCardDataAndCustomerDetailsWhileRetainingUsefulCodesAndTokens() {
        Map<String, Object> sanitized =
                CieloCollectionService.sanitize(
                        Map.of(
                                "Customer",
                                Map.of("Name", "private"),
                                "CardToken",
                                "test-token",
                                "Payment",
                                Map.of(
                                        "Status",
                                        1,
                                        "ReturnMessage",
                                        "echo PAN",
                                        "CreditCard",
                                        Map.of(
                                                "CardNumber",
                                                "4024007153763191",
                                                "SecurityCode",
                                                "123",
                                                "Brand",
                                                "Visa")),
                                "items",
                                List.of(Map.of("Code", 126, "Message", "echo card"))));
        assertFalse(sanitized.containsKey("Customer"));
        assertEquals("test-token", sanitized.get("CardToken"));
        assertNull(JsonFields.get(sanitized, "Payment.CreditCard.CardNumber"));
        assertNull(JsonFields.get(sanitized, "items.0.Message"));
        assertEquals(126, JsonFields.get(sanitized, "items.0.Code"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        JsonFields.validatePayload(
                                Map.of("Wallet", Map.of("Cavv", "secret")), false));
    }
}
