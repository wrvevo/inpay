package br.com.includelabs.inpay.contracts;

import java.util.ArrayList;
import java.util.List;

/**
 * Respostas simuladas dos conectores ClearSale e Serasa Experian. Usadas com X-Mock e quando a
 * chamada real permanece desligada.
 */
public final class AntifraudStubs {
    private AntifraudStubs() {}

    public record Probe(
            boolean knownCompromised,
            boolean vpnOrProxy,
            int declinesLastHour,
            boolean documentInconsistent,
            boolean disposableEmail,
            boolean addressMismatch) {}

    public record Verdict(int score, String decision, String policyVersion, List<String> reasons) {}

    public static Verdict evaluate(String mode, Probe probe) {
        String provider = FraudMode.canonical(mode);
        if (provider.equals(FraudMode.CLEARSALE)) {
            return clearsale(probe);
        }
        if (provider.equals(FraudMode.SERASA_EXPERIAN)) {
            return serasa(probe);
        }
        throw new IllegalArgumentException("Unknown fraud mode");
    }

    private static Verdict clearsale(Probe probe) {
        int score = 15;
        List<String> reasons = new ArrayList<>();
        reasons.add("MOCK_STUB");
        if (probe.knownCompromised()) {
            score = Math.max(score, 90);
            reasons.add("KNOWN_COMPROMISED");
        }
        if (probe.vpnOrProxy()) {
            score = Math.max(score, 58);
            reasons.add("VPN_OR_PROXY");
        }
        if (probe.declinesLastHour() >= 3) {
            score = Math.max(score, 45);
            reasons.add("RECENT_DECLINES");
        }
        return new Verdict(
                Math.min(100, score), decision(score), "clearsale-mock-v1", List.copyOf(reasons));
    }

    private static Verdict serasa(Probe probe) {
        int score = 10;
        List<String> reasons = new ArrayList<>();
        reasons.add("MOCK_STUB");
        if (probe.documentInconsistent()) {
            score = Math.max(score, 92);
            reasons.add("CPF_INCONSISTENT");
        }
        if (probe.disposableEmail()) {
            score = Math.max(score, 50);
            reasons.add("DISPOSABLE_EMAIL");
        }
        if (probe.addressMismatch()) {
            score = Math.max(score, 42);
            reasons.add("ADDRESS_MISMATCH");
        }
        return new Verdict(
                Math.min(100, score),
                decision(score),
                "serasa-experian-mock-v1",
                List.copyOf(reasons));
    }

    private static String decision(int score) {
        if (score >= 70) {
            return "REJECT";
        }
        if (score >= 40) {
            return "REVIEW";
        }
        return "ACCEPT";
    }
}
