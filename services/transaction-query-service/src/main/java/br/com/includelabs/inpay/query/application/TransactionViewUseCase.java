package br.com.includelabs.inpay.query.application;

import br.com.includelabs.inpay.query.domain.TransactionView;
import br.com.includelabs.inpay.query.domain.port.TransactionViewPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class TransactionViewUseCase {

    private final TransactionViewPort port;

    /**
     * Inicializa TransactionViewUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public TransactionViewUseCase(TransactionViewPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public TransactionView execute(UUID paymentId) {
        return port.find(paymentId);
    }
}
