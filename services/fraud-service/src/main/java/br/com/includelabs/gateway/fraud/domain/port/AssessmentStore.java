package br.com.includelabs.gateway.fraud.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.fraud.domain.RiskPolicy.Assessment;
public interface AssessmentStore { UUID save(UUID merchantId,UUID paymentId,Assessment result); }
