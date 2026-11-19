package br.com.includelabs.inpay.connector;

import static br.com.includelabs.inpay.connector.ProviderSettings.required;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Autenticação específica do adquirente. Tokens Cognito nunca são enviados à API externa. */
public class ProviderAuthentication {

    private final ProviderSettings settings;

    private final ProviderTransport transport;

    private String cachedToken;

    private Instant expiresAt = Instant.EPOCH;

    /** Recebe configuração e transporte para permitir testes sem credenciais reais. */
    public ProviderAuthentication(ProviderSettings settings, ProviderTransport transport) {
        this.settings = settings;
        this.transport = transport;
    }

    /** Produz cabeçalhos a partir dos segredos da instância, nunca de entrada do comprador. */
    public Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        switch (settings.name()) {
            case "cielo" -> {
                headers.put("MerchantId", required(settings.merchantId(), "merchant-id"));
                headers.put("MerchantKey", required(settings.merchantKey(), "merchant-key"));
            }
            case "bin" -> {
                required(settings.keyStore(), "key-store (BIN mTLS)");
                headers.put("merchant_id", required(settings.merchantId(), "merchant-id"));
                headers.put("merchant_key", required(settings.merchantKey(), "merchant-key"));
            }
            case "stone" -> {
                if (!Arrays.asList(
                                System.getProperty("jdk.httpclient.allowRestrictedHeaders", "")
                                        .split(","))
                        .contains("host")) {
                    throw new ProviderUnavailableException(
                            "Start JVM with -Djdk.httpclient.allowRestrictedHeaders=host for Stone");
                }
                headers.put(
                        "Authorization", basic(required(settings.secretKey(), "secret-key"), ""));
                headers.put("Host", required(settings.host(), "host"));
            }
            case "pagbank" ->
                    headers.put(
                            "Authorization",
                            "Bearer " + required(settings.accessToken(), "access-token"));
            case "rede", "getnet", "safrapay", "global-payments" ->
                    headers.put("Authorization", "Bearer " + token());
            default -> throw new ProviderUnavailableException("Unknown provider");
        }
        if ("global-payments".equals(settings.name())) {
            headers.put("X-GP-Version", required(settings.apiVersion(), "api-version"));
        }
        if ("getnet".equals(settings.name())) {
            headers.put("x-transaction-channel-entry", required(settings.channel(), "channel"));
            headers.put("x-seller-id", required(settings.merchantId(), "merchant-id (seller-id)"));
        }
        return headers;
    }

    /** Descarta o cache após 401. A operação financeira não é repetida automaticamente. */
    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }

    /** Renova o token antes da validade e serializa renovações simultâneas por instância. */
    private synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        String url = required(settings.tokenUrl(), "token-url");
        ProviderTransport.Reply reply;
        String field = "access_token";
        long ttl;
        if ("safrapay".equals(settings.name())) {
            reply =
                    transport.send(
                            "POST",
                            url,
                            Map.of(
                                    "Authorization",
                                    required(settings.merchantKey(), "merchant-key")),
                            null,
                            false);
            field = "accessToken";
        } else if ("global-payments".equals(settings.name())) {
            String nonce = UUID.randomUUID().toString();
            reply =
                    transport.send(
                            "POST",
                            url,
                            Map.of("X-GP-Version", required(settings.apiVersion(), "api-version")),
                            Map.of(
                                    "app_id",
                                    required(settings.appId(), "app-id"),
                                    "nonce",
                                    nonce,
                                    "secret",
                                    sha512(nonce + required(settings.appKey(), "app-key")),
                                    "grant_type",
                                    "client_credentials"),
                            false);
            field = "token";
        } else {
            reply =
                    transport.send(
                            "POST",
                            url,
                            Map.of(
                                    "Authorization",
                                    basic(
                                            required(settings.clientId(), "client-id"),
                                            required(settings.clientSecret(), "client-secret"))),
                            "grant_type=client_credentials",
                            true);
        }
        String token = JsonFields.text(reply.body(), field);
        if (reply.uncertain() || reply.status() < 200 || reply.status() >= 300 || token.isBlank()) {
            throw new ProviderUnavailableException(
                    "Provider authentication failed; payment was not sent");
        }
        if ("safrapay".equals(settings.name())) {
            ttl = jwtTtl(token);
        } else {
            String value =
                    JsonFields.text(
                            reply.body(),
                            "global-payments".equals(settings.name())
                                    ? "seconds_to_expire"
                                    : "expires_in");
            try {
                ttl = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                ttl = 0;
            }
        }
        cachedToken = token;
        expiresAt =
                Instant.now().plusSeconds(Math.max(0, ttl - Math.min(60, Math.max(1, ttl / 10))));
        return token;
    }

    /**
     * Usa exp somente para cache de token recebido em TLS; não substitui validação de identidade.
     */
    private long jwtTtl(String token) {
        try {
            var body =
                    new tools.jackson.databind.ObjectMapper()
                            .readValue(
                                    Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                                    Map.class);
            return Long.parseLong(body.get("exp").toString()) - Instant.now().getEpochSecond();
        } catch (Exception exception) {
            return 0;
        }
    }

    /** Codifica HTTP Basic apenas para provedores que documentam esse mecanismo. */
    private static String basic(String user, String password) {
        return "Basic "
                + Base64.getEncoder()
                        .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** Deriva o segredo transitório GP-API; a app-key não é transmitida. */
    private static String sha512(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-512")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ProviderUnavailableException("SHA-512 unavailable");
        }
    }
}
