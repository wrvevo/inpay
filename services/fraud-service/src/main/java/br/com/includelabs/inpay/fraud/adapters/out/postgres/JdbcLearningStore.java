package br.com.includelabs.inpay.fraud.adapters.out.postgres;

import br.com.includelabs.inpay.fraud.domain.port.LearningStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Contadores de chargeback por lojista e chave de dispositivo. */
@Repository
public class JdbcLearningStore implements LearningStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLearningStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Counts get(UUID merchantId, String deviceKey) {
        return jdbcTemplate
                .query(
                        "SELECT confirmed, legitimate FROM chargeback_lessons WHERE merchant_id=? AND device_key=?",
                        (rs, row) -> new Counts(rs.getInt("confirmed"), rs.getInt("legitimate")),
                        merchantId,
                        deviceKey)
                .stream()
                .findFirst()
                .orElse(new Counts(0, 0));
    }

    public void record(UUID merchantId, String deviceKey, boolean confirmedFraud) {
        jdbcTemplate.update(
                "INSERT INTO chargeback_lessons(merchant_id,device_key,confirmed,legitimate) VALUES(?,?,?,?)"
                        + " ON CONFLICT(merchant_id,device_key) DO UPDATE SET"
                        + " confirmed = chargeback_lessons.confirmed + EXCLUDED.confirmed,"
                        + " legitimate = chargeback_lessons.legitimate + EXCLUDED.legitimate",
                merchantId,
                deviceKey,
                confirmedFraud ? 1 : 0,
                confirmedFraud ? 0 : 1);
    }
}
