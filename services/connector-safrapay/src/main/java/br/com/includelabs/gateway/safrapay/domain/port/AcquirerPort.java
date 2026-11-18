package br.com.includelabs.gateway.safrapay.domain.port;

import br.com.includelabs.gateway.safrapay.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
