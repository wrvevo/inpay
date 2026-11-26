package br.com.includelabs.inpay.fraud.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InPayRiskEngineTest {
    private final InPayRiskEngine engine = new InPayRiskEngine(new RiskPolicy());

    @Test
    void legacySignalsKeepTheSameDecisionAndMeterTheScore() {
        var result =
                engine.evaluate(new RiskPolicy.Signals(100, 1, 0, false), null, null, null, 0, 0);
        assertEquals(RiskPolicy.Decision.ACCEPT, result.decision());
        assertEquals(InPayRiskEngine.SCORE_TOKENS, result.tokens());
        assertEquals("INPAY_RISK", result.provider());
    }

    @Test
    void compromisedStillRejects() {
        var result =
                engine.evaluate(new RiskPolicy.Signals(100, 1, 0, true), null, null, null, 0, 0);
        assertEquals(RiskPolicy.Decision.REJECT, result.decision());
    }

    @Test
    void behaviorFingerprintAndIdentityRaiseTheScore() {
        var result =
                engine.evaluate(
                        new RiskPolicy.Signals(100, 1, 0, false),
                        new RiskSignals.Behavior(20, 2, 2, true),
                        new RiskSignals.Device("device-1", "US", "BR", true, 9),
                        new RiskSignals.Identity("00000000000", "mailinator.com", false, 10, 0),
                        0,
                        0);
        assertEquals(RiskPolicy.Decision.REJECT, result.decision());
        assertTrue(result.reasons().contains("BOT_TYPING"));
        assertTrue(result.reasons().contains("VPN_OR_PROXY"));
        assertTrue(result.reasons().contains("CPF_INCONSISTENT"));
        assertEquals(
                InPayRiskEngine.SCORE_TOKENS
                        + InPayRiskEngine.BEHAVIOR_TOKENS
                        + InPayRiskEngine.FINGERPRINT_TOKENS
                        + InPayRiskEngine.IDENTITY_TOKENS,
                result.tokens());
    }

    @Test
    void confirmedChargebacksIncreaseAndLegitimateHistoryLowers() {
        var punished =
                engine.evaluate(new RiskPolicy.Signals(100, 1, 0, false), null, null, null, 3, 0);
        assertTrue(punished.reasons().contains("CHARGEBACK_HISTORY"));
        assertEquals(36, punished.score());
        var trusted =
                engine.evaluate(new RiskPolicy.Signals(100, 1, 0, false), null, null, null, 0, 2);
        assertEquals(0, trusted.score());
        assertTrue(trusted.reasons().contains("TRUSTED_HISTORY"));
    }

    @Test
    void validCpfIsNotAnInconsistency() {
        assertEquals("VALID", RiskSignals.cpfStatus("39053344705"));
        assertEquals("INVALID", RiskSignals.cpfStatus("11111111111"));
    }
}
