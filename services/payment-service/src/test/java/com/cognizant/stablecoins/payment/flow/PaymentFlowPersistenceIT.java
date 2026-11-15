package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.RecommendationService;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.support.IntentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static org.assertj.core.api.Assertions.*;
/** Actual owner migrations and payment transaction boundaries. No model, AWS or chain RPC. */
@DataJpaTest(properties={"app.flow.enabled=true","app.flow.execution-enabled=true"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true) @Import({PaymentFlowService.class,OwnerEvidenceAssembler.class,PaymentFlowPersistenceIT.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PaymentFlowPersistenceIT {
 @org.springframework.boot.test.context.TestConfiguration static class Config {
  @org.springframework.context.annotation.Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
 }
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 @Autowired PaymentFlowService flow;@Autowired EntityManager em;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
 @MockBean RecommendationService approvals;
 final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();UUID executionId,preparationId,txId;String paymentId;UUID accountId;
 static Path migrations(String service){for(Path root=Path.of("").toAbsolutePath();root!=null;root=root.getParent())for(Path candidate:List.of(root.resolve(service),root.resolve("services").resolve(service))){var folder=candidate.resolve("src/main/resources/db/migration");if(Files.isDirectory(folder))return folder;}throw new IllegalStateException("OWNER_MIGRATIONS_NOT_FOUND");}
 @BeforeEach void setup(){
  for(String service:List.of("compliance-quote-service","blockchain-service","settlement-ledger-service"))Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("filesystem:"+migrations(service).toString().replace('\\','/')).table("it_history_"+service.replace('-','_')).baselineOnMigrate(true).baselineVersion("0").load().migrate();
  new TransactionTemplate(transactions).executeWithoutResult(status->{
   jdbc.execute("truncate table blockchain_transaction,blockchain_wallet,compliance_flow_quote,compliance_execution_validation,blockchain_flow_observation,ledger_journal cascade");jdbc.execute("truncate table customer cascade");jdbc.execute("truncate table payment_outbox_event");
   var c=new Customer();c.setName("Fictional flow customer");c.setCustomerType("CORPORATE");em.persist(c);em.flush();
   var a=IntentFixtures.account();a.setCustomerId(c.getCustomerId());a.setAccountType("OPERATING");a.setBalance(new BigDecimal("20000"));em.persist(a);
   var b=IntentFixtures.beneficiary();b.setCustomerId(c.getCustomerId());b.setName("Fictional supplier");b.setCountry("USA");b.setCurrency("USD");em.persist(b);em.flush();accountId=a.getAccountId();
   var p=new Payment();paymentId="PAY-"+UUID.randomUUID();p.setPaymentId(paymentId);p.setCorrelationId("CORR-"+UUID.randomUUID());p.setSenderAccountId(a.getAccountId());p.setBeneficiaryId(b.getBeneficiaryId());p.setAmount(new BigDecimal("10000"));p.setSourceCurrency("USD");p.setDestinationCurrency("USD");p.setPaymentRail("AUTO");p.setPurpose("SUPPLIER_INVOICE");p.setStatus("COMPLIANCE_APPROVED");p.setIdempotencyKey(UUID.randomUUID().toString());p.setRequestFingerprint("a".repeat(64));
   var now=Instant.now();p.setIntent(new PaymentIntentSnapshot(UUID.randomUUID(),1,c.getCustomerId(),"creator",now,SettlementAsset.USDC,true,true,IntentFixtures.intent(BeneficiaryOutcome.USDC_WALLET),IntentFixtures.policy("v1")));em.persist(p);em.flush();
   executionId=UUID.randomUUID();preparationId=UUID.randomUUID();txId=UUID.randomUUID();UUID recommendationId=UUID.randomUUID();
   var request=new Request(preparationId,paymentId,p.getCorrelationId(),c.getCustomerId(),p.getAmount(),p.getAmount(),b.getWalletAddress(),"USDC_WALLET","AUTO","USD-USDC-WALLET",IntentFixtures.DEADLINE,p.getIntent().policy().allowedProviderIds());
   var document=Map.of("intent",p.getIntent(),"instruction",Map.of("recipientWallet",request.recipientWallet()),"evidence",Map.of("snapshotId",preparationId));
   jdbc.update("insert into routing_recommendation(recommendation_id,payment_id,correlation_id,customer_id,version,required_approvals,state,document,created_at,expires_at) values (?,?,?,?,1,2,'APPROVED',cast(? as jsonb),now(),now()+interval '5 minutes')",recommendationId,paymentId,p.getCorrelationId(),c.getCustomerId(),FlowJson.json(mapper,document));
   jdbc.update("insert into payment_flow_preparation(request_id,payment_id,correlation_id,actor_id,request_document,status,recommendation_id) values (?,?,?,'operator',cast(? as jsonb),'READY',?)",preparationId,paymentId,p.getCorrelationId(),FlowJson.json(mapper,request),recommendationId);
   jdbc.update("insert into payment_execution(execution_id,payment_id,correlation_id,recommendation_id,preparation_id,actor_id,request_document,status,expires_at) values (?,?,?,?,?,'operator',cast(? as jsonb),'PREPARING',now()+interval '5 minutes')",executionId,paymentId,p.getCorrelationId(),recommendationId,preparationId,FlowJson.json(mapper,request));
   var quote=new Quote(preparationId,UUID.randomUUID(),1,"SIMULATED-PROVIDER-A","v1",request.sourceUsd(),request.transferUsdc(),BigDecimal.ONE,990,1000,120000,BigDecimal.TEN,true,now,now.plusSeconds(300));
   jdbc.update("update routing_recommendation set document=jsonb_set(document,'{quote}',cast(? as jsonb)) where recommendation_id=?",FlowJson.json(mapper,Map.of("quoteId",quote.quoteId())),recommendationId);
   jdbc.update("insert into compliance_flow_quote(request_id,provider_id,request_fingerprint,status,document) values (?,'SIMULATED-PROVIDER-A',?,'READY',cast(? as jsonb))",preparationId,FlowJson.fingerprint(mapper,request),FlowJson.json(mapper,quote));
   jdbc.update("insert into compliance_execution_validation(execution_id,status,quote_id) values (?,'VALIDATED',?)",executionId,quote.quoteId());
   String from="0x"+"2".repeat(40);
   var treasury=new Treasury("MOCK","base-sepolia",84532,"0x036CbD53842c5426634e7929541eC2318f3dCF7e",from,new BigDecimal("20000"),BigDecimal.ZERO,new BigDecimal("20000"),BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("65000"),new BigDecimal("78000"),new BigDecimal("1000000000"),new BigDecimal("0.0002"),now,now.plusSeconds(300),List.of());
   jdbc.update("insert into blockchain_flow_observation(request_id,request_fingerprint,status,document) values (?,?,'READY',cast(? as jsonb))",preparationId,FlowJson.fingerprint(mapper,request),FlowJson.json(mapper,treasury));
   jdbc.update("insert into blockchain_wallet(wallet_id,wallet_address,network) values (?,?,'base-sepolia')",UUID.randomUUID(),from);
   jdbc.update("insert into blockchain_transaction(transaction_id,payment_id,correlation_id,network,from_address,to_address,token_contract,token_symbol,amount,status,chain_id,gas_limit,gas_price_wei,gas_reserve_eth,evidence_mode,reservation_status,reservation_expires_at) values (?,?,?,'base-sepolia',?,?,?,'USDC',?,'PREPARED',84532,78000,1000000000,0.0001,'MOCK','HELD',now()+interval '5 minutes')",txId,paymentId,p.getCorrelationId(),from,request.recipientWallet(),"0x036CbD53842c5426634e7929541eC2318f3dCF7e",request.transferUsdc());
  });
 }
 @Test void concurrentContinuationDebitsAndPublishesExactlyOnce() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();for(int n=0;n<2;n++)tasks.add(pool.submit(()->{try{start.await();flow.advance(executionId);}catch(InterruptedException e){throw new RuntimeException(e);}}));start.countDown();for(var task:tasks)task.get(30,TimeUnit.SECONDS);}
  assertThat(jdbc.queryForObject("select balance from account where account_id=?",BigDecimal.class,accountId)).isEqualByComparingTo("10000");assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event where event_type='StablecoinFundingCompleted' and aggregate_id=?",Integer.class,paymentId)).isEqualTo(1);assertThat(jdbc.queryForObject("select status from payment_execution where execution_id=?",String.class,executionId)).isEqualTo("AWAITING_LEDGER");
 }
 @Test void outboxFailureRollsBackDebitStateAndHistory(){jdbc.execute("alter table payment_outbox_event add constraint it_reject_funding check(event_type<>'StablecoinFundingCompleted')");try{assertThatThrownBy(()->flow.advance(executionId)).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("select balance from account where account_id=?",BigDecimal.class,accountId)).isEqualByComparingTo("20000");assertThat(jdbc.queryForObject("select status from payment where payment_id=?",String.class,paymentId)).isEqualTo("COMPLIANCE_APPROVED");assertThat(jdbc.queryForObject("select count(*) from payment_status_history where payment_id=?",Integer.class,paymentId)).isZero();}finally{jdbc.execute("alter table payment_outbox_event drop constraint it_reject_funding");}}
 @Test void actualOwnerSchemaDoesNotAcknowledgeUnpostedFunding(){flow.advance(executionId);flow.advance(executionId);assertThat(jdbc.queryForObject("select count(*) from payment_outbox_event where event_type='BlockchainSubmissionRequested' and aggregate_id=?",Integer.class,paymentId)).isZero();}
}
