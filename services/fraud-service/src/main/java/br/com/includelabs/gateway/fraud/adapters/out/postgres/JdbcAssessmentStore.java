package br.com.includelabs.gateway.fraud.adapters.out.postgres;

import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.includelabs.gateway.fraud.domain.RiskPolicy.Assessment;
import br.com.includelabs.gateway.fraud.domain.port.AssessmentStore;
@Repository
public class JdbcAssessmentStore implements AssessmentStore {
 private final JdbcTemplate jdbc;public JdbcAssessmentStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public UUID save(UUID merchant,UUID payment,Assessment a) {
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO assessments(id,merchant_id,payment_id,score,decision,policy_version,reasons) VALUES (?,?,?,?,?,?,?)",
   id,merchant,payment,a.score(),a.decision().name(),a.policyVersion(),String.join(",",a.reasons()));return id;
 }
}
