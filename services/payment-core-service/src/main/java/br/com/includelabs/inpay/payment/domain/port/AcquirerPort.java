package br.com.includelabs.inpay.payment.domain.port;

import java.util.UUID;

/**
 * Porta do domínio para a capacidade de AcquirerPort. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface AcquirerPort {

    /**
     * Porta do domínio para a capacidade de Outcome. Adaptadores de infraestrutura implementam este
     * contrato.
     */
    enum Outcome {
        AUTHORIZED,
        DECLINED,
        UNKNOWN,
        UNSUPPORTED
    }

    /**
     * Porta do domínio para a capacidade de Command. Adaptadores de infraestrutura implementam este
     * contrato.
     */
    record Command(
            UUID operationId,
            UUID merchantId,
            UUID paymentId,
            long amount,
            String instrumentToken,
            String affiliationId) {}

    /**
     * Porta do domínio para a capacidade de Result. Adaptadores de infraestrutura implementam este
     * contrato.
     */
    record Result(Outcome outcome, String providerReference) {}

    /**
     * Encaminha a autorização ao adaptador selecionado; o perfil local é simulado e o adaptador
     * real permanece desabilitado.
     *
     * @param command dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Result authorize(Command command);

    /**
     * Define a consulta ao resultado da operação externa sem iniciar uma nova cobrança.
     *
     * @param operationId identificador estável usado para deduplicação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Result query(UUID operationId);
}
