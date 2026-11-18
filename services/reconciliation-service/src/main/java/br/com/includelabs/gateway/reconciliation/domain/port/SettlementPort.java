package br.com.includelabs.gateway.reconciliation.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.reconciliation.domain.ReconciliationCase;
public interface SettlementPort { ReconciliationCase reconcile(UUID merchantId); }
