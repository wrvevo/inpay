package br.com.includelabs.gateway.notification.domain;

import java.util.UUID;
import java.time.Instant;
public record Delivery(UUID id, UUID merchantId, UUID eventId, String status) {}
