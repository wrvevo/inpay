package br.com.includelabs.gateway.merchant.domain;

import java.util.UUID;
import java.time.Instant;
public record Merchant(UUID id, String legalName, boolean active) {}
