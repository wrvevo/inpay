package br.com.includelabs.inpay.routing.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Route(UUID id, UUID merchantId, String provider, String affiliationId) {}
