package br.com.includelabs.inpay.connector.domain;

import java.util.Map;
import java.util.UUID;

/** Porta de saída independente de Spring. O payload segue o contrato nativo do provedor. */
public interface ProviderPort {
    /** Valida os campos obrigatórios e produz o plano da chamada antes de reservar a operação. */
    ProviderCall prepare(
            ProviderOperation operation,
            String providerId,
            UUID operationId,
            Map<String, Object> payload);

    /** Interpreta apenas identificadores e status; nunca retorna o corpo sensível da adquirente. */
    ProviderResult interpret(
            ProviderOperation operation,
            String providerId,
            int status,
            Map<String, Object> response);
}
