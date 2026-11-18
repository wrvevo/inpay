package br.com.includelabs.gateway.links.domain;

import java.time.Instant;
import java.util.UUID;
public record PaymentLink(UUID id,UUID merchantId,String publicCode,String orderId,long amount,Instant expiresAt,boolean revoked) {
 public PaymentLink {if(amount<=0||expiresAt==null||orderId==null||orderId.isBlank())throw new IllegalArgumentException("Invalid link");}
 public boolean available(Instant now){return !revoked && expiresAt.isAfter(now);}
}
