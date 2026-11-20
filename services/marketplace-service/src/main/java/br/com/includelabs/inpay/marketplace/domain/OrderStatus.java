package br.com.includelabs.inpay.marketplace.domain;

/** Pendências conservam reservas até evidência conclusiva do provedor. */
public enum OrderStatus {
    RESERVED,
    PENDING_RECONCILIATION,
    COMPENSATION_REQUIRED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
