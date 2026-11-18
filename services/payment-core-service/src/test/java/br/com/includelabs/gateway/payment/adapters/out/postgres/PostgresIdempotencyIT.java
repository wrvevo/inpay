package br.com.includelabs.gateway.payment.adapters.out.postgres;

import java.util.*;
import java.util.concurrent.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import br.com.includelabs.gateway.messaging.Outbox;
import br.com.includelabs.gateway.payment.domain.Payment;
import br.com.includelabs.gateway.payment.application.SubmitPayment;
class PostgresIdempotencyIT {
 static EmbeddedPostgres pg;static JdbcTemplate jdbc;static TransactionTemplate tx;static SubmitPayment submit;
 @BeforeAll static void start() throws Exception {
  pg=EmbeddedPostgres.builder().setPort(0).start();
  var ds=pg.getPostgresDatabase();jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  for(String migration:List.of("V1__messaging.sql","V2__payment.sql")) {
   try(InputStream in=PostgresIdempotencyIT.class.getResourceAsStream("/db/migration/"+migration)){
    for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())jdbc.execute(sql);
   }
  }
  submit=new SubmitPayment(new JdbcPaymentStore(jdbc,new Outbox(jdbc,JsonMapper.builder().findAndAddModules().build())));
 }
 @AfterAll static void stop() throws Exception {if(pg!=null)pg.close();}
 @BeforeEach void reset(){jdbc.execute("TRUNCATE payment_idempotency,operations,payments,outbox CASCADE");}
 Payment create(UUID tenant,String key,long amount){return tx.execute(s->submit.execute(tenant,key,"order",amount,"BRL","ptok_simulated_instrument_only"));}
 @Test void concurrentRequestsCreateOnePaymentAndEvent() throws Exception {
  UUID tenant=UUID.randomUUID();
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
   var futures=new ArrayList<Future<Payment>>();
   for(int i=0;i<8;i++)futures.add(pool.submit(()->create(tenant,"same-key",1000)));
   var ids=new HashSet<UUID>();for(var f:futures)ids.add(f.get(15,TimeUnit.SECONDS).id());
   assertEquals(1,ids.size());
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM payments",Integer.class));
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox",Integer.class));
  }
 }
 @Test void changedPayloadConflicts(){UUID tenant=UUID.randomUUID();create(tenant,"key",1000);assertThrows(IllegalStateException.class,()->create(tenant,"key",2000));}
 @Test void sameKeyDoesNotCrossTenants(){assertNotEquals(create(UUID.randomUUID(),"key",1000).id(),create(UUID.randomUUID(),"key",1000).id());}
 @Test void rollbackDoesNotLeavePaymentOrOutbox(){
  assertThrows(IllegalStateException.class,()->tx.execute(s->{submit.execute(UUID.randomUUID(),"key","o",100,"BRL","ptok_demo");throw new IllegalStateException("forced");}));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM payments",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM outbox",Integer.class));
 }
}
