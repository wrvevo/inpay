package br.com.includelabs.inpay.commerce;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.commerce.domain.SplitPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;

class SplitPolicyTest {
    @Test
    void mixedRulesConserveCentsWithStableRemainders() {
        var result =
                SplitPolicy.allocate(
                        101,
                        List.of(
                                new SplitPolicy.Rule("fixed", 10, 0),
                                new SplitPolicy.Rule("a", 0, 5000),
                                new SplitPolicy.Rule("b", 0, 5000)));
        assertEquals(101, result.stream().mapToLong(SplitPolicy.Allocation::amount).sum());
        assertEquals(46, result.getFirst().amount());
    }

    @Test
    void acceptsMoreThanOneHundredParticipants() {
        var rules = new ArrayList<SplitPolicy.Rule>();
        for (int i = 0; i < 150; i++) rules.add(new SplitPolicy.Rule("r" + i, 1, 0));
        assertEquals(150, SplitPolicy.allocate(150, rules).size());
    }
}
