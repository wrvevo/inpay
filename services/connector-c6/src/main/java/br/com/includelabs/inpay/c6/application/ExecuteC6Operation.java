package br.com.includelabs.inpay.c6.application;

import br.com.includelabs.inpay.c6.domain.C6Operation;
import br.com.includelabs.inpay.c6.domain.C6Product;
import br.com.includelabs.inpay.c6.domain.C6Reply;
import br.com.includelabs.inpay.c6.domain.C6RequestPlan;
import br.com.includelabs.inpay.c6.domain.port.C6BankPort;
import br.com.includelabs.inpay.contracts.MockIntentClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Caso de uso: mock ou banco, idempotência e publicação do resultado. */
public final class ExecuteC6Operation {
    private static final Logger LOG = LogManager.getLogger(ExecuteC6Operation.class);

    private final C6BankPort bank;
    private final C6ResultPublisher results;
    private final boolean liveEnabled;
    private final boolean confirmUnverified;
    private final ConcurrentHashMap<String, C6Reply> journal = new ConcurrentHashMap<>();

    public ExecuteC6Operation(
            C6BankPort bank, C6ResultPublisher results, boolean liveEnabled, boolean confirmUnverified) {
        this.bank = bank;
        this.results = results;
        this.liveEnabled = liveEnabled;
        this.confirmUnverified = confirmUnverified;
    }

    public List<Map<String, String>> catalog() {
        LOG.info("C6 catalog requested");
        List<Map<String, String>> rows = new ArrayList<>();
        for (C6Operation operation : C6Operation.values()) {
            rows.add(
                    Map.of(
                            "product",
                            operation.product().name(),
                            "operation",
                            operation.name(),
                            "method",
                            operation.method(),
                            "path",
                            operation.path(),
                            "documented",
                            Boolean.toString(operation.documented())));
        }
        return rows;
    }

    public C6Reply execute(String operationName, String operationId, String mockHeader, Map<String, Object> payload) {
        C6Operation operation = C6Operation.fromName(operationName);
        boolean mock = mockHeader == null || mockHeader.isBlank() ? false : Boolean.parseBoolean(mockHeader.trim());
        if (mockHeader != null
                && !mockHeader.isBlank()
                && !"true".equalsIgnoreCase(mockHeader.trim())
                && !"false".equalsIgnoreCase(mockHeader.trim())) {
            throw new IllegalArgumentException("X-Mock must be true or false");
        }
        LOG.info("C6 operation started operation={} product={} mock={}", operation.name(), operation.product(), mock);
        try {
            if (operation.hasBody() && operation != C6Operation.AUTH_TOKEN) {
                C6Reply replay = replay(operation, operationId, mock, payload);
                if (replay != null) {
                    LOG.info("C6 operation replayed operation={}", operation.name());
                    return replay;
                }
            }
            C6Reply reply = mock ? mock(operation, payload) : live(operation, payload);
            if (operation.hasBody() && operation != C6Operation.AUTH_TOKEN && operationId != null) {
                journal.put(operation.name() + "|" + mock + "|" + operationId, reply);
            }
            results.publish(operation, operationId, reply);
            LOG.info(
                    "C6 operation finished operation={} status={} uncertain={}",
                    operation.name(),
                    reply.status(),
                    reply.uncertain());
            return reply;
        } catch (RuntimeException exception) {
            LOG.error("C6 operation failed operation={}", operation.name(), exception);
            throw exception;
        }
    }

    private C6Reply live(C6Operation operation, Map<String, Object> payload) {
        if (!liveEnabled) {
            throw new IllegalStateException("C6 connector is disabled; send X-Mock: true");
        }
        if (!operation.documented() && !confirmUnverified) {
            throw new IllegalArgumentException("Operation waits for the contracted C6 OpenAPI path");
        }
        C6RequestPlan.Planned planned = C6RequestPlan.plan(operation, payload);
        return bank.send(operation, planned.path(), planned.body(), planned.form());
    }

    private C6Reply mock(C6Operation operation, Map<String, Object> payload) {
        if (MockIntentClient.configured()) {
            return wireMock(operation);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mocked", true);
        body.put("bank", "c6");
        body.put("product", operation.product().name());
        body.put("operation", operation.name());
        body.put("status", operation.product() == C6Product.PIX ? "ATIVA" : "CREATED");
        if (operation.product() == C6Product.PIX || operation.product() == C6Product.PIX_AUTOMATIC) {
            body.put("pixCopiaECola", "000201mocked-c6");
        }
        if (operation.product() == C6Product.BANK_SLIP || operation.product() == C6Product.BOLEPIX) {
            body.put("id", "c6-slip-mock");
            body.put("digitable_line", "00000.00000 00000.000000 00000.000000 0 00000000000000");
        }
        if (operation.product() == C6Product.CHECKOUT) {
            body.put("id", "c6-checkout-mock");
            body.put("url", "https://sandbox.c6bank.info/checkout/mock");
        }
        if (payload != null && payload.get("txid") != null) {
            body.put("txid", payload.get("txid"));
        }
        return new C6Reply(operation.name(), 200, false, true, body);
    }

    @SuppressWarnings("unchecked")
    private C6Reply wireMock(C6Operation operation) {
        try {
            var response = MockIntentClient.execute("c6", operation.name());
            if (response.status() != 200) {
                return new C6Reply(
                        operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
            }
            Map<String, Object> body = new ObjectMapper().readValue(response.body(), Map.class);
            if (!Boolean.TRUE.equals(body.get("mocked"))
                    || !operation.name().equals(body.get("operation"))) {
                throw new IllegalStateException("Invalid mock contract");
            }
            return new C6Reply(operation.name(), 200, false, true, body);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            LOG.warn("Mock intent failed operation={}", operation.name());
        }
        return new C6Reply(
                operation.name(), 503, true, true, Map.of("error", "MOCK_UNAVAILABLE"));
    }

    private C6Reply replay(C6Operation operation, String operationId, boolean mock, Map<String, Object> payload) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("operationId is required");
        }
        return journal.get(operation.name() + "|" + mock + "|" + operationId);
    }
}
