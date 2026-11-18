package br.com.includelabs.inpay.merchant.domain.port;

import br.com.includelabs.inpay.merchant.domain.Merchant;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de MerchantDirectory. Adaptadores de infraestrutura
 * implementam este contrato.
 */
public interface MerchantDirectory {

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    Merchant find(UUID merchantId);
}
