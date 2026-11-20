package br.com.includelabs.inpay.marketplace.adapters.out.provider;

import br.com.includelabs.inpay.marketplace.domain.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Simulador explicitamente local. Nunca emite códigos comercializáveis ou chama a Nuuvem. */
@Component
@Profile("local")
public class LocalFulfillmentAdapter implements FulfillmentPort {
    /** Simula composição e entrega; fornecedor identifica cenário de falha determinístico. */
    public Evidence fulfill(UUID tenant, Order order) {
        Result result =
                switch (order.provider) {
                    case "SIMULATED" -> Result.CONFIRMED;
                    case "SIMULATED_REJECT" -> Result.REJECTED;
                    case "SIMULATED_TIMEOUT" -> Result.UNKNOWN;
                    case "SIMULATED_CAPTURE_FAILURE" -> Result.COMPENSATION_REQUIRED;
                    default ->
                            throw new IllegalStateException(
                                    "Provider not configured; no external calls made");
                };
        return new Evidence(result, "SIMULATION-" + order.id, true);
    }

    /** Simula consulta de timeout sem duplicar emissão; falha de captura permanece pendente. */
    public Evidence reconcile(UUID tenant, Order order) {
        return new Evidence(
                order.provider.equals("SIMULATED_TIMEOUT")
                        ? Result.CONFIRMED
                        : Result.COMPENSATION_REQUIRED,
                "SIMULATION-" + order.id,
                true);
    }

    /** Simula estorno integral, sem movimentação de dinheiro. */
    public Evidence refund(UUID tenant, Order order) {
        return new Evidence(Result.CONFIRMED, "SIMULATION-REFUND-" + order.id, true);
    }
}
