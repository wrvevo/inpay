package br.com.includelabs.inpay.c6.domain;

import java.util.Map;

public record C6Reply(
        String operation, int status, boolean uncertain, boolean mocked, Map<String, Object> body) {
    public C6Reply {
        body = body == null ? Map.of() : Map.copyOf(body);
    }
}
