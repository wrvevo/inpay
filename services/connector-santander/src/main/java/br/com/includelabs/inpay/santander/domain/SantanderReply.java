package br.com.includelabs.inpay.santander.domain;

import java.util.Map;

public record SantanderReply(
        String operation, int status, boolean uncertain, boolean mocked, Map<String, Object> body) {
    public SantanderReply {
        body = body == null ? Map.of() : Map.copyOf(body);
    }
}
