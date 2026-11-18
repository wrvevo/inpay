package br.com.includelabs.gateway.token.adapters.in.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.includelabs.gateway.token.domain.Instrument;
import br.com.includelabs.gateway.token.domain.port.InstrumentStore;
import br.com.includelabs.gateway.token.application.CreateSimulationToken;
import br.com.includelabs.gateway.security.*;
@RestController @Profile("local")
public class TokenController {
 private final InstrumentStore store;private final TenantContext tenants;
 public TokenController(InstrumentStore store,TenantContext tenants){this.store=store;this.tenants=tenants;}
 public record Request(String provider,String affiliationId){}
 @PostMapping("/local/v1/instruments") @PreAuthorize("hasAuthority('SCOPE_tokens/write')")
 public Instrument create(@RequestBody Request b,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){return new CreateSimulationToken(store).execute(tenants.resolve(tenant),b.provider(),b.affiliationId());}
 @GetMapping("/internal/v1/instruments/{token}") @PreAuthorize("hasAuthority('SCOPE_tokens/resolve')")
 public Instrument read(@PathVariable String token,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){return store.find(tenants.resolve(tenant),token).orElseThrow(ApiErrors.NoSuchResource::new);}
 @DeleteMapping("/v1/instruments/{token}") @PreAuthorize("hasAuthority('SCOPE_tokens/write')")
 public void revoke(@PathVariable String token,@RequestHeader(value="X-Merchant-Id",required=false)String tenant){store.revoke(tenants.resolve(tenant),token);}
}
