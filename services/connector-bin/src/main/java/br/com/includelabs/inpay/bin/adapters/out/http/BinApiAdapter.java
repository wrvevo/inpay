package br.com.includelabs.inpay.bin.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Bin. Referência oficial:
 * https://dev.ecomm-bin.fiserv.com.br/docs/e-sitef/pre-autorizacao/
 */
@Component
public class BinApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body,
                    "order_id",
                    "merchant_usn",
                    "amount",
                    "authorizer_id",
                    "installments",
                    "installment_type",
                    "card");
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall("POST", "/v2/preauthorizations/", false, body, Map.of());
            case CAPTURE ->
                    new ProviderCall(
                            "POST", "/v1/preauthorizations/capture/" + id, false, body, Map.of());
            case CANCEL ->
                    new ProviderCall("POST", "/v2/cancellations/" + id, false, body, Map.of());
            case REFUND ->
                    new ProviderCall("POST", "/v2/cancellations/" + id, false, body, Map.of());
            case QUERY -> new ProviderCall("GET", "/v2/transactions/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "pre_authorization.nit");
        String status = JsonFields.text(response, "pre_authorization.status");
        String code = JsonFields.text(response, "code");
        String section =
                switch (operation) {
                    case AUTHORIZE -> "pre_authorization";
                    case CAPTURE -> "capture";
                    case CANCEL, REFUND -> "cancellation";
                    case QUERY -> "transaction";
                };
        providerId = JsonFields.text(response, section + ".nit");
        status = JsonFields.text(response, section + ".status");
        if (operation == ProviderOperation.QUERY && status.isBlank()) {
            for (String wrapper :
                    List.of("payment", "pre_authorization", "capture", "cancellation")) {
                if (!JsonFields.text(response, wrapper + ".status").isBlank()) {
                    status = JsonFields.text(response, wrapper + ".status");
                    providerId = JsonFields.text(response, wrapper + ".nit");
                    break;
                }
            }
        }
        if (!"0".equals(code)) {
            status = "PROVIDER_REJECTED";
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
