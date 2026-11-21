package br.com.includelabs.inpay.connector;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Circuit breaker concorrente. Respostas antigas não fecham um circuito aberto por outra chamada.
 */
public final class CircuitGate {
    public record Lease(long epoch) {}

    private final int threshold;
    private final long cooldown;
    private final LongSupplier clock;
    private int failures;
    private long epoch;
    private long openedUntil;
    private boolean halfOpen;

    public CircuitGate(int threshold, Duration cooldown) {
        this(threshold, cooldown, System::nanoTime);
    }

    public CircuitGate(int threshold, Duration cooldown, LongSupplier clock) {
        if (threshold < 1 || cooldown.isNegative() || cooldown.isZero())
            throw new IllegalArgumentException();
        this.threshold = threshold;
        this.cooldown = cooldown.toNanos();
        this.clock = clock;
    }

    /** Uma única sonda é admitida no estado half-open. */
    public synchronized Lease acquire() {
        if (openedUntil != 0) {
            if (clock.getAsLong() < openedUntil || halfOpen) return null;
            halfOpen = true;
            epoch++;
        }
        return new Lease(epoch);
    }

    /** Apenas chamadas da geração corrente alteram o estado. */
    public synchronized void complete(Lease lease, boolean success) {
        if (lease.epoch() != epoch) return;
        if (success) {
            failures = 0;
            openedUntil = 0;
            halfOpen = false;
        } else if (halfOpen || ++failures >= threshold) {
            openedUntil = clock.getAsLong() + cooldown;
            halfOpen = false;
            epoch++;
        }
    }
}
