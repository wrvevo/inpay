package br.com.includelabs.inpay.credits.domain;

import java.util.UUID;

/** Fronteira para composição gateway + fornecedor; resultados ambíguos não liberam reservas. */
public interface FulfillmentPort {
    enum Result {
        CONFIRMED,
        REJECTED,
        UNKNOWN,
        COMPENSATION_REQUIRED
    }

    record Evidence(Result result, String reference, boolean simulation) {}

    Evidence fulfill(UUID tenant, Order order);

    Evidence reconcile(UUID tenant, Order order);

    Evidence refund(UUID tenant, Order order);
}
