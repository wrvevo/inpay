package br.com.includelabs.inpay.marketplace.domain;

import java.util.*;
import java.util.function.Function;

/**
 * Porta de persistência; serializa operações concorrentes por tenant e publica outbox atomicamente.
 */
public interface MarketplaceRepository {
    <T> T transact(UUID tenant, Function<MarketplaceState, T> operation);

    MarketplaceState read(UUID tenant);

    List<JournalEntry> journal(UUID tenant, String owner);
}
