package br.com.includelabs.inpay.connector;

import br.com.includelabs.inpay.connector.domain.ProviderResult;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Journal com commit separado: falhas depois do envio jamais liberam a chave para cobrança
 * repetida.
 */
public class JdbcOperationJournal implements OperationJournal {

    private final JdbcTemplate jdbc;

    private final TransactionTemplate transaction;

    /** Configura transação curta e independente da chamada de rede. */
    public JdbcOperationJournal(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** ON CONFLICT torna a reserva exclusiva mesmo entre várias réplicas do connector. */
    @Override
    public ProviderResult reserve(UUID merchant, UUID operation, String fingerprint) {
        return transaction.execute(
                status -> {
                    int inserted =
                            jdbc.update(
                                    "INSERT INTO external_operations(merchant_id,operation_id,fingerprint) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                                    merchant,
                                    operation,
                                    fingerprint);
                    if (inserted == 1) {
                        return null;
                    }
                    var row =
                            jdbc.queryForMap(
                                    "SELECT * FROM external_operations WHERE merchant_id=? AND operation_id=?",
                                    merchant,
                                    operation);
                    if (!fingerprint.equals(row.get("fingerprint"))) {
                        throw new IllegalStateException(
                                "Idempotency key reused with different data");
                    }
                    return new ProviderResult(
                            (String) row.get("provider_id"),
                            (String) row.get("status"),
                            (String) row.get("code"),
                            ((Number) row.get("http_status")).intValue(),
                            (Boolean) row.get("uncertain"));
                });
    }

    /** Uma falha neste commit deixa UNKNOWN persistido, protegendo contra reenvio automático. */
    @Override
    public void complete(UUID merchant, UUID operation, ProviderResult result) {
        transaction.executeWithoutResult(
                status ->
                        jdbc.update(
                                "UPDATE external_operations SET provider_id=?,status=?,code=?,http_status=?,uncertain=?,updated_at=now() WHERE merchant_id=? AND operation_id=?",
                                result.providerId(),
                                result.status(),
                                result.code(),
                                result.httpStatus(),
                                result.uncertain(),
                                merchant,
                                operation));
    }
}
