package br.com.includelabs.inpay.merchant.adapters.out;

import br.com.includelabs.inpay.merchant.domain.Merchant;
import br.com.includelabs.inpay.merchant.domain.port.MerchantDirectory;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ponto de extensão ainda não implementado. Falha explicitamente para impedir resultados
 * financeiros fictícios.
 */
@Component
public class PendingAdapter implements MerchantDirectory {

    /**
     * Interrompe a operação porque o adaptador real ainda não foi implementado. Não produz sucesso
     * financeiro simulado.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     * @throws UnsupportedOperationException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    public Merchant find(UUID merchantId) {
        throw new UnsupportedOperationException(
                "Implement MerchantDirectory before enabling this flow");
    }
}
