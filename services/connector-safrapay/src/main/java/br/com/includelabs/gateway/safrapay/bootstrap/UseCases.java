package br.com.includelabs.gateway.safrapay.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.safrapay.application.AuthorizePayment;
import br.com.includelabs.gateway.safrapay.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
