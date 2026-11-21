package br.com.includelabs.inpay.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CircuitGateTest {
    @Test
    void staleSuccessCannotCloseOpenCircuitAndOnlyOneProbeRuns() {
        var clock = new AtomicLong(10);
        var gate = new CircuitGate(1, Duration.ofNanos(100), clock::get);
        var old = gate.acquire();
        var failure = gate.acquire();
        gate.complete(failure, false);
        gate.complete(old, true);
        assertNull(gate.acquire());
        clock.set(111);
        var probe = gate.acquire();
        assertNotNull(probe);
        assertNull(gate.acquire());
        gate.complete(probe, true);
        assertNotNull(gate.acquire());
    }
}
