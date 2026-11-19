package br.com.includelabs.inpay.connector;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ProviderRuntimeTest {
    private final UUID merchant = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ProviderSettings settings(Map<String, Object> overrides) {
        Map<String, Object> values =
                new HashMap<>(
                        Map.of(
                                "enabled",
                                true,
                                "name",
                                "pagbank",
                                "tenant-id",
                                merchant.toString(),
                                "base-url",
                                "https://provider.example",
                                "access-token",
                                "secret",
                                "connect-timeout",
                                "1s",
                                "read-timeout",
                                "2s"));
        values.putAll(overrides);
        Map<String, Object> prefixed = new HashMap<>();
        values.forEach((key, value) -> prefixed.put("inpay.provider." + key, value));
        return new Binder(new MapConfigurationPropertySource(prefixed))
                .bind("inpay.provider", Bindable.of(ProviderSettings.class))
                .get();
    }

    static class Journal implements OperationJournal {
        Map<UUID, String> fingerprints = new HashMap<>();
        Map<UUID, ProviderResult> results = new HashMap<>();

        public ProviderResult reserve(UUID merchant, UUID operation, String fingerprint) {
            String previous = fingerprints.putIfAbsent(operation, fingerprint);
            if (previous != null && !previous.equals(fingerprint))
                throw new IllegalStateException("conflict");
            return previous == null
                    ? null
                    : results.getOrDefault(operation, ProviderResult.unknown(null, 0));
        }

        public void complete(UUID merchant, UUID operation, ProviderResult result) {
            results.put(operation, result);
        }
    }

    static class Transport extends ProviderTransport {
        int calls;
        Reply reply = new Reply(0, Map.of(), true);

        Transport(ProviderSettings settings) {
            super(settings);
        }

        @Override
        public Reply send(
                String method, String url, Map<String, String> headers, Object body, boolean form) {
            calls++;
            return reply;
        }
    }

    static class Port implements ProviderPort {
        public ProviderCall prepare(
                ProviderOperation operation,
                String id,
                UUID operationId,
                Map<String, Object> payload) {
            return new ProviderCall("POST", "/payments", false, payload, Map.of());
        }

        public ProviderResult interpret(
                ProviderOperation operation, String id, int status, Map<String, Object> body) {
            return new ProviderResult("id", "AUTHORIZED", "00", status, false);
        }
    }

    private ProviderService service(ProviderSettings config, Transport transport, Journal journal) {
        return new ProviderService(
                config,
                new Port(),
                transport,
                new ProviderAuthentication(config, transport),
                journal);
    }

    @Test
    void timeoutReservesOperationAndDoesNotRepeatCharge() {
        var config = settings(Map.of());
        var transport = new Transport(config);
        var service = service(config, transport, new Journal());
        UUID operation = UUID.randomUUID();
        assertTrue(
                service.execute(
                                merchant,
                                operation,
                                ProviderOperation.AUTHORIZE,
                                null,
                                Map.of("amount", 100))
                        .uncertain());
        assertTrue(
                service.execute(
                                merchant,
                                operation,
                                ProviderOperation.AUTHORIZE,
                                null,
                                Map.of("amount", 100))
                        .uncertain());
        assertEquals(1, transport.calls);
        assertThrows(
                IllegalStateException.class,
                () ->
                        service.execute(
                                merchant,
                                operation,
                                ProviderOperation.AUTHORIZE,
                                null,
                                Map.of("amount", 101)));
        assertEquals(1, transport.calls);
    }

    @Test
    void deniesDifferentTenantBeforeAnyExternalRequest() {
        var config = settings(Map.of());
        var transport = new Transport(config);
        var service = service(config, transport, new Journal());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        service.execute(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                ProviderOperation.AUTHORIZE,
                                null,
                                Map.of("amount", 1)));
        assertEquals(0, transport.calls);
    }

    @Test
    void rejectsUnsafeUrlsAndPathInjection() {
        assertThrows(
                ProviderUnavailableException.class,
                () -> settings(Map.of("base-url", "http://provider.example")).validate());
        var config = settings(Map.of());
        var transport = new Transport(config);
        var service = service(config, transport, new Journal());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.execute(
                                merchant,
                                UUID.randomUUID(),
                                ProviderOperation.CAPTURE,
                                "../other",
                                Map.of()));
        assertEquals(0, transport.calls);
    }

    @Test
    void blocksSimulationAliasAndRawCardByDefault() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonFields.validatePayload(Map.of("card", Map.of("id", "ptok_test")), false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        JsonFields.validatePayload(
                                Map.of("card", Map.of("number", "4111111111111111")), false));
        assertDoesNotThrow(
                () ->
                        JsonFields.validatePayload(
                                Map.of("address", Map.of("number", "100")), false));
        String data =
                JsonFields.fingerprintData(
                                Map.of(
                                        "card",
                                        Map.of(
                                                "number",
                                                "4111111111111111",
                                                "security_code",
                                                "999",
                                                "token",
                                                "provider-token")))
                        .toString();
        assertFalse(data.contains("411111"));
        assertFalse(data.contains("999"));
        assertTrue(data.contains("provider-token"));
    }

    @Test
    void requiresExplicitEnablementAndBrazilContractForGpApi() {
        assertThrows(
                ProviderUnavailableException.class,
                () -> settings(Map.of("enabled", false)).validate());
        assertThrows(
                ProviderUnavailableException.class,
                () ->
                        settings(Map.of("name", "global-payments", "brazil-enabled", false))
                                .validate());
    }

    @Test
    void doesNotAutomaticallyRetryUnauthorizedOrRateLimitedRequests() {
        for (int code : new int[] {401, 429, 500}) {
            var config = settings(Map.of());
            var transport = new Transport(config);
            transport.reply = new ProviderTransport.Reply(code, Map.of(), false);
            var service = service(config, transport, new Journal());
            UUID operation = UUID.randomUUID();
            var result =
                    service.execute(
                            merchant,
                            operation,
                            ProviderOperation.AUTHORIZE,
                            null,
                            Map.of("amount", 100));
            service.execute(
                    merchant, operation, ProviderOperation.AUTHORIZE, null, Map.of("amount", 100));
            assertEquals(1, transport.calls);
            assertEquals(code != 401, result.uncertain());
        }
    }

    @Test
    void canonicalFingerprintDoesNotDependOnObjectKeyOrder() {
        var config = settings(Map.of());
        var transport = new Transport(config);
        var service = service(config, transport, new Journal());
        UUID operation = UUID.randomUUID();
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("amount", 100);
        first.put("currency", "BRL");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("currency", "BRL");
        second.put("amount", 100);
        service.execute(merchant, operation, ProviderOperation.AUTHORIZE, null, first);
        service.execute(merchant, operation, ProviderOperation.AUTHORIZE, null, second);
        assertEquals(1, transport.calls);
    }
}
