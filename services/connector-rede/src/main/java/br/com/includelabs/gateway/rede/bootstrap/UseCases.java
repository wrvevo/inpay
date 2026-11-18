package br.com.includelabs.gateway.rede.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.rede.application.AuthorizePayment;
import br.com.includelabs.gateway.rede.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
