package br.com.includelabs.inpay.billing.domain;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Cálculos determinísticos em centavos. Parâmetros contratuais não equivalem a validação jurídica.
 */
public final class BillingPolicy {
    private BillingPolicy() {}

    public record Conditions(
            int fineBps,
            int dailyInterestBps,
            long discountCents,
            LocalDate discountUntil,
            int maxSurchargeBps) {}

    public record Quote(long principal, long fine, long interest, long discount, long total) {}

    public record Plan(
            String id,
            String name,
            long monthlyCents,
            long successfulOperationCents,
            String status) {}

    /**
     * InPay Risk cobra o excedente de tokens. ClearSale e Serasa Experian são preço por análise,
     * fora do medidor de IA. Valores de proposta, não fatura de provedor de modelo.
     */
    public record RiskOffer(
            String planId,
            long includedTokens,
            int modelCentsPerThousand,
            int chargeCentsPerThousand,
            int clearsaleCents,
            int serasaCents) {}

    public record RiskQuote(
            String planId,
            String mode,
            int tokens,
            long includedTokens,
            int billableTokens,
            long modelCostCents,
            long merchantChargeCents) {}

    /**
     * Proposta comercial versionada: não confundir taxa de software com MDR/custo de adquirente.
     */
    public static List<Plan> plans() {
        return List.of(
                new Plan("START-2026-11", "Start", 0, 49, "PROPOSAL"),
                new Plan("GROW-2026-11", "Growth", 14900, 35, "PROPOSAL"),
                new Plan("SCALE-2026-11", "Scale", 49900, 25, "PROPOSAL"));
    }

    public static Plan plan(String id) {
        return plans().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan"));
    }

    public static List<RiskOffer> riskOffers() {
        return List.of(
                new RiskOffer("START-2026-11", 50_000, 3, 9, 35, 49),
                new RiskOffer("GROW-2026-11", 1_000_000, 3, 5, 28, 39),
                new RiskOffer("SCALE-2026-11", 5_000_000, 3, 4, 22, 32));
    }

    /**
     * periodTokens é o consumo do ciclo. A cobrança do lojista incide só no excedente da franquia
     * do InPay Risk. O custo de modelo é informado sobre todos os tokens consumidos.
     */
    public static RiskQuote priceFraud(
            String planId, String mode, int periodTokens, int assessments) {
        if (periodTokens < 0 || assessments < 0) {
            throw new IllegalArgumentException("Invalid fraud usage");
        }
        RiskOffer offer =
                riskOffers().stream()
                        .filter(item -> item.planId().equals(planId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown plan"));
        String canonical = br.com.includelabs.inpay.contracts.FraudMode.canonical(mode);
        if (canonical.equals(br.com.includelabs.inpay.contracts.FraudMode.CLEARSALE)) {
            return new RiskQuote(
                    planId,
                    canonical,
                    0,
                    0,
                    0,
                    0,
                    Math.multiplyExact(assessments, offer.clearsaleCents()));
        }
        if (canonical.equals(br.com.includelabs.inpay.contracts.FraudMode.SERASA_EXPERIAN)) {
            return new RiskQuote(
                    planId,
                    canonical,
                    0,
                    0,
                    0,
                    0,
                    Math.multiplyExact(assessments, offer.serasaCents()));
        }
        int billable = (int) Math.max(0, periodTokens - offer.includedTokens());
        return new RiskQuote(
                planId,
                canonical,
                periodTokens,
                offer.includedTokens(),
                billable,
                tokensToCents(periodTokens, offer.modelCentsPerThousand()),
                tokensToCents(billable, offer.chargeCentsPerThousand()));
    }

    private static long tokensToCents(int tokens, int centsPerThousand) {
        return BigDecimal.valueOf(tokens)
                .multiply(BigDecimal.valueOf(centsPerThousand))
                .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Limita parâmetros técnicos e impede desconto superior ao principal. */
    public static void validate(long amount, String method, LocalDate due, Conditions terms) {
        if (amount <= 0
                || amount > 100000000
                || due == null
                || !Set.of("PIX", "BOLETO", "CREDIT_CARD").contains(method)
                || terms == null) throw new IllegalArgumentException("Invalid invoice");
        if (terms.fineBps() < 0
                || terms.fineBps() > 10000
                || terms.dailyInterestBps() < 0
                || terms.dailyInterestBps() > 100
                || terms.maxSurchargeBps() < 0
                || terms.maxSurchargeBps() > 10000
                || terms.discountCents() < 0
                || terms.discountCents() >= amount)
            throw new IllegalArgumentException("Invalid terms");
        if (terms.discountCents() > 0
                && (terms.discountUntil() == null || terms.discountUntil().isAfter(due)))
            throw new IllegalArgumentException("Invalid discount deadline");
        if (method.equals("CREDIT_CARD") && (terms.fineBps() > 0 || terms.dailyInterestBps() > 0))
            throw new IllegalArgumentException(
                    "Late card charges require contracted provider and explicit mandate");
    }

    /** Multa única, juros simples diários e teto; desconto somente até a data contratada. */
    public static Quote quote(long amount, LocalDate due, Conditions terms, LocalDate paidOn) {
        long days = Math.max(0, ChronoUnit.DAYS.between(due, paidOn));
        long cap = percentage(amount, terms.maxSurchargeBps());
        long fine = days > 0 ? Math.min(cap, percentage(amount, terms.fineBps())) : 0;
        long interest =
                days > 0
                        ? Math.min(
                                cap - fine,
                                percentage(
                                        amount,
                                        Math.multiplyExact((long) terms.dailyInterestBps(), days)))
                        : 0;
        long discount =
                terms.discountUntil() != null && !paidOn.isAfter(terms.discountUntil())
                        ? terms.discountCents()
                        : 0;
        return new Quote(
                amount,
                fine,
                interest,
                discount,
                Math.addExact(amount, Math.addExact(fine, interest)) - discount);
    }

    private static long percentage(long amount, long bps) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(bps))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Mantém o dia âncora: 31/jan → último dia de fevereiro → 31/mar. */
    public static LocalDate nextCycle(LocalDate current, int anchor) {
        var month = YearMonth.from(current).plusMonths(1);
        return month.atDay(Math.min(anchor, month.lengthOfMonth()));
    }

    /** Falha incerta sempre exige consulta, nunca nova cobrança automática. */
    public static String nextAction(String outcome, int attempts) {
        if (Set.of("UNKNOWN", "TIMEOUT", "PROCESSING").contains(outcome)) return "RECONCILE";
        if (Set.of("HARD_DECLINE", "LOST_CARD", "STOLEN_CARD", "REVOKED_MANDATE").contains(outcome))
            return "STOP";
        if (outcome.equals("SOFT_DECLINE") && attempts < 3)
            return "OFFER_ALTERNATIVE_OR_RETRY_WITH_MANDATE";
        return "MANUAL_REVIEW";
    }
}
