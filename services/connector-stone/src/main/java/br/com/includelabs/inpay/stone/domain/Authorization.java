package br.com.includelabs.inpay.stone.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Authorization(
        UUID operationId,
        UUID merchantId,
        long amount,
        String instrumentToken,
        Outcome outcome,
        java.util.Map<String, Object> providerPayload,
        br.com.includelabs.inpay.connector.domain.ProviderResult externalResult) {

    /** Preserva o contrato do simulador local. */
    public Authorization(
            UUID operationId,
            UUID merchantId,
            long amount,
            String instrumentToken,
            Outcome outcome) {
        this(operationId, merchantId, amount, instrumentToken, outcome, null, null);
    }

    /** Impede exposição acidental de tokens e dados nativos em logs. */
    @Override
    public String toString() {
        return "Authorization[operationId=" + operationId + ", outcome=" + outcome + "]";
    }

    /**
     * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
     * persistência.
     */
    public enum Outcome {
        AUTHORIZED,
        DECLINED,
        UNKNOWN,
        UNSUPPORTED
    }
}
