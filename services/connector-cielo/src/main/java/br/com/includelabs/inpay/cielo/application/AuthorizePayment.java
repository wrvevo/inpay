package br.com.includelabs.inpay.cielo.application;

import br.com.includelabs.inpay.cielo.domain.Authorization;
import br.com.includelabs.inpay.cielo.domain.port.AcquirerPort;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class AuthorizePayment {

    private final AcquirerPort port;

    /**
     * Inicializa AuthorizePayment com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public AuthorizePayment(AcquirerPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Authorization execute(Authorization request) {
        if (request.amount() <= 0
                || request.operationId() == null
                || request.instrumentToken() == null) {
            throw new IllegalArgumentException();
        }
        return port.authorize(request);
    }
}
