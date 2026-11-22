package br.com.includelabs.inpay.payment.adapters.out.postgres;

import br.com.includelabs.inpay.payment.domain.*;
import br.com.includelabs.inpay.security.ApiErrors;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador PostgreSQL: compare-and-set e outbox na mesma transação local. */
@Repository
public class JdbcJourneyStore implements JourneyStore {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public JdbcJourneyStore(JdbcTemplate db, TransactionTemplate tx, JsonMapper json) {
        this.db = db;
        this.tx = tx;
        this.json = json;
    }

    /** UNIQUE(tenant,key) serializa reservas concorrentes sem manter lock durante HTTP externo. */
    public Reservation reserve(
            UUID tenant,
            String key,
            String fingerprint,
            String merchantOrder,
            long amount,
            String method) {
        return tx.execute(
                s -> {
                    UUID id = UUID.randomUUID();
                    int n =
                            db.update(
                                    "INSERT INTO payment_journeys(id,tenant,request_key,request_hash,merchant_order,amount,method,status) VALUES(?,?,?,?,?,?,?,'PROCESSING') ON CONFLICT(tenant,request_key) DO NOTHING",
                                    id,
                                    tenant,
                                    key,
                                    fingerprint,
                                    merchantOrder,
                                    amount,
                                    method);
                    if (n == 1) {
                        event(id, tenant, "payment.processing");
                        return new Reservation(id, true);
                    }
                    var existing =
                            db.queryForMap(
                                    "SELECT id,request_hash FROM payment_journeys WHERE tenant=? AND request_key=?",
                                    tenant,
                                    key);
                    if (!fingerprint.equals(existing.get("request_hash")))
                        throw new IllegalStateException("Idempotency conflict");
                    return new Reservation((UUID) existing.get("id"), false);
                });
    }

    public Map<String, Object> get(UUID tenant, UUID id) {
        return one("SELECT * FROM payment_journeys WHERE tenant=? AND id=?", tenant, id);
    }

    public Map<String, Object> byKey(UUID tenant, String key) {
        return one("SELECT * FROM payment_journeys WHERE tenant=? AND request_key=?", tenant, key);
    }

    /** Apenas uma requisição ganha o direito de enviar cancelamento. */
    public boolean claimCancellation(UUID tenant, UUID id, long version) {
        return tx.execute(
                s -> {
                    int n =
                            db.update(
                                    "UPDATE payment_journeys SET status='CANCELLING',version=version+1,updated_at=now() WHERE tenant=? AND id=? AND version=?",
                                    tenant,
                                    id,
                                    version);
                    if (n == 1) event(id, tenant, "payment.cancelling");
                    return n == 1;
                });
    }

    /** Respostas antigas não sobrescrevem um cancelamento mais recente. */
    public void apply(UUID tenant, UUID id, long version, JourneyPort.Result result) {
        tx.executeWithoutResult(
                s -> {
                    int n =
                            db.update(
                                    "UPDATE payment_journeys SET status=?,provider_id=COALESCE(NULLIF(?,''),provider_id),artifacts=?::jsonb,version=version+1,updated_at=now() WHERE tenant=? AND id=? AND version=?",
                                    result.status(),
                                    result.providerId(),
                                    json.writeValueAsString(result.artifacts()),
                                    tenant,
                                    id,
                                    version);
                    if (n == 1)
                        event(id, tenant, "payment." + result.status().toLowerCase(Locale.ROOT));
                });
    }

    private Map<String, Object> one(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        var row = new LinkedHashMap<>(rows.getFirst());
        row.remove("request_hash");
        row.remove("request_key");
        row.put("artifacts", json.readValue(row.get("artifacts").toString(), Map.class));
        return row;
    }

    private void event(UUID id, UUID tenant, String type) {
        UUID eventId = UUID.randomUUID();
        db.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES(?,?,?,?)",
                eventId,
                "inpay.payment.journeys.v1",
                id.toString(),
                json.writeValueAsString(
                        Map.of(
                                "eventId",
                                eventId,
                                "journeyId",
                                id,
                                "tenantId",
                                tenant,
                                "type",
                                type)));
    }
}
