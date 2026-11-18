package br.com.includelabs.inpay.payment.bootstrap;

import br.com.includelabs.inpay.payment.application.SubmitPayment;
import br.com.includelabs.inpay.payment.domain.port.PaymentStore;
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
    SubmitPayment submitPayment(PaymentStore store) {
        return new SubmitPayment(store);
    }
}
