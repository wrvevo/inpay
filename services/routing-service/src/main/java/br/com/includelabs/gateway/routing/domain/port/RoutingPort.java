package br.com.includelabs.gateway.routing.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.routing.domain.Route;
public interface RoutingPort { Route select(UUID merchantId); }
