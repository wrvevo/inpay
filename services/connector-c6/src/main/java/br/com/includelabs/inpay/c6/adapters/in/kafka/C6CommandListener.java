package br.com.includelabs.inpay.c6.adapters.in.kafka;

import br.com.includelabs.inpay.c6.application.ExecuteC6Operation;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Comando assíncrono publicado pelo gateway. A execução é a mesma do REST. */
@Component
@ConditionalOnProperty(prefix = "inpay.c6", name = "kafka-enabled", havingValue = "true")
public class C6CommandListener {
    static final String TOPIC = "inpay.c6.commands.v1";
    private static final Logger LOG = LogManager.getLogger(C6CommandListener.class);

    private final ExecuteC6Operation useCase;
    private final ObjectMapper mapper = new ObjectMapper();

    public C6CommandListener(ExecuteC6Operation useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = TOPIC, groupId = "connector-c6")
    public void onCommand(String payload) {
        try {
            Map<?, ?> command = mapper.readValue(payload, Map.class);
            String operation = String.valueOf(command.get("operation"));
            String operationId = String.valueOf(command.get("operationId"));
            String mock = command.get("mock") == null ? "false" : String.valueOf(command.get("mock"));
            Object body = command.get("payload");
            Map<String, Object> fields = body instanceof Map<?, ?> map ? cast(map) : Map.of();
            LOG.info("C6 Kafka command received operation={}", operation);
            useCase.execute(operation, operationId, mock, fields);
        } catch (Exception exception) {
            LOG.error("C6 Kafka command failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
