package br.com.includelabs.inpay.checkout.application;

import br.com.includelabs.inpay.checkout.domain.CheckoutSession;
import br.com.includelabs.inpay.checkout.domain.port.CheckoutPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class CheckoutSessionUseCase {

    private final CheckoutPort port;

    /**
     * Inicializa CheckoutSessionUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public CheckoutSessionUseCase(CheckoutPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param linkId identificador do link que deve originar a sessão de checkout
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public CheckoutSession execute(UUID linkId) {
        return port.open(linkId);
    }
}
