package br.com.includelabs.inpay.links.domain.port;

import br.com.includelabs.inpay.links.domain.PaymentLink;
import java.util.*;

/**
 * Porta do domínio para a capacidade de LinkStore. Adaptadores de infraestrutura implementam este
 * contrato.
 */
public interface LinkStore {

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param link link com valor, lojista e prazo de validade
     */
    void save(PaymentLink link);

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchant identificador do lojista autorizado para a operação
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    Optional<PaymentLink> find(UUID merchant, UUID id);

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param merchant identificador do lojista autorizado para a operação
     * @param id identificador do recurso, sempre validado no contexto do lojista
     */
    void revoke(UUID merchant, UUID id);

    /**
     * Revoga links vencidos ainda ativos e informa a quantidade alterada.
     *
     * @return quantidade de links revogados por expiração
     */
    int expire();
}
