package br.com.includelabs.inpay.payment.adapters.out.postgres;

import static org.junit.jupiter.api.Assertions.*;

import br.com.includelabs.inpay.messaging.Outbox;
import br.com.includelabs.inpay.messaging.OutboxPublisher;
import br.com.includelabs.inpay.payment.application.SubmitPayment;
import br.com.includelabs.inpay.payment.domain.Payment;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class PostgresIdempotencyIT {

    static EmbeddedPostgres pg;

    static JdbcTemplate jdbc;

    static TransactionTemplate tx;

    static SubmitPayment submit;

    @BeforeAll
    static void start() throws Exception {
        pg = EmbeddedPostgres.builder().setPort(0).start();
        var ds = pg.getPostgresDatabase();
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        for (String migration : List.of("V1__messaging.sql", "V2__payment.sql", "V5__outbox_lease.sql")) {
            try (InputStream in =
                    PostgresIdempotencyIT.class.getResourceAsStream("/db/migration/" + migration)) {
                for (String sql :
                        new String(
                                        Objects.requireNonNull(in).readAllBytes(),
                                        StandardCharsets.UTF_8)
                                .split(";")) {
                    if (!sql.isBlank()) {
                        jdbc.execute(sql);
                    }
                }
            }
        }
        submit =
                new SubmitPayment(
                        new JdbcPaymentStore(
                                jdbc,
                                new Outbox(
                                        jdbc, JsonMapper.builder().findAndAddModules().build())));
    }

    @AfterAll
    static void stop() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE payment_idempotency,operations,payments,outbox CASCADE");
    }

    Payment create(UUID tenant, String key, long amount) {
        return tx.execute(
                s ->
                        submit.execute(
                                tenant,
                                key,
                                "order",
                                amount,
                                "BRL",
                                "ptok_simulated_instrument_only"));
    }

    @Test
    void concurrentRequestsCreateOnePaymentAndEvent() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Payment>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> create(tenant, "same-key", 1000)));
            }
            var ids = new HashSet<UUID>();
            for (var f : futures) {
                ids.add(f.get(15, TimeUnit.SECONDS).id());
            }
            assertEquals(1, ids.size());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class));
        }
    }

    @Test
    void changedPayloadConflicts() {
        UUID tenant = UUID.randomUUID();
        create(tenant, "key", 1000);
        assertThrows(IllegalStateException.class, () -> create(tenant, "key", 2000));
    }

    @Test
    void sameKeyDoesNotCrossTenants() {
        assertNotEquals(
                create(UUID.randomUUID(), "key", 1000).id(),
                create(UUID.randomUUID(), "key", 1000).id());
    }

    @Test
    void rollbackDoesNotLeavePaymentOrOutbox() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                s -> {
                                    submit.execute(
                                            UUID.randomUUID(), "key", "o", 100, "BRL", "ptok_demo");
                                    throw new IllegalStateException("forced");
                                }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox", Integer.class));
    }

    /** Confirma que o envio Kafka acontece fora da transação e mantém a ordem por chave. */
    @Test
    @SuppressWarnings("unchecked")
    void outboxPublishesOutsideTransactionAndLeasesHeadOfEachKey() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload,created_at)"
                        + " VALUES (?, 'payments', 'same-key', '{}', now() - interval '1 second')",
                first);
        jdbc.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload)"
                        + " VALUES (?, 'payments', 'same-key', '{}')",
                second);
        KafkaTemplate<String, String> kafka = org.mockito.Mockito.mock(KafkaTemplate.class);
        org.mockito.Mockito.when(kafka.send("payments", "same-key", "{}"))
                .thenAnswer(
                        call -> {
                            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                            return CompletableFuture.completedFuture(null);
                        });
        OutboxPublisher publisher = new OutboxPublisher(jdbc, kafka, tx);
        publisher.publish();
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL AND event_id=?",
                Integer.class, first));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL AND event_id=?",
                Integer.class, second));
        publisher.publish();
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class));
    }

    /** Uma confirmação perdida mantém o evento recuperável e não avança a mesma chave. */
    @Test
    @SuppressWarnings("unchecked")
    void outboxRetriesExpiredLeaseWithoutMarkingUnconfirmedEvent() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO outbox(event_id,topic,partition_key,payload)"
                        + " VALUES (?, 'payments', 'key', '{}')",
                id);
        KafkaTemplate<String, String> kafka = org.mockito.Mockito.mock(KafkaTemplate.class);
        org.mockito.Mockito.when(kafka.send("payments", "key", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("test")))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxPublisher publisher = new OutboxPublisher(jdbc, kafka, tx);
        publisher.publish();
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class));
        publisher.publish();
        org.mockito.Mockito.verify(kafka, org.mockito.Mockito.times(1))
                .send("payments", "key", "{}");
        jdbc.update("UPDATE outbox SET lease_until=now() - interval '1 second' WHERE event_id=?", id);
        publisher.publish();
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE published_at IS NOT NULL", Integer.class));
    }
}
