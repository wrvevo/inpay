package br.com.includelabs.inpay.accounts.adapters.in.rest;

import br.com.includelabs.inpay.contracts.SandboxBindings;
import br.com.includelabs.inpay.resources.ResourceRepository;
import br.com.includelabs.inpay.security.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/** Persistent, tenant-scoped configuration for onboarding tests. Never opens a bank account. */
@RestController
@Profile("local")
@RequestMapping("/accounts/v1/sandbox-configurations")
public class SandboxConfigurationController {
    private static final String KIND = "store-payment-config";
    private final ResourceRepository store;
    private final TenantContext tenants;

    public SandboxConfigurationController(ResourceRepository store, TenantContext tenants) {
        this.store = store;
        this.tenants = tenants;
    }

    public record Configuration(UUID storeId, Map<String, String> bindings) {}

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_accounts/write')")
    public JsonNode provision(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody Configuration request) {
        if (request.storeId() == null) throw new IllegalArgumentException("Store required");
        SandboxBindings.validate(request.bindings());
        return store.create(
                tenants.resolve(tenant), KIND, key, request,
                id -> Map.of("id", id, "storeId", request.storeId(), "bindings", request.bindings(),
                        "status", "SANDBOX_CONFIGURED", "productionEnabled", false));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_accounts/read')")
    public JsonNode get(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable String id) {
        return store.get(tenants.resolve(tenant), KIND, id);
    }
}
