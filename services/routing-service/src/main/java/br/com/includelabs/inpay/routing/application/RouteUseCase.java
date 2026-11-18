package br.com.includelabs.inpay.routing.application;

import br.com.includelabs.inpay.routing.domain.Route;
import br.com.includelabs.inpay.routing.domain.port.RoutingPort;
import java.util.UUID;

/**
 * Caso de uso responsável por coordenar a regra de negócio e a porta de saída, sem depender dos
 * frameworks de infraestrutura.
 */
public final class RouteUseCase {

    private final RoutingPort port;

    /**
     * Inicializa RouteUseCase com suas dependências explícitas.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     */
    public RouteUseCase(RoutingPort port) {
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
    public Route execute(UUID merchantId) {
        return port.select(merchantId);
    }
}
