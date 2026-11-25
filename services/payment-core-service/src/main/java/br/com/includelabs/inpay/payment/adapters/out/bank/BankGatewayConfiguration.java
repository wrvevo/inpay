package br.com.includelabs.inpay.payment.adapters.out.bank;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BankGatewaySettings.class)
public class BankGatewayConfiguration {}
