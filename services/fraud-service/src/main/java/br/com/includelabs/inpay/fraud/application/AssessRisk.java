package br.com.includelabs.inpay.fraud.application;

import br.com.includelabs.inpay.contracts.AntifraudStubs;
import br.com.includelabs.inpay.contracts.FraudMode;
import br.com.includelabs.inpay.fraud.domain.InPayRiskEngine;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy;
import br.com.includelabs.inpay.fraud.domain.RiskSignals;
import br.com.includelabs.inpay.fraud.domain.port.AssessmentStore;
import br.com.includelabs.inpay.fraud.domain.port.ExternalRiskPort;
import br.com.includelabs.inpay.fraud.domain.port.FraudModeSource;
import br.com.includelabs.inpay.fraud.domain.port.LearningStore;
import java.util.UUID;

/** Avalia pelo modo ativo: InPay Risk ou o stub do conector selecionado. */
public final class AssessRisk {
    private final InPayRiskEngine engine;
    private final AssessmentStore assessmentStore;
    private final LearningStore learningStore;
    private final FraudModeSource modes;
    private final ExternalRiskPort external;

    public AssessRisk(
            InPayRiskEngine engine,
            AssessmentStore assessmentStore,
            LearningStore learningStore,
            FraudModeSource modes,
            ExternalRiskPort external) {
        this.engine = engine;
        this.assessmentStore = assessmentStore;
        this.learningStore = learningStore;
        this.modes = modes;
        this.external = external;
    }

    public record Result(UUID assessmentId, RiskPolicy.Assessment assessment) {}

    public Result execute(
            UUID merchantId,
            UUID paymentId,
            RiskPolicy.Signals signals,
            RiskSignals.Behavior behavior,
            RiskSignals.Device device,
            RiskSignals.Identity identity) {
        String mode = modes.current();
        RiskPolicy.Assessment assessment =
                mode.equals(FraudMode.INPAY_RISK)
                        ? nativeAssessment(merchantId, signals, behavior, device, identity)
                        : externalAssessment(mode, signals, device, identity);
        return new Result(assessmentStore.save(merchantId, paymentId, assessment), assessment);
    }

    public void learn(UUID merchantId, String deviceKey, boolean confirmedFraud) {
        if (deviceKey == null || !deviceKey.matches("[A-Za-z0-9_\\-]{1,128}")) {
            throw new IllegalArgumentException("Invalid device key");
        }
        learningStore.record(merchantId, deviceKey, confirmedFraud);
    }

    private RiskPolicy.Assessment nativeAssessment(
            UUID merchantId,
            RiskPolicy.Signals signals,
            RiskSignals.Behavior behavior,
            RiskSignals.Device device,
            RiskSignals.Identity identity) {
        LearningStore.Counts counts =
                device == null || device.deviceKey().isBlank()
                        ? new LearningStore.Counts(0, 0)
                        : learningStore.get(merchantId, device.deviceKey());
        return engine.evaluate(
                signals, behavior, device, identity, counts.confirmed(), counts.legitimate());
    }

    private RiskPolicy.Assessment externalAssessment(
            String mode,
            RiskPolicy.Signals signals,
            RiskSignals.Device device,
            RiskSignals.Identity identity) {
        return external.evaluate(
                mode,
                new AntifraudStubs.Probe(
                        signals.knownCompromised(),
                        device != null && device.vpnOrProxy(),
                        signals.declinesLastHour(),
                        identity != null && identity.cpfStatus().equals("INVALID"),
                        identity != null && identity.disposableEmail(),
                        identity != null && !identity.deliveryMatchesBilling()));
    }
}
