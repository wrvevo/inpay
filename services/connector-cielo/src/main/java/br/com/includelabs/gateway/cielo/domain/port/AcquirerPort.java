package br.com.includelabs.gateway.cielo.domain.port;

import br.com.includelabs.gateway.cielo.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
