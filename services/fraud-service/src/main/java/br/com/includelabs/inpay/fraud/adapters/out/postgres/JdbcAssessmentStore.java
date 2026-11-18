package br.com.includelabs.inpay.fraud.adapters.out.postgres;

import br.com.includelabs.inpay.fraud.domain.RiskPolicy.Assessment;
import br.com.includelabs.inpay.fraud.domain.port.AssessmentStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostgreSQL do módulo. Consultas de recursos incluem o contexto do lojista quando
 * aplicável.
 */
@Repository
public class JdbcAssessmentStore implements AssessmentStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Inicializa JdbcAssessmentStore com suas dependências explícitas.
     *
     * @param jdbcTemplate adaptador JDBC da base exclusiva deste serviço
     */
    public JdbcAssessmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Persiste o registro na base exclusiva deste módulo, preservando o vínculo com o lojista.
     *
     * @param merchantId identificador do lojista autorizado para a operação
     * @param paymentId identificador do recurso, sempre validado no contexto do lojista
     * @param assessment avaliação de risco com decisão, score e versão da política
     * @return resultado definido pelo contrato; exceções interrompem a operação sem anunciar
     *     sucesso
     */
    public UUID save(UUID merchantId, UUID paymentId, Assessment assessment) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO"
                        + " assessments(id,merchant_id,payment_id,score,decision,policy_version,reasons)"
                        + " VALUES (?,?,?,?,?,?,?)",
                id,
                merchantId,
                paymentId,
                assessment.score(),
                assessment.decision().name(),
                assessment.policyVersion(),
                String.join(",", assessment.reasons()));
        return id;
    }
}
