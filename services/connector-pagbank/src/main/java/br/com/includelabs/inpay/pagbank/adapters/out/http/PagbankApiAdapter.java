package br.com.includelabs.inpay.pagbank.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Pagbank. Referência oficial:
 * https://developer.pagbank.com.br/reference/criar-pagar-pedido-com-token-pagbank
 */
@Component
public class PagbankApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body,
                    "reference_id",
                    "customer",
                    "items",
                    "charges.0.amount.value",
                    "charges.0.payment_method.capture");
        }
        if (operation == ProviderOperation.AUTHORIZE
                && (!(body.get("charges") instanceof java.util.List<?> charges)
                        || charges.size() != 1)) {
            throw new IllegalArgumentException("Use one charge per connector operation");
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall(
                            "POST",
                            "/orders",
                            false,
                            body,
                            Map.of("x-idempotency-key", operationId.toString()));
            case CAPTURE ->
                    new ProviderCall(
                            "POST",
                            "/charges/" + id + "/capture",
                            false,
                            body,
                            Map.of("x-idempotency-key", operationId.toString()));
            case CANCEL ->
                    new ProviderCall(
                            "POST",
                            "/charges/" + id + "/cancel",
                            false,
                            body,
                            Map.of("x-idempotency-key", operationId.toString()));
            case REFUND ->
                    new ProviderCall(
                            "POST",
                            "/charges/" + id + "/cancel",
                            false,
                            body,
                            Map.of("x-idempotency-key", operationId.toString()));
            case QUERY -> new ProviderCall("GET", "/charges/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "charges.0.id");
        String status = JsonFields.text(response, "charges.0.status");
        String code = JsonFields.text(response, "charges.0.payment_response.code");
        if (operation != ProviderOperation.AUTHORIZE) {
            providerId = JsonFields.text(response, "id");
            status = JsonFields.text(response, "status");
            code = JsonFields.text(response, "payment_response.code");
        }
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        return new ProviderResult(providerId, status, code, httpStatus, false);
    }
}
