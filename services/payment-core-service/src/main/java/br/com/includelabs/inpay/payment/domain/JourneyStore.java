package br.com.includelabs.inpay.payment.domain;

import java.util.*;

/** Porta de persistência; a reserva é confirmada antes de qualquer chamada financeira remota. */
public interface JourneyStore {
    record Reservation(UUID id, boolean created) {}

    Reservation reserve(
            UUID tenant,
            String key,
            String fingerprint,
            String merchantOrder,
            long amount,
            String method);

    Map<String, Object> get(UUID tenant, UUID id);

    Map<String, Object> byKey(UUID tenant, String key);

    boolean claimCancellation(UUID tenant, UUID id, long version);

    void apply(UUID tenant, UUID id, long expectedVersion, JourneyPort.Result result);
}
