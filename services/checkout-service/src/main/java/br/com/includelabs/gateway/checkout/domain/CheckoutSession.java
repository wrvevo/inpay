package br.com.includelabs.gateway.checkout.domain;

import java.util.UUID;
import java.time.Instant;
public record CheckoutSession(UUID id, UUID merchantId, UUID linkId, Instant expiresAt) {}
