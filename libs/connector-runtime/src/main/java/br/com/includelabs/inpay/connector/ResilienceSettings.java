package br.com.includelabs.inpay.connector;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Proteções técnicas configuráveis; retry financeiro de POST/PUT/DELETE permanece proibido. */
@ConfigurationProperties("inpay.resilience")
public record ResilienceSettings(
        int failureThreshold,
        Duration openDuration,
        int queryAttempts,
        Duration retryDelay,
        int maxConcurrent) {
    public ResilienceSettings {
        if (failureThreshold == 0) failureThreshold = 5;
        if (openDuration == null) openDuration = Duration.ofSeconds(30);
        if (queryAttempts == 0) queryAttempts = 2;
        if (retryDelay == null) retryDelay = Duration.ofMillis(100);
        if (maxConcurrent == 0) maxConcurrent = 32;
        if (failureThreshold < 1
                || queryAttempts < 1
                || queryAttempts > 3
                || maxConcurrent < 1
                || maxConcurrent > 256
                || retryDelay.isNegative()
                || retryDelay.compareTo(Duration.ofSeconds(2)) > 0
                || openDuration.isNegative()
                || openDuration.isZero())
            throw new IllegalArgumentException("Invalid resilience settings");
    }

    public static ResilienceSettings defaults() {
        return new ResilienceSettings(5, Duration.ofSeconds(30), 2, Duration.ofMillis(100), 32);
    }
}
