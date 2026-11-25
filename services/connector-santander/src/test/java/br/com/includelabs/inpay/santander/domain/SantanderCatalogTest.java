package br.com.includelabs.inpay.santander.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.includelabs.inpay.santander.application.ExecuteSantanderOperation;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SantanderCatalogTest {
    @Test
    void everyFamilyCanBeMocked() {
        ExecuteSantanderOperation useCase = new ExecuteSantanderOperation(null, false, false);
        EnumSet<SantanderProduct> products = EnumSet.noneOf(SantanderProduct.class);
        Map<String, Object> ids =
                Map.of(
                        "id", "abc123",
                        "txid", "abc123",
                        "e2eid", "abc123",
                        "chave", "chave",
                        "workspaceId", "abc123",
                        "bankSlipId", "abc123",
                        "billId", "abc123");
        for (SantanderOperation operation : SantanderOperation.values()) {
            products.add(operation.product());
            SantanderReply reply = useCase.execute(operation.name(), "op-" + operation.name(), "true", ids);
            assertEquals(200, reply.status(), operation.name());
            assertTrue(reply.mocked());
        }
        assertEquals(EnumSet.allOf(SantanderProduct.class), products);
    }

    @Test
    void liveStaysOffAndUnverifiedPaymentIsBlocked() {
        ExecuteSantanderOperation disabled = new ExecuteSantanderOperation(null, false, false);
        assertThrows(
                IllegalStateException.class,
                () -> disabled.execute("PIX_COB_GET", "live-1", "false", Map.of("txid", "abc123")));
        ExecuteSantanderOperation live = new ExecuteSantanderOperation(null, true, false);
        assertThrows(
                IllegalArgumentException.class,
                () -> live.execute("PAYMENT_CREATE", "live-2", "false", Map.of("workspaceId", "abc123")));
    }
}
