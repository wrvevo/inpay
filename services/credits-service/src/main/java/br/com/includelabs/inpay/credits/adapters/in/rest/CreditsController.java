package br.com.includelabs.inpay.credits.adapters.in.rest;

import br.com.includelabs.inpay.credits.application.CreditsService;
import br.com.includelabs.inpay.credits.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** APIs administrativas e do titular, fora do namespace público de pagamentos. */
@RestController
@RequestMapping("/credits/v1")
@Tag(name = "Credits: crédito, saldos e compras combinadas")
public class CreditsController {
    private final CreditsService service;
    private final CreditsIdentity identity;

    /** Injeta casos de uso e autorização contextual. */
    public CreditsController(CreditsService service, CreditsIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @Schema(
            description =
                    "Todos os valores são centavos; operationId é a chave idempotente por tenant.")
    public record AccountRequest(
            @NotNull UUID operationId,
            @NotBlank String owner,
            @NotBlank String creditor,
            @NotBlank String contract) {}

    public record ProductRequest(@NotNull UUID operationId, @NotNull Product product) {}

    public record AdjustmentRequest(
            @NotNull UUID operationId,
            @NotBlank String owner,
            @NotBlank String kind,
            @PositiveOrZero long amount,
            @NotBlank String evidence,
            UUID relatedOrder) {}

    public record Command(@NotNull UUID operationId) {}

    public record BlockRequest(
            @NotNull UUID operationId, @NotBlank String owner, boolean blocked) {}

    public record PurchaseRequest(
            @NotNull UUID operationId, @NotBlank String sku, @NotNull List<Tender> tenders) {}

    /** Retorna visão gerencial exclusiva do tenant. */
    @GetMapping("/admin")
    @PreAuthorize("@creditsIdentity.hasRole('admin')")
    @Operation(summary = "Consultar contas, catálogo, pedidos, aprovações e extrato do marketplace")
    public Map<String, Object> admin(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant) {
        UUID id = identity.tenant(tenant);
        CreditsState state = service.view(id);
        return Map.of(
                "accounts",
                state.accounts.values(),
                "products",
                state.products.values(),
                "orders",
                state.orders.values(),
                "adjustments",
                state.adjustments.values(),
                "journal",
                service.journal(id, null));
    }

    /** Cria cadastro operacional sem conceder limite automaticamente. */
    @PostMapping("/admin/accounts")
    @PreAuthorize("@creditsIdentity.hasRole('admin')")
    @Operation(summary = "Cadastrar conta com titular, concedente e referência contratual")
    public Map<String, String> account(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody AccountRequest body) {
        service.createAccount(
                identity.tenant(tenant),
                body.operationId(),
                identity.actor(),
                body.owner(),
                body.creditor(),
                body.contract());
        return ok();
    }

    /** Atualiza catálogo controlado pelo operador. */
    @PostMapping("/admin/products")
    @PreAuthorize("@creditsIdentity.hasRole('admin')")
    @Operation(summary = "Cadastrar produto elegível e seu fornecedor")
    public Map<String, String> product(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody ProductRequest body) {
        service.product(
                identity.tenant(tenant), body.operationId(), identity.actor(), body.product());
        return ok();
    }

    /** Registra solicitação; não credita saldo antes da revisão independente. */
    @PostMapping("/admin/adjustments")
    @PreAuthorize("@creditsIdentity.hasRole('admin')")
    @Operation(
            summary = "Solicitar limite, repasse, voucher ou amortização",
            description =
                    "Tipos: PROMOTIONAL_GRANT, CREDIT_LIMIT, SETTLED_REPASS, VERIFIED_VOUCHER, CREDIT_REPAYMENT. Amortização exige relatedOrder. Apenas CREDIT_LIMIT aceita zero. Evidência externa obrigatória; não constitui integração Nuuvem automática.")
    public Map<String, String> adjustment(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody AdjustmentRequest body) {
        service.requestAdjustment(
                identity.tenant(tenant),
                body.operationId(),
                identity.actor(),
                body.owner(),
                body.kind(),
                body.amount(),
                body.evidence(),
                body.relatedOrder());
        return ok();
    }

    /** Confirma evidência sob identidade diferente da solicitante. */
    @PostMapping("/admin/adjustments/{id}/approve")
    @PreAuthorize("@creditsIdentity.hasRole('approve')")
    @Operation(summary = "Aprovar alteração com dupla checagem e evidência única")
    public Map<String, String> approve(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id,
            @Valid @RequestBody Command body) {
        service.approve(identity.tenant(tenant), body.operationId(), identity.actor(), id);
        return ok();
    }

    /** Bloqueia novas compras sem apagar histórico. */
    @PostMapping("/admin/accounts/block")
    @PreAuthorize("@creditsIdentity.hasRole('admin')")
    @Operation(summary = "Bloquear ou desbloquear novas compras")
    public Map<String, String> block(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody BlockRequest body) {
        service.block(
                identity.tenant(tenant),
                body.operationId(),
                identity.actor(),
                body.owner(),
                body.blocked());
        return ok();
    }

    /** Executa emissão ou conciliação usando somente adaptador configurado no servidor. */
    @PostMapping("/admin/orders/{id}/{action}")
    @PreAuthorize("@creditsIdentity.hasRole('operate')")
    @Operation(
            summary = "Processar, conciliar ou estornar pedido",
            description =
                    "action: process, reconcile, refund. Simulação exclusiva do perfil local; operação real bloqueada sem conector homologado.")
    public Map<String, String> operate(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id,
            @PathVariable String action,
            @Valid @RequestBody Command body) {
        UUID tenantId = identity.tenant(tenant);
        if (action.equals("refund")) {
            service.refund(tenantId, body.operationId(), identity.actor(), id);
        } else if (Set.of("process", "reconcile").contains(action)) {
            service.process(
                    tenantId, body.operationId(), identity.actor(), id, action.equals("reconcile"));
        } else {
            throw new IllegalArgumentException("Unknown action");
        }
        return ok();
    }

    /** Filtra dados pelo subject do access token; não aceita owner fornecido pelo cliente. */
    @GetMapping("/me")
    @PreAuthorize("@creditsIdentity.hasRole('customer')")
    @Operation(summary = "Consultar meu limite, saldo, vouchers, compras e extrato")
    public Map<String, Object> me(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant) {
        UUID tenantId = identity.tenant(tenant);
        String owner = identity.customer();
        CreditsState state = service.view(tenantId);
        return Map.of(
                "account",
                state.account(owner),
                "products",
                state.products.values().stream().filter(Product::active).toList(),
                "orders",
                state.orders.values().stream().filter(order -> order.owner.equals(owner)).toList(),
                "journal",
                service.journal(tenantId, owner));
    }

    /** Efetua reserva de até duas formas de pagamento para o titular autenticado. */
    @PostMapping("/me/orders")
    @PreAuthorize("@creditsIdentity.hasRole('customer')")
    @Operation(
            summary = "Criar compra com uma ou duas fontes",
            description =
                    "PROMOTIONAL, CREDIT, BALANCE, VOUCHER ou EXTERNAL. Valores devem somar exatamente o preço do catálogo. EXTERNAL exige confirmação pelo adaptador antes da entrega.")
    public Map<String, Object> purchase(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @Valid @RequestBody PurchaseRequest body) {
        UUID tenantId = identity.tenant(tenant);
        String owner = identity.customer();
        service.order(
                tenantId, body.operationId(), identity.actor(), owner, body.sku(), body.tenders());
        return Map.of("order", service.ownedOrder(tenantId, owner, body.operationId()));
    }

    /** Cancela apenas pedido do próprio usuário ainda não enviado ao fornecedor. */
    @PostMapping("/me/orders/{id}/cancel")
    @PreAuthorize("@creditsIdentity.hasRole('customer')")
    @Operation(summary = "Cancelar minha reserva antes do processamento")
    public Map<String, String> cancel(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id,
            @Valid @RequestBody Command body) {
        service.cancel(
                identity.tenant(tenant),
                body.operationId(),
                identity.actor(),
                identity.customer(),
                id);
        return ok();
    }

    private Map<String, String> ok() {
        return Map.of("status", "OK");
    }
}
