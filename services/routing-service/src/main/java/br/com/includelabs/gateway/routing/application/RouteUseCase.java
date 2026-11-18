package br.com.includelabs.gateway.routing.application;

import java.util.UUID;
import br.com.includelabs.gateway.routing.domain.Route;
import br.com.includelabs.gateway.routing.domain.port.RoutingPort;
public final class RouteUseCase {
private final RoutingPort port;public RouteUseCase(RoutingPort port){this.port=port;}
public Route execute(UUID merchantId){return port.select(merchantId);}
}
