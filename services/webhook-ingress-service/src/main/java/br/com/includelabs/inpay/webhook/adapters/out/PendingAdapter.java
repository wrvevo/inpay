package br.com.includelabs.inpay.webhook.adapters.out;

import br.com.includelabs.inpay.webhook.domain.ProviderObservation;
import br.com.includelabs.inpay.webhook.domain.port.ProviderVerificationPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements ProviderVerificationPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param observationId identificador da notificação externa que precisa ser verificada
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public ProviderObservation verify(UUID observationId) {
        throw new UnsupportedOperationException(
                "Implement ProviderVerificationPort before enabling this flow");
    }
}
