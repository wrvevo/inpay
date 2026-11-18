package br.com.includelabs.gateway.getnet.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.getnet.application.AuthorizePayment;
import br.com.includelabs.gateway.getnet.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
