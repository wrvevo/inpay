package br.com.includelabs.inpay.bank;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.ObjectMapper;

/** Monta a operação, escolhe mock ou adquirente e preserva idempotência em memória. */
public final class BankConnector {
    private static final Logger LOG = LogManager.getLogger(BankConnector.class);
    private static final int JOURNAL_LIMIT = 10_000;

    private final BankSettings settings;
    private final BankTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Journaled> journal = new ConcurrentHashMap<>();

    public BankConnector(BankSettings settings, BankTransport transport) {
        this.settings = settings;
        this.transport = transport;
    }

    public BankResult execute(
            String operationName,
            String operationId,
            String mockHeader,
            Map<String, Object> payload) {
        BankOperation operation = BankOperation.fromName(operationName);
        MockMode mode = MockMode.parse(mockHeader, settings.mockEnabled());
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        LOG.info(
                "Bank operation started bank={} operation={} mock={} operationIdPresent={}",
                settings.id(),
                operation.name(),
                mode == MockMode.MOCK,
                operationId != null && !operationId.isBlank());
        String journalKey = null;
        boolean dispatched = false;
        try {
            Prepared prepared = prepare(operation, safePayload);
            if (mode == MockMode.LIVE) {
                assertLiveAllowed(operation);
            }
            if (operation.hasBody()) {
                BankResult replayed = reserve(operation, operationId, safePayload, mode);
                if (replayed != null) {
                    LOG.info(
                            "Bank operation replayed bank={} operation={}",
                            settings.id(),
                            operation.name());
                    return replayed;
                }
                journalKey = key(operation, operationId, mode);
            }
            URI uri = target(mode, prepared.path());
            String body =
                    prepared.body().isEmpty() ? "" : mapper.writeValueAsString(prepared.body());
            dispatched = true;
            BankResult result =
                    transport.send(settings.bank(), operation, mode == MockMode.MOCK, uri, body);
            if (journalKey != null) {
                journal.put(journalKey, new Journaled(fingerprint(operation, safePayload), result));
            }
            LOG.info(
                    "Bank operation finished bank={} operation={} status={} uncertain={}",
                    settings.id(),
                    operation.name(),
                    result.status(),
                    result.uncertain());
            return result;
        } catch (RuntimeException exception) {
            if (!dispatched) {
                release(journalKey);
            }
            LOG.error(
                    "Bank operation failed bank={} operation={}",
                    settings.id(),
                    operation.name());
            throw exception;
        } catch (Exception exception) {
            if (!dispatched) {
                release(journalKey);
            }
            LOG.error(
                    "Bank operation failed bank={} operation={}",
                    settings.id(),
                    operation.name());
            throw new IllegalStateException("Cannot encode bank request");
        }
    }

    public List<Map<String, String>> catalog() {
        LOG.info("Bank catalog requested bank={}", settings.id());
        List<Map<String, String>> operations = new ArrayList<>();
        for (BankOperation operation : BankOperation.values()) {
            operations.add(
                    Map.of(
                            "operation",
                            operation.name(),
                            "method",
                            operation.method(),
                            "path",
                            operation.path(),
                            "pixStandard",
                            Boolean.toString(operation.pixStandard())));
        }
        return operations;
    }

    private BankResult reserve(
            BankOperation operation, String operationId, Map<String, Object> payload, MockMode mode) {
        if (operationId == null || !operationId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("operationId is required");
        }
        String finger = fingerprint(operation, payload);
        if (journal.size() >= JOURNAL_LIMIT && !journal.containsKey(key(operation, operationId, mode))) {
            throw new IllegalStateException("Bank journal is full");
        }
        Journaled previous =
                journal.putIfAbsent(key(operation, operationId, mode), new Journaled(finger, null));
        if (previous != null) {
            if (!previous.fingerprint().equals(finger) || previous.result() == null) {
                throw new IllegalStateException("operationId was reused with a different command");
            }
            return previous.result();
        }
        return null;
    }

    private void assertLiveAllowed(BankOperation operation) {
        if (!settings.enabled()) {
            throw new IllegalStateException("Bank connector is disabled; send X-Mock: true");
        }
        if (!operation.pixStandard() && !settings.confirmUnverifiedPaths()) {
            throw new IllegalArgumentException(
                    "Operation is mocked until the contracted bank path is confirmed");
        }
        URI live = URI.create(settings.liveBaseUrl());
        if (!"https".equals(live.getScheme())
                || live.getHost() == null
                || live.getUserInfo() != null) {
            throw new IllegalStateException("Live bank URL must be HTTPS");
        }
    }

    private URI target(MockMode mode, String path) {
        String base = mode == MockMode.MOCK ? settings.mockBaseUrl() : settings.liveBaseUrl();
        URI root = URI.create(base);
        if (mode == MockMode.MOCK
                && !"http".equals(root.getScheme())
                && !"https".equals(root.getScheme())) {
            throw new IllegalStateException("Mock base URL is invalid");
        }
        String prefix = mode == MockMode.MOCK ? "/" + settings.id() : "";
        String joined = root.toString().replaceAll("/+$", "") + prefix + path;
        return URI.create(joined);
    }

    private Prepared prepare(BankOperation operation, Map<String, Object> payload) {
        String path = operation.path();
        Map<String, Object> body = new LinkedHashMap<>();
        StringBuilder query = new StringBuilder();
        for (var entry : payload.entrySet()) {
            if ("operationId".equals(entry.getKey())) {
                continue;
            }
            String token = "{" + entry.getKey() + "}";
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            if (path.contains(token)) {
                path = path.replace(token, encodePath(value));
            } else if (!operation.hasBody()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(encodePath(entry.getKey())).append('=').append(encodePath(value));
            } else {
                body.put(entry.getKey(), entry.getValue());
            }
        }
        if (path.contains("{")) {
            throw new IllegalArgumentException("Missing path parameter");
        }
        if (query.length() > 0) {
            path = path + "?" + query;
        }
        return new Prepared(path, body);
    }

    private static String encodePath(String value) {
        if (value.isBlank()
                || value.length() > 77
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || value.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid path parameter");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Libera apenas a reserva desta tentativa quando ainda não houve despacho. */
    private void release(String journalKey) {
        if (journalKey != null) {
            journal.computeIfPresent(
                    journalKey, (key, current) -> current.result() == null ? null : current);
        }
    }

    /** Separa as chaves de laboratório das chamadas reais. */
    private String key(BankOperation operation, String operationId, MockMode mode) {
        return settings.id() + "|" + operation.name() + "|" + mode + "|" + operationId;
    }

    private String fingerprint(BankOperation operation, Map<String, Object> payload) {
        try {
            String canonical = operation.name() + "|" + mapper.writeValueAsString(payload);
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot fingerprint request");
        }
    }

    private record Prepared(String path, Map<String, Object> body) {}

    private record Journaled(String fingerprint, BankResult result) {}
}
