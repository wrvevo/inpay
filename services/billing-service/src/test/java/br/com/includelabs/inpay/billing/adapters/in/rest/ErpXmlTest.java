package br.com.includelabs.inpay.billing.adapters.in.rest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ErpXmlTest {
    @Test
    void rejectsDtdAndExternalEntities() {
        assertThrows(
                Exception.class,
                () ->
                        ErpController.parse(
                                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><foo>&xxe;</foo>"));
    }

    @Test
    void rejectsOversizedMessages() {
        assertThrows(IllegalArgumentException.class, () -> ErpController.parse(" ".repeat(65537)));
    }
}
