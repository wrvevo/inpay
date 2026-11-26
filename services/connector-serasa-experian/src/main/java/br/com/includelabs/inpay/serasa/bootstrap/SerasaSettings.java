package br.com.includelabs.inpay.serasa.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SerasaSettings {
    private final boolean enabled;

    public SerasaSettings(@Value("${inpay.serasa.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
