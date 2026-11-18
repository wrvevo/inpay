package br.com.includelabs.gateway.stone.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.stone.application.AuthorizePayment;
import br.com.includelabs.gateway.stone.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
