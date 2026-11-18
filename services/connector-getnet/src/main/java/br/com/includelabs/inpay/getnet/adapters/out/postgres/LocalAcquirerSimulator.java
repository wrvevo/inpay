package br.com.includelabs.inpay.getnet.adapters.out.postgres;

import br.com.includelabs.inpay.getnet.domain.Authorization;
import br.com.includelabs.inpay.getnet.domain.port.AcquirerPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulador PostgreSQL ativado apenas pelo perfil local; não faz chamadas para a adquirente real.
 */
@Component
@Profile("local")
public class LocalAcquirerSimulator implements AcquirerPort {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inicializa LocalAcquirerSimulator com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     */
    public LocalAcquirerSimulator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Simula uma autorização idempotente somente no perfil local. Valores 13 e 51 representam,
     * respectivamente, resultado incerto e recusa.
     *
     * @param request dados de entrada da operação, sem dados sensíveis de cartão
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     * @throws IllegalStateException quando a validação ou a capacidade exigida impedir a conclusão
     *     da operação
     */
    @Transactional
    public Authorization authorize(Authorization request) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                resultSet -> {},
                request.operationId().toString());
        var existingOperations =
                jdbcTemplate.queryForList(
                        "SELECT * FROM provider_operations WHERE id=?", request.operationId());
        if (!existingOperations.isEmpty()) {
            var existingOperation = existingOperations.getFirst();
            if (!existingOperation.get("merchant_id").equals(request.merchantId())
                    || ((Number) existingOperation.get("amount")).longValue() != request.amount()
                    || !existingOperation
                            .get("instrument_token")
                            .equals(request.instrumentToken())) {
                throw new IllegalStateException("Conflict");
            }
            return new Authorization(
                    request.operationId(),
                    request.merchantId(),
                    request.amount(),
                    request.instrumentToken(),
                    Authorization.Outcome.valueOf((String) existingOperation.get("outcome")));
        }
        // Deliberate test scenarios: 13 = uncertain; 51 = declined. Only the local profile uses
        // these.
        var outcome =
                request.amount() == 13
                        ? Authorization.Outcome.UNKNOWN
                        : request.amount() == 51
                                ? Authorization.Outcome.DECLINED
                                : Authorization.Outcome.AUTHORIZED;
        jdbcTemplate.update(
                "INSERT INTO provider_operations(id,merchant_id,amount,instrument_token,outcome)"
                        + " VALUES (?,?,?,?,?)",
                request.operationId(),
                request.merchantId(),
                request.amount(),
                request.instrumentToken(),
                outcome.name());
        return new Authorization(
                request.operationId(),
                request.merchantId(),
                request.amount(),
                request.instrumentToken(),
                outcome);
    }
}
