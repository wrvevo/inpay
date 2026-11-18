package br.com.includelabs.inpay.token.adapters.out.postgres;

import br.com.includelabs.inpay.token.domain.Instrument;
import br.com.includelabs.inpay.token.domain.port.InstrumentStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostgreSQL do módulo. Consultas de recursos incluem o contexto do lojista quando
 * aplicável.
 */
@Repository
public class JdbcInstrumentStore implements InstrumentStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inicializa JdbcInstrumentStore com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     */
    public JdbcInstrumentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param instrument instrumento simulado vinculado ao lojista, provedor e afiliação
     */
    public void save(Instrument instrument) {
        jdbcTemplate.update(
                "INSERT INTO instruments(token,merchant_id,provider,affiliation_id,active) VALUES"
                        + " (?,?,?,?,?)",
                instrument.token(),
                instrument.merchantId(),
                instrument.provider(),
                instrument.affiliationId(),
                instrument.active());
    }

    /**
     * Consulta o recurso com o filtro de lojista aplicável, impedindo acesso entre tenants.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     * @return recurso encontrado no lojista informado, ou Optional vazio quando ausente
     */
    public Optional<Instrument> find(UUID merchantId, String token) {
        return jdbcTemplate
                .query(
                        "SELECT * FROM instruments WHERE merchant_id=? AND token=?",
                        (resultSet, rowNumber) ->
                                new Instrument(
                                        resultSet.getString("token"),
                                        resultSet.getObject("merchant_id", UUID.class),
                                        resultSet.getString("provider"),
                                        resultSet.getString("affiliation_id"),
                                        resultSet.getBoolean("active")),
                        merchantId,
                        token)
                .stream()
                .findFirst();
    }

    /**
     * Revoga o recurso do lojista informado, bloqueando futuras utilizações.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param token alias opaco do instrumento ou credencial do contexto; nunca informar PAN ou CVV
     */
    public void revoke(UUID merchantId, String token) {
        jdbcTemplate.update(
                "UPDATE instruments SET active=false WHERE merchant_id=? AND token=?",
                merchantId,
                token);
    }
}
