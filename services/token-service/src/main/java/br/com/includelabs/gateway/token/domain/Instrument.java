package br.com.includelabs.gateway.token.domain;

import java.util.UUID;
public record Instrument(String token,UUID merchantId,String provider,String affiliationId,boolean active) {
 public boolean usableBy(UUID tenant,String destination,String affiliation){return active&&merchantId.equals(tenant)&&provider.equals(destination)&&affiliationId.equals(affiliation);}
}
