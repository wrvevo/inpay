package br.com.includelabs.gateway.contracts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public record GatewayEvent(UUID eventId, String eventType, int schemaVersion,
    UUID aggregateId, long aggregateVersion, UUID merchantId, Instant occurredAt,
    Map<String, Object> data) {
    public GatewayEvent { data = Map.copyOf(data); }
}
