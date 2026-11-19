package br.com.includelabs.inpay.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ProviderAuthenticationTest {
    private ProviderSettings settings(String provider, Map<String, Object> extra) {
        Map<String, Object> values =
                new HashMap<>(
                        Map.of(
                                "name",
                                provider,
                                "connect-timeout",
                                "1s",
                                "read-timeout",
                                "2s",
                                "token-url",
                                "https://auth.example/token",
                                "client-id",
                                "client",
                                "client-secret",
                                "secret",
                                "merchant-id",
                                "merchant",
                                "merchant-key",
                                "mk_test",
                                "api-version",
                                "2021-03-22"));
        values.putAll(extra);
        Map<String, Object> source = new HashMap<>();
        values.forEach((key, value) -> source.put("inpay.provider." + key, value));
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("inpay.provider", Bindable.of(ProviderSettings.class))
                .get();
    }

    static class Transport extends ProviderTransport {
        int calls;
        String method, url;
        Map<String, String> headers;
        Object body;
        boolean form;
        Reply response =
                new Reply(200, Map.of("access_token", "external-token", "expires_in", 3600), false);

        Transport(ProviderSettings settings) {
            super(settings);
        }

        @Override
        public Reply send(
                String method, String url, Map<String, String> headers, Object body, boolean form) {
            calls++;
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.form = form;
            return response;
        }
    }

    @Test
    void redeUsesOauthAndReusesTokenUntilInvalidated() {
        var settings = settings("rede", Map.of());
        var transport = new Transport(settings);
        var auth = new ProviderAuthentication(settings, transport);
        assertEquals("Bearer external-token", auth.headers().get("Authorization"));
        auth.headers();
        assertEquals(1, transport.calls);
        assertEquals("POST", transport.method);
        assertTrue(transport.form);
        assertEquals("grant_type=client_credentials", transport.body);
        assertEquals(
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)),
                transport.headers.get("Authorization"));
        auth.invalidate();
        auth.headers();
        assertEquals(2, transport.calls);
    }

    @Test
    void expiresImmediatelyWhenProviderOmitsTtl() {
        var settings = settings("rede", Map.of());
        var transport = new Transport(settings);
        transport.response =
                new ProviderTransport.Reply(200, Map.of("access_token", "token"), false);
        var auth = new ProviderAuthentication(settings, transport);
        auth.headers();
        auth.headers();
        assertEquals(2, transport.calls);
    }

    @Test
    void safraSendsMerchantTokenWithoutBearerAndWithoutBody() {
        var settings = settings("safrapay", Map.of());
        var transport = new Transport(settings);
        transport.response =
                new ProviderTransport.Reply(200, Map.of("accessToken", "jwt-token"), false);
        var headers = new ProviderAuthentication(settings, transport).headers();
        assertEquals("mk_test", transport.headers.get("Authorization"));
        assertNull(transport.body);
        assertFalse(transport.form);
        assertEquals("Bearer jwt-token", headers.get("Authorization"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void gpApiHashesNonceWithSecretWithoutSendingAppKey() throws Exception {
        var settings =
                settings("global-payments", Map.of("app-id", "app", "app-key", "private-key"));
        var transport = new Transport(settings);
        transport.response =
                new ProviderTransport.Reply(
                        200, Map.of("token", "gp-token", "seconds_to_expire", "3600"), false);
        var headers = new ProviderAuthentication(settings, transport).headers();
        Map<String, Object> body = (Map<String, Object>) transport.body;
        String expected =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-512")
                                        .digest(
                                                (body.get("nonce") + "private-key")
                                                        .getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, body.get("secret"));
        assertFalse(body.containsKey("app_key"));
        assertEquals("Bearer gp-token", headers.get("Authorization"));
        assertEquals("2021-03-22", headers.get("X-GP-Version"));
    }

    @Test
    void getnetIncludesSellerAndEntryChannel() {
        var settings = settings("getnet", Map.of("channel", "GW"));
        var transport = new Transport(settings);
        var headers = new ProviderAuthentication(settings, transport).headers();
        assertEquals("merchant", headers.get("x-seller-id"));
        assertEquals("GW", headers.get("x-transaction-channel-entry"));
    }

    @Test
    void cieloUsesMerchantHeadersWithoutOauth() {
        var settings = settings("cielo", Map.of());
        var transport = new Transport(settings);
        var headers = new ProviderAuthentication(settings, transport).headers();
        assertEquals(0, transport.calls);
        assertEquals("merchant", headers.get("MerchantId"));
        assertEquals("mk_test", headers.get("MerchantKey"));
    }

    @Test
    void authErrorDoesNotProduceAuthorizationHeader() {
        var settings = settings("rede", Map.of());
        var transport = new Transport(settings);
        transport.response =
                new ProviderTransport.Reply(
                        401, Map.of("message", "sensitive-provider-content"), false);
        var exception =
                assertThrows(
                        ProviderUnavailableException.class,
                        () -> new ProviderAuthentication(settings, transport).headers());
        assertFalse(exception.getMessage().contains("sensitive-provider-content"));
        assertEquals(1, transport.calls);
    }

    @Test
    void binRequiresConfiguredMtlsInsteadOfSilentlySkippingSignature() {
        var settings = settings("bin", Map.of());
        var transport = new Transport(settings);
        assertThrows(
                ProviderUnavailableException.class,
                () -> new ProviderAuthentication(settings, transport).headers());
        assertEquals(0, transport.calls);
    }
}
