package br.com.includelabs.gateway.globalpayments.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.globalpayments.application.AuthorizePayment;
import br.com.includelabs.gateway.globalpayments.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
