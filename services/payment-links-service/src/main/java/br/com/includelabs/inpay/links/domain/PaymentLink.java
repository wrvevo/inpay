package br.com.includelabs.inpay.links.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record PaymentLink(
        UUID id,
        UUID merchantId,
        String publicCode,
        String orderId,
        long amount,
        Instant expiresAt,
        boolean revoked) {

    /** Valida e normaliza os componentes do registro antes de disponibilizá-lo ao domínio. */
    public PaymentLink {
        if (amount <= 0 || expiresAt == null || orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Invalid link");
        }
    }

    /**
     * Verifica se o link não foi revogado e ainda está dentro do prazo de validade.
     *
     * @param now instante usado para verificar validade sem depender do relógio dentro da regra
     * @return true quando todas as condições de uso forem atendidas
     */
    public boolean available(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
