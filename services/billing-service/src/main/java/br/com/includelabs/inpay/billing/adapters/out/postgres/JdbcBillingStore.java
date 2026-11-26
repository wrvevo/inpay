package br.com.includelabs.inpay.billing.adapters.out.postgres;

import br.com.includelabs.inpay.billing.application.BillingCommands.*;
import br.com.includelabs.inpay.billing.domain.*;
import br.com.includelabs.inpay.billing.domain.BillingPolicy.Conditions;
import br.com.includelabs.inpay.security.ApiErrors;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistência normalizada, comandos idempotentes e locks por recurso; não bloqueia um tenant
 * inteiro.
 */
@Repository
public class JdbcBillingStore
        implements BillingStore, br.com.includelabs.inpay.billing.application.BillingOperations {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public JdbcBillingStore(JdbcTemplate jdbc, TransactionTemplate tx, JsonMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    /** Registra solicitação de homologação; nenhuma conta bancária é aberta. */
    public Map<String, Object> register(String actor, Merchant body) {
        BillingPolicy.plan(body.plan());
        String fraudMode =
                body.fraudMode() == null || body.fraudMode().isBlank()
                        ? "INPAY_RISK"
                        : br.com.includelabs.inpay.contracts.FraudMode.canonical(body.fraudMode());
        return command(
                "merchant",
                body.operationId(),
                actor,
                body,
                () -> {
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO billing_merchants(id,tenant,name,plan,status,requested_by,evidence,fraud_mode) VALUES(?,?,?,?,'PENDING',?,?,?)",
                            id,
                            body.tenant(),
                            body.name(),
                            body.plan(),
                            actor,
                            body.evidence(),
                            fraudMode);
                    return Map.of("id", id, "status", "PENDING", "fraudMode", fraudMode);
                });
    }

    /** Aprovação exige identidade diferente da que solicitou e mantém trilha auditável. */
    public Map<String, Object> approve(String actor, UUID id, Command body) {
        return command(
                "approve",
                body.operationId(),
                actor,
                Map.of("id", id),
                () -> {
                    var merchant = merchant(id, true);
                    if (!merchant.get("status").equals("PENDING")
                            || merchant.get("requested_by").equals(actor))
                        throw new IllegalStateException("Independent review required");
                    jdbc.update(
                            "UPDATE billing_merchants SET status='APPROVED',approved_by=? WHERE id=?",
                            actor,
                            id);
                    return Map.of("id", id, "status", "APPROVED");
                });
    }

    /** Emite fatura operacional; geração de boleto/QR é uma etapa de provedor distinta. */
    public Map<String, Object> createInvoice(String actor, Invoice body) {
        BillingPolicy.validate(body.amount(), body.method(), body.dueDate(), body.conditions());
        return command(
                "invoice",
                body.operationId(),
                actor,
                body,
                () -> {
                    approved(body.merchantId());
                    UUID id =
                            insertInvoice(
                                    body.merchantId(),
                                    body.customerRef(),
                                    body.amount(),
                                    body.method(),
                                    body.dueDate(),
                                    body.conditions());
                    return Map.of(
                            "id", id, "status", "OPEN", "collection_status", "NOT_DISPATCHED");
                });
    }

    /** Guarda autorização de recorrência; o agendador gera faturas, não debita o cartão. */
    public Map<String, Object> subscribe(String actor, Subscription body) {
        BillingPolicy.validate(body.amount(), body.method(), body.firstDue(), body.conditions());
        return command(
                "subscription",
                body.operationId(),
                actor,
                body,
                () -> {
                    approved(body.merchantId());
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO billing_subscriptions(id,merchant_id,customer_ref,amount,method,anchor_day,next_on,conditions) VALUES(?,?,?,?,?,?,?,?::jsonb)",
                            id,
                            body.merchantId(),
                            body.customerRef(),
                            body.amount(),
                            body.method(),
                            body.anchorDay(),
                            body.firstDue(),
                            json.writeValueAsString(body.conditions()));
                    return Map.of(
                            "id",
                            id,
                            "status",
                            "ACTIVE",
                            "mandate_reference",
                            body.mandateReference());
                });
    }

    /** Cancela apenas ciclos futuros; faturas emitidas permanecem auditáveis. */
    public Map<String, Object> cancelSubscription(String actor, UUID id, Command body) {
        return command(
                "cancel-subscription",
                body.operationId(),
                actor,
                Map.of("id", id),
                () -> {
                    if (jdbc.update("UPDATE billing_subscriptions SET active=false WHERE id=?", id)
                            == 0) throw new ApiErrors.NoSuchResource();
                    return Map.of("id", id, "status", "CANCELLED");
                });
    }

    /** Processa lote limitado com SKIP LOCKED; cada assinatura/período produz uma única fatura. */
    public int generateCycles(LocalDate today, int batch) {
        if (batch < 1 || batch > 500) throw new IllegalArgumentException("Invalid batch");
        return tx.execute(
                status -> {
                    var rows =
                            jdbc.queryForList(
                                    "SELECT s.* FROM billing_subscriptions s JOIN billing_merchants m ON m.id=s.merchant_id WHERE s.active AND s.next_on<=? AND m.status='APPROVED' ORDER BY s.next_on,s.id LIMIT ? FOR UPDATE OF s SKIP LOCKED",
                                    today,
                                    batch);
                    for (var row : rows) {
                        UUID id = (UUID) row.get("id");
                        LocalDate cycle = ((java.sql.Date) row.get("next_on")).toLocalDate();
                        UUID invoice =
                                insertInvoice(
                                        (UUID) row.get("merchant_id"),
                                        (String) row.get("customer_ref"),
                                        ((Number) row.get("amount")).longValue(),
                                        (String) row.get("method"),
                                        cycle,
                                        json.readValue(
                                                row.get("conditions").toString(),
                                                Conditions.class));
                        jdbc.update(
                                "INSERT INTO billing_cycles(subscription_id,cycle,invoice_id) VALUES(?,?,?)",
                                id,
                                cycle,
                                invoice);
                        jdbc.update(
                                "UPDATE billing_subscriptions SET next_on=? WHERE id=?",
                                BillingPolicy.nextCycle(
                                        cycle, ((Number) row.get("anchor_day")).intValue()),
                                id);
                        audit("scheduler", "cycle-created", invoice.toString());
                    }
                    return rows.size();
                });
    }

    /** Evento de liquidação só entra pelo serviço autorizado, nunca por botão do operador. */
    public Map<String, Object> settle(String actor, Settlement body) {
        if (body.providerCost() > body.amount()
                || body.settledAt().isAfter(Instant.now().plusSeconds(300))
                || body.paidOn().isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo"))))
            throw new IllegalArgumentException("Invalid settlement");
        return tx.execute(
                status -> {
                    String payload = json.writeValueAsString(body);
                    jdbc.query(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                            rs -> {},
                            body.provider() + ":" + body.eventId());
                    var existing =
                            jdbc.queryForList(
                                    "SELECT payload::text FROM billing_events WHERE provider=? AND event_id=?",
                                    body.provider(),
                                    body.eventId());
                    if (!existing.isEmpty()) {
                        if (!json.readTree((String) existing.getFirst().get("payload"))
                                .equals(json.readTree(payload)))
                            throw new IllegalStateException("Event conflict");
                        return Map.of("status", "REPLAY");
                    }
                    var invoice =
                            one(
                                    "SELECT * FROM billing_invoices WHERE id=? FOR UPDATE",
                                    body.invoiceId());
                    if (!invoice.get("status").equals("OPEN"))
                        throw new IllegalStateException("Invoice already settled");
                    var merchant = approved((UUID) invoice.get("merchant_id"));
                    var terms =
                            json.readValue(invoice.get("conditions").toString(), Conditions.class);
                    var quote =
                            BillingPolicy.quote(
                                    ((Number) invoice.get("amount")).longValue(),
                                    ((java.sql.Date) invoice.get("due_date")).toLocalDate(),
                                    terms,
                                    body.paidOn());
                    if (quote.total() != body.amount())
                        throw new IllegalArgumentException("Amount differs from contractual quote");
                    if (!jdbc.queryForList(
                                    "SELECT id FROM billing_costs WHERE provider=? AND provider_operation=?",
                                    body.provider(),
                                    body.providerOperation())
                            .isEmpty())
                        throw new IllegalStateException("Provider operation reused");
                    var plan = BillingPolicy.plan((String) merchant.get("plan"));
                    long fee =
                            Math.min(
                                    plan.successfulOperationCents(),
                                    body.amount() - body.providerCost());
                    jdbc.update(
                            "INSERT INTO billing_events(provider,event_id,payload) VALUES(?,?,?::jsonb)",
                            body.provider(),
                            body.eventId(),
                            payload);
                    jdbc.update(
                            "INSERT INTO billing_costs(id,invoice_id,provider,provider_operation,gross,provider_cost,gateway_fee,plan,settled_at,deposit_reference) VALUES(?,?,?,?,?,?,?,?,?,?)",
                            UUID.randomUUID(),
                            body.invoiceId(),
                            body.provider(),
                            body.providerOperation(),
                            body.amount(),
                            body.providerCost(),
                            fee,
                            plan.id(),
                            java.sql.Timestamp.from(body.settledAt()),
                            body.depositReference());
                    jdbc.update(
                            "UPDATE billing_invoices SET status='PAID' WHERE id=?",
                            body.invoiceId());
                    jdbc.update(
                            "UPDATE billing_notifications SET status='CANCELLED_PAID' WHERE invoice_id=? AND status='PENDING_PROVIDER'",
                            body.invoiceId());
                    audit(actor, "settled", body.invoiceId().toString());
                    return Map.of(
                            "status",
                            "PAID",
                            "gateway_fee",
                            fee,
                            "provider_cost",
                            body.providerCost());
                });
    }

    /** Registra compromisso a pagar; não transfere dinheiro automaticamente. */
    public Map<String, Object> payable(String actor, Payable body) {
        return command(
                "payable",
                body.operationId(),
                actor,
                body,
                () -> {
                    approved(body.merchantId());
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO billing_payables(id,merchant_id,description,amount,due_date) VALUES(?,?,?,?,?)",
                            id,
                            body.merchantId(),
                            body.description(),
                            body.amount(),
                            body.dueDate());
                    return Map.of("id", id, "status", "PLANNED");
                });
    }

    /**
     * Agenda comunicação somente com referência de consentimento; não anuncia envio sem provedor.
     */
    public Map<String, Object> reminder(String actor, Reminder body) {
        if (!Set.of("EMAIL", "SMS", "WHATSAPP").contains(body.channel()))
            throw new IllegalArgumentException("Invalid channel");
        return command(
                "reminder",
                body.operationId(),
                actor,
                body,
                () -> {
                    var invoice =
                            one(
                                    "SELECT * FROM billing_invoices WHERE id=? FOR UPDATE",
                                    body.invoiceId());
                    if (!invoice.get("status").equals("OPEN"))
                        throw new IllegalStateException("Invoice not open");
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "INSERT INTO billing_notifications(id,invoice_id,channel,recipient_reference,template,consent_reference,scheduled_on) VALUES(?,?,?,?,?,?,?)",
                            id,
                            body.invoiceId(),
                            body.channel(),
                            body.recipientReference(),
                            body.template(),
                            body.consentReference(),
                            body.scheduledOn());
                    return Map.of("id", id, "status", "PENDING_PROVIDER");
                });
    }

    /** Visão global restrita à equipe InPay; totais agregados não dependem da página exibida. */
    public Map<String, Object> dashboard(int page, int size) {
        return Map.of(
                "merchants",
                resources("merchants", page, size),
                "invoices",
                resources("invoices", page, size),
                "costs",
                resources("costs", page, size),
                "summary",
                jdbc.queryForMap(
                        "SELECT (SELECT count(*) FROM billing_merchants WHERE status='APPROVED') AS approved_merchants, (SELECT coalesce(sum(amount),0) FROM billing_invoices WHERE status='OPEN') AS receivable, (SELECT coalesce(sum(amount),0) FROM billing_payables WHERE status='PLANNED') AS payable, (SELECT coalesce(sum(gateway_fee),0) FROM billing_costs) AS gateway_revenue, (SELECT coalesce(sum(provider_cost),0) FROM billing_costs) AS provider_cost, (SELECT coalesce(sum(gross),0) FROM billing_costs) AS settled_gross"),
                "page",
                page,
                "size",
                size,
                "plans",
                BillingPolicy.plans(),
                "riskOffers",
                BillingPolicy.riskOffers());
    }

    /** Paginação com tabelas fixas; nenhum nome SQL vem diretamente do cliente. */
    public List<Map<String, Object>> resources(String type, int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination");
        String table =
                switch (type) {
                    case "merchants" -> "billing_merchants";
                    case "invoices" -> "billing_invoices";
                    case "subscriptions" -> "billing_subscriptions";
                    case "costs" -> "billing_costs";
                    case "payables" -> "billing_payables";
                    case "notifications" -> "billing_notifications";
                    case "audit" -> "billing_audit";
                    default -> throw new IllegalArgumentException("Invalid resource");
                };
        return jdbc.queryForList(
                "SELECT * FROM " + table + " ORDER BY id LIMIT ? OFFSET ?",
                size,
                (page - 1) * size);
    }

    /** Fluxo previsto por vencimento; separado de caixa liquidado e sem promessa de recebimento. */
    public Map<String, Object> cashflow(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(366)))
            throw new IllegalArgumentException("Maximum interval is 366 days");
        return Map.of(
                "receivables",
                jdbc.queryForList(
                        "SELECT due_date, sum(amount) AS amount FROM billing_invoices WHERE status='OPEN' AND due_date BETWEEN ? AND ? GROUP BY due_date ORDER BY due_date",
                        from,
                        to),
                "payables",
                jdbc.queryForList(
                        "SELECT due_date, sum(amount) AS amount FROM billing_payables WHERE status='PLANNED' AND due_date BETWEEN ? AND ? GROUP BY due_date ORDER BY due_date",
                        from,
                        to));
    }

    public Map<String, Object> invoice(UUID id) {
        return one("SELECT * FROM billing_invoices WHERE id=?", id);
    }

    public UUID merchantTenant(UUID id) {
        return (UUID) merchant(id, false).get("tenant");
    }

    private Map<String, Object> merchant(UUID id, boolean lock) {
        return one("SELECT * FROM billing_merchants WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }

    private Map<String, Object> approved(UUID id) {
        var m = merchant(id, false);
        if (!m.get("status").equals("APPROVED"))
            throw new IllegalStateException("Merchant not approved");
        return m;
    }

    private Map<String, Object> one(String sql, UUID id) {
        var rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw new ApiErrors.NoSuchResource();
        return rows.getFirst();
    }

    private UUID insertInvoice(
            UUID merchant,
            String customer,
            long amount,
            String method,
            LocalDate due,
            Conditions conditions) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO billing_invoices(id,merchant_id,customer_ref,amount,method,due_date,conditions) VALUES(?,?,?,?,?,?,?::jsonb)",
                id,
                merchant,
                customer,
                amount,
                method,
                due,
                json.writeValueAsString(conditions));
        return id;
    }

    private Map<String, Object> command(
            String kind,
            UUID key,
            String actor,
            Object request,
            Supplier<Map<String, Object>> work) {
        return tx.execute(
                status -> {
                    jdbc.query(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                            rs -> {},
                            kind + ":" + key);
                    String data = json.writeValueAsString(request);
                    var rows =
                            jdbc.queryForList(
                                    "SELECT actor,request::text,result::text FROM billing_commands WHERE kind=? AND key=?",
                                    kind,
                                    key);
                    if (!rows.isEmpty()) {
                        var row = rows.getFirst();
                        if (!row.get("actor").equals(actor)
                                || !json.readTree((String) row.get("request"))
                                        .equals(json.readTree(data)))
                            throw new IllegalStateException("Idempotency conflict");
                        return json.readValue((String) row.get("result"), Map.class);
                    }
                    Map<String, Object> result = work.get();
                    jdbc.update(
                            "INSERT INTO billing_commands(kind,key,actor,request,result) VALUES(?,?,?,?::jsonb,?::jsonb)",
                            kind,
                            key,
                            actor,
                            data,
                            json.writeValueAsString(result));
                    audit(actor, kind, key.toString());
                    return result;
                });
    }

    private void audit(String actor, String action, String resource) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO billing_audit(id,actor,action,resource) VALUES(?,?,?,?)",
                id,
                actor,
                action,
                resource);
        jdbc.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES(?,?,?,?)",
                id,
                "inpay.billing.events.v1",
                resource,
                json.writeValueAsString(
                        Map.of(
                                "eventId",
                                id,
                                "type",
                                action,
                                "resource",
                                resource,
                                "occurredAt",
                                Instant.now().toString())));
    }
}
