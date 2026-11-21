package br.com.includelabs.inpay.billing.application;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gera faturas em lotes limitados; não efetua débito nem reenvia comandos financeiros. */
@Component
public class BillingScheduler {
    private final BillingOperations operations;
    private final boolean enabled;
    private final int batch;

    public BillingScheduler(
            BillingOperations operations,
            @Value("${inpay.billing.scheduler-enabled:false}") boolean enabled,
            @Value("${inpay.billing.cycle-batch:100}") int batch) {
        this.operations = operations;
        this.enabled = enabled;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "${inpay.billing.scheduler-delay:60000}")
    public void run() {
        if (enabled)
            operations.generateCycles(LocalDate.now(ZoneId.of("America/Sao_Paulo")), batch);
    }
}
