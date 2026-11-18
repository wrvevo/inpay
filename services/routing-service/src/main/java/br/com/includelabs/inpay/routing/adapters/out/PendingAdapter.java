package br.com.includelabs.inpay.routing.adapters.out;

import br.com.includelabs.inpay.routing.domain.Route;
import br.com.includelabs.inpay.routing.domain.port.RoutingPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements RoutingPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Route select(UUID merchantId) {
        throw new UnsupportedOperationException("Implement RoutingPort before enabling this flow");
    }
}
