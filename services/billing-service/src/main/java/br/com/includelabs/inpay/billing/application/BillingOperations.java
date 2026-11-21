package br.com.includelabs.inpay.billing.application;

import br.com.includelabs.inpay.billing.application.BillingCommands.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Porta de comandos operacionais. Adaptadores REST e SOAP compartilham idempotência e autorização.
 */
public interface BillingOperations {
    Map<String, Object> register(String actor, Merchant command);

    Map<String, Object> approve(String actor, UUID id, Command command);

    Map<String, Object> createInvoice(String actor, Invoice command);

    Map<String, Object> subscribe(String actor, Subscription command);

    Map<String, Object> cancelSubscription(String actor, UUID id, Command command);

    int generateCycles(LocalDate date, int batch);

    Map<String, Object> settle(String actor, Settlement command);

    Map<String, Object> payable(String actor, Payable command);

    Map<String, Object> reminder(String actor, Reminder command);
}
