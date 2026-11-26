package br.com.includelabs.inpay.c6.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credenciais só por ambiente. O toString não inclui segredo nem caminho de chave. */
@ConfigurationProperties("inpay.c6")
public record C6Settings(
        @DefaultValue("https://baas-api-sandbox.c6bank.info") String baseUrl,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("") String pixKey,
        @DefaultValue("") String certificate,
        @DefaultValue("") String privateKey,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean mockEnabled,
        @DefaultValue("false") boolean confirmUnverifiedPaths,
        @DefaultValue("false") boolean kafkaEnabled,
        @DefaultValue("21") String billingScheme,
        @DefaultValue("inpay") String partnerName,
        @DefaultValue("0.2.0") String partnerVersion) {

    public boolean credentialsPresent() {
        return !clientId.isBlank()
                && !clientSecret.isBlank()
                && !certificate.isBlank()
                && !privateKey.isBlank();
    }

    @Override
    public String toString() {
        return "C6Settings[enabled=" + enabled + ", sandbox=" + baseUrl + "]";
    }
}
