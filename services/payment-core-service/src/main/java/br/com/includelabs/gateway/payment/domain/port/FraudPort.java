package br.com.includelabs.gateway.payment.domain.port;

import java.util.UUID;
public interface FraudPort {
 enum Decision { ACCEPT, REVIEW, REJECT, UNAVAILABLE }
 Decision assess(UUID merchantId,UUID paymentId,long amount,String instrumentToken);
}
