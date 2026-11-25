package br.com.includelabs.inpay.payment.adapters.out.bank;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** URLs internas dos conectores. Não aceita destino livre informado pelo chamador. */
@ConfigurationProperties("inpay.bank-gateway")
public record BankGatewaySettings(Map<String, String> urls, String username, String password) {
    public BankGatewaySettings {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("c6", "http://127.0.0.1:8111");
        defaults.put("itau", "http://127.0.0.1:8112");
        defaults.put("santander", "http://127.0.0.1:8113");
        defaults.put("bb", "http://127.0.0.1:8114");
        defaults.put("bradesco", "http://127.0.0.1:8115");
        defaults.put("caixa", "http://127.0.0.1:8116");
        defaults.put("banrisul", "http://127.0.0.1:8117");
        if (urls != null) {
            defaults.putAll(urls);
        }
        urls = Map.copyOf(defaults);
        if (username == null || username.isBlank()) {
            username = "developer";
        }
        if (password == null || password.isBlank()) {
            password = "local-only-change-me";
        }
    }

    public String url(String bank) {
        String value = urls.get(bank);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unknown bank");
        }
        return value.replaceAll("/+$", "");
    }
}
