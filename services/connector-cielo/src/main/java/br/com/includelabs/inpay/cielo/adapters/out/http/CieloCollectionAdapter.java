package br.com.includelabs.inpay.cielo.adapters.out.http;

import br.com.includelabs.inpay.cielo.domain.*;
import br.com.includelabs.inpay.connector.JsonFields;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Traduz todos os endpoints distintos da coleção 7313fe78130211f5f009. */
@Component
public class CieloCollectionAdapter implements CieloCollectionPort {
    /** Preserva campos opcionais nativos e valida estrutura, IDs e escala monetária. */
    @Override
    public CieloRequestPlan prepare(
            CieloOperation operation, String resourceId, Long amount, Object payload) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation required");
        }
        if (amount != null
                && (amount <= 0
                        || (operation != CieloOperation.CAPTURE_PAYMENT
                                && operation != CieloOperation.VOID_PAYMENT
                                && operation != CieloOperation.VOID_ORDER))) {
            throw new IllegalArgumentException(
                    "Amount only accepted for capture/void, in positive cents");
        }
        String id = identifier(operation, resourceId);
        String suffix = amount == null ? "" : "?amount=" + amount;
        return switch (operation) {
            case CREATE_SALE -> {
                Map<String, Object> body = object(payload);
                JsonFields.require(body, "MerchantOrderId", "Payment.Type", "Payment.Amount");
                positive(JsonFields.get(body, "Payment.Amount"), Integer.MAX_VALUE);
                yield new CieloRequestPlan("POST", "/1/sales", false, body);
            }
            case GET_PAYMENT -> new CieloRequestPlan("GET", "/1/sales/" + id, true, null);
            case CAPTURE_PAYMENT -> empty(payload, "/1/sales/" + id + "/capture" + suffix);
            case VOID_PAYMENT -> empty(payload, "/1/sales/" + id + "/void" + suffix);
            case VOID_ORDER -> empty(payload, "/1/sales/orderId/" + id + "/void" + suffix);
            case GET_RECURRENT ->
                    new CieloRequestPlan("GET", "/1/RecurrentPayment/" + id, true, null);
            case UPDATE_RECURRENT_CUSTOMER -> update(id, "Customer", object(payload));
            case UPDATE_RECURRENT_PAYMENT -> update(id, "Payment", object(payload));
            case UPDATE_RECURRENT_INTERVAL -> update(id, "Interval", positive(payload, 6));
            case UPDATE_RECURRENT_INSTALLMENTS -> update(id, "Installments", positive(payload, 99));
            case UPDATE_RECURRENT_DAY -> update(id, "RecurrencyDay", positive(payload, 31));
            case UPDATE_RECURRENT_AMOUNT ->
                    update(id, "Amount", positive(payload, Integer.MAX_VALUE));
            case UPDATE_RECURRENT_END_DATE -> update(id, "EndDate", date(payload));
            case UPDATE_RECURRENT_NEXT_DATE -> update(id, "NextPaymentDate", date(payload));
            case REACTIVATE_RECURRENT ->
                    empty(payload, "/1/RecurrentPayment/" + id + "/Reactivate");
            case DEACTIVATE_RECURRENT ->
                    empty(payload, "/1/RecurrentPayment/" + id + "/Deactivate");
            case CREATE_CARD -> {
                Map<String, Object> body = object(payload);
                JsonFields.require(
                        body, "CustomerName", "CardNumber", "Holder", "ExpirationDate", "Brand");
                yield new CieloRequestPlan("POST", "/1/card", false, body);
            }
            case GET_CARD -> new CieloRequestPlan("GET", "/1/card/" + id, true, null);
            case ZERO_AUTH -> {
                Map<String, Object> body = object(payload);
                if (JsonFields.text(body, "CardToken").isBlank()) {
                    JsonFields.require(body, "CardNumber", "ExpirationDate", "Brand");
                }
                yield new CieloRequestPlan("POST", "/1/zeroauth", false, body);
            }
            case QUERY_BIN -> new CieloRequestPlan("GET", "/1/cardBin/" + id, true, null);
        };
    }

    /** Impede path injection e aceita somente IDs compatíveis com o recurso solicitado. */
    private String identifier(CieloOperation operation, String value) {
        if (operation == CieloOperation.CREATE_SALE
                || operation == CieloOperation.CREATE_CARD
                || operation == CieloOperation.ZERO_AUTH) {
            if (value != null && !value.isBlank()) {
                throw new IllegalArgumentException("Unexpected resourceId");
            }
            return "";
        }
        if (value == null) {
            throw new IllegalArgumentException("resourceId required");
        }
        String pattern =
                operation == CieloOperation.QUERY_BIN
                        ? "[0-9]{6}([0-9]{2})?"
                        : operation == CieloOperation.VOID_ORDER
                                ? "[a-zA-Z0-9_-]{1,50}"
                                : "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException("Invalid resourceId");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Exige objeto JSON nas operações cujo contrato não aceita valores escalares. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new IllegalArgumentException("Object payload required");
        }
        return JsonFields.copy((Map<String, Object>) map);
    }

    /** Exige inteiro positivo sem conversão silenciosa de decimais. */
    private long positive(Object value, long maximum) {
        if (!(value instanceof Number) || !value.toString().matches("[0-9]+")) {
            throw new IllegalArgumentException("Integer required");
        }
        long number = Long.parseLong(value.toString());
        if (number < 1 || number > maximum) {
            throw new IllegalArgumentException("Value outside valid range");
        }
        return number;
    }

    /** Conserva o formato ISO de data exigido nas alterações de recorrência. */
    private String date(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("ISO date required");
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("ISO date required");
        }
    }

    /** Monta alteração de recorrência com corpo escalar ou objeto conforme o contrato. */
    private CieloRequestPlan update(String id, String field, Object payload) {
        return new CieloRequestPlan(
                "PUT", "/1/RecurrentPayment/" + id + "/" + field, false, payload);
    }

    /** Impede enviar corpo em comandos documentados sem payload. */
    private CieloRequestPlan empty(Object payload, String path) {
        if (payload != null) {
            throw new IllegalArgumentException("This operation has no payload");
        }
        return new CieloRequestPlan("PUT", path, false, null);
    }
}
