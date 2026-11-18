package br.com.includelabs.gateway.payment.domain;

import java.util.UUID;
public record Payment(UUID id,UUID merchantId,String merchantOrderId,long amount,String currency,
 String instrumentToken,Status status,long captured,long refunded,boolean uncertain,long version) {
 public enum Status { CREATED, AUTHORIZED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, VOIDED, DECLINED }
 public Payment {
  if(id==null || merchantId==null || merchantOrderId==null || merchantOrderId.isBlank() || amount<=0
    || !"BRL".equals(currency) || instrumentToken==null || !instrumentToken.startsWith("ptok_"))
    throw new IllegalArgumentException("Invalid payment");
  if(captured<0 || captured>amount || refunded<0 || refunded>captured) throw new IllegalArgumentException("Invalid balances");
 }
 public Payment authorize() {
  if(uncertain || status!=Status.CREATED) throw new IllegalStateException("Cannot authorize");
  return change(Status.AUTHORIZED,captured,refunded,false);
 }
 public Payment capture(long value,boolean riskAccepted) {
  if(uncertain || !riskAccepted || status!=Status.AUTHORIZED || value<=0 || value>amount-captured)
    throw new IllegalStateException("Capture blocked");
  return change(Status.CAPTURED,captured+value,refunded,false);
 }
 public Payment refund(long value) {
  if(uncertain || (status!=Status.CAPTURED && status!=Status.PARTIALLY_REFUNDED) || value<=0 || value>captured-refunded)
    throw new IllegalStateException("Refund blocked");
  return change(refunded+value==captured?Status.REFUNDED:Status.PARTIALLY_REFUNDED,captured,refunded+value,false);
 }
 public Payment voidAuthorization() {
  if(uncertain || status!=Status.AUTHORIZED || captured!=0) throw new IllegalStateException("Void blocked");
  return change(Status.VOIDED,captured,refunded,false);
 }
 public Payment markUnknown() { return change(status,captured,refunded,true); }
 private Payment change(Status s,long c,long r,boolean u) { return new Payment(id,merchantId,merchantOrderId,amount,currency,instrumentToken,s,c,r,u,version+1); }
}
