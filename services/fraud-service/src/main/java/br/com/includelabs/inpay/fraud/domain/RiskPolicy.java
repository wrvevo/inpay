package br.com.includelabs.inpay.fraud.domain;

import java.util.ArrayList;
import java.util.List;

/** Política demonstrativa explicável. Os limites precisam de calibração antes do uso real. */
public final class RiskPolicy {

    private static final int REVIEW_THRESHOLD = 40;

    private static final int REJECT_THRESHOLD = 70;

    private static final int MAX_SCORE = 100;

    /**
     * Modelo ou regra de negócio do módulo InPay, independente de Spring e da infraestrutura de
     * persistência.
     */
    public enum Decision {
        ACCEPT,
        REVIEW,
        REJECT
    }

    /**
     * Sinais fornecidos por um chamador interno autorizado; a coleta automática ainda é uma
     * extensão.
     */
    public record Signals(
            long amount, int attemptsLastHour, int declinesLastHour, boolean knownCompromised) {

        /** Impede valores ou contadores negativos antes de aplicar a política. */
        public Signals {
            if (amount <= 0 || attemptsLastHour < 0 || declinesLastHour < 0) {
                throw new IllegalArgumentException("Invalid signals");
            }
        }
    }

    /**
     * Resultado explicável, com a versão da política e os motivos que contribuíram para o score.
     */
    public record Assessment(
            int score, Decision decision, String policyVersion, List<String> reasons) {}

    /**
     * Soma os fatores de risco e limita o resultado a 100, preservando os motivos da decisão.
     *
     * @param signals valor e sinais de comportamento da tentativa
     * @return score, decisão e motivos; não produz cobrança nem altera o estado do pagamento
     */
    public Assessment evaluate(Signals signals) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (signals.knownCompromised()) {
            score += 70;
            reasons.add("KNOWN_COMPROMISED");
        }
        if (signals.attemptsLastHour() >= 5) {
            score += 30;
            reasons.add("VELOCITY");
        }
        if (signals.declinesLastHour() >= 3) {
            score += 25;
            reasons.add("RECENT_DECLINES");
        }
        if (signals.amount() > 100000) {
            score += 20;
            reasons.add("HIGH_AMOUNT");
        }
        int boundedScore = Math.min(MAX_SCORE, score);
        return new Assessment(
                boundedScore, decisionFor(boundedScore), "demo-rules-v1", List.copyOf(reasons));
    }

    /** Converte o score em uma decisão usando faixas sem sobreposição. */
    private Decision decisionFor(int score) {
        if (score >= REJECT_THRESHOLD) {
            return Decision.REJECT;
        }
        if (score >= REVIEW_THRESHOLD) {
            return Decision.REVIEW;
        }
        return Decision.ACCEPT;
    }
}
