package br.com.includelabs.inpay.cielo.application;

import br.com.includelabs.inpay.cielo.domain.*;
import br.com.includelabs.inpay.connector.*;
import br.com.includelabs.inpay.connector.domain.ProviderResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Executa contratos Cielo preservando tenant, idempotência e resultados indeterminados. */
@Service
public class CieloCollectionService {
    private final CieloCollectionPort port;
    private final ProviderSettings settings;
    private final ProviderAuthentication authentication;
    private final ProviderTransport transport;
    private final OperationJournal journal;

    /** Injeta as portas e os recursos compartilhados com o endpoint transacional existente. */
    public CieloCollectionService(
            CieloCollectionPort port,
            ProviderSettings settings,
            ProviderAuthentication authentication,
            ProviderTransport transport,
            OperationJournal journal) {
        this.port = port;
        this.settings = settings;
        this.authentication = authentication;
        this.transport = transport;
        this.journal = journal;
    }

    /** Faz uma única chamada externa; nenhuma resposta ambígua provoca retry automático. */
    public CieloResponse execute(
            UUID tenant,
            UUID operationId,
            CieloOperation operation,
            String resourceId,
            Long amount,
            Object payload) {
        settings.validate();
        if (!tenant.equals(settings.tenantId())) {
            throw new AccessDeniedException("Wrong provider tenant");
        }
        if (operation == null || (!operation.readOnly() && operationId == null)) {
            throw new IllegalArgumentException("Operation and idempotency key required");
        }
        JsonFields.validatePayload(payload, settings.allowSensitiveCardData());
        CieloRequestPlan plan = port.prepare(operation, resourceId, amount, payload);
        Map<String, String> headers = new HashMap<>(authentication.headers());
        if (operationId != null) {
            headers.put("RequestId", operationId.toString());
        }
        if (!operation.readOnly()) {
            ProviderResult previous =
                    journal.reserve(
                            tenant,
                            operationId,
                            fingerprint(operation, resourceId, amount, payload));
            if (previous != null) {
                Map<String, Object> data = new LinkedHashMap<>();
                if (previous.providerId() != null) {
                    data.put("ProviderId", previous.providerId());
                }
                data.put("Status", Objects.toString(previous.status(), "UNKNOWN"));
                data.put("ReturnCode", Objects.toString(previous.code(), ""));
                return new CieloResponse(previous.httpStatus(), previous.uncertain(), true, data);
            }
        }
        String base = plan.queryHost() ? settings.queryUrl() : settings.baseUrl();
        var reply =
                transport.send(
                        plan.method(),
                        base.replaceAll("/+$", "") + plan.path(),
                        headers,
                        plan.body(),
                        false);
        boolean uncertain =
                reply.uncertain()
                        || reply.status() == 0
                        || reply.status() == 408
                        || reply.status() == 429
                        || reply.status() >= 500;
        if (reply.status() == 401) {
            authentication.invalidate();
        }
        if (reply.status() >= 200
                && reply.status() < 300
                && (operation == CieloOperation.CREATE_SALE
                        || operation == CieloOperation.CREATE_CARD)
                && (operation == CieloOperation.CREATE_SALE
                        ? JsonFields.text(reply.body(), "Payment.PaymentId").isBlank()
                        : JsonFields.text(reply.body(), "CardToken").isBlank())) {
            uncertain = true;
        }
        Map<String, Object> result = sanitize(reply.body());
        if (!operation.readOnly()) {
            String providerId = JsonFields.text(result, "Payment.PaymentId");
            if (providerId.isBlank()) {
                providerId = JsonFields.text(result, "Payment.RecurrentPayment.RecurrentPaymentId");
            }
            if (providerId.isBlank()
                    && operation != CieloOperation.CREATE_CARD
                    && operation != CieloOperation.ZERO_AUTH) {
                providerId = resourceId;
            }
            String status = JsonFields.text(result, "Payment.Status");
            if (status.isBlank()) {
                status = JsonFields.text(result, "Status");
            }
            if (status.isBlank()) {
                status = uncertain ? "UNKNOWN" : "HTTP_" + reply.status();
            }
            String code = JsonFields.text(result, "Payment.ReturnCode");
            if (code.isBlank()) {
                code = JsonFields.text(result, "ReturnCode");
            }
            // Never store native bodies, customer information, CardToken or card details.
            journal.complete(
                    tenant,
                    operationId,
                    new ProviderResult(providerId, status, code, reply.status(), uncertain));
        }
        return new CieloResponse(reply.status(), uncertain, false, result);
    }

    /** Canonicaliza comando sem dados efêmeros de cartão, incluindo operação e parâmetros. */
    private String fingerprint(CieloOperation operation, String id, Long amount, Object payload) {
        try {
            String canonical =
                    "CIELO_COLLECTION|"
                            + operation
                            + "|"
                            + Objects.toString(id, "")
                            + "|"
                            + amount
                            + "|"
                            + new ObjectMapper()
                                    .writeValueAsString(JsonFields.fingerprintData(payload));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid command");
        }
    }

    /** Remove dados sensíveis e textos livres que possam refletir a entrada de cartão. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> sanitize(Map<String, Object> body) {
        return (Map<String, Object>) clean(body, "");
    }

    /** Mantém códigos, status, tokens e URLs funcionais em memória; não os registra em log. */
    private static Object clean(Object value, String parent) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach(
                    (key, item) -> {
                        String field = key.toString();
                        if (!JsonFields.sensitive(field, parent)
                                && !Set.of(
                                                "customer",
                                                "customername",
                                                "holder",
                                                "identity",
                                                "email",
                                                "address",
                                                "avs",
                                                "message",
                                                "returnmessage",
                                                "error",
                                                "description",
                                                "walletkey",
                                                "cavv",
                                                "ucaf",
                                                "paymentdata")
                                        .contains(field.toLowerCase(Locale.ROOT))) {
                            safe.put(field, clean(item, field));
                        }
                    });
            return safe;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> clean(item, parent)).toList();
        }
        return value;
    }
}
