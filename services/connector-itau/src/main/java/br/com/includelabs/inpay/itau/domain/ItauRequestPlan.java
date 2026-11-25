package br.com.includelabs.inpay.itau.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Monta path e corpo sem depender de framework. */
public final class ItauRequestPlan {
    private ItauRequestPlan() {}

    public record Planned(String path, Map<String, Object> body, boolean form) {}

    public static Planned plan(ItauOperation operation, Map<String, Object> payload) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        String path = operation.path();
        Map<String, Object> body = new LinkedHashMap<>();
        StringBuilder query = new StringBuilder();
        boolean form = operation == ItauOperation.AUTH_TOKEN;
        for (var entry : source.entrySet()) {
            if ("operationId".equals(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            String token = "{" + entry.getKey() + "}";
            String value = entry.getValue().toString();
            if (path.contains(token)) {
                path = path.replace(token, encodePath(value));
            } else if (form || !operation.hasBody()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(encodeForm(entry.getKey())).append('=').append(encodeForm(value));
            } else {
                body.put(entry.getKey(), entry.getValue());
            }
        }
        if (path.contains("{")) {
            throw new IllegalArgumentException("Missing path parameter");
        }
        if (!form && query.length() > 0) {
            path = path + "?" + query;
        }
        if (form) {
            return new Planned(path, Map.of("form", query.toString()), true);
        }
        return new Planned(path, body, false);
    }

    private static String encodePath(String value) {
        if (value.isBlank()
                || value.length() > 120
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.contains("..")) {
            throw new IllegalArgumentException("Invalid path parameter");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodeForm(String value) {
        if (value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("Invalid query parameter");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
