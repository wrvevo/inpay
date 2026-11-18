package br.com.includelabs.gateway.payment.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaymentTest {
 Payment created() {return new Payment(UUID.randomUUID(),UUID.randomUUID(),"order",1000,"BRL","ptok_test",Payment.Status.CREATED,0,0,false,0);}
 @Test void cannotCaptureWithoutRisk() {assertThrows(IllegalStateException.class,()->created().authorize().capture(1000,false));}
 @Test void cannotRefundBeyondCaptured() {assertThrows(IllegalStateException.class,()->created().authorize().capture(1000,true).refund(1001));}
 @Test void unknownBlocksCapture() {assertThrows(IllegalStateException.class,()->created().authorize().markUnknown().capture(1000,true));}
 @Test void capturedCannotBeVoided() {assertThrows(IllegalStateException.class,()->created().authorize().capture(1000,true).voidAuthorization());}
 @Test void partialThenFullRefund() {var p=created().authorize().capture(1000,true).refund(400).refund(600);assertEquals(Payment.Status.REFUNDED,p.status());}
 @Test void secondCaptureIsBlocked() {assertThrows(IllegalStateException.class,()->created().authorize().capture(1000,true).capture(1000,true));}
}
