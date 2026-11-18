package br.com.includelabs.inpay.query.adapters.out;

import br.com.includelabs.inpay.query.domain.TransactionView;
import br.com.includelabs.inpay.query.domain.port.TransactionViewPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements TransactionViewPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public TransactionView find(UUID paymentId) {
        throw new UnsupportedOperationException(
                "Implement TransactionViewPort before enabling this flow");
    }
}
