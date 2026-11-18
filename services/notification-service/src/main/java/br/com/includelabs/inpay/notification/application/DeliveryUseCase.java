package br.com.includelabs.inpay.notification.application;

import br.com.includelabs.inpay.notification.domain.Delivery;
import br.com.includelabs.inpay.notification.domain.port.DeliveryPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class DeliveryUseCase {

    private final DeliveryPort port;

    /**
     * Inicializa DeliveryUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public DeliveryUseCase(DeliveryPort port) {
        this.port = port;
    }

    /**
     * Executa o caso de uso delegando à porta de saída injetada, sem acoplar o domínio à
     * infraestrutura.
     *
     * @param deliveryId identificador da entrega de notificação a ser processada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public Delivery execute(UUID deliveryId) {
        return port.deliver(deliveryId);
    }
}
