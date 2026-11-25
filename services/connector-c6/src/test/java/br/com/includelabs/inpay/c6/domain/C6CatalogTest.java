package br.com.includelabs.inpay.c6.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.includelabs.inpay.c6.application.C6ResultPublisher;
import br.com.includelabs.inpay.c6.application.ExecuteC6Operation;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class C6CatalogTest {
    @Test
    void everyPortalProductCanBeMocked() {
        ExecuteC6Operation useCase = new ExecuteC6Operation(null, C6ResultPublisher.NONE, false, false);
        EnumSet<C6Product> products = EnumSet.noneOf(C6Product.class);
        Map<String, Object> ids =
                Map.of("id", "abc123", "txid", "abc123", "e2eid", "abc123", "chave", "chave", "data", "2026-11-23");
        for (C6Operation operation : C6Operation.values()) {
            products.add(operation.product());
            C6Reply reply = useCase.execute(operation.name(), "op-" + operation.name(), "true", ids);
            assertEquals(200, reply.status(), operation.name());
            assertTrue(reply.mocked());
        }
        assertEquals(EnumSet.allOf(C6Product.class), products);
        assertEquals(10, products.size());
    }
}
