package br.com.includelabs.inpay.connector.domain;

/**
 * Resultado mínimo, sem cartão, credenciais, corpo bruto ou dados pessoais. HTTP 2xx não implica
 * aprovação.
 */
public record ProviderResult(
        String providerId,
        String status,
        String code,
        int httpStatus,
        boolean uncertain,
        java.util.Map<String, String> paymentArtifacts) {
    /** Compatibilidade: respostas anteriores não carregam artefatos públicos de pagamento. */
    public ProviderResult(
            String providerId, String status, String code, int httpStatus, boolean uncertain) {
        this(providerId, status, code, httpStatus, uncertain, java.util.Map.of());
    }

    public ProviderResult {
        paymentArtifacts =
                paymentArtifacts == null
                        ? java.util.Map.of()
                        : java.util.Map.copyOf(paymentArtifacts);
    }

    /** Representa resultado que exige consulta antes de qualquer nova tentativa financeira. */
    public static ProviderResult unknown(String id, int httpStatus) {
        return new ProviderResult(id, "UNKNOWN", "CONSULT_PROVIDER", httpStatus, true);
    }
}
