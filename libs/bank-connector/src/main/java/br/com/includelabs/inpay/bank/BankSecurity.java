package br.com.includelabs.inpay.bank;

import org.springframework.stereotype.Component;

/** Expõe o banco deste processo para a autorização do endpoint. */
@Component("bankSecurity")
public class BankSecurity {
    private final BankSettings settings;

    public BankSecurity(BankSettings settings) {
        this.settings = settings;
    }

    public String scopeId() {
        return settings.id();
    }
}
