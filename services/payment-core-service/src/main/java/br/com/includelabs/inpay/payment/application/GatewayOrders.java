package br.com.includelabs.inpay.payment.application;

import br.com.includelabs.inpay.payment.application.GatewayRequests.*;
import br.com.includelabs.inpay.resources.ResourceRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Registra pedidos e cobranças atomicamente; nunca confunde aceitação com pagamento aprovado. */
@Service
public class GatewayOrders {
    private final ResourceRepository store;

    public GatewayOrders(ResourceRepository store) {
        this.store = store;
    }

    /** Valida composição e referência de cliente dentro do mesmo tenant. */
    public JsonNode create(UUID tenant, String key, Order request) {
        store.get(tenant, "cus", request.customer_id());
        long total = 0;
        long paid = 0;
        for (Item item : request.items())
            total = Math.addExact(total, Math.multiplyExact(item.amount(), item.quantity()));
        for (Payment p : request.payments()) {
            paid = Math.addExact(paid, p.amount());
            if (p.payment_method().equals("pix")
                    ? p.pix() == null || p.credit_card() != null
                    : p.credit_card() == null || p.pix() != null)
                throw new IllegalArgumentException("Invalid payment instructions");
        }
        if (total != paid || total > 100000000)
            throw new IllegalArgumentException("Payment sum differs from order");
        long amount = total;
        return store.create(
                tenant,
                "or",
                key,
                request,
                id -> {
                    List<JsonNode> charges = new ArrayList<>();
                    int index = 0;
                    for (Payment payment : request.payments()) {
                        charges.add(
                                store.create(
                                        tenant,
                                        "ch",
                                        id + ":" + index++,
                                        payment,
                                        chargeId ->
                                                Map.of(
                                                        "id",
                                                        chargeId,
                                                        "order_id",
                                                        id,
                                                        "amount",
                                                        payment.amount(),
                                                        "payment_method",
                                                        payment.payment_method(),
                                                        "status",
                                                        "pending",
                                                        "processing_status",
                                                        "not_dispatched",
                                                        "created_at",
                                                        Instant.now().toString())));
                    }
                    return Map.of(
                            "id",
                            id,
                            "code",
                            request.code(),
                            "customer_id",
                            request.customer_id(),
                            "amount",
                            amount,
                            "currency",
                            "BRL",
                            "status",
                            "pending",
                            "items",
                            request.items(),
                            "charges",
                            charges,
                            "created_at",
                            Instant.now().toString());
                });
    }
}
