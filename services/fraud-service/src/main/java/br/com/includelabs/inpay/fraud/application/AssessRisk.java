package br.com.includelabs.inpay.fraud.application;

import br.com.includelabs.inpay.fraud.domain.RiskPolicy;
import br.com.includelabs.inpay.fraud.domain.port.AssessmentStore;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class AssessRisk {

    private final RiskPolicy policy;

    private final AssessmentStore assessmentStore;

    /**
     * Inicializa AssessRisk com suas dependências explícitas.
     *
     * @param policy política de risco usada para produzir a decisão explicável
     * @param assessmentStore porta de persistência pertencente ao módulo
     */
    public AssessRisk(RiskPolicy policy, AssessmentStore assessmentStore) {
        this.policy = policy;
        this.assessmentStore = assessmentStore;
    }

    /**
     * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
     * frameworks de infraestrutura.
     */
    public record Result(UUID assessmentId, RiskPolicy.Assessment assessment) {}

    /**
     * Executa a política nativa, persiste a avaliação e retorna o identificador junto da decisão.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @param signals sinais de risco fornecidos pelo chamador interno autorizado
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public Result execute(UUID merchantId, UUID paymentId, RiskPolicy.Signals signals) {
        var result = policy.evaluate(signals);
        return new Result(assessmentStore.save(merchantId, paymentId, result), result);
    }
}
