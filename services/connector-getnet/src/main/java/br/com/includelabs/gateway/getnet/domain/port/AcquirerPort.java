package br.com.includelabs.gateway.getnet.domain.port;

import br.com.includelabs.gateway.getnet.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
