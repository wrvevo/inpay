package br.com.includelabs.inpay.notification.adapters.out;

import br.com.includelabs.inpay.notification.domain.Delivery;
import br.com.includelabs.inpay.notification.domain.port.DeliveryPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements DeliveryPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param deliveryId identificador da entrega de notificação a ser processada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Delivery deliver(UUID deliveryId) {
        throw new UnsupportedOperationException("Implement DeliveryPort before enabling this flow");
    }
}
