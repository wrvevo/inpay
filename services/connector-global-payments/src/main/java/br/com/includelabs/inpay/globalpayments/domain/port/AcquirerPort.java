package br.com.includelabs.inpay.globalpayments.domain.port;

import br.com.includelabs.inpay.globalpayments.domain.Authorization;

/**
 * Porta do domínio para a capacidade de AcquirerPort. Adaptadores de infraestrutura implementam
 * este contrato.
 */
public interface AcquirerPort {

    /**
     * Encaminha a autorização ao adaptador selecionado; o perfil local é simulado e o adaptador
     * externo utiliza o contrato nativo e exige configuração explícita.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    Authorization authorize(Authorization request);
}
