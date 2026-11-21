package br.com.includelabs.inpay.credits.domain;

import java.util.*;

/** Pedido independente do payment-core; não contém PAN, CVV ou código real de gift card. */
public class Order {
    public UUID id;
    public String owner, sku, title, provider, providerReference;
    public long total;
    public long creditOutstanding;
    public List<Tender> tenders = new ArrayList<>();
    public OrderStatus status;
    public boolean simulation;
    public String createdAt;
}
