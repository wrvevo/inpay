package br.com.includelabs.inpay.connector;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração por instância e por estabelecimento; segredos vêm exclusivamente do ambiente. */
@ConfigurationProperties("inpay.provider")
public record ProviderSettings(
        boolean enabled,
        String name,
        UUID tenantId,
        String baseUrl,
        String queryUrl,
        String tokenUrl,
        String merchantId,
        String merchantKey,
        String clientId,
        String clientSecret,
        String accessToken,
        String secretKey,
        String appId,
        String appKey,
        String apiVersion,
        String host,
        String channel,
        String keyStore,
        String keyStorePassword,
        Duration connectTimeout,
        Duration readTimeout,
        boolean allowSensitiveCardData,
        boolean brazilEnabled) {

    /** Exige a configuração antes de aceitar comandos financeiros. */
    public void validate() {
        if (!enabled) {
            throw new ProviderUnavailableException("External connector is disabled");
        }
        if (tenantId == null) {
            throw new ProviderUnavailableException("Configure provider tenant-id");
        }
        if (connectTimeout == null
                || readTimeout == null
                || connectTimeout.isNegative()
                || connectTimeout.isZero()
                || readTimeout.isNegative()
                || readTimeout.isZero()) {
            throw new ProviderUnavailableException("Provider timeouts must be positive");
        }
        requireHttps(baseUrl);
        if (queryUrl != null && !queryUrl.isBlank()) {
            requireHttps(queryUrl);
        }
        if (tokenUrl != null && !tokenUrl.isBlank()) {
            requireHttps(tokenUrl);
        }
        if ("global-payments".equals(name) && !brazilEnabled) {
            throw new ProviderUnavailableException(
                    "Confirm BR/BRL enablement with Global Payments before enabling this account");
        }
    }

    /** Rejeita URLs com credenciais, fragmentos ou protocolo inseguro. */
    private static void requireHttps(String value) {
        var uri = java.net.URI.create(value == null ? "" : value);
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getQuery() != null) {
            throw new ProviderUnavailableException("Provider endpoints must be HTTPS base URLs");
        }
    }

    /** Evita que uma configuração incompleta seja enviada como credencial vazia. */
    public static String required(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new ProviderUnavailableException("Configure " + key);
        }
        return value;
    }

    /** Não expõe segredos ao imprimir a configuração. */
    @Override
    public String toString() {
        return "ProviderSettings[" + name + ", enabled=" + enabled + "]";
    }
}
