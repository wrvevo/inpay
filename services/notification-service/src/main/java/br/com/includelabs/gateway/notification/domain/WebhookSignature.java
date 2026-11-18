package br.com.includelabs.gateway.notification.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
public final class WebhookSignature {
 public String sign(byte[] secret,long timestamp,byte[] body){
  if(secret.length<32)throw new IllegalArgumentException("Use at least 256-bit secret");
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
   mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(mac.doFinal(body));
  }catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
 }
}
