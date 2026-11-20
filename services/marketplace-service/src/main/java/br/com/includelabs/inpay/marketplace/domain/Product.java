package br.com.includelabs.inpay.marketplace.domain;

/** Oferta controlada pelo marketplace, com preço e fornecedor definidos no servidor. */
public record Product(String sku, String title, long price, String provider, boolean active) {}
