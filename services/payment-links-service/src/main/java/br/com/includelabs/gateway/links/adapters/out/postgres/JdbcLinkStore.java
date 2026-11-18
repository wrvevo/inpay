package br.com.includelabs.gateway.links.adapters.out.postgres;

import java.util.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.includelabs.gateway.links.domain.PaymentLink;
import br.com.includelabs.gateway.links.domain.port.LinkStore;
@Repository public class JdbcLinkStore implements LinkStore {
 private final JdbcTemplate jdbc;public JdbcLinkStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void save(PaymentLink p){jdbc.update("INSERT INTO payment_links(id,merchant_id,public_code,order_id,amount,expires_at,revoked) VALUES (?,?,?,?,?,?,?)",p.id(),p.merchantId(),p.publicCode(),p.orderId(),p.amount(),Timestamp.from(p.expiresAt()),p.revoked());}
 public Optional<PaymentLink> find(UUID tenant,UUID id){return jdbc.query("SELECT * FROM payment_links WHERE merchant_id=? AND id=?",(r,n)->new PaymentLink(r.getObject("id",UUID.class),r.getObject("merchant_id",UUID.class),r.getString("public_code"),r.getString("order_id"),r.getLong("amount"),r.getTimestamp("expires_at").toInstant(),r.getBoolean("revoked")),tenant,id).stream().findFirst();}
 public void revoke(UUID tenant,UUID id){jdbc.update("UPDATE payment_links SET revoked=true WHERE merchant_id=? AND id=?",tenant,id);}
 public int expire(){return jdbc.update("UPDATE payment_links SET revoked=true WHERE revoked=false AND expires_at<now()");}
}
