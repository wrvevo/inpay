package br.com.includelabs.inpay.globalpayments.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional GlobalPayments. Referência oficial:
 * https://developer.globalpayments.com/api/transactions
 */
@Component
public class GlobalPaymentsApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body,
                    "account_name",
                    "channel",
                    "country",
                    "amount",
                    "currency",
                    "reference",
                    "capture_mode",
                    "payment_method");
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall(
                            "POST",
                            "/transactions",
                            false,
                            body,
                            Map.of("X-GP-Idempotency", operationId.toString()));
            case CAPTURE ->
                    new ProviderCall(
                            "POST",
                            "/transactions/" + id + "/capture",
                            false,
                            body,
                            Map.of("X-GP-Idempotency", operationId.toString()));
            case CANCEL ->
                    new ProviderCall(
                            "POST",
                            "/transactions/" + id + "/reversal",
                            false,
                            body,
                            Map.of("X-GP-Idempotency", operationId.toString()));
            case REFUND ->
                    new ProviderCall(
                            "POST",
                            "/transactions/" + id + "/refund",
                            false,
                            body,
                            Map.of("X-GP-Idempotency", operationId.toString()));
            case QUERY -> new ProviderCall("GET", "/transactions/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "id");
        String status = JsonFields.text(response, "status");
        String code = JsonFields.text(response, "payment_method.result");
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        return new ProviderResult(providerId, status, code, httpStatus, false);
    }
}
