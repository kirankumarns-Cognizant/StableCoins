package com.cognizant.stablecoins.reconciliation.flow;
import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.flow.FlowContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
/** Real PostgreSQL owner-report persistence/rollback. Source capture is stubbed;
 * full per-leg comparisons run independently in platform-flow tests and the opt-in runner. */
@Testcontainers(disabledWithoutDocker=true)
class ReconciliationPersistenceIT {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
 JdbcTemplate jdbc;TransactionTemplate tx;ReconciliationService service;ClosureEvidence checker;Execution execution;UUID executionId;
 static Path migrations(String name){for(Path base=Path.of("").toAbsolutePath();base!=null;base=base.getParent())for(Path root:List.of(base.resolve(name),base.resolve("services").resolve(name))){var path=root.resolve("src/main/resources/db/migration");if(Files.isDirectory(path))return path;}throw new IllegalStateException("MIGRATIONS_NOT_FOUND");}
 @BeforeEach void setup(){
  for(String name:List.of("payment-service","reconciliation-service"))Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("filesystem:"+migrations(name).toString().replace('\\','/')).table("it_history_"+name.replace('-','_')).baselineOnMigrate(true).baselineVersion("0").load().migrate();
  var datasource=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());jdbc=new JdbcTemplate(datasource);tx=new TransactionTemplate(new DataSourceTransactionManager(datasource));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  jdbc.execute("truncate reconciliation cascade");jdbc.execute("truncate customer cascade");UUID customer=UUID.randomUUID(),account=UUID.randomUUID(),beneficiary=UUID.randomUUID();
  jdbc.update("insert into customer(customer_id,name,customer_type,status) values (?,'Fictional reconciliation customer','CORPORATE','ACTIVE')",customer);
  jdbc.update("insert into account(account_id,customer_id,currency,account_type,balance,status) values (?,?,'USD','DEMO_SIMULATED_USD',10000,'ACTIVE')",account,customer);
  jdbc.update("insert into beneficiary(beneficiary_id,customer_id,name,country,currency,status) values (?,?,'Fictional supplier','USA','USD','ACTIVE')",beneficiary,customer);
  jdbc.update("insert into payment(payment_id,correlation_id,sender_account_id,beneficiary_id,amount,source_currency,destination_currency,payment_rail,purpose,status,idempotency_key,request_fingerprint) values ('PAY-RECON','CORR-RECON',?,?,10000,'USD','USD','AUTO','SUPPLIER_INVOICE','RECONCILIATION_PENDING',?,?)",account,beneficiary,UUID.randomUUID().toString(),"a".repeat(64));
  executionId=UUID.randomUUID();var request=new Request(UUID.randomUUID(),"PAY-RECON","CORR-RECON",customer,new BigDecimal("10000"),new BigDecimal("10000"),"0x"+"1".repeat(40),"USDC_WALLET","AUTO","DEMO",Instant.now().plusSeconds(300),Set.of("A"));execution=new Execution(executionId,UUID.randomUUID(),request.requestId(),request.paymentId(),request.correlationId(),"CONFIRMED",request,UUID.randomUUID(),Instant.now().plusSeconds(300));
  var flows=mock(FlowReadModels.class);when(flows.execution(executionId)).thenReturn(execution);checker=mock(ClosureEvidence.class);service=new ReconciliationService(jdbc,new ObjectMapper().findAndRegisterModules(),new MockEnvironment(),flows,checker);capture(List.of());
 }
 void capture(List<String> findings){var c=new ClosureEvidence.Capture(execution,null,null,null,null,List.of(),findings);when(checker.capture(execution,ControlLedgerEvidence.Stage.COMPLETE)).thenReturn(c);when(checker.fingerprint(c)).thenReturn("b".repeat(64));}
 ReconciliationService.Report run(UUID id){return tx.execute(status->service.run(id,executionId));}
 @Test void duplicateRequestReturnsOneImmutableReport(){UUID id=UUID.randomUUID();assertThat(run(id).status()).isEqualTo("MATCHED");assertThat(run(id).reconciliationId()).isEqualTo(id);assertThat(jdbc.queryForObject("select count(*) from reconciliation",Integer.class)).isEqualTo(1);}
 @Test void missingEvidenceRemainsPendingWithRecordedFinding(){capture(List.of("FEE_EVIDENCE_INCOMPLETE"));assertThat(run(UUID.randomUUID()).status()).isEqualTo("PENDING");assertThat(jdbc.queryForObject("select finding from reconciliation_detail",String.class)).isEqualTo("FEE_EVIDENCE_INCOMPLETE");}
 @Test void amountDiscrepancyIsAnAuditableMismatch(){capture(List.of("ENTRIES_BLOCKCHAIN_CONFIRMATION"));assertThat(run(UUID.randomUUID()).status()).isEqualTo("MISMATCH");}
 @Test void failedDetailWriteRollsBackItsSummary(){capture(List.of("FORCE_REJECT"));jdbc.execute("alter table reconciliation_detail add constraint it_reject check(finding<>'FORCE_REJECT')");try{assertThatThrownBy(()->run(UUID.randomUUID())).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("select count(*) from reconciliation",Integer.class)).isZero();}finally{jdbc.execute("alter table reconciliation_detail drop constraint it_reject");}}
}
