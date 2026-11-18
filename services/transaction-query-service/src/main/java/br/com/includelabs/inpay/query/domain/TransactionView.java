package br.com.includelabs.inpay.query.domain;

import java.util.UUID;

/**
 * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
 * persistência.
 */
public record TransactionView(UUID id, UUID merchantId, long amount, String status, long version) {}
