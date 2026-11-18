package br.com.includelabs.gateway.stone.domain.port;

import br.com.includelabs.gateway.stone.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
