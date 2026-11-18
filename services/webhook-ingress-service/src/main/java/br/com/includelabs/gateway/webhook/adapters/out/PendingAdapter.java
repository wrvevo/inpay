package br.com.includelabs.gateway.webhook.adapters.out;

import java.util.UUID;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.webhook.domain.ProviderObservation;
import br.com.includelabs.gateway.webhook.domain.port.ProviderVerificationPort;
@Component public class PendingAdapter implements ProviderVerificationPort {
public ProviderObservation verify(UUID observationId){throw new UnsupportedOperationException("Implement ProviderVerificationPort before enabling this flow");}
}
