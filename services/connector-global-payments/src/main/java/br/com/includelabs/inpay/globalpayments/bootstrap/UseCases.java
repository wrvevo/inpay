package br.com.includelabs.inpay.globalpayments.bootstrap;

import br.com.includelabs.inpay.globalpayments.application.AuthorizePayment;
import br.com.includelabs.inpay.globalpayments.domain.port.AcquirerPort;
import org.springframework.context.annotation.*;

/**
 * Composição dos casos de uso com suas portas de saída. A configuração Spring fica fora do domínio.
 */
@Configuration
public class UseCases {

    /**
     * Conecta o caso de uso à sua porta de saída, mantendo a composição Spring fora do domínio.
     *
     * @param port porta de saída injetada, substituível por adaptador real ou simulado
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    AuthorizePayment authorizePayment(AcquirerPort port) {
        return new AuthorizePayment(port);
    }
}
