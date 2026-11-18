package br.com.includelabs.gateway.fraud.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.fraud.application.AssessRisk;
import br.com.includelabs.gateway.fraud.domain.RiskPolicy;
import br.com.includelabs.gateway.fraud.domain.port.AssessmentStore;
@Configuration public class UseCases {
 @Bean AssessRisk assessRisk(AssessmentStore store){return new AssessRisk(new RiskPolicy(),store);}
}
