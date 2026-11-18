package br.com.includelabs.gateway.notification.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.notification.domain.Delivery;
public interface DeliveryPort { Delivery deliver(UUID deliveryId); }
