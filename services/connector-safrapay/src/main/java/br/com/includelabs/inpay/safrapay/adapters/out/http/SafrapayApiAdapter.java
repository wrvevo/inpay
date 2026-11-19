package br.com.includelabs.inpay.safrapay.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Safrapay. Referência oficial: https://developers.safrapay.com.br/gateway
 */
@Component
public class SafrapayApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body, "charge.merchantChargeId", "charge.transactions", "charge.source");
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall("POST", "/v2/charge/preauthorization", false, body, Map.of());
            case CAPTURE ->
                    new ProviderCall("PUT", "/v2/charge/capture/" + id, false, body, Map.of());
            case CANCEL ->
                    new ProviderCall("PUT", "/v2/charge/cancelation/" + id, false, body, Map.of());
            case REFUND ->
                    new ProviderCall("PUT", "/v2/charge/cancelation/" + id, false, body, Map.of());
            case QUERY -> new ProviderCall("GET", "/v2/charge/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "charge.id");
        String status = JsonFields.text(response, "charge.chargeStatus");
        String code = JsonFields.text(response, "charge.transactions.0.authorizationResponseCode");
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        return new ProviderResult(providerId, status, code, httpStatus, false);
    }
}
