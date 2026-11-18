package br.com.includelabs.gateway.payment.adapters.out.postgres;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import br.com.includelabs.gateway.payment.domain.*;
import br.com.includelabs.gateway.payment.domain.port.*;
import br.com.includelabs.gateway.contracts.GatewayEvent;
import br.com.includelabs.gateway.messaging.Outbox;
@Repository
public class JdbcPaymentStore implements PaymentStore {
 private final JdbcTemplate jdbc; private final Outbox outbox;
 public JdbcPaymentStore(JdbcTemplate jdbc,Outbox outbox) {this.jdbc=jdbc;this.outbox=outbox;}
 @Transactional public Payment register(Payment p,String key,String hash) {
  jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},p.merchantId()+":"+key);
  var existing=jdbc.queryForList("SELECT request_hash,payment_id FROM payment_idempotency WHERE merchant_id=? AND key=?",p.merchantId(),key);
  if(!existing.isEmpty()) {
   if(!hash.equals(existing.getFirst().get("request_hash"))) throw new IllegalStateException("Idempotency conflict");
   return find(p.merchantId(),(UUID)existing.getFirst().get("payment_id")).orElseThrow();
  }
  jdbc.update("INSERT INTO payments(id,merchant_id,merchant_order_id,amount,currency,instrument_token,status) VALUES (?,?,?,?,?,?,?)",
   p.id(),p.merchantId(),p.merchantOrderId(),p.amount(),p.currency(),p.instrumentToken(),p.status().name());
  jdbc.update("INSERT INTO payment_idempotency(merchant_id,key,request_hash,payment_id) VALUES (?,?,?,?)",p.merchantId(),key,hash,p.id());
  outbox.append("payment.events.v1",new GatewayEvent(UUID.randomUUID(),"PaymentCreated",1,p.id(),0,p.merchantId(),Instant.now(),
   Map.of("status",p.status().name(),"amount",p.amount(),"currency",p.currency())));
  return p;
 }
 public Optional<Payment> find(UUID tenant,UUID id) {
  return jdbc.query("SELECT * FROM payments WHERE merchant_id=? AND id=?",(rs,n)->new Payment(
   rs.getObject("id",UUID.class),rs.getObject("merchant_id",UUID.class),rs.getString("merchant_order_id"),rs.getLong("amount"),
   rs.getString("currency"),rs.getString("instrument_token"),Payment.Status.valueOf(rs.getString("status")),
   rs.getLong("captured"),rs.getLong("refunded"),rs.getBoolean("uncertain"),rs.getLong("version")),tenant,id).stream().findFirst();
 }
}
