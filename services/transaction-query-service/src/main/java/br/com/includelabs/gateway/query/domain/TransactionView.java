package br.com.includelabs.gateway.query.domain;

import java.util.UUID;
import java.time.Instant;
public record TransactionView(UUID id, UUID merchantId, long amount, String status, long version) {}
