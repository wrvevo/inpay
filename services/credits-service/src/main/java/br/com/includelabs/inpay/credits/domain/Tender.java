package br.com.includelabs.inpay.credits.domain;

/** Parcela do preço; a soma das fontes deve coincidir exatamente com o preço do catálogo. */
public record Tender(Source source, long amount) {}
