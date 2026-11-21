package br.com.includelabs.inpay.billing.bootstrap;

import br.com.includelabs.inpay.security.ApiErrors;
import org.springframework.context.annotation.*;

@Configuration
@Import({ApiErrors.class, br.com.includelabs.inpay.security.TenantContext.class})
public class BillingConfiguration {}
