package br.com.includelabs.inpay.credits.domain;

import java.util.*;

/** Agregado transacional por tenant; mapas de crédito e pedidos são exclusivos do marketplace. */
public class CreditsState {
    public Map<String, Account> accounts = new LinkedHashMap<>();
    public Map<String, Product> products = new LinkedHashMap<>();
    public Map<UUID, Order> orders = new LinkedHashMap<>();
    public Map<UUID, Adjustment> adjustments = new LinkedHashMap<>();
    public Map<UUID, String> commands = new LinkedHashMap<>();
    public Set<String> evidence = new HashSet<>();
    public List<JournalEntry> pendingEntries = new ArrayList<>();

    /** Localiza conta sem divulgar existência de contas de outro tenant. */
    public Account account(String owner) {
        Account account = accounts.get(owner);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        return account;
    }

    /** Localiza pedido dentro do agregado autenticado. */
    public Order order(UUID id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new IllegalArgumentException("Order not found");
        }
        return order;
    }
}
