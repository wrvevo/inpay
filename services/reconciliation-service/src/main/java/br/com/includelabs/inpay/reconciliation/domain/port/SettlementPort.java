package br.com.includelabs.inpay.reconciliation.domain.port;

import br.com.includelabs.inpay.reconciliation.domain.ReconciliationCase;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de SettlementPort. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface SettlementPort {

    /**
     * Define o contrato de reconcile no limite de SettlementPort. A implementação é fornecida pelo
     * adaptador do módulo.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    ReconciliationCase reconcile(UUID merchantId);
}
