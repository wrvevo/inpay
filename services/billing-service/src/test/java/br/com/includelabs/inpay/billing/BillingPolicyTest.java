package br.com.includelabs.inpay.billing;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.billing.domain.BillingPolicy;
import br.com.includelabs.inpay.billing.domain.BillingPolicy.Conditions;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillingPolicyTest {
    @Test
    void capsLateFeesAndExpiresDiscount() {
        var due = LocalDate.of(2026, 1, 10);
        var terms = new Conditions(200, 10, 100, due, 300);
        assertEquals(9900, BillingPolicy.quote(10000, due, terms, due).total());
        var late = BillingPolicy.quote(10000, due, terms, due.plusDays(20));
        assertEquals(200, late.fine());
        assertEquals(100, late.interest());
        assertEquals(10300, late.total());
    }

    @Test
    void restoresMonthAnchor() {
        var feb = BillingPolicy.nextCycle(LocalDate.of(2026, 1, 31), 31);
        assertEquals(LocalDate.of(2026, 2, 28), feb);
        assertEquals(LocalDate.of(2026, 3, 31), BillingPolicy.nextCycle(feb, 31));
    }

    @Test
    void forbidsUnsupportedCardCharges() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BillingPolicy.validate(
                                100,
                                "CREDIT_CARD",
                                LocalDate.now(),
                                new Conditions(100, 0, 0, null, 100)));
    }

    @Test
    void uncertainPaymentsNeverRetryBlindly() {
        assertEquals("RECONCILE", BillingPolicy.nextAction("TIMEOUT", 0));
        assertEquals("STOP", BillingPolicy.nextAction("HARD_DECLINE", 0));
        assertEquals("MANUAL_REVIEW", BillingPolicy.nextAction("SOFT_DECLINE", 3));
    }
}
