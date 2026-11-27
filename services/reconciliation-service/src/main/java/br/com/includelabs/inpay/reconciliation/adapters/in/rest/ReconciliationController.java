package br.com.includelabs.inpay.reconciliation.adapters.in.rest;

import br.com.includelabs.inpay.reconciliation.application.ReconciliationCaseUseCase;
import br.com.includelabs.inpay.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.inpay.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {
    private final ReconciliationCaseUseCase reconciliation;
    private final TenantContext tenantContext;

    public ReconciliationController(
            ReconciliationCaseUseCase reconciliation, TenantContext tenantContext) {
        this.reconciliation = reconciliation;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/internal/v1/reconciliation/run")
    @PreAuthorize("hasAuthority('SCOPE_reconciliation/run')")
    @Operation(summary = "Compara os registros esperados e liquidados do lojista autenticado")
    public ReconciliationCase run() {
        return reconciliation.execute(tenantContext.resolve(null));
    }
}
