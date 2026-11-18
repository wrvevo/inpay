package br.com.includelabs.gateway.pagbank.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.pagbank.application.AuthorizePayment;
import br.com.includelabs.gateway.pagbank.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
