package br.com.includelabs.inpay.notification.domain.port;

import br.com.includelabs.inpay.notification.domain.Delivery;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de DeliveryPort. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface DeliveryPort {

    /**
     * Define o contrato de deliver no limite de DeliveryPort. A implementação é fornecida pelo
     * adaptador do módulo.
     *
     * @param deliveryId identificador da entrega de notificação a ser processada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Delivery deliver(UUID deliveryId);
}
