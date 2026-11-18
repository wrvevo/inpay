package br.com.includelabs.gateway.messaging;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
public class Inbox {
 private final JdbcTemplate jdbc;
 public Inbox(JdbcTemplate jdbc) {this.jdbc=jdbc;}
 public boolean first(String consumer,UUID eventId) {
  if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Inbox requires transaction");
  return jdbc.update("INSERT INTO inbox(consumer,event_id) VALUES (?,?) ON CONFLICT DO NOTHING",consumer,eventId)==1;
 }
}
