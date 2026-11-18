package br.com.includelabs.inpay.token.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstrumentTest {

    UUID tenant = UUID.randomUUID();

    @Test
    void cannotCrossProvider() {
        assertFalse(new Instrument("t", tenant, "CIELO", "a", true).usableBy(tenant, "REDE", "a"));
    }

    @Test
    void cannotCrossTenant() {
        assertFalse(
                new Instrument("t", tenant, "CIELO", "a", true)
                        .usableBy(UUID.randomUUID(), "CIELO", "a"));
    }

    @Test
    void revokedCannotPay() {
        assertFalse(
                new Instrument("t", tenant, "CIELO", "a", false).usableBy(tenant, "CIELO", "a"));
    }
}
