package br.com.includelabs.inpay.billing.application;

import br.com.includelabs.inpay.billing.domain.BillingPolicy.Conditions;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** Comandos públicos: nenhuma requisição aceita PAN, CVV ou um saldo financeiro arbitrário. */
public final class BillingCommands {
    private BillingCommands() {}

    public record Merchant(
            @NotNull UUID operationId,
            @NotNull UUID tenant,
            @NotBlank @Size(max = 200) String name,
            @NotBlank String plan,
            @NotBlank @Size(max = 500) String evidence,
            String fraudMode) {}

    public record Command(@NotNull UUID operationId) {}

    public record Invoice(
            @NotNull UUID operationId,
            @NotNull UUID merchantId,
            @NotBlank @Size(max = 128) String customerRef,
            @Positive long amount,
            @NotBlank String method,
            @NotNull LocalDate dueDate,
            @NotNull Conditions conditions,
            @NotBlank String contractReference) {}

    public record Subscription(
            @NotNull UUID operationId,
            @NotNull UUID merchantId,
            @NotBlank String customerRef,
            @Positive long amount,
            @NotBlank String method,
            @NotNull LocalDate firstDue,
            @Min(1) @Max(31) int anchorDay,
            @NotNull Conditions conditions,
            @NotBlank String mandateReference) {}

    public record Payable(
            @NotNull UUID operationId,
            @NotNull UUID merchantId,
            @NotBlank @Size(max = 200) String description,
            @Positive long amount,
            @NotNull LocalDate dueDate) {}

    public record Reminder(
            @NotNull UUID operationId,
            @NotNull UUID invoiceId,
            @NotBlank String channel,
            @NotBlank String recipientReference,
            @NotBlank String template,
            @NotBlank String consentReference,
            @NotNull LocalDate scheduledOn) {}

    public record Settlement(
            @NotBlank String provider,
            @NotBlank String eventId,
            @NotNull UUID invoiceId,
            @NotBlank String providerOperation,
            @Positive long amount,
            @PositiveOrZero long providerCost,
            @NotNull LocalDate paidOn,
            @NotNull Instant settledAt,
            @NotBlank String depositReference) {}
}
