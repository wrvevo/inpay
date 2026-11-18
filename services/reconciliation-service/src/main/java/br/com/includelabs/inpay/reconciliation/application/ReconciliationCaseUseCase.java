package br.com.includelabs.inpay.reconciliation.application;

import br.com.includelabs.inpay.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.inpay.reconciliation.domain.port.SettlementPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class ReconciliationCaseUseCase {

    private final SettlementPort port;

    /**
     * Inicializa ReconciliationCaseUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public ReconciliationCaseUseCase(SettlementPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public ReconciliationCase execute(UUID merchantId) {
        return port.reconcile(merchantId);
    }
}
