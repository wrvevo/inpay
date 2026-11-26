package br.com.includelabs.inpay.itau.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.includelabs.inpay.itau.application.ExecuteItauOperation;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItauCatalogTest {
    @Test
    void everyDocumentedFamilyCanBeMocked() {
        ExecuteItauOperation useCase = new ExecuteItauOperation(null, false, false);
        EnumSet<ItauProduct> products = EnumSet.noneOf(ItauProduct.class);
        Map<String, Object> ids =
                Map.of(
                        "id",
                        "abc123",
                        "txid",
                        "abc123",
                        "e2eid",
                        "abc123",
                        "chave",
                        "chave",
                        "id_boleto",
                        "abc123");
        for (ItauOperation operation : ItauOperation.values()) {
            products.add(operation.product());
            ItauReply reply =
                    useCase.execute(operation.name(), "op-" + operation.name(), "true", ids);
            assertEquals(200, reply.status(), operation.name());
            assertTrue(reply.mocked());
        }
        assertEquals(EnumSet.allOf(ItauProduct.class), products);
    }

    @Test
    void liveStaysOffAndUnverifiedSispagIsBlocked() {
        ExecuteItauOperation useCase = new ExecuteItauOperation(null, false, false);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> useCase.execute("PIX_COB_GET", "live-1", "false", Map.of("txid", "abc123")));
        ExecuteItauOperation confirmedOff = new ExecuteItauOperation(null, true, false);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        confirmedOff.execute(
                                "SISPAG_PIX_POST", "live-2", "false", Map.of("id", "abc123")));
    }
}
