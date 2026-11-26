package br.com.includelabs.inpay.clearsale.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClearSaleSettings {
    private final boolean enabled;

    public ClearSaleSettings(@Value("${inpay.clearsale.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
