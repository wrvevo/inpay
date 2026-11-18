package br.com.includelabs.gateway.rede.domain.port;

import br.com.includelabs.gateway.rede.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
