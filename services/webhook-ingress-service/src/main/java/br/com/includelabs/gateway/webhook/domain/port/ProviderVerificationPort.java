package br.com.includelabs.gateway.webhook.domain.port;

import java.util.UUID;
import br.com.includelabs.gateway.webhook.domain.ProviderObservation;
public interface ProviderVerificationPort { ProviderObservation verify(UUID observationId); }
