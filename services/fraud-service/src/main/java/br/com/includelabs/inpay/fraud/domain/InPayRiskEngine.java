package br.com.includelabs.inpay.fraud.domain;

import br.com.includelabs.inpay.fraud.domain.RiskPolicy.Assessment;
import br.com.includelabs.inpay.fraud.domain.RiskPolicy.Decision;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor explicável do InPay Risk. Os pesos são regras versionadas; o consumo de tokens mede o custo
 * de enviar esses sinais a um modelo de IA e não chama um provedor externo.
 */
public final class InPayRiskEngine {
    public static final String POLICY = "inpay-risk-v1";
    static final int BEHAVIOR_TOKENS = 180;
    static final int FINGERPRINT_TOKENS = 140;
    static final int IDENTITY_TOKENS = 220;
    static final int SCORE_TOKENS = 90;
    static final int LEARNING_TOKENS = 160;

    private final RiskPolicy policy;

    public InPayRiskEngine(RiskPolicy policy) {
        this.policy = policy;
    }

    public Assessment evaluate(
            RiskPolicy.Signals signals,
            RiskSignals.Behavior behavior,
            RiskSignals.Device device,
            RiskSignals.Identity identity,
            int confirmedChargebacks,
            int legitimateOutcomes) {
        if (confirmedChargebacks < 0 || legitimateOutcomes < 0) {
            throw new IllegalArgumentException("Invalid learning counters");
        }
        Assessment base = policy.evaluate(signals);
        int score = base.score();
        List<String> reasons = new ArrayList<>(base.reasons());
        int tokens = SCORE_TOKENS;
        if (behavior != null) {
            tokens += BEHAVIOR_TOKENS;
            score = behavior(score, reasons, behavior);
        }
        if (device != null) {
            tokens += FINGERPRINT_TOKENS;
            score = device(score, reasons, device);
        }
        if (identity != null) {
            tokens += IDENTITY_TOKENS;
            score = identity(score, reasons, identity);
        }
        if (confirmedChargebacks > 0 || legitimateOutcomes > 0) {
            tokens += LEARNING_TOKENS;
            score = learning(score, reasons, confirmedChargebacks, legitimateOutcomes);
        }
        int bounded = Math.max(0, Math.min(100, score));
        return new Assessment(
                bounded, decision(bounded), POLICY, List.copyOf(reasons), "INPAY_RISK", tokens);
    }

    private static int behavior(int score, List<String> reasons, RiskSignals.Behavior behavior) {
        if (behavior.typingIntervalMs() > 0 && behavior.typingIntervalMs() < 40) {
            score += 18;
            reasons.add("BOT_TYPING");
        }
        if (behavior.navigationSeconds() > 0 && behavior.mouseEntropy() < 8) {
            score += 12;
            reasons.add("LOW_POINTER");
        }
        if (behavior.navigationSeconds() > 0 && behavior.navigationSeconds() < 4) {
            score += 10;
            reasons.add("RUSHED_CHECKOUT");
        }
        if (behavior.unusualJourney()) {
            score += 22;
            reasons.add("JOURNEY_ANOMALY");
        }
        return score;
    }

    private static int device(int score, List<String> reasons, RiskSignals.Device device) {
        if (device.vpnOrProxy()) {
            score += 25;
            reasons.add("VPN_OR_PROXY");
        }
        if (!device.country().isEmpty() && !device.country().equals(device.homeCountry())) {
            score += 15;
            reasons.add("GEO_MISMATCH");
        }
        if (device.seenAtOtherMerchants() >= 8) {
            score += 20;
            reasons.add("DEVICE_MULTI_MERCHANT");
        }
        return score;
    }

    private static int identity(int score, List<String> reasons, RiskSignals.Identity identity) {
        if (identity.cpfStatus().equals("INVALID")) {
            score += 40;
            reasons.add("CPF_INCONSISTENT");
        }
        if (!identity.deliveryMatchesBilling()) {
            score += 15;
            reasons.add("ADDRESS_MISMATCH");
        }
        if (identity.disposableEmail()) {
            score += 15;
            reasons.add("DISPOSABLE_EMAIL");
        }
        if (identity.cardholderMatch() < 40) {
            score += 20;
            reasons.add("CARDHOLDER_MISMATCH");
        }
        if (identity.priorPurchases() >= 5 && identity.cardholderMatch() >= 80) {
            score -= 8;
            reasons.add("RETURNING_CUSTOMER");
        }
        return score;
    }

    private static int learning(
            int score, List<String> reasons, int confirmedChargebacks, int legitimateOutcomes) {
        if (confirmedChargebacks > 0) {
            score += Math.min(36, confirmedChargebacks * 12);
            reasons.add("CHARGEBACK_HISTORY");
        } else if (legitimateOutcomes >= 2) {
            score -= 10;
            reasons.add("TRUSTED_HISTORY");
        }
        return score;
    }

    private static Decision decision(int score) {
        if (score >= 70) {
            return Decision.REJECT;
        }
        if (score >= 40) {
            return Decision.REVIEW;
        }
        return Decision.ACCEPT;
    }
}
