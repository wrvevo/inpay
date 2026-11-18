package br.com.includelabs.gateway.checkout.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.checkout.domain.CheckoutSession;
import br.com.includelabs.gateway.checkout.domain.port.CheckoutPort;
@Component public class PendingAdapter implements CheckoutPort {
public CheckoutSession open(UUID linkId){throw new UnsupportedOperationException("Implement CheckoutPort before enabling this flow");}
}
