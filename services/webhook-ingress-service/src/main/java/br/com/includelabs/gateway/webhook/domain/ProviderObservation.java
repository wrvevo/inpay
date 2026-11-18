package br.com.includelabs.gateway.webhook.domain;

import java.util.UUID;
import java.time.Instant;
public record ProviderObservation(UUID id, UUID merchantId, String provider, String externalReference) {}
