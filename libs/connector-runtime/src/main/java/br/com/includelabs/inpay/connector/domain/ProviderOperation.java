package br.com.includelabs.inpay.connector.domain;

/** Operações financeiras disponíveis nos adaptadores externos. */
public enum ProviderOperation {
    AUTHORIZE,
    CAPTURE,
    CANCEL,
    REFUND,
    QUERY
}
