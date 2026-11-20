package br.com.includelabs.inpay.marketplace.domain;

import java.util.UUID;

/** Solicitação de alteração sujeita a aprovação por outra identidade. */
public class Adjustment {
    public UUID id;
    public UUID relatedOrder;
    public String owner, kind, evidence, requestedBy, approvedBy;
    public long amount;
    public String status = "PENDING";
}
