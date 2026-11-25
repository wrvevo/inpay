package br.com.includelabs.inpay.bank;

import java.time.Duration;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Circuit breaker das chamadas bancárias. Uma sonda half-open por vez; respostas atrasadas não
 * fecham um circuito aberto por outra chamada.
 */
public final class BankCircuit {
    private static final Logger LOG = LogManager.getLogger(BankCircuit.class);

    public record Lease(long epoch) {}

    private final int threshold;
    private final long cooldown;
    private final LongSupplier clock;
    private int failures;
    private long epoch;
    private long openedUntil;
    private boolean halfOpen;

    public BankCircuit(int threshold, Duration cooldown) {
        this(threshold, cooldown, System::nanoTime);
    }

    public BankCircuit(int threshold, Duration cooldown, LongSupplier clock) {
        if (threshold < 1 || cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("Invalid circuit settings");
        }
        this.threshold = threshold;
        this.cooldown = cooldown.toNanos();
        this.clock = clock;
    }

    public synchronized Lease acquire() {
        if (openedUntil != 0) {
            if (clock.getAsLong() < openedUntil || halfOpen) {
                LOG.warn("Circuit open; external bank call rejected");
                return null;
            }
            halfOpen = true;
            epoch++;
            LOG.info("Circuit half-open; one probe admitted");
        }
        return new Lease(epoch);
    }

    public synchronized void complete(Lease lease, boolean success) {
        if (lease == null || lease.epoch() != epoch) {
            return;
        }
        if (success) {
            failures = 0;
            openedUntil = 0;
            halfOpen = false;
            LOG.info("Circuit closed after a successful external call");
            return;
        }
        if (halfOpen || ++failures >= threshold) {
            openedUntil = clock.getAsLong() + cooldown;
            halfOpen = false;
            epoch++;
            LOG.error("Circuit opened after external call failures failures={}", failures);
        }
    }
}
