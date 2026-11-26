package br.com.includelabs.inpay.fraud.bootstrap;

import br.com.includelabs.inpay.contracts.FraudMode;
import br.com.includelabs.inpay.fraud.domain.port.FraudModeSource;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Seleção em memória do modo nativo. O valor inicial vem de inpay.fraud.mode. */
@Component
public class FraudModeHolder implements FraudModeSource {
    private final AtomicReference<String> mode;

    public FraudModeHolder(@Value("${inpay.fraud.mode:INPAY_RISK}") String initial) {
        this.mode = new AtomicReference<>(FraudMode.canonical(initial));
    }

    public String current() {
        return mode.get();
    }

    public String select(String next) {
        String canonical = FraudMode.canonical(next);
        mode.set(canonical);
        return canonical;
    }
}
