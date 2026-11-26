package br.com.includelabs.inpay.payment.bootstrap;

import br.com.includelabs.inpay.contracts.FraudMode;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Modo de antifraude selecionado no gateway. A avaliação em si roda no fraud-service. */
@Component
public class FraudModeHolder {
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
