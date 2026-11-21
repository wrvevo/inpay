package br.com.includelabs.inpay.resources;

import br.com.includelabs.inpay.security.ApiErrors;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador transacional: cada serviço usa seu próprio datasource e histórico de migrações. */
@Repository
public class JdbcResourceRepository implements ResourceRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JdbcResourceRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Serializa repetições concorrentes e publica apenas um evento por criação. */
    @Transactional
    public JsonNode create(
            UUID tenant,
            String kind,
            String key,
            Object request,
            Function<String, Object> factory) {
        if (key == null || key.isBlank() || key.length() > 128)
            throw new IllegalArgumentException("Invalid idempotency key");
        String body = json.writeValueAsString(request);
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                rs -> {},
                tenant + ":" + kind + ":" + key);
        var rows =
                jdbc.queryForList(
                        "SELECT request::text,payload::text FROM api_resources WHERE tenant=? AND kind=? AND operation_key=?",
                        tenant,
                        kind,
                        key);
        if (!rows.isEmpty()) {
            if (!json.readTree(body).equals(json.readTree((String) rows.getFirst().get("request"))))
                throw new IllegalStateException("Idempotency conflict");
            return json.readTree((String) rows.getFirst().get("payload"));
        }
        String id = kind + "_" + UUID.randomUUID().toString().replace("-", "");
        String payload = json.writeValueAsString(factory.apply(id));
        jdbc.update(
                "INSERT INTO api_resources(tenant,kind,id,operation_key,request,payload) VALUES(?,?,?,?,?::jsonb,?::jsonb)",
                tenant,
                kind,
                id,
                key,
                body,
                payload);
        jdbc.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES(?,?,?,?)",
                UUID.randomUUID(),
                kind.startsWith("mp_") ? "inpay.marketplace.events.v1" : "inpay.gateway.events.v1",
                tenant.toString(),
                json.writeValueAsString(
                        Map.of("type", kind + ".created", "tenant", tenant, "resource_id", id)));
        return json.readTree(payload);
    }

    /** Identificadores opacos não dispensam a verificação do tenant. */
    public JsonNode get(UUID tenant, String kind, String id) {
        var rows =
                jdbc.queryForList(
                        "SELECT payload::text FROM api_resources WHERE tenant=? AND kind=? AND id=?",
                        tenant,
                        kind,
                        id);
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        return json.readTree((String) rows.getFirst().get("payload"));
    }

    /** Ordenação estável e limite de 100 registros por página. */
    public List<JsonNode> list(UUID tenant, String kind, int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination");
        return jdbc.query(
                "SELECT payload::text FROM api_resources WHERE tenant=? AND kind=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                (rs, n) -> json.readTree(rs.getString(1)),
                tenant,
                kind,
                size,
                (page - 1) * size);
    }
}
