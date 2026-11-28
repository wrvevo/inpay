package br.com.includelabs.inpay.c6.adapters.out.kafka;

import br.com.includelabs.inpay.c6.application.C6ResultPublisher;
import br.com.includelabs.inpay.c6.domain.C6Operation;
import br.com.includelabs.inpay.c6.domain.C6Reply;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Resultado durável para o gateway. O corpo nativo do banco não entra no evento. */
@Component
@Profile("local & !aws")
@ConditionalOnProperty(prefix = "inpay.c6", name = "kafka-enabled", havingValue = "true")
public class C6ResultPublisherAdapter implements C6ResultPublisher {
    static final String TOPIC = "inpay.c6.results.v1";
    private static final Logger LOG = LogManager.getLogger(C6ResultPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Inicializa o produtor Kafka responsável pelos resultados. */
    public C6ResultPublisherAdapter(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    /** Publica o resultado normalizado da operação no tópico configurado. */
    @Override
    public void publish(C6Operation operation, String operationId, C6Reply reply) {
        if (!reply.mocked()) {
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("bank", "c6");
            event.put("operation", operation.name());
            event.put("product", operation.product().name());
            event.put("operationId", operationId);
            event.put("status", reply.status());
            event.put("uncertain", reply.uncertain());
            event.put("mocked", reply.mocked());
            kafka.send(
                    TOPIC,
                    operationId == null ? operation.name() : operationId,
                    mapper.writeValueAsString(event))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            LOG.info(
                    "C6 result published operation={} status={}", operation.name(), reply.status());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("C6 sandbox publication interrupted");
        } catch (Exception exception) {
            LOG.error("C6 result publication not confirmed");
            throw new IllegalStateException("C6 sandbox publication failed");
        }
    }
}
