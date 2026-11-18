package br.com.includelabs.gateway.checkout.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.checkout.domain.CheckoutSession;
public interface CheckoutPort { CheckoutSession open(UUID linkId); }
