package br.com.includelabs.gateway.payment.adapters.in.rest;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import br.com.includelabs.gateway.payment.application.SubmitPayment;
import br.com.includelabs.gateway.payment.domain.Payment;
import br.com.includelabs.gateway.payment.domain.port.PaymentStore;
import br.com.includelabs.gateway.security.*;
@RestController @RequestMapping("/v1/payments")
public class PaymentController {
 private final SubmitPayment submit;private final PaymentStore store;private final TenantContext tenants;
 public PaymentController(SubmitPayment submit,PaymentStore store,TenantContext tenants) {this.submit=submit;this.store=store;this.tenants=tenants;}
 public record Create(@NotBlank @Size(max=128) String merchantOrderId,@Positive long amount,
   @Pattern(regexp="BRL") @NotNull String currency,@Pattern(regexp="ptok_[A-Za-z0-9_-]{20,100}") @NotNull String instrumentToken){}
 public record View(UUID id,String status,long amount,String currency,long version) {
  static View of(Payment p) {return new View(p.id(),p.status().name(),p.amount(),p.currency(),p.version());}
 }
 @PostMapping @PreAuthorize("hasAuthority('SCOPE_payments/create')")
 public ResponseEntity<View> create(@RequestHeader("Idempotency-Key") String key,
   @RequestHeader(value="X-Merchant-Id",required=false) String tenant,@Valid @RequestBody Create body) {
  return ResponseEntity.accepted().body(View.of(submit.execute(tenants.resolve(tenant),key,body.merchantOrderId(),body.amount(),body.currency(),body.instrumentToken())));
 }
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_payments/read')")
 public View get(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false) String tenant) {
  return View.of(store.find(tenants.resolve(tenant),id).orElseThrow(ApiErrors.NoSuchResource::new));
 }
 @PostMapping("/{id}/captures") @PreAuthorize("hasAuthority('SCOPE_payments/capture')")
 public void capture(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false) String tenant) {owned(id,tenant);throw new UnsupportedOperationException();}
 @PostMapping("/{id}/refunds") @PreAuthorize("hasAuthority('SCOPE_payments/refund')")
 public void refund(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false) String tenant) {owned(id,tenant);throw new UnsupportedOperationException();}
 @PostMapping("/{id}/voids") @PreAuthorize("hasAuthority('SCOPE_payments/void')")
 public void cancel(@PathVariable UUID id,@RequestHeader(value="X-Merchant-Id",required=false) String tenant) {owned(id,tenant);throw new UnsupportedOperationException();}
 private void owned(UUID id,String tenant) {store.find(tenants.resolve(tenant),id).orElseThrow(ApiErrors.NoSuchResource::new);}
}
