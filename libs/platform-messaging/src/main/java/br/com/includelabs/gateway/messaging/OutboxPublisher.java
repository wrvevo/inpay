package br.com.includelabs.gateway.messaging;

import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
/** At-least-once. Kafka ACK followed by failed DB commit can publish duplicates. */
@Component
public class OutboxPublisher {
 private final JdbcTemplate jdbc; private final KafkaTemplate<String,String> kafka; private final TransactionTemplate tx;
 public OutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,TransactionTemplate tx) {
  this.jdbc=jdbc;this.kafka=kafka;this.tx=tx;
 }
 @Scheduled(fixedDelayString="${gateway.outbox.delay:2000}")
 public void publish() {
  tx.executeWithoutResult(status -> {
   var rows=jdbc.queryForList("SELECT * FROM outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED");
   for(var row:rows) {
    try {
     kafka.send((String)row.get("topic"),(String)row.get("partition_key"),(String)row.get("payload")).get(5,TimeUnit.SECONDS);
     jdbc.update("UPDATE outbox SET published_at=now() WHERE event_id=?",row.get("event_id"));
    } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("Publication interrupted");}
      catch(Exception e) {throw new IllegalStateException("Publication failed; outbox retained");}
   }
  });
 }
}
