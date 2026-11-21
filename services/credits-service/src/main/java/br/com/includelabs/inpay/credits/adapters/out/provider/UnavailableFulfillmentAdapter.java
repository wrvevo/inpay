package br.com.includelabs.inpay.credits.adapters.out.provider;

import br.com.includelabs.inpay.credits.domain.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Bloqueia execução real até contratação e implementação do protocolo homologado. */
@Component
@Profile("!local")
public class UnavailableFulfillmentAdapter implements FulfillmentPort {
    /** Nenhuma reserva é liberada como se houvesse confirmação externa. */
    public Evidence fulfill(UUID tenant, Order order) {
        throw unavailable();
    }

    /** Consulta real exige conector contratado. */
    public Evidence reconcile(UUID tenant, Order order) {
        throw unavailable();
    }

    /** Estorno real exige confirmação financeira independente. */
    public Evidence refund(UUID tenant, Order order) {
        throw unavailable();
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("Credits fulfillment not homologated");
    }
}
