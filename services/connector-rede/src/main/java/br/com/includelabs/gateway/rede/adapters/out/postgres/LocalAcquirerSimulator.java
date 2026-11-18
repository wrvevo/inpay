package br.com.includelabs.gateway.rede.adapters.out.postgres;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import br.com.includelabs.gateway.rede.domain.Authorization;
import br.com.includelabs.gateway.rede.domain.port.AcquirerPort;
@Component @Profile("local")
public class LocalAcquirerSimulator implements AcquirerPort {
private final JdbcTemplate jdbc;public LocalAcquirerSimulator(JdbcTemplate jdbc){this.jdbc=jdbc;}
@Transactional public Authorization authorize(Authorization r){
 jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},r.operationId().toString());
 var old=jdbc.queryForList("SELECT * FROM provider_operations WHERE id=?",r.operationId());
 if(!old.isEmpty()){var o=old.getFirst();
  if(!o.get("merchant_id").equals(r.merchantId()) || ((Number)o.get("amount")).longValue()!=r.amount() || !o.get("instrument_token").equals(r.instrumentToken()))throw new IllegalStateException("Conflict");
  return new Authorization(r.operationId(),r.merchantId(),r.amount(),r.instrumentToken(),Authorization.Outcome.valueOf((String)o.get("outcome")));
 }
 // Deliberate test scenarios: 13 = uncertain; 51 = declined. Only the local profile uses these.
 var outcome=r.amount()==13?Authorization.Outcome.UNKNOWN:r.amount()==51?Authorization.Outcome.DECLINED:Authorization.Outcome.AUTHORIZED;
 jdbc.update("INSERT INTO provider_operations(id,merchant_id,amount,instrument_token,outcome) VALUES (?,?,?,?,?)",r.operationId(),r.merchantId(),r.amount(),r.instrumentToken(),outcome.name());
 return new Authorization(r.operationId(),r.merchantId(),r.amount(),r.instrumentToken(),outcome);
}
}
