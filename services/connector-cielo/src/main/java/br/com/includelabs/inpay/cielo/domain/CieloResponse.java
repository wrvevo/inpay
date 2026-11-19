package br.com.includelabs.inpay.cielo.domain;

import java.util.Map;

/** Resultado nativo sanitizado. HTTP externo e resultado financeiro são conceitos distintos. */
public record CieloResponse(
        int httpStatus, boolean uncertain, boolean replayed, Map<String, Object> data) {
    /** Não inclui payload ou tokens na representação de diagnóstico. */
    @Override
    public String toString() {
        return "CieloResponse[httpStatus=" + httpStatus + ", uncertain=" + uncertain + "]";
    }
}
