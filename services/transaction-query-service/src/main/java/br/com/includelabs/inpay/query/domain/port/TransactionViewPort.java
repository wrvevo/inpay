package br.com.includelabs.inpay.query.domain.port;

import br.com.includelabs.inpay.query.domain.TransactionView;
import java.util.UUID;

/**
 * Porta do domínio para a capacidade de TransactionViewPort. Adaptadores de infraestrutura
 * implementam este contrato.
 */
public interface TransactionViewPort {

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    TransactionView find(UUID paymentId);
}
