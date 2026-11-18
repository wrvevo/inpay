package br.com.includelabs.inpay.messaging;

import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** At-least-once. Kafka ACK followed by failed DB commit can publish duplicates. */
@Component
public class OutboxPublisher {

    private final JdbcTemplate jdbcTemplate;

    private final KafkaTemplate<String, String> kafka;

    private final TransactionTemplate transactionTemplate;

    /**
     * Inicializa OutboxPublisher com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     * @param kafka produtor Kafka configurado com confirmação de recebimento
     * @param transactionTemplate controle do limite transacional do PostgreSQL
     */
    public OutboxPublisher(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafka,
            TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafka = kafka;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Publica um lote com confirmação Kafka e marca os eventos enviados. Falhas mantêm a outbox
     * pendente; duplicatas continuam possíveis.
     *
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    @Scheduled(fixedDelayString = "${inpay.outbox.delay:2000}")
    public void publish() {
        transactionTemplate.executeWithoutResult(
                transactionStatus -> {
                    var rows =
                            jdbcTemplate.queryForList(
                                    "SELECT * FROM outbox WHERE published_at IS NULL ORDER BY"
                                            + " created_at LIMIT 20 FOR UPDATE SKIP LOCKED");
                    for (var row : rows) {
                        try {
                            kafka.send(
                                            (String) row.get("topic"),
                                            (String) row.get("partition_key"),
                                            (String) row.get("payload"))
                                    .get(5, TimeUnit.SECONDS);
                            jdbcTemplate.update(
                                    "UPDATE outbox SET published_at=now() WHERE event_id=?",
                                    row.get("event_id"));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Publication interrupted");
                        } catch (Exception exception) {
                            throw new IllegalStateException("Publication failed; outbox retained");
                        }
                    }
                });
    }
}
