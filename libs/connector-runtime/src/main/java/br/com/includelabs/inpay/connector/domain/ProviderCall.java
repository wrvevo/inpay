package br.com.includelabs.inpay.connector.domain;

import java.util.Map;

/** Requisição restrita a um caminho conhecido; a URL base provém da configuração do servidor. */
public record ProviderCall(
        String method,
        String path,
        boolean queryHost,
        Map<String, Object> body,
        Map<String, String> headers) {
    /** Evita expor o corpo ou os cabeçalhos em logs acidentais. */
    @Override
    public String toString() {
        return "ProviderCall[method=" + method + "]";
    }
}
