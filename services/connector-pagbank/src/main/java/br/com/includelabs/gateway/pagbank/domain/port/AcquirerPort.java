package br.com.includelabs.gateway.pagbank.domain.port;

import br.com.includelabs.gateway.pagbank.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
