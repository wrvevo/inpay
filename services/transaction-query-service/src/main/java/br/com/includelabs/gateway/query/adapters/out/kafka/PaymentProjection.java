package br.com.includelabs.gateway.query.adapters.out.kafka;

import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import br.com.includelabs.gateway.contracts.GatewayEvent;
import br.com.includelabs.gateway.messaging.Inbox;
@Component public class PaymentProjection {
 private final JdbcTemplate jdbc;private final JsonMapper json;private final Inbox inbox;
 public PaymentProjection(JdbcTemplate jdbc,JsonMapper json,Inbox inbox){this.jdbc=jdbc;this.json=json;this.inbox=inbox;}
 @KafkaListener(topics="payment.events.v1") @Transactional
 public void consume(String payload){
  var e=json.readValue(payload,GatewayEvent.class);
  if(e.schemaVersion()!=1)throw new IllegalArgumentException("Unsupported event version");
  if(!inbox.first("payment-projection",e.eventId()))return;
  if(!e.eventType().equals("PaymentCreated"))return;
  jdbc.update("INSERT INTO transaction_view(id,merchant_id,amount,status,version) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET amount=EXCLUDED.amount,status=EXCLUDED.status,version=EXCLUDED.version WHERE transaction_view.version<EXCLUDED.version AND transaction_view.merchant_id=EXCLUDED.merchant_id",
   e.aggregateId(),e.merchantId(),((Number)e.data().get("amount")).longValue(),(String)e.data().get("status"),e.aggregateVersion());
 }
}
