package br.com.includelabs.gateway.bin.domain.port;

import br.com.includelabs.gateway.bin.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
