package br.com.includelabs.inpay.links.adapters.out.postgres;

import br.com.includelabs.inpay.links.domain.PaymentLink;
import br.com.includelabs.inpay.links.domain.port.LinkStore;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostgreSQL do módulo. Consultas de recursos incluem o contexto do lojista quando
 * aplicável.
 */
@Repository
public class JdbcLinkStore implements LinkStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inicializa JdbcLinkStore com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     */
    public JdbcLinkStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param link link com valor, lojista e prazo de validade
     */
    public void save(PaymentLink link) {
        jdbcTemplate.update(
                "INSERT INTO"
                        + " payment_links(id,merchant_id,public_code,order_id,amount,expires_at,revoked)"
                        + " VALUES (?,?,?,?,?,?,?)",
                link.id(),
                link.merchantId(),
                link.publicCode(),
                link.orderId(),
                link.amount(),
                Timestamp.from(link.expiresAt()),
                link.revoked());
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param id identificador do recurso, sempre validado no contexto do lojista
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    public Optional<PaymentLink> find(UUID merchantId, UUID id) {
        return jdbcTemplate
                .query(
                        "SELECT * FROM payment_links WHERE merchant_id=? AND id=?",
                        (resultSet, rowNumber) ->
                                new PaymentLink(
                                        resultSet.getObject("id", UUID.class),
                                        resultSet.getObject("merchant_id", UUID.class),
                                        resultSet.getString("public_code"),
                                        resultSet.getString("order_id"),
                                        resultSet.getLong("amount"),
                                        resultSet.getTimestamp("expires_at").toInstant(),
                                        resultSet.getBoolean("revoked")),
                        merchantId,
                        id)
                .stream()
                .findFirst();
    }

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param id identificador do recurso, sempre validado no contexto do lojista
     */
    public void revoke(UUID merchantId, UUID id) {
        jdbcTemplate.update(
                "UPDATE payment_links SET revoked=true WHERE merchant_id=? AND id=?",
                merchantId,
                id);
    }

    /**
     * Revoga links vencidos ainda ativos e informa a quantidade alterada.
     *
     * @return quantidade de links revogados por expiração
     */
    public int expire() {
        return jdbcTemplate.update(
                "UPDATE payment_links SET revoked=true WHERE revoked=false AND expires_at<now()");
    }
}
