package br.com.includelabs.inpay.contracts;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.contracts.ProviderRegistry.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {
    @Test
    void configurationCannotInventPixImplementation() {
        UUID tenant = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProviderRegistry.installed()
                                .require(
                                        tenant,
                                        Capability.PIX_COLLECTION,
                                        new Binding(
                                                tenant,
                                                "cielo",
                                                Set.of(Capability.PIX_COLLECTION))));
    }

    @Test
    void capabilitiesDoNotCrossProductsOrTenants() {
        UUID tenant = UUID.randomUUID();
        var registry =
                new ProviderRegistry(
                        List.of(new Provider("psp-test", Set.of(Capability.PIX_COLLECTION))));
        var binding =
                new Binding(
                        tenant,
                        "psp-test",
                        Set.of(Capability.PIX_COLLECTION, Capability.PIX_TRANSFER));
        assertEquals("psp-test", registry.require(tenant, Capability.PIX_COLLECTION, binding).id());
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(tenant, Capability.PIX_TRANSFER, binding));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(UUID.randomUUID(), Capability.PIX_COLLECTION, binding));
    }
}
