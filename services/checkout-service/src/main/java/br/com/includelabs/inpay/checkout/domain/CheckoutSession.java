package br.com.includelabs.inpay.checkout.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record CheckoutSession(UUID id, UUID merchantId, UUID linkId, Instant expiresAt) {}
