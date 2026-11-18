package br.com.includelabs.gateway.payment.domain.port;

import java.util.*;
import br.com.includelabs.gateway.payment.domain.Payment;
public interface PaymentStore {
 Payment register(Payment payment,String idempotencyKey,String fingerprint);
 Optional<Payment> find(UUID merchantId,UUID paymentId);
}
