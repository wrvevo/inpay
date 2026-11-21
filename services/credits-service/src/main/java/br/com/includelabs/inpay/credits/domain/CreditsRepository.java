package br.com.includelabs.inpay.credits.domain;

import java.util.*;
import java.util.function.Function;

/**
 * Porta de persistência; serializa operações concorrentes por tenant e publica outbox atomicamente.
 */
public interface CreditsRepository {
    <T> T transact(UUID tenant, Function<CreditsState, T> operation);

    CreditsState read(UUID tenant);

    List<JournalEntry> journal(UUID tenant, String owner);
}
