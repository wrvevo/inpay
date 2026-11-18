package br.com.includelabs.inpay.contracts;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Componente de infraestrutura do InPay responsável por InPayEvent. Não deve ser referenciado pelo
 * domínio.
 */
public record InPayEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID aggregateId,
        long aggregateVersion,
        UUID merchantId,
        Instant occurredAt,
        Map<String, Object> data) {

    /** Valida e normaliza os componentes do registro antes de disponibilizá-lo ao domínio. */
    public InPayEvent {
        data = Map.copyOf(data);
    }
}
