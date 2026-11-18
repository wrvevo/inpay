package br.com.includelabs.inpay.reconciliation.adapters.out;

import br.com.includelabs.inpay.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.inpay.reconciliation.domain.port.SettlementPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements SettlementPort {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public ReconciliationCase reconcile(UUID merchantId) {
        throw new UnsupportedOperationException(
                "Implement SettlementPort before enabling this flow");
    }
}
