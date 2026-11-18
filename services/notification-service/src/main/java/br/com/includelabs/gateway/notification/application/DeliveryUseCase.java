package br.com.includelabs.gateway.notification.application;

import java.util.UUID;
import br.com.includelabs.gateway.notification.domain.Delivery;
import br.com.includelabs.gateway.notification.domain.port.DeliveryPort;
public final class DeliveryUseCase {
private final DeliveryPort port;public DeliveryUseCase(DeliveryPort port){this.port=port;}
public Delivery execute(UUID deliveryId){return port.deliver(deliveryId);}
}
