package br.com.includelabs.inpay.commerce.adapters.in.rest;

import br.com.includelabs.inpay.commerce.domain.SplitPolicy;
import br.com.includelabs.inpay.resources.ResourceRepository;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Gestão comercial isolada de crédito e custódia; split planejado não comprova liquidação. */
@RestController
@RequestMapping("/marketplace/v1")
@Tag(
        name = "INPAY Marketplace",
        description =
                "Vendedores, planos de split e solicitações de repasse. Não mantém saldo de benefícios.")
public class CommerceController {
    private final jakarta.servlet.http.HttpServletRequest http;
    private final ResourceRepository store;
    private final TenantContext tenants;

    public CommerceController(
            ResourceRepository store,
            TenantContext tenants,
            jakarta.servlet.http.HttpServletRequest http) {
        this.http = http;
        this.store = store;
        this.tenants = tenants;
    }

    public record Recipient(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[0-9]{11}|[0-9]{14}") String document,
            @NotBlank String provider_recipient_id) {}

    public record Split(
            @NotBlank String order_id,
            @Positive long amount,
            @NotEmpty List<SplitPolicy.Allocation> allocations) {}

    public record Settlement(@NotBlank String split_id, @NotBlank String evidence_reference) {}

    /**
     * Referência externa identifica vendedor; não cadastra automaticamente recebedor na adquirente.
     */
    @PostMapping("/recipients")
    @PreAuthorize("hasAuthority('SCOPE_marketplace/write')")
    @Operation(
            summary = "Cadastrar vendedor/recebedor",
            description =
                    "marketplace/write. Idempotency-Key obrigatório. Cadastro comercial não confirma KYC no provedor.")
    public JsonNode recipient(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Recipient body) {
        return store.create(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                "mp_rec",
                key,
                body,
                id ->
                        Map.of(
                                "id",
                                id,
                                "name",
                                body.name(),
                                "document",
                                body.document(),
                                "provider_recipient_id",
                                body.provider_recipient_id(),
                                "status",
                                "pending_verification"));
    }

    /** Valida soma e existência de todos os vendedores antes de persistir o plano. */
    @PostMapping("/split-plans")
    @PreAuthorize("hasAuthority('SCOPE_marketplace/write')")
    @Operation(
            summary = "Criar plano de split",
            description =
                    "Valores fixos em centavos. Planejamento não altera saldo nem executa split no provedor.")
    public JsonNode split(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Split body) {
        UUID tenant = tenants.resolve(http.getHeader("X-Merchant-Id"));
        SplitPolicy.validate(body.amount(), body.allocations());
        for (var allocation : body.allocations())
            store.get(tenant, "mp_rec", allocation.recipient_id());
        return store.create(
                tenant,
                "mp_split",
                key,
                body,
                id ->
                        Map.of(
                                "id",
                                id,
                                "order_id",
                                body.order_id(),
                                "amount",
                                body.amount(),
                                "allocations",
                                body.allocations(),
                                "status",
                                "planned"));
    }

    /**
     * Registra revisão de evidência; operador não pode transformar evidência livre em dinheiro
     * disponível.
     */
    @PostMapping("/settlements")
    @PreAuthorize("hasAuthority('SCOPE_marketplace/write')")
    @Operation(
            summary = "Solicitar conciliação de split",
            description =
                    "Registra revisão pendente. Confirmação automática depende de consulta autenticada ao provedor.")
    public JsonNode settlement(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Settlement body) {
        UUID tenant = tenants.resolve(http.getHeader("X-Merchant-Id"));
        store.get(tenant, "mp_split", body.split_id());
        return store.create(
                tenant,
                "mp_set",
                key,
                body,
                id ->
                        Map.of(
                                "id",
                                id,
                                "split_id",
                                body.split_id(),
                                "evidence_reference",
                                body.evidence_reference(),
                                "status",
                                "pending_reconciliation"));
    }

    /** Lista exclusivamente dados comerciais do tenant. */
    @GetMapping({"/recipients", "/split-plans", "/settlements"})
    @PreAuthorize("hasAuthority('SCOPE_marketplace/read')")
    @Operation(
            summary = "Listar vendedores, splits ou conciliações",
            description = "marketplace/read. Paginação de 1 a 100 itens por página.")
    public Map<String, Object> list(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Map.of(
                "data",
                store.list(
                        tenants.resolve(http.getHeader("X-Merchant-Id")),
                        kind(request.getRequestURI()),
                        page,
                        size),
                "paging",
                Map.of("page", page, "size", size));
    }

    /** Consulta um recurso comercial sem acesso cruzado entre organizações. */
    @GetMapping({"/recipients/{id}", "/split-plans/{id}", "/settlements/{id}"})
    @PreAuthorize("hasAuthority('SCOPE_marketplace/read')")
    @Operation(summary = "Obter vendedor, split ou conciliação")
    public JsonNode get(jakarta.servlet.http.HttpServletRequest request, @PathVariable String id) {
        return store.get(
                tenants.resolve(http.getHeader("X-Merchant-Id")),
                kind(request.getRequestURI()),
                id);
    }

    /** Repasse exige liquidação comprovada; execução permanece bloqueada sem parceiro. */
    @PostMapping("/settlements/{id}/payouts")
    @PreAuthorize("hasAuthority('SCOPE_marketplace/write')")
    @Operation(
            summary = "Executar repasse a vendedores",
            description = "Não habilitado. Não aceita saldo manual como comprovação de liquidação.")
    @ApiResponse(responseCode = "501", description = "Execução de repasse não integrada")
    public void payout(@PathVariable String id, @RequestHeader("Idempotency-Key") String key) {
        store.get(tenants.resolve(http.getHeader("X-Merchant-Id")), "mp_set", id);
        throw new UnsupportedOperationException();
    }

    public record RulePreview(
            @Positive long amount,
            @NotBlank String paymentMethod,
            @NotEmpty List<SplitPolicy.Rule> rules) {}

    /**
     * Prévia determinística; cada meio possui seu plano próprio e limites externos são verificados
     * na execução.
     */
    @PostMapping("/split-plans/preview")
    @PreAuthorize("hasAuthority('SCOPE_marketplace/write')")
    @Operation(
            summary = "Calcular split fixo, percentual ou misto",
            description =
                    "Percentuais em basis points sobre o restante após parcelas fixas. Não promete participantes ilimitados no provedor.")
    public Map<String, Object> preview(@Valid @RequestBody RulePreview body) {
        tenants.resolve(http.getHeader("X-Merchant-Id"));
        if (!Set.of("PIX", "BOLETO", "CREDIT_CARD").contains(body.paymentMethod()))
            throw new IllegalArgumentException();
        return Map.of(
                "payment_method",
                body.paymentMethod(),
                "allocations",
                SplitPolicy.allocate(body.amount(), body.rules()),
                "executed",
                false);
    }

    private String kind(String uri) {
        if (uri.contains("/recipients")) return "mp_rec";
        if (uri.contains("/split-plans")) return "mp_split";
        return "mp_set";
    }
}
