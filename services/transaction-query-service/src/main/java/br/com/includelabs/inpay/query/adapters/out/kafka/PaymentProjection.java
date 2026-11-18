package br.com.includelabs.inpay.query.adapters.out.kafka;

import br.com.includelabs.inpay.contracts.InPayEvent;
import br.com.includelabs.inpay.messaging.Inbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Componente de infraestrutura do InPay responsável por PaymentProjection. Não deve ser
 * referenciado pelo domínio.
 */
@Component
public class PaymentProjection {

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper;

    private final Inbox inbox;

    /**
     * Inicializa PaymentProjection com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     * @param jsonMapper serializador JSON configurado pela aplicação
     * @param inbox controle de eventos já processados pelo consumidor
     */
    public PaymentProjection(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper, Inbox inbox) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.inbox = inbox;
    }

    /**
     * Deduplica PaymentCreated e atualiza a projeção na mesma transação; versões antigas não
     * sobrescrevem dados mais recentes.
     *
     * @param payload evento recebido, sem dados de cartão nem tokens de autenticação
     * @throws IllegalArgumentException quando a validação ou a capacidade exigida impedir a
     *     conclusão da operação
     */
    @KafkaListener(topics = "payment.events.v1")
    @Transactional
    public void consume(String payload) {
        var event = jsonMapper.readValue(payload, InPayEvent.class);
        if (event.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported event version");
        }
        if (!inbox.first("payment-projection", event.eventId())) {
            return;
        }
        if (!event.eventType().equals("PaymentCreated")) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO transaction_view(id,merchant_id,amount,status,version) VALUES"
                        + " (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET"
                        + " amount=EXCLUDED.amount,status=EXCLUDED.status,version=EXCLUDED.version"
                        + " WHERE transaction_view.version<EXCLUDED.version AND"
                        + " transaction_view.merchant_id=EXCLUDED.merchant_id",
                event.aggregateId(),
                event.merchantId(),
                ((Number) event.data().get("amount")).longValue(),
                (String) event.data().get("status"),
                event.aggregateVersion());
    }
}
