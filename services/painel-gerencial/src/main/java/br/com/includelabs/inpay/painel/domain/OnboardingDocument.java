package br.com.includelabs.inpay.painel.domain;

import java.time.Instant;
import java.util.UUID;

/** Metadata only. Encrypted file contents are never returned by lists or audit. */
public record OnboardingDocument(
        UUID id,
        String kind,
        int version,
        String mime,
        String sha256,
        String scanSource,
        String uploadedBy,
        String decision,
        Instant expiresAt) {}
