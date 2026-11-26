package br.com.includelabs.inpay.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LocalApiTokenTest {
    @Test
    void roundTripAndRejectsTampering() {
        String token =
                LocalApiToken.issue(
                        "inpay@123",
                        "admin@inpay.local",
                        "ADMIN",
                        "PANEL",
                        java.util.List.of("painel/admin"),
                        java.time.Instant.now().getEpochSecond() + 60);
        assertEquals("admin@inpay.local", LocalApiToken.verify("inpay@123", token).subject());
        assertThrows(IllegalArgumentException.class, () -> LocalApiToken.verify("outra", token));
    }
}
