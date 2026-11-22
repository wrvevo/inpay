package br.com.includelabs.inpay.payment.domain;

import java.util.*;

/** Porta de saída para aplicações conectoras independentes. Não referencia SDK de adquirente. */
public interface JourneyPort {
    record Request(
            UUID operationId,
            String merchantOrder,
            long amount,
            String method,
            String instrument) {}

    record Result(String status, String providerId, Map<String, String> artifacts) {}

    Result create(UUID tenant, Request request);

    Result query(UUID tenant, String providerId, long expectedAmount);

    Result cancel(UUID tenant, UUID operationId, String providerId, long amount);
}
