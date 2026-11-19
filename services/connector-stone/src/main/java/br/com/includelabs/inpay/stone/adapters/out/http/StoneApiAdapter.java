package br.com.includelabs.inpay.stone.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Stone. Referência oficial:
 * https://online.stone.com.br/reference/post_charges
 */
@Component
public class StoneApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body,
                    "payment_method",
                    "amount",
                    "initiator_id",
                    "local_datetime",
                    "card_transaction");
        }
        if (operation == ProviderOperation.CANCEL || operation == ProviderOperation.REFUND) {
            body.put("id", id);
        }
        if (operation != ProviderOperation.AUTHORIZE && operation != ProviderOperation.QUERY) {
            JsonFields.require(body, "amount");
        }
        return switch (operation) {
            case AUTHORIZE -> new ProviderCall("POST", "/v1/charges", false, body, Map.of());
            case CAPTURE ->
                    new ProviderCall(
                            "POST", "/v1/charges/" + id + "/capture", false, body, Map.of());
            case CANCEL -> new ProviderCall("POST", "/v1/charges/cancel", false, body, Map.of());
            case REFUND -> new ProviderCall("POST", "/v1/charges/cancel", false, body, Map.of());
            case QUERY -> new ProviderCall("GET", "/v1/charges?ids=" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        if (operation == ProviderOperation.QUERY) {
            for (String collection : List.of("items", "charges", "data")) {
                Object entries = response.get(collection);
                if (entries instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry instanceof Map<?, ?> map
                                && id.equals(JsonFields.text(map, "id"))) {
                            return new ProviderResult(
                                    id,
                                    JsonFields.text(map, "status"),
                                    JsonFields.text(map, "card_transaction.acquirer_return_code"),
                                    httpStatus,
                                    JsonFields.text(map, "status").isBlank());
                        }
                    }
                }
            }
        }
        String providerId = JsonFields.text(response, "id");
        String status = JsonFields.text(response, "status");
        String code = JsonFields.text(response, "card_transaction.acquirer_return_code");
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        return new ProviderResult(providerId, status, code, httpStatus, false);
    }
}
