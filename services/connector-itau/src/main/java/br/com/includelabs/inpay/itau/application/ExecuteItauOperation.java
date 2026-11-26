package br.com.includelabs.inpay.itau.application;

import br.com.includelabs.inpay.itau.domain.ItauOperation;
import br.com.includelabs.inpay.itau.domain.ItauProduct;
import br.com.includelabs.inpay.itau.domain.ItauReply;
import br.com.includelabs.inpay.itau.domain.ItauRequestPlan;
import br.com.includelabs.inpay.itau.domain.port.ItauBankPort;
import br.com.includelabs.inpay.contracts.MockIntentClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Caso de uso: mock ou banco e idempotência. Não registra segredo nem payload. */
public final class ExecuteItauOperation {
    private static final Logger LOG = LogManager.getLogger(ExecuteItauOperation.class);

    private final ItauBankPort bank;
    private final boolean liveEnabled;
    private final boolean confirmUnverified;
    private final ConcurrentHashMap<String, ItauReply> journal = new ConcurrentHashMap<>();

    public ExecuteItauOperation(ItauBankPort bank, boolean liveEnabled, boolean confirmUnverified) {
        this.bank = bank;
        this.liveEnabled = liveEnabled;
        this.confirmUnverified = confirmUnverified;
    }

    public List<Map<String, String>> catalog() {
        LOG.info("Itau catalog requested");
        List<Map<String, String>> rows = new ArrayList<>();
        for (ItauOperation operation : ItauOperation.values()) {
            rows.add(
                    Map.of(
                            "product", operation.product().name(),
                            "operation", operation.name(),
                            "method", operation.method(),
                            "path", operation.path(),
                            "documented", Boolean.toString(operation.documented())));
        }
        return rows;
    }

    public ItauReply execute(
            String operationName,
            String operationId,
            String mockHeader,
            Map<String, Object> payload) {
        ItauOperation operation = ItauOperation.fromName(operationName);
        boolean mock =
                mockHeader != null
                        && !mockHeader.isBlank()
                        && Boolean.parseBoolean(mockHeader.trim());
        if (mockHeader != null
                && !mockHeader.isBlank()
                && !"true".equalsIgnoreCase(mockHeader.trim())
                && !"false".equalsIgnoreCase(mockHeader.trim())) {
            throw new IllegalArgumentException("X-Mock must be true or false");
        }
        LOG.info(
                "Itau operation started operation={} product={} mock={}",
                operation.name(),
                operation.product(),
                mock);
        try {
            if (operation.hasBody() && operation != ItauOperation.AUTH_TOKEN) {
                ItauReply replay = replay(operation, operationId, mock);
                if (replay != null) {
                    LOG.info("Itau operation replayed operation={}", operation.name());
                    return replay;
                }
            }
            ItauReply reply = mock ? mock(operation, payload) : live(operation, payload);
            if (operation.hasBody()
                    && operation != ItauOperation.AUTH_TOKEN
                    && operationId != null) {
                journal.put(operation.name() + "|" + mock + "|" + operationId, reply);
            }
            LOG.info(
                    "Itau operation finished operation={} status={}",
                    operation.name(),
                    reply.status());
            return reply;
        } catch (RuntimeException exception) {
            LOG.error("Itau operation failed operation={}", operation.name(), exception);
            throw exception;
        }
    }

    private ItauReply live(ItauOperation operation, Map<String, Object> payload) {
        if (!liveEnabled) {
            throw new IllegalStateException("Itau connector is disabled; send X-Mock: true");
        }
        if (!operation.documented() && !confirmUnverified) {
            throw new IllegalArgumentException(
                    "Operation waits for the contracted Itau OpenAPI path");
        }
        ItauRequestPlan.Planned planned = ItauRequestPlan.plan(operation, payload);
        return bank.send(operation, planned.path(), planned.body(), planned.form());
    }

    private ItauReply mock(ItauOperation operation, Map<String, Object> payload) {
        if (MockIntentClient.configured()) {
            return wireMock(operation);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mocked", true);
        body.put("bank", "itau");
        body.put("product", operation.product().name());
        body.put("operation", operation.name());
        body.put(
                "status",
                operation.product() == ItauProduct.PIX_RECEBIMENTOS ? "ATIVA" : "CREATED");
        if (operation.product() == ItauProduct.PIX_RECEBIMENTOS) {
            body.put("pixCopiaECola", "000201mocked-itau");
        }
        if (operation.product() == ItauProduct.BOLETO) {
            body.put("id_boleto", "itau-boleto-mock");
        }
        if (payload != null && payload.get("txid") != null) {
            body.put("txid", payload.get("txid"));
        }
        return new ItauReply(operation.name(), 200, false, true, body);
    }

    @SuppressWarnings("unchecked")
    private ItauReply wireMock(ItauOperation operation) {
        try {
            var response = MockIntentClient.execute("itau", operation.name());
            if (response.status() != 200) {
                return new ItauReply(
                        operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
            }
            Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
            if (!Boolean.TRUE.equals(body.get("mocked"))
                    || !operation.name().equals(body.get("operation"))) {
                throw new IllegalStateException("Invalid mock contract");
            }
            return new ItauReply(operation.name(), 200, false, true, body);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            LOG.warn("Mock intent failed operation={}", operation.name());
        }
        return new ItauReply(
                operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
    }

    private ItauReply replay(ItauOperation operation, String operationId, boolean mock) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("operationId is required");
        }
        return journal.get(operation.name() + "|" + mock + "|" + operationId);
    }
}
