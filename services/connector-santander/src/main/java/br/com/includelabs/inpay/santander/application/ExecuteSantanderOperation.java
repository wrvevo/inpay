package br.com.includelabs.inpay.santander.application;

import br.com.includelabs.inpay.contracts.MockIntentClient;
import br.com.includelabs.inpay.santander.domain.SantanderOperation;
import br.com.includelabs.inpay.santander.domain.SantanderProduct;
import br.com.includelabs.inpay.santander.domain.SantanderReply;
import br.com.includelabs.inpay.santander.domain.SantanderRequestPlan;
import br.com.includelabs.inpay.santander.domain.port.SantanderBankPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Caso de uso: mock ou banco e idempotência. Não registra segredo nem payload. */
public final class ExecuteSantanderOperation {
    private static final Logger LOG = LogManager.getLogger(ExecuteSantanderOperation.class);

    private final SantanderBankPort bank;
    private final boolean liveEnabled;
    private final boolean confirmUnverified;
    private final ConcurrentHashMap<String, SantanderReply> journal = new ConcurrentHashMap<>();

    public ExecuteSantanderOperation(
            SantanderBankPort bank, boolean liveEnabled, boolean confirmUnverified) {
        this.bank = bank;
        this.liveEnabled = liveEnabled;
        this.confirmUnverified = confirmUnverified;
    }

    public List<Map<String, String>> catalog() {
        LOG.info("Santander catalog requested");
        List<Map<String, String>> rows = new ArrayList<>();
        for (SantanderOperation operation : SantanderOperation.values()) {
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

    public SantanderReply execute(
            String operationName,
            String operationId,
            String mockHeader,
            Map<String, Object> payload) {
        SantanderOperation operation = SantanderOperation.fromName(operationName);
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
                "Santander operation started operation={} product={} mock={}",
                operation.name(),
                operation.product(),
                mock);
        try {
            if (operation.hasBody() && operation != SantanderOperation.AUTH_TOKEN) {
                SantanderReply replay = replay(operation, operationId, mock);
                if (replay != null) {
                    LOG.info("Santander operation replayed operation={}", operation.name());
                    return replay;
                }
            }
            SantanderReply reply = mock ? mock(operation, payload) : live(operation, payload);
            if (operation.hasBody()
                    && operation != SantanderOperation.AUTH_TOKEN
                    && operationId != null) {
                journal.put(operation.name() + "|" + mock + "|" + operationId, reply);
            }
            LOG.info(
                    "Santander operation finished operation={} status={}",
                    operation.name(),
                    reply.status());
            return reply;
        } catch (RuntimeException exception) {
            LOG.error("Santander operation failed operation={}", operation.name(), exception);
            throw exception;
        }
    }

    private SantanderReply live(SantanderOperation operation, Map<String, Object> payload) {
        if (!liveEnabled) {
            throw new IllegalStateException("Santander connector is disabled; send X-Mock: true");
        }
        if (!operation.documented() && !confirmUnverified) {
            throw new IllegalArgumentException(
                    "Operation waits for the contracted Santander OpenAPI path");
        }
        SantanderRequestPlan.Planned planned = SantanderRequestPlan.plan(operation, payload);
        return bank.send(operation, planned.path(), planned.body(), planned.form());
    }

    private SantanderReply mock(SantanderOperation operation, Map<String, Object> payload) {
        if (MockIntentClient.configured()) {
            return wireMock(operation);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mocked", true);
        body.put("bank", "santander");
        body.put("product", operation.product().name());
        body.put("operation", operation.name());
        body.put("status", operation.product() == SantanderProduct.PIX ? "ATIVA" : "CREATED");
        if (operation.product() == SantanderProduct.PIX) {
            body.put("pixCopiaECola", "000201mocked-santander");
        }
        if (operation.product() == SantanderProduct.BILLING) {
            body.put("id", "santander-billing-mock");
        }
        if (payload != null && payload.get("txid") != null) {
            body.put("txid", payload.get("txid"));
        }
        return new SantanderReply(operation.name(), 200, false, true, body);
    }

    @SuppressWarnings("unchecked")
    private SantanderReply wireMock(SantanderOperation operation) {
        try {
            var response = MockIntentClient.execute("santander", operation.name());
            if (response.status() != 200) {
                return new SantanderReply(
                        operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
            }
            Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
            if (!Boolean.TRUE.equals(body.get("mocked"))
                    || !operation.name().equals(body.get("operation"))) {
                throw new IllegalStateException("Invalid mock contract");
            }
            return new SantanderReply(operation.name(), 200, false, true, body);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            LOG.warn("Mock intent failed operation={}", operation.name());
        }
        return new SantanderReply(
                operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
    }

    private SantanderReply replay(SantanderOperation operation, String operationId, boolean mock) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("operationId is required");
        }
        return journal.get(operation.name() + "|" + mock + "|" + operationId);
    }
}
