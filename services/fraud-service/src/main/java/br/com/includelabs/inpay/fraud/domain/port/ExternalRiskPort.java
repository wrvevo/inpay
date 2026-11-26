package br.com.includelabs.inpay.fraud.domain.port;

import br.com.includelabs.inpay.contracts.AntifraudStubs;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy;

/** External decisions are unavailable unless an explicitly configured adapter exists. */
public interface ExternalRiskPort {
    /** Avalia os sinais de risco por meio do parceiro configurado. */
    RiskPolicy.Assessment evaluate(String mode, AntifraudStubs.Probe probe);
}
