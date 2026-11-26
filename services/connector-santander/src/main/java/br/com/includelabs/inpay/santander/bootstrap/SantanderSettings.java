package br.com.includelabs.inpay.santander.bootstrap;

import br.com.includelabs.inpay.santander.domain.SantanderProduct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credenciais só por ambiente. O toString não inclui segredo nem caminho de chave. */
@ConfigurationProperties("inpay.santander")
public record SantanderSettings(
        @DefaultValue("https://trust-sandbox.api.santander.com.br/auth/oauth/v2/token")
                String tokenUrl,
        @DefaultValue("https://pix.santander.com.br/api/v1/sandbox") String pixBaseUrl,
        @DefaultValue("https://trust-sandbox.api.santander.com.br/collection_bill_management/v2")
                String billingBaseUrl,
        @DefaultValue("https://trust-sandbox.api.santander.com.br/management_payments_partners/v1")
                String paymentsBaseUrl,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("") String certificate,
        @DefaultValue("") String privateKey,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean confirmUnverifiedPaths) {

    public String baseUrl(SantanderProduct product) {
        return switch (product) {
            case AUTHENTICATION -> tokenUrl;
            case PIX -> pixBaseUrl;
            case BILLING -> billingBaseUrl;
            case PAYMENTS -> paymentsBaseUrl;
        };
    }

    @Override
    public String toString() {
        return "SantanderSettings[enabled=" + enabled + "]";
    }
}
