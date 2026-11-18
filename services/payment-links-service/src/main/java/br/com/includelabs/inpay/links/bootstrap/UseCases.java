package br.com.includelabs.inpay.links.bootstrap;

import br.com.includelabs.inpay.links.application.CreateLink;
import br.com.includelabs.inpay.links.domain.port.LinkStore;
import org.springframework.context.annotation.*;

/**
 * Composição dos casos de uso com suas portas de saída. A configuração Spring fica fora do domínio.
 */
@Configuration
public class UseCases {

    /**
     * Conecta o caso de uso à sua porta de saída, mantendo a composição Spring fora do domínio.
     *
     * @param store porta de persistência pertencente ao módulo
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    CreateLink createLink(LinkStore store) {
        return new CreateLink(store);
    }
}
