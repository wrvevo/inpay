package br.com.includelabs.inpay.routing.domain.port;

import br.com.includelabs.inpay.routing.domain.Route;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de RoutingPort. Adaptadores de infraestrutura implementam este
 * contrato.
 */
public interface RoutingPort {

    /**
     * Define o contrato de select no limite de RoutingPort. A implementação é fornecida pelo
     * adaptador do módulo.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Route select(UUID merchantId);
}
