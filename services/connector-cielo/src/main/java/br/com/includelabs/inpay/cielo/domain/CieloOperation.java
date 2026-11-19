package br.com.includelabs.inpay.cielo.domain;

/** Operações distintas da coleção oficial; variantes de venda compartilham CREATE_SALE. */
public enum CieloOperation {
    CREATE_SALE,
    GET_PAYMENT,
    CAPTURE_PAYMENT,
    VOID_PAYMENT,
    VOID_ORDER,
    GET_RECURRENT,
    UPDATE_RECURRENT_CUSTOMER,
    UPDATE_RECURRENT_INTERVAL,
    UPDATE_RECURRENT_END_DATE,
    UPDATE_RECURRENT_DAY,
    REACTIVATE_RECURRENT,
    DEACTIVATE_RECURRENT,
    UPDATE_RECURRENT_INSTALLMENTS,
    UPDATE_RECURRENT_PAYMENT,
    UPDATE_RECURRENT_NEXT_DATE,
    UPDATE_RECURRENT_AMOUNT,
    CREATE_CARD,
    GET_CARD,
    ZERO_AUTH,
    QUERY_BIN;

    /** Identifica consultas sem efeitos financeiros; o controller exige HTTP GET para elas. */
    public boolean readOnly() {
        return this == GET_PAYMENT
                || this == GET_RECURRENT
                || this == GET_CARD
                || this == QUERY_BIN;
    }
}
