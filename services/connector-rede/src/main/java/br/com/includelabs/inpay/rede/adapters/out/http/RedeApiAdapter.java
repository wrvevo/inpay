package br.com.includelabs.inpay.rede.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Rede. Referência oficial:
 * https://developer.userede.com.br/files/traducoes/erede/e-rede-24032026.pdf
 */
@Component
public class RedeApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(body, "reference", "amount", "kind", "capture", "installments");
        }
        return switch (operation) {
            case AUTHORIZE -> new ProviderCall("POST", "/v2/transactions", false, body, Map.of());
            case CAPTURE ->
                    new ProviderCall("PUT", "/v2/transactions/" + id, false, body, Map.of());
            case CANCEL ->
                    new ProviderCall(
                            "POST", "/v2/transactions/" + id + "/refunds", false, body, Map.of());
            case REFUND ->
                    new ProviderCall(
                            "POST", "/v2/transactions/" + id + "/refunds", false, body, Map.of());
            case QUERY -> new ProviderCall("GET", "/v2/transactions/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "tid");
        String status = JsonFields.text(response, "authorization.status");
        String code = JsonFields.text(response, "returnCode");
        if (operation != ProviderOperation.QUERY) {
            status =
                    "00".equals(code)
                            ? (operation == ProviderOperation.AUTHORIZE ? "AUTHORIZED" : "CAPTURED")
                            : "359".equals(code)
                                    ? "REFUNDED"
                                    : "360".equals(code) ? "REFUND_PENDING" : "PROVIDER_REJECTED";
        } else {
            String captureStatus = JsonFields.text(response, "capture.status");
            if (!captureStatus.isBlank()) {
                status = "capture:" + captureStatus;
            }
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
