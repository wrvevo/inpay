package br.com.includelabs.inpay.messaging;

import br.com.includelabs.inpay.contracts.InPayEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Componente de infraestrutura do InPay responsável por Outbox. Não deve ser referenciado pelo
 * domínio.
 */
@Component
public class Outbox {

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper;

    /**
     * Inicializa Outbox com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     * @param jsonMapper serializador JSON configurado pela aplicação
     */
    public Outbox(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Registra o evento para publicação posterior, exigindo a mesma transação da alteração de
     * negócio.
     *
     * @param topic tópico Kafka versionado que receberá o evento
     * @param event evento recebido, sem dados de cartão nem tokens de autenticação
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public void append(String topic, InPayEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox requires business transaction");
        }
        jdbcTemplate.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload) VALUES (?,?,?,?)",
                event.eventId(),
                topic,
                event.merchantId() + ":" + event.aggregateId(),
                jsonMapper.writeValueAsString(event));
    }
}
