package br.com.includelabs.gateway.routing.domain;

import java.util.UUID;
import java.time.Instant;
public record Route(UUID id, UUID merchantId, String provider, String affiliationId) {}
