package br.com.includelabs.inpay.credits.domain;

/** Fontes independentes; crédito não representa dinheiro depositado. */
public enum Source {
    PROMOTIONAL,
    CREDIT,
    BALANCE,
    VOUCHER,
    EXTERNAL
}
