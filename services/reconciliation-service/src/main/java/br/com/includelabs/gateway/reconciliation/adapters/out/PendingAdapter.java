package br.com.includelabs.gateway.reconciliation.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.gateway.reconciliation.domain.port.SettlementPort;
@Component public class PendingAdapter implements SettlementPort {
public ReconciliationCase reconcile(UUID merchantId){throw new UnsupportedOperationException("Implement SettlementPort before enabling this flow");}
}
