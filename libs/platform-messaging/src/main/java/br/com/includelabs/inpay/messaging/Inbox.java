package br.com.includelabs.inpay.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Componente de infraestrutura do InPay responsável por Inbox. Não deve ser referenciado pelo
 * domínio.
 */
@Component
public class Inbox {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inicializa Inbox com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     */
    public Inbox(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reserva a chave do evento dentro da transação do consumidor, impedindo processamento
     * duplicado após commit.
     *
     * @param consumer nome estável do consumidor, parte da chave de deduplicação
     * @param eventId identificador estável usado para deduplicação
     * @return true somente quando este consumidor ainda não registrou o evento
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    public boolean first(String consumer, UUID eventId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Inbox requires transaction");
        }
        return jdbcTemplate.update(
                        "INSERT INTO inbox(consumer,event_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                        consumer,
                        eventId)
                == 1;
    }
}
