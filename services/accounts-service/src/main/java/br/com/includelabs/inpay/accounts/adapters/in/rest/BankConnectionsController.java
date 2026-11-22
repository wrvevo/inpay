package br.com.includelabs.inpay.accounts.adapters.in.rest;

import br.com.includelabs.inpay.resources.ResourceRepository;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Configurações de parceiros por tenant. Armazena referências de segredos, nunca credenciais. */
@RestController
@RequestMapping("/accounts/v1")
@Tag(name = "Conexões bancárias")
public class BankConnectionsController {
    private final ResourceRepository store;
    private final TenantContext tenants;
    private final JsonMapper json;

    public BankConnectionsController(
            ResourceRepository store, TenantContext tenants, JsonMapper json) {
        this.store = store;
        this.tenants = tenants;
        this.json = json;
    }

    public record Connection(
            @NotBlank String bank,
            @NotBlank @Pattern(regexp = "sandbox|production") String environment,
            @NotBlank @Size(max = 256) String credentialReference,
            @NotBlank @Size(max = 256) String certificateReference) {}

    /** Inventário distingue configuração disponível de integração implementada. */
    @GetMapping("/banks")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @Operation(summary = "Listar instituições e maturidade dos adaptadores")
    public JsonNode banks() throws java.io.IOException {
        try (var input = getClass().getResourceAsStream("/banks/catalog.json")) {
            return json.readTree(input);
        }
    }

    @GetMapping("/connections")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    @Operation(summary = "Consultar configurações bancárias do tenant")
    public Map<String, Object> connections(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant) {
        return Map.of("data", store.list(tenants.resolve(tenant), "bank", 1, 100));
    }

    /** Registro pendente, sem chamada ao banco nem autoativação em produção. */
    @PostMapping("/connections")
    @PreAuthorize("hasAuthority('SCOPE_accounts/write')")
    @Operation(
            summary = "Registrar referências de configuração bancária",
            description =
                    "Estado PENDING_IMPLEMENTATION; não testa credenciais nem habilita operações.")
    public JsonNode create(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Connection body)
            throws java.io.IOException {
        boolean known = false;
        for (var bank : banks()) if (bank.path("id").asText().equals(body.bank())) known = true;
        if (!known) throw new IllegalArgumentException("Unknown bank");
        return store.create(
                tenants.resolve(tenant),
                "bank",
                key,
                body,
                id ->
                        Map.of(
                                "id",
                                id,
                                "bank",
                                body.bank(),
                                "environment",
                                body.environment(),
                                "credential_reference",
                                body.credentialReference(),
                                "certificate_reference",
                                body.certificateReference(),
                                "status",
                                "PENDING_IMPLEMENTATION",
                                "enabled",
                                false));
    }
}
