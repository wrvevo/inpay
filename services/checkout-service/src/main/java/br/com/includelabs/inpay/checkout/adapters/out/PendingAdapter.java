package br.com.includelabs.inpay.checkout.adapters.out;

import br.com.includelabs.inpay.checkout.domain.CheckoutSession;
import br.com.includelabs.inpay.checkout.domain.port.CheckoutPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements CheckoutPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param linkId identificador do link que deve originar a sessão de checkout
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public CheckoutSession open(UUID linkId) {
        throw new UnsupportedOperationException("Implement CheckoutPort before enabling this flow");
    }
}
