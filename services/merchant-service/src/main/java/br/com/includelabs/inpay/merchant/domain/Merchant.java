package br.com.includelabs.inpay.merchant.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record Merchant(UUID id, String legalName, boolean active) {}
