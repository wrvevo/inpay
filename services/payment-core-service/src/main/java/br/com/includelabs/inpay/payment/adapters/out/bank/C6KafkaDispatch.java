package br.com.includelabs.inpay.payment.adapters.out.bank;

import br.com.includelabs.inpay.contracts.IntegrationDataPolicy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Publica o comando C6 para o conector executar e responder em inpay.c6.results.v1. */
@Component
@Profile("local & !aws")
@ConditionalOnProperty(prefix = "inpay.c6", name = "gateway-kafka", havingValue = "true")
public class C6KafkaDispatch {
    public static final String TOPIC = "inpay.c6.commands.v1";
    private static final Logger LOG = LogManager.getLogger(C6KafkaDispatch.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    public C6KafkaDispatch(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    public void publish(
            String operation, String operationId, String mock, Map<String, Object> payload) {
        if (!"true".equalsIgnoreCase(mock) || operation == null
                || !operation.matches("[A-Z0-9_]{1,80}") || operationId == null
                || !operationId.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Legacy Kafka dispatch is sandbox only");
        }
        IntegrationDataPolicy.validate(payload);
        try {
            Map<String, Object> command = new LinkedHashMap<>();
            command.put("operation", operation);
            command.put("operationId", operationId);
            command.put("mock", "true");
            command.put("payload", payload == null ? Map.of() : payload);
            kafka.send(TOPIC, operationId, mapper.writeValueAsString(command))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            LOG.info("Gateway published C6 command operation={}", operation);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("C6 Kafka dispatch interrupted");
        } catch (Exception exception) {
            LOG.error("Gateway could not confirm C6 command publication");
            throw new IllegalStateException("C6 Kafka dispatch failed");
        }
    }
}
