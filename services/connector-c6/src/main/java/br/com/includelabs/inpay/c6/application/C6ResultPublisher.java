package br.com.includelabs.inpay.c6.application;

import br.com.includelabs.inpay.c6.domain.C6Operation;
import br.com.includelabs.inpay.c6.domain.C6Reply;

/** Publica o resultado para o gateway. Implementação Kafka fica no adaptador. */
public interface C6ResultPublisher {
    void publish(C6Operation operation, String operationId, C6Reply reply);

    C6ResultPublisher NONE = (operation, operationId, reply) -> {};
}
