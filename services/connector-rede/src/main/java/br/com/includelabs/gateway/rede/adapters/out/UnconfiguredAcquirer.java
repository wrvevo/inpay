package br.com.includelabs.gateway.rede.adapters.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import br.com.includelabs.gateway.rede.domain.Authorization;
import br.com.includelabs.gateway.rede.domain.port.AcquirerPort;
@Component @Profile("!local") public class UnconfiguredAcquirer implements AcquirerPort {
public Authorization authorize(Authorization request){throw new UnsupportedOperationException("Provider adapter requires implementation and homologation");}
}
