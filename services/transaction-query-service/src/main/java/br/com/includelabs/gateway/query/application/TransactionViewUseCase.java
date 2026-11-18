package br.com.includelabs.gateway.query.application;

import java.util.UUID;
import br.com.includelabs.gateway.query.domain.TransactionView;
import br.com.includelabs.gateway.query.domain.port.TransactionViewPort;
public final class TransactionViewUseCase {
private final TransactionViewPort port;public TransactionViewUseCase(TransactionViewPort port){this.port=port;}
public TransactionView execute(UUID paymentId){return port.find(paymentId);}
}
