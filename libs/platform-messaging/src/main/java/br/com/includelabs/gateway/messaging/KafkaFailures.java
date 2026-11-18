package br.com.includelabs.gateway.messaging;

import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class KafkaFailures {
 @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> kafka) {
  var recoverer=new DeadLetterPublishingRecoverer(kafka);
  recoverer.setFailIfSendResultIsError(true);
  return new DefaultErrorHandler(recoverer,new FixedBackOff(2000L,3L));
 }
}
