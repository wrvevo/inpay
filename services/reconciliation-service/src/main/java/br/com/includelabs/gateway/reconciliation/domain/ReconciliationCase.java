package br.com.includelabs.gateway.reconciliation.domain;

import java.util.UUID;
import java.time.Instant;
public record ReconciliationCase(UUID id, UUID merchantId, String provider, String status) {}
