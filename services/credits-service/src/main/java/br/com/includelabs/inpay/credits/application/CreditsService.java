package br.com.includelabs.inpay.credits.application;

import br.com.includelabs.inpay.credits.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Casos de uso separados do gateway. Toda mutação exige tenant, identidade e chave idempotente. */
@Service
public class CreditsService {
    private final CreditsRepository repository;
    private final FulfillmentPort fulfillment;
    private final JsonMapper json;

    /** Injeta portas; regras financeiras permanecem neste contexto de marketplace. */
    public CreditsService(
            CreditsRepository repository, FulfillmentPort fulfillment, JsonMapper json) {
        this.repository = repository;
        this.fulfillment = fulfillment;
        this.json = json;
    }

    /** Retorna visão administrativa somente do tenant autenticado. */
    public CreditsState view(UUID tenant) {
        return repository.read(tenant);
    }

    /** Extrato operacional imutável, opcionalmente limitado ao titular. */
    public List<JournalEntry> journal(UUID tenant, String owner) {
        return repository.journal(tenant, owner);
    }

    /** Cria conta sem limite; concessão e repasses exigem aprovação separada. */
    public void createAccount(
            UUID tenant, UUID key, String actor, String owner, String creditor, String contract) {
        text(owner);
        text(creditor);
        text(contract);
        mutate(
                tenant,
                key,
                actor,
                List.of("account", owner, creditor, contract),
                state -> {
                    if (state.accounts.containsKey(owner)) {
                        throw new IllegalArgumentException("Account already exists");
                    }
                    Account account = new Account();
                    account.owner = owner;
                    account.creditor = creditor;
                    account.contract = contract;
                    state.accounts.put(owner, account);
                    entry(
                            state,
                            owner,
                            "ACCOUNT",
                            "CONTROL",
                            0,
                            "ACCOUNT_CREATED",
                            actor,
                            key.toString());
                });
    }

    /**
     * Publica oferta com preço controlado pelo servidor; o comprador não escolhe valor ou
     * fornecedor.
     */
    public void product(UUID tenant, UUID key, String actor, Product product) {
        text(product.sku());
        text(product.title());
        text(product.provider());
        positive(product.price());
        mutate(
                tenant,
                key,
                actor,
                List.of("product", product),
                state -> {
                    state.products.put(product.sku(), product);
                    entry(
                            state,
                            "",
                            "CATALOG",
                            "CONTROL",
                            0,
                            "PRODUCT_CHANGED",
                            actor,
                            key.toString());
                });
    }

    /**
     * Solicita limite, repasse conciliado, voucher ou amortização; evidência é obrigatória e única
     * por tipo.
     */
    public void requestAdjustment(
            UUID tenant,
            UUID key,
            String actor,
            String owner,
            String kind,
            long amount,
            String evidence,
            UUID relatedOrder) {
        if ("CREDIT_LIMIT".equals(kind)) {
            if (amount < 0 || amount > 100_000_000L) {
                throw new IllegalArgumentException("Limit outside supported range");
            }
        } else {
            positive(amount);
        }
        text(evidence);
        if (!Set.of(
                        "PROMOTIONAL_GRANT",
                        "CREDIT_LIMIT",
                        "SETTLED_REPASS",
                        "VERIFIED_VOUCHER",
                        "CREDIT_REPAYMENT")
                .contains(kind)) {
            throw new IllegalArgumentException("Invalid adjustment kind");
        }
        mutate(
                tenant,
                key,
                actor,
                List.of(
                        "request",
                        owner,
                        kind,
                        amount,
                        evidence,
                        Objects.toString(relatedOrder, "")),
                state -> {
                    state.account(owner);
                    Adjustment adjustment = new Adjustment();
                    adjustment.id = key;
                    adjustment.owner = owner;
                    adjustment.kind = kind;
                    adjustment.amount = amount;
                    adjustment.evidence = evidence;
                    adjustment.relatedOrder = relatedOrder;
                    adjustment.requestedBy = actor;
                    state.adjustments.put(key, adjustment);
                    entry(
                            state,
                            owner,
                            "APPROVAL",
                            "CONTROL",
                            0,
                            "ADJUSTMENT_REQUESTED",
                            actor,
                            key.toString());
                });
    }

    /** Exige dupla aprovação e impede aplicar a mesma evidência financeira duas vezes. */
    public void approve(UUID tenant, UUID key, String actor, UUID adjustmentId) {
        mutate(
                tenant,
                key,
                actor,
                List.of("approve", adjustmentId),
                state -> {
                    Adjustment adjustment = state.adjustments.get(adjustmentId);
                    if (adjustment == null || !"PENDING".equals(adjustment.status)) {
                        throw new IllegalArgumentException("Adjustment not pending");
                    }
                    if (actor.equals(adjustment.requestedBy)) {
                        throw new IllegalArgumentException("Independent approver required");
                    }
                    String evidence = adjustment.kind + ":" + adjustment.evidence;
                    if (!state.evidence.add(evidence)) {
                        throw new IllegalArgumentException("Evidence already reconciled");
                    }
                    Account account = state.account(adjustment.owner);
                    long amount = adjustment.amount;
                    long priorLimit = account.creditLimit;
                    switch (adjustment.kind) {
                        case "PROMOTIONAL_GRANT" ->
                                account.promotional = Math.addExact(account.promotional, amount);
                        case "CREDIT_LIMIT" -> {
                            if (amount < account.creditUsed + account.creditReserved) {
                                throw new IllegalArgumentException("Limit below exposure");
                            }
                            account.creditLimit = amount;
                        }
                        case "SETTLED_REPASS" ->
                                account.balance = Math.addExact(account.balance, amount);
                        case "VERIFIED_VOUCHER" ->
                                account.voucher = Math.addExact(account.voucher, amount);
                        case "CREDIT_REPAYMENT" -> {
                            Order paid = state.order(adjustment.relatedOrder);
                            if (!paid.owner.equals(adjustment.owner)
                                    || paid.status != OrderStatus.DELIVERED
                                    || amount > paid.creditOutstanding) {
                                throw new IllegalArgumentException(
                                        "Repayment must match outstanding order credit");
                            }
                            paid.creditOutstanding -= amount;
                            account.creditUsed -= amount;
                        }
                        default -> throw new IllegalArgumentException("Unknown adjustment");
                    }
                    adjustment.status = "APPROVED";
                    adjustment.approvedBy = actor;
                    String debit = "EXTERNAL_EVIDENCE";
                    String credit =
                            adjustment.kind.equals("SETTLED_REPASS")
                                    ? "BALANCE_AVAILABLE"
                                    : "VOUCHER_AVAILABLE";
                    long movement = amount;
                    if (adjustment.kind.equals("PROMOTIONAL_GRANT"))
                        credit = "PROMOTIONAL_AVAILABLE";
                    if (adjustment.kind.equals("CREDIT_LIMIT")) {
                        long delta = amount - priorLimit;
                        debit = delta >= 0 ? "CREDIT_CAPACITY" : "CREDIT_AVAILABLE";
                        credit = delta >= 0 ? "CREDIT_AVAILABLE" : "CREDIT_CAPACITY";
                        movement = Math.abs(delta);
                    } else if (adjustment.kind.equals("CREDIT_REPAYMENT")) {
                        debit = "CREDIT_CONSUMED";
                        credit = "CREDIT_AVAILABLE";
                    }
                    entry(
                            state,
                            adjustment.owner,
                            debit,
                            credit,
                            movement,
                            "ADJUSTMENT_APPROVED",
                            actor,
                            evidence);
                });
    }

    /** Bloqueia novas compras, preservando reservas e obrigações existentes. */
    public void block(UUID tenant, UUID key, String actor, String owner, boolean blocked) {
        mutate(
                tenant,
                key,
                actor,
                List.of("block", owner, blocked),
                state -> {
                    state.account(owner).blocked = blocked;
                    entry(
                            state,
                            owner,
                            "ACCOUNT",
                            "CONTROL",
                            0,
                            blocked ? "BLOCKED" : "UNBLOCKED",
                            actor,
                            key.toString());
                });
    }

    /**
     * Reserva uma ou duas fontes, sem liberar produto antes da confirmação de toda a composição.
     */
    public void order(
            UUID tenant, UUID key, String actor, String owner, String sku, List<Tender> tenders) {
        if (tenders == null || tenders.isEmpty() || tenders.size() > 2) {
            throw new IllegalArgumentException("Choose one or two payment sources");
        }
        long sum = 0;
        Set<Source> sources = new HashSet<>();
        for (Tender tender : tenders) {
            if (tender == null || tender.source() == null || !sources.add(tender.source())) {
                throw new IllegalArgumentException("Distinct payment sources required");
            }
            positive(tender.amount());
            sum = Math.addExact(sum, tender.amount());
        }
        final long total = sum;
        mutate(
                tenant,
                key,
                actor,
                List.of("order", owner, sku, tenders),
                state -> {
                    Product product = state.products.get(sku);
                    if (product == null || !product.active() || product.price() != total) {
                        throw new IllegalArgumentException("Invalid product or tender total");
                    }
                    Account account = state.account(owner);
                    for (Tender tender : tenders) {
                        account.reserve(tender.source(), tender.amount());
                    }
                    Order order = new Order();
                    order.id = key;
                    order.owner = owner;
                    order.sku = sku;
                    order.title = product.title();
                    order.provider = product.provider();
                    order.total = total;
                    order.tenders = List.copyOf(tenders);
                    order.status = OrderStatus.RESERVED;
                    order.createdAt = Instant.now().toString();
                    state.orders.put(key, order);
                    for (Tender tender : tenders) {
                        entry(
                                state,
                                owner,
                                tender.source() + "_AVAILABLE",
                                tender.source() + "_RESERVED",
                                tender.amount(),
                                "ORDER_RESERVED",
                                actor,
                                key.toString());
                    }
                });
    }

    /** Processa somente pedido reservado. Adaptador real permanece indisponível sem homologação. */
    public void process(UUID tenant, UUID key, String actor, UUID id, boolean reconcile) {
        mutate(
                tenant,
                key,
                actor,
                List.of(reconcile ? "reconcile" : "process", id),
                state -> {
                    Order order = state.order(id);
                    if (reconcile) {
                        if (order.status != OrderStatus.PENDING_RECONCILIATION
                                && order.status != OrderStatus.COMPENSATION_REQUIRED) {
                            throw new IllegalArgumentException(
                                    "Order has no pending reconciliation");
                        }
                    } else if (order.status != OrderStatus.RESERVED) {
                        throw new IllegalArgumentException("Order not reserved");
                    }
                    FulfillmentPort.Evidence evidence =
                            reconcile
                                    ? fulfillment.reconcile(tenant, order)
                                    : fulfillment.fulfill(tenant, order);
                    order.providerReference = evidence.reference();
                    order.simulation = evidence.simulation();
                    switch (evidence.result()) {
                        case CONFIRMED -> {
                            for (Tender tender : order.tenders) {
                                state.account(order.owner)
                                        .consume(tender.source(), tender.amount());
                                entry(
                                        state,
                                        order.owner,
                                        tender.source() + "_RESERVED",
                                        tender.source() + "_CONSUMED",
                                        tender.amount(),
                                        "ORDER_DELIVERED",
                                        actor,
                                        id.toString());
                            }
                            order.creditOutstanding =
                                    order.tenders.stream()
                                            .filter(t -> t.source() == Source.CREDIT)
                                            .mapToLong(Tender::amount)
                                            .sum();
                            order.status = OrderStatus.DELIVERED;
                        }
                        case REJECTED -> release(state, order, actor);
                        case UNKNOWN -> order.status = OrderStatus.PENDING_RECONCILIATION;
                        case COMPENSATION_REQUIRED ->
                                order.status = OrderStatus.COMPENSATION_REQUIRED;
                    }
                    entry(
                            state,
                            order.owner,
                            "ORDER",
                            "CONTROL",
                            0,
                            "ORDER_" + order.status,
                            actor,
                            id.toString());
                });
    }

    /** Cancela apenas antes do envio ao fornecedor; estados ambíguos exigem conciliação. */
    public void cancel(UUID tenant, UUID key, String actor, String owner, UUID id) {
        mutate(
                tenant,
                key,
                actor,
                List.of("cancel", owner, id),
                state -> {
                    Order order = state.order(id);
                    if (!order.owner.equals(owner) || order.status != OrderStatus.RESERVED) {
                        throw new IllegalArgumentException("Order cannot be cancelled");
                    }
                    release(state, order, actor);
                });
    }

    /**
     * Estorna fontes originais somente após confirmação do fornecedor; nunca transforma crédito em
     * saldo sacável.
     */
    public void refund(UUID tenant, UUID key, String actor, UUID id) {
        mutate(
                tenant,
                key,
                actor,
                List.of("refund", id),
                state -> {
                    Order order = state.order(id);
                    if (order.status != OrderStatus.DELIVERED) {
                        throw new IllegalArgumentException("Only delivered orders can be refunded");
                    }
                    Account account = state.account(order.owner);
                    long credit =
                            order.tenders.stream()
                                    .filter(t -> t.source() == Source.CREDIT)
                                    .mapToLong(Tender::amount)
                                    .sum();
                    if (credit > order.creditOutstanding) {
                        throw new IllegalArgumentException(
                                "Repaid credit refund requires creditor allocation");
                    }
                    FulfillmentPort.Evidence evidence = fulfillment.refund(tenant, order);
                    if (evidence.result() != FulfillmentPort.Result.CONFIRMED) {
                        throw new IllegalArgumentException(
                                "Refund not confirmed; original balance retained");
                    }
                    for (Tender tender : order.tenders) {
                        switch (tender.source()) {
                            case PROMOTIONAL ->
                                    account.promotional =
                                            Math.addExact(account.promotional, tender.amount());
                            case CREDIT -> account.creditUsed -= tender.amount();
                            case BALANCE ->
                                    account.balance =
                                            Math.addExact(account.balance, tender.amount());
                            case VOUCHER ->
                                    account.voucher =
                                            Math.addExact(account.voucher, tender.amount());
                            case EXTERNAL -> {}
                        }
                        entry(
                                state,
                                order.owner,
                                tender.source() + "_CONSUMED",
                                tender.source() + "_AVAILABLE",
                                tender.amount(),
                                "ORDER_REFUNDED",
                                actor,
                                id.toString());
                    }
                    order.creditOutstanding = 0;
                    order.status = OrderStatus.REFUNDED;
                });
    }

    /**
     * Retorna pedido somente ao seu titular; nunca usa owner fornecido pelo navegador como
     * identidade.
     */
    public Order ownedOrder(UUID tenant, String owner, UUID id) {
        Order order = repository.read(tenant).order(id);
        if (!order.owner.equals(owner)) {
            throw new IllegalArgumentException("Order not found");
        }
        return order;
    }

    /**
     * Idempotência cobre identidade, comando e corpo; rollback preserva o estado anterior em
     * falhas.
     */
    private void mutate(
            UUID tenant,
            UUID key,
            String actor,
            Object command,
            java.util.function.Consumer<CreditsState> operation) {
        if (key == null) {
            throw new IllegalArgumentException("operationId required");
        }
        String signature = json.writeValueAsString(List.of(actor, command));
        repository.transact(
                tenant,
                state -> {
                    String previous = state.commands.get(key);
                    if (previous != null) {
                        if (!previous.equals(signature)) {
                            throw new IllegalArgumentException("Idempotency conflict");
                        }
                        return null;
                    }
                    operation.accept(state);
                    state.commands.put(key, signature);
                    return null;
                });
    }

    /** Reverte reservas de todas as fontes em uma única transação. */
    private void release(CreditsState state, Order order, String actor) {
        for (Tender tender : order.tenders) {
            state.account(order.owner).release(tender.source(), tender.amount());
            entry(
                    state,
                    order.owner,
                    tender.source() + "_RESERVED",
                    tender.source() + "_AVAILABLE",
                    tender.amount(),
                    "ORDER_CANCELLED",
                    actor,
                    order.id.toString());
        }
        order.status = OrderStatus.CANCELLED;
    }

    /**
     * Registra transferência balanceada e evidência; postagem não equivale à movimentação bancária.
     */
    private void entry(
            CreditsState state,
            String owner,
            String debit,
            String credit,
            long amount,
            String reason,
            String actor,
            String reference) {
        state.pendingEntries.add(
                new JournalEntry(
                        UUID.randomUUID(),
                        owner,
                        debit,
                        credit,
                        amount,
                        reason,
                        actor,
                        reference,
                        Instant.now().toString()));
    }

    /** Valida valores e limita exposição por operação a cem milhões de centavos. */
    private void positive(long value) {
        if (value <= 0 || value > 100_000_000L) {
            throw new IllegalArgumentException("Amount outside supported range");
        }
    }

    /** Rejeita identificadores vazios ou excessivos antes da persistência. */
    private void text(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Invalid identifier");
        }
    }
}
