package br.com.includelabs.inpay.messaging;

import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Componente de infraestrutura do InPay responsável por KafkaFailures. Não deve ser referenciado
 * pelo domínio.
 */
@Configuration
public class KafkaFailures {

    /**
     * Configura três retentativas com intervalo de dois segundos e encaminhamento à DLT somente
     * após confirmação de publicação.
     *
     * @param kafka produtor Kafka configurado com confirmação de recebimento
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka);
        recoverer.setFailIfSendResultIsError(true);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
