package br.com.includelabs.inpay.fraud.domain.port;

import br.com.includelabs.inpay.fraud.domain.RiskPolicy.Assessment;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de AssessmentStore. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface AssessmentStore {

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @param result decisão de risco, score e motivos que devem ser persistidos
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    UUID save(UUID merchantId, UUID paymentId, Assessment result);
}
