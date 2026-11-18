package br.com.includelabs.gateway.payment.bootstrap;

import org.springframework.context.annotation.*;
import br.com.includelabs.gateway.payment.application.SubmitPayment;
import br.com.includelabs.gateway.payment.domain.port.PaymentStore;
@Configuration
public class UseCases { @Bean SubmitPayment submitPayment(PaymentStore store) {return new SubmitPayment(store);} }
