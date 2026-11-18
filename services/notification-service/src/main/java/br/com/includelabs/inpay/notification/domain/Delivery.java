package br.com.includelabs.inpay.notification.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Delivery(UUID id, UUID merchantId, UUID eventId, String status) {}
