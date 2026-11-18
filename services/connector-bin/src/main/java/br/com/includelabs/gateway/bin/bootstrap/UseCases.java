package br.com.includelabs.gateway.bin.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.bin.application.AuthorizePayment;
import br.com.includelabs.gateway.bin.domain.port.AcquirerPort;
@Configuration public class UseCases {@Bean AuthorizePayment authorizePayment(AcquirerPort port){return new AuthorizePayment(port);}}
