package br.com.includelabs.inpay.commerce.domain;

import java.util.*;

/** Valida divisão em centavos, sem arredondamento e sem movimentar recursos. */
public final class SplitPolicy {
    private SplitPolicy() {}

    public record Allocation(String recipient_id, long amount) {}

    /** Exige recebedores distintos e conservação exata do valor. */
    public static void validate(long amount, List<Allocation> allocations) {
        if (amount <= 0
                || amount > 100000000
                || allocations == null
                || allocations.isEmpty()
                || allocations.size() > 100) throw new IllegalArgumentException("Invalid split");
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
}
