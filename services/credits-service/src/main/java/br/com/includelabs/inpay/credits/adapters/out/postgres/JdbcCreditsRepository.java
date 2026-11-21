package br.com.includelabs.inpay.credits.adapters.out.postgres;

import br.com.includelabs.inpay.credits.domain.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Banco exclusivo. Bloqueio por tenant mantém reservas, journal e outbox consistentes. */
@Repository
public class JdbcCreditsRepository implements CreditsRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    /** Injeta persistência transacional e serializador. */
    public JdbcCreditsRepository(JdbcTemplate jdbc, TransactionTemplate tx, JsonMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    /** Executa mutação serializada sem permitir escrita parcial de fonte de pagamento. */
    public <T> T transact(UUID tenant, Function<CreditsState, T> operation) {
        return tx.execute(
                status -> {
                    jdbc.update(
                            "INSERT INTO marketplace_state(tenant,state) VALUES (?, '{}'::jsonb) ON CONFLICT DO NOTHING",
                            tenant);
                    CreditsState state =
                            json.readValue(
                                    jdbc.queryForObject(
                                            "SELECT state::text FROM marketplace_state WHERE tenant=? FOR UPDATE",
                                            String.class,
                                            tenant),
                                    CreditsState.class);
                    T result = operation.apply(state);
                    for (JournalEntry entry : state.pendingEntries) {
                        String payload = json.writeValueAsString(entry);
                        jdbc.update(
                                "INSERT INTO marketplace_journal(id,tenant,owner,payload) VALUES (?,?,?,?::jsonb)",
                                entry.id(),
                                tenant,
                                entry.owner(),
                                payload);
                        jdbc.update(
                                "INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES (?,?,?,?)",
                                entry.id(),
                                "inpay.credits.events.v1",
                                tenant + ":" + entry.owner(),
                                json.writeValueAsString(
                                        Map.of(
                                                "eventId",
                                                entry.id(),
                                                "merchantId",
                                                tenant,
                                                "type",
                                                entry.reason(),
                                                "reference",
                                                entry.reference(),
                                                "occurredAt",
                                                entry.occurredAt())));
                    }
                    state.pendingEntries.clear();
                    jdbc.update(
                            "UPDATE marketplace_state SET state=?::jsonb,version=version+1 WHERE tenant=?",
                            json.writeValueAsString(state),
                            tenant);
                    return result;
                });
    }

    /** Consulta snapshot consistente; tenant desconhecido não recebe dados demonstrativos. */
    public CreditsState read(UUID tenant) {
        var rows =
                jdbc.queryForList(
                        "SELECT state::text AS state FROM marketplace_state WHERE tenant=?",
                        tenant);
        return rows.isEmpty()
                ? new CreditsState()
                : json.readValue((String) rows.getFirst().get("state"), CreditsState.class);
    }

    /** Lista os 500 lançamentos mais recentes do contexto autorizado. */
    public List<JournalEntry> journal(UUID tenant, String owner) {
        return jdbc.query(
                "SELECT payload::text FROM marketplace_journal WHERE tenant=? AND (?::text IS NULL OR owner=?) ORDER BY created_at DESC,id LIMIT 500",
                (rs, n) -> json.readValue(rs.getString(1), JournalEntry.class),
                tenant,
                owner,
                owner);
    }
}
