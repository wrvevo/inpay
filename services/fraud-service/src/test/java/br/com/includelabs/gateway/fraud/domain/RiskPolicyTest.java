package br.com.includelabs.gateway.fraud.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiskPolicyTest {
 private final RiskPolicy policy=new RiskPolicy();
 @Test void lowRisk(){assertEquals(RiskPolicy.Decision.ACCEPT,policy.evaluate(new RiskPolicy.Signals(100,1,0,false)).decision());}
 @Test void review(){assertEquals(RiskPolicy.Decision.REVIEW,policy.evaluate(new RiskPolicy.Signals(200000,5,0,false)).decision());}
 @Test void reject(){assertEquals(RiskPolicy.Decision.REJECT,policy.evaluate(new RiskPolicy.Signals(100,1,0,true)).decision());}
 @Test void scoreIsCapped(){assertEquals(100,policy.evaluate(new RiskPolicy.Signals(200000,10,5,true)).score());}
}
