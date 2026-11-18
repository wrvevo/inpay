package br.com.includelabs.inpay.payment.adapters.out.postgres;

import br.com.includelabs.inpay.contracts.InPayEvent;
import br.com.includelabs.inpay.messaging.Outbox;
import br.com.includelabs.inpay.payment.domain.*;
import br.com.includelabs.inpay.payment.domain.port.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador PostgreSQL do módulo. Consultas de recursos incluem o contexto do lojista quando
 * aplicável.
 */
@Repository
public class JdbcPaymentStore implements PaymentStore {

    private final JdbcTemplate jdbcTemplate;

    private final Outbox outbox;

    /**
     * Inicializa JdbcPaymentStore com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     * @param outbox gravador de eventos na mesma transação da alteração de negócio
     */
    public JdbcPaymentStore(JdbcTemplate jdbcTemplate, Outbox outbox) {
        this.jdbcTemplate = jdbcTemplate;
        this.outbox = outbox;
    }

    /**
     * Serializa tentativas da mesma chave, grava pagamento e outbox atomicamente e recupera o
     * recurso em repetições válidas.
     *
     * @param payment agregado de pagamento com identidade, valor e último estado confirmado
     * @param idempotencyKey chave que permite repetir o comando sem duplicar o recurso
     * @param requestFingerprint SHA-256 do comando canônico, usado para detectar reutilização
     *     indevida da chave
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    @Transactional
    public Payment register(Payment payment, String idempotencyKey, String requestFingerprint) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                resultSet -> {},
                payment.merchantId() + ":" + idempotencyKey);
        var existing =
                jdbcTemplate.queryForList(
                        "SELECT request_hash,payment_id FROM payment_idempotency WHERE"
                                + " merchant_id=? AND key=?",
                        payment.merchantId(),
                        idempotencyKey);
        if (!existing.isEmpty()) {
            if (!requestFingerprint.equals(existing.getFirst().get("request_hash"))) {
                throw new IllegalStateException("Idempotency conflict");
            }
            return find(payment.merchantId(), (UUID) existing.getFirst().get("payment_id"))
                    .orElseThrow();
        }
        jdbcTemplate.update(
                "INSERT INTO"
                        + " payments(id,merchant_id,merchant_order_id,amount,currency,instrument_token,status)"
                        + " VALUES (?,?,?,?,?,?,?)",
                payment.id(),
                payment.merchantId(),
                payment.merchantOrderId(),
                payment.amount(),
                payment.currency(),
                payment.instrumentToken(),
                payment.status().name());
        jdbcTemplate.update(
                "INSERT INTO payment_idempotency(merchant_id,key,request_hash,payment_id) VALUES"
                        + " (?,?,?,?)",
                payment.merchantId(),
                idempotencyKey,
                requestFingerprint,
                payment.id());
        outbox.append(
                "payment.events.v1",
                new InPayEvent(
                        UUID.randomUUID(),
                        "PaymentCreated",
                        1,
                        payment.id(),
                        0,
                        payment.merchantId(),
                        Instant.now(),
                        Map.of(
                                "status",
                                payment.status().name(),
                                "amount",
                                payment.amount(),
                                "currency",
                                payment.currency())));
        return payment;
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    public Optional<Payment> find(UUID merchantId, UUID id) {
        return jdbcTemplate
                .query(
                        "SELECT * FROM payments WHERE merchant_id=? AND id=?",
                        (resultSet, rowNumber) ->
                                new Payment(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("merchant_id", UUID.class),
                                        resultSet.getString("merchant_order_id"),
                                        resultSet.getLong("amount"),
                                        resultSet.getString("currency"),
                                        resultSet.getString("instrument_token"),
                                        Payment.Status.valueOf(resultSet.getString("status")),
                                        resultSet.getLong("captured"),
                                        resultSet.getLong("refunded"),
                                        resultSet.getBoolean("uncertain"),
                                        resultSet.getLong("version")),
                        merchantId,
                        id)
                .stream()
                .findFirst();
    }
}
