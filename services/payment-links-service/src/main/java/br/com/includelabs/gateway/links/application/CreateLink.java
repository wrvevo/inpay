package br.com.includelabs.gateway.links.application;

import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import br.com.includelabs.gateway.links.domain.PaymentLink;
import br.com.includelabs.gateway.links.domain.port.LinkStore;
public final class CreateLink {
 private final LinkStore store;private final SecureRandom random=new SecureRandom();
 public CreateLink(LinkStore store){this.store=store;}
 public PaymentLink execute(UUID tenant,String order,long amount,Instant expiresAt){
  if(expiresAt==null||!expiresAt.isAfter(Instant.now()))throw new IllegalArgumentException("Future expiry required");
  byte[] bytes=new byte[24];random.nextBytes(bytes);
  var link=new PaymentLink(UUID.randomUUID(),tenant,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),order,amount,expiresAt,false);
  store.save(link);return link;
 }
}
