package br.com.includelabs.inpay.credits.domain;

import java.util.UUID;

/** Transferência balanceada entre dois livros operacionais, preservada sem UPDATE/DELETE. */
public record JournalEntry(
        UUID id,
        String owner,
        String debitBook,
        String creditBook,
        long amount,
        String reason,
        String actor,
        String reference,
        String occurredAt) {}
