package br.com.includelabs.inpay.cielo.adapters.out.http;

import br.com.includelabs.inpay.connector.JsonFields;
import br.com.includelabs.inpay.connector.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Contrato transacional Cielo. Referência oficial:
 * https://docs.cielo.com.br/ecommerce-cielo/reference/cartao-tokenizado-api
 */
@Component
public class CieloApiAdapter implements ProviderPort {

    /** Preserva parâmetros opcionais nativos e usa somente os caminhos documentados do provedor. */
    @Override
    public ProviderCall prepare(
            ProviderOperation operation, String id, UUID operationId, Map<String, Object> payload) {
        Map<String, Object> body = JsonFields.copy(payload);
        if (operation == ProviderOperation.AUTHORIZE) {
            JsonFields.require(body, "MerchantOrderId", "Payment.Type", "Payment.Amount");
            if ("Pix".equals(JsonFields.text(body, "Payment.Type"))) {
                JsonFields.require(body, "Payment.Provider", "Payment.QrCode.Expiration");
                if (!"Cielo2".equals(JsonFields.text(body, "Payment.Provider")))
                    throw new IllegalArgumentException("Use contracted Cielo2 Pix integration");
                int expiration =
                        Integer.parseInt(JsonFields.text(body, "Payment.QrCode.Expiration"));
                if (expiration < 1 || expiration > 86400)
                    throw new IllegalArgumentException("Pix expiration exceeds 24h");
            } else {
                JsonFields.require(body, "Payment.Installments", "Payment.Capture");
            }
        }
        if (operation == ProviderOperation.CAPTURE
                || operation == ProviderOperation.CANCEL
                || operation == ProviderOperation.REFUND) {
            JsonFields.require(body, "Amount");
            long amount;
            try {
                amount = Long.parseLong(body.get("Amount").toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Amount must be integer cents");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            String suffix = operation == ProviderOperation.CAPTURE ? "/capture" : "/void";
            return new ProviderCall(
                    "PUT",
                    "/1/sales/" + id + suffix + "?amount=" + amount,
                    false,
                    null,
                    Map.of("RequestId", operationId.toString()));
        }
        return switch (operation) {
            case AUTHORIZE ->
                    new ProviderCall(
                            "POST",
                            "/1/sales/",
                            false,
                            body,
                            Map.of("RequestId", operationId.toString()));
            case CAPTURE ->
                    new ProviderCall(
                            "PUT",
                            "/1/sales/" + id + "/capture",
                            false,
                            body,
                            Map.of("RequestId", operationId.toString()));
            case CANCEL ->
                    new ProviderCall(
                            "PUT",
                            "/1/sales/" + id + "/void",
                            false,
                            body,
                            Map.of("RequestId", operationId.toString()));
            case REFUND ->
                    new ProviderCall(
                            "PUT",
                            "/1/sales/" + id + "/void",
                            false,
                            body,
                            Map.of("RequestId", operationId.toString()));
            case QUERY -> new ProviderCall("GET", "/1/sales/" + id, true, null, Map.of());
        };
    }

    /** Mantém o status nativo; aprovação HTTP isolada nunca se torna aprovação financeira. */
    @Override
    public ProviderResult interpret(
            ProviderOperation operation, String id, int httpStatus, Map<String, Object> response) {
        String providerId = JsonFields.text(response, "Payment.PaymentId");
        String status = JsonFields.text(response, "Payment.Status");
        String code = JsonFields.text(response, "Payment.ReturnCode");
        if (status.isBlank()) {
            status = JsonFields.text(response, "Status");
        }
        if (code.isBlank()) {
            code = JsonFields.text(response, "ReturnCode");
        }
        if (providerId.isBlank()) {
            providerId = id;
        }
        if (status.isBlank() || providerId == null || providerId.isBlank()) {
            return ProviderResult.unknown(providerId, httpStatus);
        }
        String qr = JsonFields.text(response, "Payment.QrCodeString");
        return new ProviderResult(
                providerId,
                status,
                code,
                httpStatus,
                false,
                qr.isBlank() ? Map.of() : Map.of("pix_copy_paste", qr));
    }
}
