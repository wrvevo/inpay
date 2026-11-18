package br.com.includelabs.gateway.links.adapters.in.rest;

import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.includelabs.gateway.links.application.CreateLink;
import br.com.includelabs.gateway.links.domain.PaymentLink;
import br.com.includelabs.gateway.links.domain.port.LinkStore;
import br.com.includelabs.gateway.security.*;
@RestController public class LinkController {
 private final CreateLink create;private final LinkStore store;private final TenantContext tenants;
 public LinkController(CreateLink create,LinkStore store,TenantContext tenants){this.create=create;this.store=store;this.tenants=tenants;}
 public record Request(String merchantOrderId,long amount,Instant expiresAt){}
 @PostMapping("/v1/payment-links") @PreAuthorize("hasAuthority('SCOPE_links/write')")
 public PaymentLink create(@RequestBody Request b,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){return create.execute(tenants.resolve(tenant),b.merchantOrderId(),b.amount(),b.expiresAt());}
 @GetMapping("/v1/payment-links/{id}") @PreAuthorize("hasAuthority('SCOPE_links/read')")
 public PaymentLink get(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){return store.find(tenants.resolve(tenant),id).orElseThrow(ApiErrors.NoSuchResource::new);}
 @PostMapping("/v1/payment-links/{id}/revoke") @PreAuthorize("hasAuthority('SCOPE_links/write')")
 public void revoke(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){UUID t=tenants.resolve(tenant);store.find(t,id).orElseThrow(ApiErrors.NoSuchResource::new);store.revoke(t,id);}
 @PostMapping("/internal/v1/links/expire") @PreAuthorize("hasAuthority('SCOPE_links/write')")
 public Map<String,Integer> expire(){return Map.of("expired",store.expire());}
}
