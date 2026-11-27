package com.cognizant.stablecoins.reconciliation.besu;

import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL report ownership/atomicity/immutability; owner-source capture is stubbed.
 * Does not constitute live chain, source-reader or full payment closure acceptance. */
@Testcontainers(disabledWithoutDocker=true)
class BesuReconciliationPersistenceIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    JdbcTemplate jdbc;TransactionTemplate tx;BesuReconciliationService service;BesuClosureEvidence checker;
    SettlementBinding.Execution e;ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    static Path migrations(){for(Path base=Path.of("").toAbsolutePath();base!=null;base=base.getParent())for(Path root:List.of(base.resolve("reconciliation-service"),base.resolve("services/reconciliation-service"))){var path=root.resolve("src/main/resources/db/migration");if(Files.isDirectory(path))return path;}throw new IllegalStateException("MIGRATIONS_NOT_FOUND");}
    @BeforeEach void setup(){
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("filesystem:"+migrations().toString().replace('\\','/')).load().migrate();
        var ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        jdbc.execute("truncate reconciliation cascade");e=execution();checker=mock(BesuClosureEvidence.class);
        service=new BesuReconciliationService(jdbc,mapper,checker,mock(SettlementExecutionReads.class));comparison("MATCHED",List.of());
    }
    static SettlementBinding.Execution execution(){
        var chain=new SettlementNetwork.Chain("besu-local",20261125,"0x"+"1".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO);
        var context=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,"0x"+"2".repeat(40),"DEMOUSD",6,"0x"+"3".repeat(64)),new SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"4".repeat(40),SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-v1");
        var now=Instant.parse("2026-11-26T10:00:00Z");
        var i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),"PAY-BESU","CORR-BESU",context,"0x"+"5".repeat(40),BigDecimal.TEN,BigDecimal.TEN,SettlementBinding.Outcome.TOKEN_WALLET,BigDecimal.ONE,now.plusSeconds(3600));
        return new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,"PRIVATE-PROVIDER",i,BigDecimal.ONE,BigDecimal.ZERO,now,now.plusSeconds(900)));
    }
    void comparison(String status,List<String> findings){when(checker.compare(any())).thenReturn(new BesuClosureEvidence.Comparison(BesuClosureEvidence.KIND,BesuClosureEvidence.GATE,new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"),mapper.createObjectNode(),findings,status));}
    com.cognizant.stablecoins.reconciliation.flow.ReconciliationService.Report run(UUID id){return tx.execute(t->service.run(id,e));}
    @Test void reportReplayKeepsOneOriginalRecord(){var id=UUID.randomUUID();assertThat(run(id).status()).isEqualTo("MATCHED");assertThat(run(id).reconciliationId()).isEqualTo(id);assertThat(jdbc.queryForObject("select count(*) from reconciliation",Integer.class)).isOne();}
    @Test void mismatchPersistsItsExactFinding(){comparison("MISMATCH",List.of("TRANSFER_REVERTED_REQUIRES_REVIEW"));assertThat(run(UUID.randomUUID()).status()).isEqualTo("MISMATCH");assertThat(jdbc.queryForObject("select finding from reconciliation_detail",String.class)).isEqualTo("TRANSFER_REVERTED_REQUIRES_REVIEW");}
    @Test void summaryAndDetailsRollbackTogether(){comparison("MISMATCH",List.of("FORCE_REJECT"));jdbc.execute("alter table reconciliation_detail add constraint it_besu_reject check(finding<>'FORCE_REJECT')");try{assertThatThrownBy(()->run(UUID.randomUUID())).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("select count(*) from reconciliation",Integer.class)).isZero();}finally{jdbc.execute("alter table reconciliation_detail drop constraint it_besu_reject");}}
    @Test void privateReportCannotBeEditedOrDeleted(){var id=UUID.randomUUID();run(id);assertThatThrownBy(()->jdbc.update("update reconciliation set status='MISMATCH' where reconciliation_id=?",id)).hasMessageContaining("BESU_RECONCILIATION_IMMUTABLE");assertThatThrownBy(()->jdbc.update("delete from reconciliation where reconciliation_id=?",id)).hasMessageContaining("BESU_RECONCILIATION_IMMUTABLE");}
    @Test void privateFindingCannotBeEditedDeletedOrInvented(){comparison("PENDING",List.of("MISSING_BESU_RECEIPT"));var id=UUID.randomUUID();run(id);assertThatThrownBy(()->jdbc.update("update reconciliation_detail set finding='FORGED' where reconciliation_id=?",id)).hasMessageContaining("BESU_RECONCILIATION_IMMUTABLE");assertThatThrownBy(()->jdbc.update("delete from reconciliation_detail where reconciliation_id=?",id)).hasMessageContaining("BESU_RECONCILIATION_IMMUTABLE");assertThatThrownBy(()->jdbc.update("insert into reconciliation_detail(reconciliation_id,finding) values (?,'FORGED')",id)).hasMessageContaining("BESU_RECONCILIATION_DETAIL_MISMATCH");}
    @Test void privateReportRequiresMatchingExecutionProjection(){var id=UUID.randomUUID();run(id);assertThatThrownBy(()->jdbc.update("insert into reconciliation(reconciliation_id,execution_id,payment_id,correlation_id,status,source_hash,document) select ?,? ,payment_id,correlation_id,status,source_hash,document from reconciliation where reconciliation_id=?",UUID.randomUUID(),UUID.randomUUID(),id)).hasMessageContaining("BESU_RECONCILIATION_BINDING_REQUIRED");}
    @Test void concurrentSameKeyCanRecoverWithoutDuplicateReport()throws Exception{
        var id=UUID.randomUUID();var barrier=new CyclicBarrier(2);
        when(checker.capture(e)).thenAnswer(call->{barrier.await(10,TimeUnit.SECONDS);return null;});
        var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Boolean> task=()->{try{run(id);return true;}catch(org.springframework.dao.ConcurrencyFailureException retryable){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).contains(true);
            // A serialization conflict must retry the same immutable key; no automatic chain resubmission.
            assertThat(run(id).reconciliationId()).isEqualTo(id);
            assertThat(jdbc.queryForObject("select count(*) from reconciliation",Integer.class)).isOne();
        }finally{pool.shutdownNow();}
    }
}
