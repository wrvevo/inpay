package br.com.includelabs.inpay.webhook.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record ProviderObservation(
        UUID id, UUID merchantId, String provider, String externalReference) {}
