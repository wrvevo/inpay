package br.com.includelabs.inpay.globalpayments.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Authorization(
        UUID operationId, UUID merchantId, long amount, String instrumentToken, Outcome outcome) {

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
