package br.com.includelabs.inpay.messaging;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Publicação at-least-once com reserva persistente e sem transação durante I/O Kafka. */
@Component
public class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactionTemplate;

    /** Inicializa as portas de persistência e publicação de eventos. */
    public OutboxPublisher(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafka,
            TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafka = kafka;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Reserva até 20 eventos, um por chave de partição, com prazo de recuperação de dois minutos.
     * A reserva termina antes de qualquer chamada ao broker. A confirmação é condicionada ao
     * identificador da reserva; consumidores precisam deduplicar eventos e validar versões.
     */
    @Scheduled(fixedDelayString = "${inpay.outbox.delay:2000}")
    public void publish() {
        UUID lease = UUID.randomUUID();
        var rows =
                transactionTemplate.execute(
                        status ->
                                jdbcTemplate.queryForList(
                                        """
                                        WITH candidates AS (
                                            SELECT o.event_id FROM outbox o
                                            WHERE o.published_at IS NULL
                                              AND (o.lease_until IS NULL OR o.lease_until < now())
                                              AND NOT EXISTS (
                                                SELECT 1 FROM outbox older
                                                WHERE older.published_at IS NULL
                                                  AND older.topic = o.topic
                                                  AND older.partition_key = o.partition_key
                                                  AND (older.created_at, older.event_id)
                                                      < (o.created_at, o.event_id))
                                            ORDER BY o.created_at, o.event_id
                                            LIMIT 20 FOR UPDATE OF o SKIP LOCKED
                                        )
                                        UPDATE outbox o SET lease_id = ?,
                                            lease_until = now() + interval '2 minutes'
                                        FROM candidates c WHERE o.event_id = c.event_id
                                        RETURNING o.event_id, o.topic, o.partition_key, o.payload
                                        """,
                                        lease));
        if (rows == null || rows.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        var pending = new ArrayList<Publication>();
        for (Map<String, Object> row : rows) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            try {
                pending.add(
                        new Publication(
                                (UUID) row.get("event_id"),
                                kafka.send(
                                        (String) row.get("topic"),
                                        (String) row.get("partition_key"),
                                        (String) row.get("payload"))));
            } catch (RuntimeException failure) {
                LOG.warn("Outbox send unavailable; event remains leased");
                break;
            }
        }
        for (Publication publication : pending) {
            try {
                publication.ack().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                jdbcTemplate.update(
                        "UPDATE outbox SET published_at=now(), lease_id=NULL, lease_until=NULL"
                                + " WHERE event_id=? AND lease_id=? AND published_at IS NULL",
                        publication.eventId(),
                        lease);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failure) {
                LOG.warn("Outbox confirmation unavailable; event remains eligible after lease expiry");
            }
        }
    }

    private record Publication(UUID eventId, CompletableFuture<?> ack) {}
}
