package br.com.includelabs.inpay.itau.bootstrap;

import br.com.includelabs.inpay.itau.domain.ItauProduct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credenciais só por ambiente. O toString não inclui segredo nem caminho de chave. */
@ConfigurationProperties("inpay.itau")
public record ItauSettings(
        @DefaultValue("https://devportal.itau.com.br/api/jwt") String tokenUrl,
        @DefaultValue("https://devportal.itau.com.br/sandboxapi/pix_recebimentos_ext_v2/v2")
                String pixBaseUrl,
        @DefaultValue("https://devportal.itau.com.br/sandboxapi/cash_management_ext_v2/v2")
                String boletoBaseUrl,
        @DefaultValue("https://devportal.itau.com.br/sandboxapi/sispag/v1") String sispagBaseUrl,
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("") String certificate,
        @DefaultValue("") String privateKey,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean confirmUnverifiedPaths) {

    public String baseUrl(ItauProduct product) {
        return switch (product) {
            case AUTHENTICATION -> tokenUrl;
            case PIX_RECEBIMENTOS -> pixBaseUrl;
            case BOLETO -> boletoBaseUrl;
            case SISPAG -> sispagBaseUrl;
        };
    }

    @Override
    public String toString() {
        return "ItauSettings[enabled=" + enabled + "]";
    }
}
