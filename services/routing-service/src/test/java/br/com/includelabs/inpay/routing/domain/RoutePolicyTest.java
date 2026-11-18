package br.com.includelabs.inpay.routing.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RoutePolicyTest {

    @Test
    void tokenRestrictsRoute() {
        var selected =
                new RoutePolicy()
                        .choose(
                                List.of(
                                        new RoutePolicy.Candidate("REDE", "a", true, 1),
                                        new RoutePolicy.Candidate("CIELO", "b", true, 2)),
                                Set.of("CIELO:b"),
                                false);
        assertEquals("CIELO", selected.provider());
    }

    @Test
    void unknownBlocksFallback() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new RoutePolicy()
                                .choose(
                                        List.of(new RoutePolicy.Candidate("CIELO", "b", true, 2)),
                                        Set.of("CIELO:b"),
                                        true));
    }
}
