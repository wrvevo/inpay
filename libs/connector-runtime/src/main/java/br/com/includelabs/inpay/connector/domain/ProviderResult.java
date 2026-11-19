package br.com.includelabs.inpay.connector.domain;

/**
 * Resultado mínimo, sem cartão, credenciais, corpo bruto ou dados pessoais. HTTP 2xx não implica
 * aprovação.
 */
public record ProviderResult(
        String providerId, String status, String code, int httpStatus, boolean uncertain) {
    /** Representa resultado que exige consulta antes de qualquer nova tentativa financeira. */
    public static ProviderResult unknown(String id, int httpStatus) {
        return new ProviderResult(id, "UNKNOWN", "CONSULT_PROVIDER", httpStatus, true);
    }
}
