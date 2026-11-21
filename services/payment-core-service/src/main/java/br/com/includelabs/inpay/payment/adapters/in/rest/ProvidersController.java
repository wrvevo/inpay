package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.contracts.ProviderRegistry;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Inventário central dos adaptadores. Não divulga credenciais nem permite URLs de destino livres.
 */
@RestController
@RequestMapping("/gateway/v1/providers")
@Tag(name = "Provedores e capacidades")
public class ProvidersController {
    private final jakarta.servlet.http.HttpServletRequest http;
    private final TenantContext tenants;

    public ProvidersController(
            TenantContext tenants, jakarta.servlet.http.HttpServletRequest http) {
        this.http = http;
        this.tenants = tenants;
    }

    /** Consulta capacidades de código; habilitação comercial permanece por tenant. */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(
            summary = "Consultar conectores e capacidades implementadas",
            description =
                    "Nenhum conector Pix normalizado está habilitado nesta versão. Cobrança Pix, transferência e gestão de chaves são capacidades distintas.")
    public Map<String, Object> providers() {
        tenants.resolve(http.getHeader("X-Merchant-Id"));
        return Map.of("data", ProviderRegistry.installed().catalog(), "routing_enabled", false);
    }
}
