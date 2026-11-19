package br.com.includelabs.inpay.connector;

import br.com.includelabs.inpay.connector.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

/** Coordena isolamento de estabelecimento, autenticação externa, journal e chamada do adaptador. */
public class ProviderService {

    private final ProviderSettings settings;

    private final ProviderPort port;

    private final ProviderTransport transport;

    private final ProviderAuthentication authentication;

    private final OperationJournal journal;

    /** Injeta portas testáveis; nenhum segredo pertence ao domínio de pagamentos. */
    public ProviderService(
            ProviderSettings settings,
            ProviderPort port,
            ProviderTransport transport,
            ProviderAuthentication authentication,
            OperationJournal journal) {
        this.settings = settings;
        this.port = port;
        this.transport = transport;
        this.authentication = authentication;
        this.journal = journal;
    }

    /** Executa uma operação externa sem retry financeiro, preservando resultados ambíguos. */
    public ProviderResult execute(
            UUID merchant,
            UUID operationId,
            ProviderOperation operation,
            String id,
            Map<String, Object> payload) {
        settings.validate();
        if (!merchant.equals(settings.tenantId())) {
            throw new AccessDeniedException("Provider credentials belong to another merchant");
        }
        if (operationId == null || operation == null || payload == null) {
            throw new IllegalArgumentException("operationId, operation and payload are required");
        }
        if (operation != ProviderOperation.AUTHORIZE
                && (id == null || !id.matches("[a-zA-Z0-9_-]{1,128}"))) {
            throw new IllegalArgumentException("Invalid provider identifier");
        }
        JsonFields.validatePayload(payload, settings.allowSensitiveCardData());
        ProviderCall call = port.prepare(operation, id, operationId, payload);
        Map<String, String> headers = new HashMap<>(authentication.headers());
        headers.putAll(call.headers());
        String base =
                call.queryHost() && settings.queryUrl() != null && !settings.queryUrl().isBlank()
                        ? settings.queryUrl()
                        : settings.baseUrl();
        String url = base.replaceAll("/+$", "") + call.path();
        if (operation != ProviderOperation.QUERY) {
            ProviderResult previous =
                    journal.reserve(merchant, operationId, fingerprint(operation, id, payload));
            if (previous != null) {
                return previous;
            }
        }
        var reply = transport.send(call.method(), url, headers, call.body(), false);
        if (reply.status() == 401) {
            authentication.invalidate();
        }
        ProviderResult result;
        if (reply.uncertain()
                || reply.status() >= 500
                || reply.status() == 408
                || reply.status() == 429
                || reply.status() == 0) {
            result = ProviderResult.unknown(id, reply.status());
        } else if (reply.status() < 200 || reply.status() >= 300) {
            result =
                    new ProviderResult(
                            id, "HTTP_REJECTED", "HTTP_" + reply.status(), reply.status(), false);
        } else {
            result = port.interpret(operation, id, reply.status(), reply.body());
        }
        if (operation != ProviderOperation.QUERY) {
            journal.complete(merchant, operationId, result);
        }
        return result;
    }

    /**
     * Canonicaliza JSON sem CVV/PAN/criptogramas; dados brutos nunca são armazenados no journal.
     */
    private static String fingerprint(
            ProviderOperation operation, String id, Map<String, Object> body) {
        try {
            String value =
                    operation
                            + "|"
                            + Objects.toString(id, "")
                            + "|"
                            + new ObjectMapper()
                                    .writeValueAsString(JsonFields.fingerprintData(body));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot canonicalize request");
        }
    }
}
