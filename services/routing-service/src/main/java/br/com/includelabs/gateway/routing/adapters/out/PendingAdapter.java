package br.com.includelabs.gateway.routing.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.routing.domain.Route;
import br.com.includelabs.gateway.routing.domain.port.RoutingPort;
@Component public class PendingAdapter implements RoutingPort {
public Route select(UUID merchantId){throw new UnsupportedOperationException("Implement RoutingPort before enabling this flow");}
}
