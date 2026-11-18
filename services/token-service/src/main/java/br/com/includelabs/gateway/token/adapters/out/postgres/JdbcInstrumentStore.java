package br.com.includelabs.gateway.token.adapters.out.postgres;

import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.includelabs.gateway.token.domain.Instrument;
import br.com.includelabs.gateway.token.domain.port.InstrumentStore;
@Repository public class JdbcInstrumentStore implements InstrumentStore {
 private final JdbcTemplate jdbc;public JdbcInstrumentStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void save(Instrument t){jdbc.update("INSERT INTO instruments(token,merchant_id,provider,affiliation_id,active) VALUES (?,?,?,?,?)",t.token(),t.merchantId(),t.provider(),t.affiliationId(),t.active());}
 public Optional<Instrument> find(UUID tenant,String token){return jdbc.query("SELECT * FROM instruments WHERE merchant_id=? AND token=?",(r,n)->new Instrument(r.getString("token"),r.getObject("merchant_id",UUID.class),r.getString("provider"),r.getString("affiliation_id"),r.getBoolean("active")),tenant,token).stream().findFirst();}
 public void revoke(UUID tenant,String token){jdbc.update("UPDATE instruments SET active=false WHERE merchant_id=? AND token=?",tenant,token);}
}
