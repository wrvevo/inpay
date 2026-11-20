package br.com.includelabs.inpay.marketplace.bootstrap;

import br.com.includelabs.inpay.security.TenantContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Reutiliza resolução de tenant sem importar os escopos do gateway no marketplace. */
@Configuration
@Import(TenantContext.class)
public class IdentityConfiguration {}
