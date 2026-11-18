package br.com.includelabs.gateway.cielo.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.cielo.application.AuthorizePayment;
import br.com.includelabs.gateway.cielo.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
