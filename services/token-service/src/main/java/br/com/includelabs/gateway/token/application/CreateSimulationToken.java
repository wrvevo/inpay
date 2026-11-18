package br.com.includelabs.gateway.token.application;

import java.security.SecureRandom;
import java.util.*;
import br.com.includelabs.gateway.token.domain.Instrument;
import br.com.includelabs.gateway.token.domain.port.InstrumentStore;
/** Only wired by the local profile. Never represents a real payment instrument. */
public final class CreateSimulationToken {
 private final InstrumentStore store;
 public CreateSimulationToken(InstrumentStore store){this.store=store;}
 public Instrument execute(UUID tenant,String provider,String affiliation){
  if(provider==null||affiliation==null||affiliation.isBlank())throw new IllegalArgumentException();
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
  var token=new Instrument("ptok_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),tenant,provider,affiliation,true);
  store.save(token);return token;
 }
}
