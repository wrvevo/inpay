package br.com.includelabs.inpay.marketplace.domain;

/** Fontes independentes; crédito não representa dinheiro depositado. */
public enum Source {
    CREDIT,
    BALANCE,
    VOUCHER,
    EXTERNAL
}
