package br.com.includelabs.inpay.payment.application;

import br.com.includelabs.inpay.payment.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Casos de uso dependem de portas: persistência local e aplicação conectora remota. */
@Service
public class PaymentJourneys {
    private final JourneyStore store;
    private final JourneyPort provider;
    private final JsonMapper json;

    public PaymentJourneys(JourneyStore store, JourneyPort provider, JsonMapper json) {
        this.store = store;
        this.provider = provider;
        this.json = json;
    }

    public record Create(String merchantOrder, long amount, String method, String instrument) {}

    /** Reserva idempotente durável. Queda após envio exige reconciliação, nunca reenvio cego. */
    public Map<String, Object> create(UUID tenant, String key, Create request) {
        if (key == null
                || key.isBlank()
                || key.length() > 128
                || request.merchantOrder() == null
                || request.merchantOrder().isBlank()
                || request.merchantOrder().length() > 128
                || request.amount() < 1
                || request.amount() > 100000000
                || request.method() == null
                || !Set.of("credit_card", "debit_card", "pix", "boleto", "transfer")
                        .contains(request.method()))
            throw new IllegalArgumentException("Invalid journey");
        var reservation =
                store.reserve(
                        tenant,
                        key,
                        hash(json.writeValueAsString(request)),
                        request.merchantOrder(),
                        request.amount(),
                        request.method());
        if (!reservation.created()) return store.get(tenant, reservation.id());
        JourneyPort.Result result;
        try {
            result =
                    provider.create(
                            tenant,
                            new JourneyPort.Request(
                                    reservation.id(),
                                    request.merchantOrder(),
                                    request.amount(),
                                    request.method(),
                                    request.instrument()));
        } catch (Exception unknown) {
            result = new JourneyPort.Result("UNKNOWN", "", Map.of());
        }
        store.apply(tenant, reservation.id(), 0, result);
        return store.get(tenant, reservation.id());
    }

    public Map<String, Object> get(UUID tenant, UUID id) {
        return store.get(tenant, id);
    }

    /** Recupera resposta perdida do checkout pela chave original, sem criar outra cobrança. */
    public Map<String, Object> byKey(UUID tenant, String key) {
        return store.byKey(tenant, key);
    }

    /**
     * Consulta ao provedor original; webhook pode solicitar esta consulta, não declarar pagamento.
     */
    public Map<String, Object> refresh(UUID tenant, UUID id) {
        var row = store.get(tenant, id);
        String providerId = Objects.toString(row.get("provider_id"), "");
        if (providerId.isBlank() || Set.of("CANCELLED", "DECLINED").contains(row.get("status")))
            return row;
        var result = provider.query(tenant, providerId, ((Number) row.get("amount")).longValue());
        store.apply(tenant, id, ((Number) row.get("version")).longValue(), result);
        return store.get(tenant, id);
    }

    /**
     * Cancelamento com chave estável e exclusão concorrente; resultado incerto permanece visível.
     */
    public Map<String, Object> cancel(UUID tenant, UUID id) {
        var row = store.get(tenant, id);
        String state = (String) row.get("status");
        if (state.equals("CANCELLED")) return row;
        if (!Set.of("PAID", "AUTHORIZED", "PENDING").contains(state))
            throw new IllegalStateException("Reconcile before cancellation");
        long version = ((Number) row.get("version")).longValue();
        if (!store.claimCancellation(tenant, id, version)) return store.get(tenant, id);
        JourneyPort.Result result;
        try {
            result =
                    provider.cancel(
                            tenant,
                            UUID.nameUUIDFromBytes(
                                    (id + ":cancel").getBytes(StandardCharsets.UTF_8)),
                            (String) row.get("provider_id"),
                            ((Number) row.get("amount")).longValue());
        } catch (Exception unknown) {
            result =
                    new JourneyPort.Result(
                            "CANCEL_UNKNOWN", (String) row.get("provider_id"), Map.of());
        }
        if (!result.status().equals("CANCELLED"))
            result =
                    new JourneyPort.Result(
                            "CANCEL_UNKNOWN", (String) row.get("provider_id"), Map.of());
        store.apply(tenant, id, version + 1, result);
        return store.get(tenant, id);
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
    }
}
