package br.com.includelabs.inpay.bank;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuração de um processo conector. Segredos não entram neste objeto. */
@ConfigurationProperties("inpay.bank")
public record BankSettings(
        @DefaultValue("c6") String id,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean mockEnabled,
        String liveBaseUrl,
        @DefaultValue("http://127.0.0.1:8099") String mockBaseUrl,
        @DefaultValue("false") boolean confirmUnverifiedPaths,
        @DefaultValue("5") int failureThreshold,
        @DefaultValue("30s") Duration openDuration) {

    public BankSettings {
        BankId bank = BankId.fromSlug(id);
        id = bank.slug();
        if (liveBaseUrl == null || liveBaseUrl.isBlank()) {
            liveBaseUrl = bank.defaultSandbox();
        }
        if (mockBaseUrl == null || mockBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Mock base URL is required");
        }
        if (failureThreshold < 1 || openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("Invalid circuit settings");
        }
    }

    public BankId bank() {
        return BankId.fromSlug(id);
    }
}
