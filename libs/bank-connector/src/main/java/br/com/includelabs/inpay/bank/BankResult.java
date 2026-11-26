package br.com.includelabs.inpay.bank;

import java.util.Map;

/** Resultado normalizado. O corpo nativo fica em memória e não é gravado em log. */
public record BankResult(
        String bank,
        String operation,
        int status,
        boolean uncertain,
        boolean mocked,
        Map<String, Object> body) {

    public BankResult {
        body = body == null ? Map.of() : Map.copyOf(body);
    }

    public static BankResult unavailable(String bank, String operation, boolean mocked) {
        return new BankResult(
                bank, operation, 503, true, mocked, Map.of("error", "PROVIDER_UNAVAILABLE"));
    }
}
