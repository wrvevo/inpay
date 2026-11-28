package br.com.includelabs.inpay.contracts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationDataPolicyTest {
    @Test
    void permitsMinimalBusinessEvent() {
        assertDoesNotThrow(
                () ->
                        IntegrationDataPolicy.validate(
                                Map.of("paymentId", UUID.randomUUID(), "amount", 1000, "status", "PAID")));
    }

    @Test
    void rejectsRestrictedFieldsAtAnyDepth() {
        for (String field : List.of("CVV", "card_number", "client-secret", "Authorization")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> IntegrationDataPolicy.validate(Map.of("data", List.of(Map.of(field, "test")))));
        }
    }

    @Test
    void rejectsCardLikeValueEvenWithAnUnrecognizedField() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationDataPolicy.validate(Map.of("reference", "4111 1111 1111 1111")));
    }
}
