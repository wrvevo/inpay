package br.com.includelabs.gateway.globalpayments.adapters.in.rest;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.includelabs.gateway.globalpayments.application.AuthorizePayment;
import br.com.includelabs.gateway.globalpayments.domain.Authorization;
import br.com.includelabs.gateway.security.TenantContext;
@RestController public class AuthorizationController {
private final AuthorizePayment useCase;private final TenantContext tenants;
public AuthorizationController(AuthorizePayment useCase,TenantContext tenants){this.useCase=useCase;this.tenants=tenants;}
public record Request(UUID operationId,long amount,String instrumentToken){}
@PostMapping("/internal/v1/authorizations") @PreAuthorize("hasAuthority('SCOPE_connectors-global-payments/execute')")
public Authorization authorize(@RequestBody Request b,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){return useCase.execute(new Authorization(b.operationId(),tenants.resolve(tenant),b.amount(),b.instrumentToken(),null));}
}
