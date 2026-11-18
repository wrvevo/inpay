package br.com.includelabs.inpay.webhook.domain.port;

import br.com.includelabs.inpay.webhook.domain.ProviderObservation;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de ProviderVerificationPort. Adaptadores de infraestrutura
 * implementam este contrato.
 */
public interface ProviderVerificationPort {

    /**
     * Define o contrato de verify no limite de ProviderVerificationPort. A implementação é
     * fornecida pelo adaptador do módulo.
     *
     * @param observationId identificador da notificação externa que precisa ser verificada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    ProviderObservation verify(UUID observationId);
}
