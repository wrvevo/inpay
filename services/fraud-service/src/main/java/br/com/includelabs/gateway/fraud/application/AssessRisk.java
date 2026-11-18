package br.com.includelabs.gateway.fraud.application;

import java.util.UUID;
import br.com.includelabs.gateway.fraud.domain.RiskPolicy;
import br.com.includelabs.gateway.fraud.domain.port.AssessmentStore;
public final class AssessRisk {
 private final RiskPolicy policy;private final AssessmentStore store;
 public AssessRisk(RiskPolicy policy,AssessmentStore store){this.policy=policy;this.store=store;}
 public record Result(UUID assessmentId,RiskPolicy.Assessment assessment){}
 public Result execute(UUID merchant,UUID payment,RiskPolicy.Signals signals) {
  var result=policy.evaluate(signals);return new Result(store.save(merchant,payment,result),result);
 }
}
