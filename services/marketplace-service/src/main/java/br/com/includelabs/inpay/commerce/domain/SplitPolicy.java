package br.com.includelabs.inpay.commerce.domain;

import java.util.*;

/** Valida divisão em centavos, sem arredondamento e sem movimentar recursos. */
public final class SplitPolicy {
    private SplitPolicy() {}

    public record Allocation(String recipient_id, long amount) {}

    /** Exige recebedores distintos e conservação exata do valor. */
    public static void validate(long amount, List<Allocation> allocations) {
        if (amount <= 0 || amount > 100000000 || allocations == null || allocations.isEmpty())
            throw new IllegalArgumentException("Invalid split");
        Set<String> recipients = new HashSet<>();
        long total = 0;
        for (var part : allocations) {
            if (part.recipient_id() == null
                    || part.recipient_id().isBlank()
                    || part.amount() <= 0
                    || !recipients.add(part.recipient_id()))
                throw new IllegalArgumentException("Invalid recipient allocation");
            total = Math.addExact(total, part.amount());
        }
        if (total != amount) throw new IllegalArgumentException("Split does not conserve amount");
    }

    public record Rule(String recipientId, long fixedCents, int percentageBps) {}

    /**
     * Percentuais incidem no restante após valores fixos. Sobras seguem maior resto, com desempate
     * por ID.
     */
    public static List<Allocation> allocate(long total, List<Rule> rules) {
        if (total <= 0 || rules == null || rules.isEmpty())
            throw new IllegalArgumentException("Invalid split");
        Set<String> seen = new HashSet<>();
        long fixed = 0;
        int bps = 0;
        for (Rule rule : rules) {
            if (rule.recipientId() == null
                    || rule.recipientId().isBlank()
                    || !seen.add(rule.recipientId())
                    || rule.fixedCents() < 0
                    || rule.percentageBps() < 0
                    || rule.percentageBps() > 10000
                    || (rule.fixedCents() > 0) == (rule.percentageBps() > 0))
                throw new IllegalArgumentException("Invalid split rule");
            fixed = Math.addExact(fixed, rule.fixedCents());
            bps = Math.addExact(bps, rule.percentageBps());
        }
        long remaining = total - fixed;
        if (remaining < 0 || (remaining > 0 && bps != 10000) || (remaining == 0 && bps != 0))
            throw new IllegalArgumentException("Split does not cover total");
        Map<String, Long> amounts = new TreeMap<>();
        Map<String, Long> residues = new HashMap<>();
        long assigned = fixed;
        for (Rule rule : rules) {
            if (rule.fixedCents() > 0) {
                amounts.put(rule.recipientId(), rule.fixedCents());
                continue;
            }
            var quotient =
                    java.math.BigInteger.valueOf(remaining)
                            .multiply(java.math.BigInteger.valueOf(rule.percentageBps()))
                            .divideAndRemainder(java.math.BigInteger.valueOf(10000));
            long amount = quotient[0].longValueExact();
            amounts.put(rule.recipientId(), amount);
            residues.put(rule.recipientId(), quotient[1].longValueExact());
            assigned += amount;
        }
        var order =
                residues.keySet().stream()
                        .sorted(
                                Comparator.<String>comparingLong(residues::get)
                                        .reversed()
                                        .thenComparing(Comparator.naturalOrder()))
                        .toList();
        for (int index = 0; assigned < total; index++, assigned++)
            amounts.merge(order.get(index), 1L, Long::sum);
        // Zero allocations remain visible in the preview but must not be sent to providers
        // requiring positive shares.
        return amounts.entrySet().stream()
                .map(e -> new Allocation(e.getKey(), e.getValue()))
                .toList();
    }
}
