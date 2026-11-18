package br.com.includelabs.gateway.reconciliation.adapters.in.rest;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController public class ReconciliationController {
 @PostMapping("/internal/v1/reconciliation/run") @PreAuthorize("hasAuthority('SCOPE_reconciliation/run')")
 public void run(){throw new UnsupportedOperationException("Settlement adapter pending");}
}
