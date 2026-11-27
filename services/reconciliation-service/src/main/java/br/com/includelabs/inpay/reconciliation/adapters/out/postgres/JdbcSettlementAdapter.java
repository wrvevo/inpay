package br.com.includelabs.inpay.reconciliation.adapters.out.postgres;

import br.com.includelabs.inpay.reconciliation.domain.ReconciliationCase;
import br.com.includelabs.inpay.reconciliation.domain.port.SettlementPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Compares imported settlement records against the expected ledger without moving funds. */
@Component
public final class JdbcSettlementAdapter implements SettlementPort {
    private static final String RECONCILE =
            """
            WITH selected AS (
                SELECT source, reference, amount_minor, currency
                FROM reconciliation_entries
                WHERE merchant_id = :merchant AND provider = :provider
                    AND business_date = :businessDate
            ), expected AS (
                SELECT * FROM selected WHERE source = 'EXPECTED'
            ), settled AS (
                SELECT * FROM selected WHERE source = 'SETTLED'
            ), totals AS (
                SELECT count(*) AS reference_count,
                       count(*) FILTER (WHERE e.reference IS NULL OR s.reference IS NULL
                           OR e.amount_minor <> s.amount_minor OR e.currency <> s.currency)
                           AS difference_count
                FROM expected e FULL JOIN settled s USING (reference)
            )
            INSERT INTO reconciliation_cases
                (id, merchant_id, provider, business_date, status, reference_count, difference_count)
            SELECT :id, :merchant, :provider, :businessDate,
                   CASE WHEN reference_count = 0 THEN 'NO_DATA'
                        WHEN difference_count = 0 THEN 'MATCHED'
                        ELSE 'DIFFERENCES' END,
                   reference_count, difference_count
            FROM totals
            ON CONFLICT (merchant_id, provider, business_date)
            DO UPDATE SET status = EXCLUDED.status, reference_count = EXCLUDED.reference_count,
                          difference_count = EXCLUDED.difference_count, updated_at = now()
            RETURNING id, merchant_id, provider, status
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final String provider;
    private final ZoneId businessZone;
    private final int lookbackDays;

    public JdbcSettlementAdapter(
            NamedParameterJdbcTemplate jdbc,
            @Value("${inpay.reconciliation.provider}") String provider,
            @Value("${inpay.reconciliation.business-zone}") String businessZone,
            @Value("${inpay.reconciliation.lookback-days}") int lookbackDays) {
        if (provider.isBlank() || provider.length() > 64 || lookbackDays < 0) {
            throw new IllegalArgumentException("Invalid reconciliation configuration");
        }
        this.jdbc = jdbc;
        this.provider = provider;
        this.businessZone = ZoneId.of(businessZone);
        this.lookbackDays = lookbackDays;
    }

    @Override
    public ReconciliationCase reconcile(UUID merchantId) {
        return jdbc.queryForObject(
                RECONCILE,
                Map.of(
                        "id",
                        UUID.randomUUID(),
                        "merchant",
                        merchantId,
                        "provider",
                        provider,
                        "businessDate",
                        LocalDate.now(businessZone).minusDays(lookbackDays)),
                (row, index) ->
                        new ReconciliationCase(
                                row.getObject("id", UUID.class),
                                row.getObject("merchant_id", UUID.class),
                                row.getString("provider"),
                                row.getString("status")));
    }
}
