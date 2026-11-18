package br.com.includelabs.gateway.messaging;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import br.com.includelabs.gateway.contracts.GatewayEvent;
@Component
public class Outbox {
 private final JdbcTemplate jdbc; private final JsonMapper json;
 public Outbox(JdbcTemplate jdbc,JsonMapper json) {this.jdbc=jdbc;this.json=json;}
 public void append(String topic,GatewayEvent event) {
  if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Outbox requires business transaction");
  jdbc.update("INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES (?,?,?,?)",
    event.eventId(),topic,event.merchantId()+":"+event.aggregateId(),json.writeValueAsString(event));
 }
}
