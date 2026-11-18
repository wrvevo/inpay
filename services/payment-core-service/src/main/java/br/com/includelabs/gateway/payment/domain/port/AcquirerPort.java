package br.com.includelabs.gateway.payment.domain.port;

import java.util.UUID;
public interface AcquirerPort {
 enum Outcome { AUTHORIZED, DECLINED, UNKNOWN, UNSUPPORTED }
 record Command(UUID operationId,UUID merchantId,UUID paymentId,long amount,String instrumentToken,String affiliationId){}
 record Result(Outcome outcome,String providerReference){}
 Result authorize(Command command);
 Result query(UUID operationId);
}
