package br.com.includelabs.inpay.payment.adapters.in.rest;

import br.com.includelabs.inpay.payment.application.PaymentJourneys;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gateway/v1/journeys")
@Tag(
        name = "Jornadas transacionais",
        description =
                "API de integração da loja local; conector independente via REST e eventos via outbox Kafka.")
public class JourneyController {
    private final PaymentJourneys service;
    private final TenantContext tenants;

    public JourneyController(PaymentJourneys service, TenantContext tenants) {
        this.service = service;
        this.tenants = tenants;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_payments/create')")
    @Operation(
            summary = "Iniciar jornada idempotente",
            description =
                    "Não recebe PAN/CVV. Instrumento tokenizado ou fixture exclusiva do simulador local.")
    public Map<String, Object> create(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentJourneys.Create body) {
        return service.create(tenants.resolve(tenant), key, body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(summary = "Consultar jornada persistida")
    public Map<String, Object> get(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id) {
        return service.get(tenants.resolve(tenant), id);
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(summary = "Conciliar estado por consulta ao provedor original")
    public Map<String, Object> refresh(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id) {
        return service.refresh(tenants.resolve(tenant), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_payments/void')")
    @Operation(
            summary = "Cancelar jornada no provedor",
            description = "Cancelar uma única vez; incerteza exige consulta.")
    public Map<String, Object> cancel(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable UUID id) {
        return service.cancel(tenants.resolve(tenant), id);
    }

    /** Consulta de recuperação quando a resposta original se perde na rede. */
    @GetMapping("/by-key/{key}")
    @PreAuthorize("hasAuthority('SCOPE_payments/read')")
    @Operation(summary = "Recuperar jornada pela chave idempotente")
    public Map<String, Object> byKey(
            @RequestHeader(value = "X-Merchant-Id", required = false) String tenant,
            @PathVariable String key) {
        return service.byKey(tenants.resolve(tenant), key);
    }
}
