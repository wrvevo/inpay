package br.com.includelabs.gateway.reconciliation.application;

import java.util.UUID;
import br.com.includelabs.gateway.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.gateway.reconciliation.domain.port.SettlementPort;
public final class ReconciliationCaseUseCase {
private final SettlementPort port;public ReconciliationCaseUseCase(SettlementPort port){this.port=port;}
public ReconciliationCase execute(UUID merchantId){return port.reconcile(merchantId);}
}
