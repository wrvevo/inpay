package br.com.includelabs.inpay.billing.adapters.in.rest;

import br.com.includelabs.inpay.billing.application.*;
import br.com.includelabs.inpay.billing.application.BillingCommands.*;
import br.com.includelabs.inpay.billing.bootstrap.BillingIdentity;
import br.com.includelabs.inpay.billing.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Backoffice global: acesso exclusivo de equipe financeira, não exposto a tokens comuns de
 * lojistas.
 */
@RestController
@RequestMapping("/billing/v1")
@Tag(name = "INPAY Gestão financeira")
public class BillingController {
    private final BillingOperations operations;
    private final BillingStore store;
    private final BillingIdentity identity;

    public BillingController(
            BillingOperations operations, BillingStore store, BillingIdentity identity) {
        this.operations = operations;
        this.store = store;
        this.identity = identity;
    }

    /** Totais globais e recursos paginados da equipe de cobrança. */
    @GetMapping("/dashboard")
    @PreAuthorize("@billingIdentity.allowed('read')")
    @Operation(
            summary = "Consultar painel financeiro",
            description = "billing/read + grupo inpay-finance. Não é endpoint público de lojistas.")
    public Map<String, Object> dashboard(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return store.dashboard(page, size);
    }

    /** Lista fechada de recursos impede acesso livre a tabelas. */
    @GetMapping("/resources/{type}")
    @PreAuthorize("@billingIdentity.allowed('read')")
    @Operation(
            summary = "Consultar contas, faturas, custos, assinaturas, compromissos e comunicações")
    public Map<String, Object> resources(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Map.of("data", store.resources(type, page, size), "page", page, "size", size);
    }

    /** Cadastro não habilita cobranças até aprovação independente. */
    @PostMapping("/merchants")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(
            summary = "Solicitar homologação de cliente e plano",
            description =
                    "operationId idempotente. Evidência contratual obrigatória; não equivale a autorização regulatória.")
    public Map<String, Object> merchant(@Valid @RequestBody Merchant body) {
        return operations.register(identity.actor(), body);
    }

    /** Revisão usa grupo separado e impede autoaprovação. */
    @PostMapping("/merchants/{id}/approve")
    @PreAuthorize("@billingIdentity.allowed('approve')")
    @Operation(
            summary = "Aprovar cliente homologado",
            description =
                    "billing/approve + inpay-finance-reviewer. Identidade distinta da solicitante.")
    public Map<String, Object> approve(@PathVariable UUID id, @Valid @RequestBody Command body) {
        return operations.approve(identity.actor(), id, body);
    }

    /** Fatura operacional não constitui boleto registrado ou QR Pix emitido. */
    @PostMapping("/invoices")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(
            summary = "Criar cobrança única",
            description =
                    "PIX, BOLETO ou CREDIT_CARD. Emissão externa é etapa separada; valores em centavos.")
    public Map<String, Object> invoice(@Valid @RequestBody Invoice body) {
        return operations.createInvoice(identity.actor(), body);
    }

    /** Agenda geração mensal mantendo a referência de mandato. */
    @PostMapping("/subscriptions")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(
            summary = "Cadastrar recorrência mensal",
            description =
                    "Gera faturas. Débito automático exige executor/token/mandato homologado; não é realizado aqui.")
    public Map<String, Object> subscription(@Valid @RequestBody Subscription body) {
        return operations.subscribe(identity.actor(), body);
    }

    @PostMapping("/subscriptions/{id}/cancel")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(summary = "Cancelar ciclos futuros")
    public Map<String, Object> cancel(@PathVariable UUID id, @Valid @RequestBody Command body) {
        return operations.cancelSubscription(identity.actor(), id, body);
    }

    /** Execução manual e agendada usam a mesma restrição única por assinatura/período. */
    @PostMapping("/cycles/run")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(summary = "Gerar lote de faturas vencidas para processamento")
    public Map<String, Object> cycles() {
        return Map.of(
                "created",
                operations.generateCycles(LocalDate.now(ZoneId.of("America/Sao_Paulo")), 100));
    }

    @PostMapping("/payables")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(
            summary = "Registrar conta a pagar",
            description = "Planejamento; não executa transferência de saída.")
    public Map<String, Object> payable(@Valid @RequestBody Payable body) {
        return operations.payable(identity.actor(), body);
    }

    @PostMapping("/reminders")
    @PreAuthorize("@billingIdentity.allowed('write')")
    @Operation(
            summary = "Agendar lembrete por e-mail, SMS ou WhatsApp",
            description =
                    "Consentimento e template obrigatórios. Sem adaptador de comunicação, estado PENDING_PROVIDER.")
    public Map<String, Object> reminder(@Valid @RequestBody Reminder body) {
        return operations.reminder(identity.actor(), body);
    }

    @GetMapping("/cashflow")
    @PreAuthorize("@billingIdentity.allowed('read')")
    @Operation(
            summary = "Consultar fluxo de caixa previsto",
            description = "Intervalo máximo de 366 dias. Previsão não é saldo bancário.")
    public Map<String, Object> cashflow(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return store.cashflow(from, to);
    }

    /**
     * Evento interno confiável: consumidores verificam assinatura/consulta do provedor antes de
     * chamar.
     */
    @PostMapping("/internal/settlements")
    @PreAuthorize("@billingIdentity.allowed('events')")
    @Operation(
            summary = "Registrar liquidação confirmada",
            description =
                    "Exclusivo de client_id em BILLING_EVENT_CLIENTS + billing/events. Nunca chamar diretamente de webhook público.")
    public Map<String, Object> settlement(@Valid @RequestBody Settlement body) {
        return operations.settle(identity.actor(), body);
    }

    @GetMapping("/retry-advice")
    @PreAuthorize("@billingIdentity.allowed('read')")
    @Operation(
            summary = "Consultar decisão de retentativa",
            description =
                    "Não executa cobrança. UNKNOWN/TIMEOUT exige reconciliação; recusa definitiva interrompe o fluxo.")
    public Map<String, Object> retry(@RequestParam String outcome, @RequestParam int attempts) {
        if (attempts < 0) throw new IllegalArgumentException();
        return Map.of("action", BillingPolicy.nextAction(outcome, attempts));
    }
}
