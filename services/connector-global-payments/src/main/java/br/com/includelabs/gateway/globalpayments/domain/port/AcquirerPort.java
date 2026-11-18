package br.com.includelabs.gateway.globalpayments.domain.port;

import br.com.includelabs.gateway.globalpayments.domain.Authorization;
public interface AcquirerPort { Authorization authorize(Authorization request); }
