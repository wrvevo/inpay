package br.com.includelabs.gateway.fraud.adapters.in.rest;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.includelabs.gateway.fraud.application.AssessRisk;
import br.com.includelabs.gateway.fraud.domain.RiskPolicy;
import br.com.includelabs.gateway.security.TenantContext;
@RestController public class FraudController {
 private final AssessRisk useCase;private final TenantContext tenants;
 public FraudController(AssessRisk useCase,TenantContext tenants){this.useCase=useCase;this.tenants=tenants;}
 public record Request(UUID paymentId,long amount,int attemptsLastHour,int declinesLastHour,boolean knownCompromised){}
 @PostMapping("/internal/v1/assessments") @PreAuthorize("hasAuthority('SCOPE_fraud/evaluate')")
 public AssessRisk.Result evaluate(@RequestBody Request b,@RequestHeader(value="X-Merchant-Id",required=false) String tenant){
  if(b.paymentId()==null)throw new IllegalArgumentException();
  return useCase.execute(tenants.resolve(tenant),b.paymentId(),new RiskPolicy.Signals(b.amount(),b.attemptsLastHour(),b.declinesLastHour(),b.knownCompromised()));
 }
}
