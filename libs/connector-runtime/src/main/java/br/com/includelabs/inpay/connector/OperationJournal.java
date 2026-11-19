package br.com.includelabs.inpay.connector;

import br.com.includelabs.inpay.connector.domain.ProviderResult;
import java.util.UUID;

/** Porta de idempotência durável. Uma reserva deve ser confirmada antes do envio externo. */
public interface OperationJournal {
    /** Reserva a chave ou devolve o resultado anterior; null identifica uma reserva nova. */
    ProviderResult reserve(UUID merchant, UUID operation, String fingerprint);

    /** Armazena apenas o resultado mínimo da operação que já possui reserva. */
    void complete(UUID merchant, UUID operation, ProviderResult result);
}
