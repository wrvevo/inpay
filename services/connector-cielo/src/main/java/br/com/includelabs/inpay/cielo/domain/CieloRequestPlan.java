package br.com.includelabs.inpay.cielo.domain;

/**
 * Plano de requisição com destino restrito; suporta JSON objeto, número, texto ou corpo ausente.
 */
public record CieloRequestPlan(String method, String path, boolean queryHost, Object body) {
    /** Impede divulgação acidental de corpo, identificadores e dados de cartão em logs. */
    @Override
    public String toString() {
        return "CieloRequestPlan[method=" + method + "]";
    }
}
