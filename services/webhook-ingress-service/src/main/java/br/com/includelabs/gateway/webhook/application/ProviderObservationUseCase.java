package br.com.includelabs.gateway.webhook.application;

import java.util.UUID;
import br.com.includelabs.gateway.webhook.domain.ProviderObservation;
import br.com.includelabs.gateway.webhook.domain.port.ProviderVerificationPort;
public final class ProviderObservationUseCase {
private final ProviderVerificationPort port;public ProviderObservationUseCase(ProviderVerificationPort port){this.port=port;}
public ProviderObservation execute(UUID observationId){return port.verify(observationId);}
}
