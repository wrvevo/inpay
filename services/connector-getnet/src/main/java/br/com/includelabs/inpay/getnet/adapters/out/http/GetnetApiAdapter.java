package br.com.includelabs.inpay.getnet.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Getnet. Referência oficial:
 * https://docs.globalgetnet.com/en/products/online-payments/regional-api?doc=quickstart-guide
 */
@Component
public class GetnetApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(
                    body,
                    "order_id",
                    "data.amount",
                    "data.currency",
                    "data.payment.payment_method",
                    "data.payment.transaction_type");
        }
        body.put("idempotency_key", operationId.toString());
        if (operation != ProviderOperation.AUTHORIZE && operation != ProviderOperation.QUERY) {
            body.put("payment_id", id);
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall(
                            "POST", "/dpm/payments-gwproxy/v2/payments", false, body, Map.of());
            case CAPTURE ->
                    new ProviderCall(
                            "POST",
                            "/dpm/payments-gwproxy/v2/payments/capture",
                            false,
                            body,
                            Map.of());
            case CANCEL ->
                    new ProviderCall(
                            "POST",
                            "/dpm/payments-gwproxy/v2/payments/cancel",
                            false,
                            body,
                            Map.of());
            case REFUND ->
                    new ProviderCall(
                            "POST",
                            "/dpm/payments-gwproxy/v2/payments/cancel",
                            false,
                            body,
                            Map.of());
            case QUERY ->
                    new ProviderCall(
                            "GET",
                            "/dpm/hub-payment-info/v1/payments/info/" + id,
                            true,
                            null,
                            Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "payment_id");
        String status = JsonFields.text(response, "status");
        String code = JsonFields.text(response, "reason_code");
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        return new ProviderResult(providerId, status, code, httpStatus, false);
    }
}
