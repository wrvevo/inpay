package br.com.includelabs.gateway.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import br.com.includelabs.gateway.payment.domain.Payment;
import br.com.includelabs.gateway.payment.domain.port.PaymentStore;
public final class SubmitPayment {
 private final PaymentStore store;
 public SubmitPayment(PaymentStore store) {this.store=store;}
 public Payment execute(UUID tenant,String key,String order,long amount,String currency,String token) {
  if(key==null || key.isBlank() || key.length()>128) throw new IllegalArgumentException("Invalid idempotency key");
  var payment=new Payment(UUID.randomUUID(),tenant,order,amount,currency,token,Payment.Status.CREATED,0,0,false,0);
  // Length-prefixed strings prevent ambiguous concatenations.
  String canonical=part(order)+amount+":"+part(currency)+part(token);
  try {
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
   return store.register(payment,key,hash);
  } catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
 private static String part(String value) {return value.length()+":"+value;}
}
