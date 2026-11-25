package br.com.includelabs.inpay.itau.domain;

import java.util.Map;

public record ItauReply(
        String operation, int status, boolean uncertain, boolean mocked, Map<String, Object> body) {
    public ItauReply {
        body = body == null ? Map.of() : Map.copyOf(body);
    }
}
