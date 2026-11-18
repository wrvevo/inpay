package br.com.includelabs.gateway.notification.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.notification.domain.Delivery;
import br.com.includelabs.gateway.notification.domain.port.DeliveryPort;
@Component public class PendingAdapter implements DeliveryPort {
public Delivery deliver(UUID deliveryId){throw new UnsupportedOperationException("Implement DeliveryPort before enabling this flow");}
}
