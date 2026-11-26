package br.com.includelabs.inpay.contracts;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FraudModeTest {
    @Test
    void acceptsAliasesAndRejectsUnknown() {
        assertEquals(FraudMode.INPAY_RISK, FraudMode.canonical("inpay-risk"));
        assertEquals(FraudMode.CLEARSALE, FraudMode.canonical("clear-sale"));
        assertEquals(FraudMode.SERASA_EXPERIAN, FraudMode.canonical("serasa-experience"));
        assertThrows(IllegalArgumentException.class, () -> FraudMode.canonical("outro"));
    }

    @Test
    void stubsStayExplainable() {
        var rejected =
                AntifraudStubs.evaluate(
                        FraudMode.CLEARSALE,
                        new AntifraudStubs.Probe(true, false, 0, false, false, false));
        assertEquals("REJECT", rejected.decision());
        assertTrue(rejected.reasons().contains("MOCK_STUB"));
        var review =
                AntifraudStubs.evaluate(
                        FraudMode.SERASA_EXPERIAN,
                        new AntifraudStubs.Probe(false, false, 0, false, true, false));
        assertEquals("REVIEW", review.decision());
    }
}
