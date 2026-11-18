package br.com.includelabs.gateway.checkout.application;

import java.util.UUID;
import br.com.includelabs.gateway.checkout.domain.CheckoutSession;
import br.com.includelabs.gateway.checkout.domain.port.CheckoutPort;
public final class CheckoutSessionUseCase {
private final CheckoutPort port;public CheckoutSessionUseCase(CheckoutPort port){this.port=port;}
public CheckoutSession execute(UUID linkId){return port.open(linkId);}
}
