package com.cognizant.stablecoins.ledger.integration;

import com.cognizant.stablecoins.flow.SettlementBinding;
import com.cognizant.stablecoins.ledger.accounting.*;
import com.cognizant.stablecoins.ledger.besu.*;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingTestData.*;

/** Real ledger migrations/JPA/JDBC/locks. Payment and chain owner reads are mocked here;
 * full cross-owner/live-chain acceptance remains B08-B10. */
@DataJpaTest(properties="app.besu.accounting.enabled=true") @ActiveProfiles("test")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({LedgerPostingService.class,AccountingProperties.class,AccountingPolicy.class,BesuAccountingPolicy.class,BesuAccountingService.class})
class BesuAccountingPersistenceIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired BesuAccountingService accounting;
    @Autowired LedgerPostingService ledger;
    @Autowired JdbcTemplate jdbc;
    @MockBean BesuAccountingStore owners;
    SettlementBinding.Execution e;
    @BeforeEach void setup() {
        reset(owners);e=execution("PAY-"+UUID.randomUUID());var actual=new BesuAccountingStore(jdbc);
        when(owners.submission(e)).thenReturn(new BesuAccountingStore.Submission(HASH,"CONFIRMED",BigInteger.valueOf(25000),BigInteger.valueOf(3)));
        when(owners.receipt(eq(e),any())).thenReturn(receipt(true,3));
        when(owners.acknowledgement(e.executionId())).thenAnswer(i->actual.acknowledgement(e.executionId()));
        doAnswer(i->{actual.acknowledge(i.getArgument(0),i.getArgument(1));return null;}).when(owners).acknowledge(eq(e),any());
    }
    void submitted(){accounting.fundAndAllocate(e,"SIM-PG-1");accounting.submitted(e);}
    int journals(){return jdbc.queryForObject("select count(*) from ledger_journal where payment_id=?",Integer.class,e.instruction().paymentId());}
    int acknowledgements(){return jdbc.queryForObject("select count(*) from besu_ledger_receipt where execution_id=?",Integer.class,e.executionId());}
    @Test void allMigrationsAndPrivateJsonEvidenceRoundTrip() {
        submitted();var result=accounting.resolve(e);assertThat(journals()).isEqualTo(6);assertThat(acknowledgements()).isOne();
        assertThat(accounting.resolve(e)).isEqualTo(result);
        assertThat(ledger.getJournal(result.confirmationJournal()).getEvidence().privateSettlement()).isEqualTo(result.evidence());
        assertThat(jdbc.queryForObject("select count(*) from ledger_account where asset in ('DEMOUSD','BESU_GAS')",Integer.class)).isEqualTo(6);
    }
    @Test void allocationFailureRollsBackEveryJournal() {
        jdbc.execute("alter table ledger_journal add constraint reject_demo_allocation check(journal_type<>'CONVERSION_DEMOUSD') NOT VALID");
        try {assertThatThrownBy(()->accounting.fundAndAllocate(e,"SIM-PG-1")).isInstanceOf(RuntimeException.class);assertThat(journals()).isZero();}
        finally {jdbc.execute("alter table ledger_journal drop constraint reject_demo_allocation");}
    }
    @Test void acknowledgementFailureRollsBackConfirmationAndFeeTogether() {
        submitted();jdbc.execute("alter table besu_ledger_receipt add constraint reject_ack check(false) NOT VALID");
        try {assertThatThrownBy(()->accounting.resolve(e)).isInstanceOf(RuntimeException.class);assertThat(journals()).isEqualTo(4);assertThat(acknowledgements()).isZero();}
        finally {jdbc.execute("alter table besu_ledger_receipt drop constraint reject_ack");}
        assertThat(accounting.resolve(e).feeJournal()).isNotNull();assertThat(journals()).isEqualTo(6);
    }
    @Test void concurrentReceiptReplayPostsOnce() throws Exception {
        submitted();try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);var a=pool.submit(()->{await(start);return accounting.resolve(e);});var b=pool.submit(()->{await(start);return accounting.resolve(e);});
            start.countDown();assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(journals()).isEqualTo(6);assertThat(acknowledgements()).isOne();
    }
    @Test void revertedReceiptPostsOnlyActualFee() {
        submitted();when(owners.receipt(eq(e),any())).thenReturn(receipt(false,3));var r=accounting.resolve(e);
        assertThat(r.confirmationJournal()).isNull();assertThat(r.feeJournal()).isNotNull();assertThat(journals()).isEqualTo(5);assertThat(acknowledgements()).isOne();
    }
    @Test void zeroFeeRevertHasDurableAcknowledgementWithoutFinancialJournal() {
        submitted();when(owners.receipt(eq(e),any())).thenReturn(receipt(false,0));var r=accounting.resolve(e);
        assertThat(r.confirmationJournal()).isNull();assertThat(r.feeJournal()).isNull();assertThat(journals()).isEqualTo(4);assertThat(acknowledgements()).isOne();
        assertThat(accounting.resolve(e)).isEqualTo(r);
    }
    @Test void receiptAcknowledgementRejectsSqlMutation() {
        submitted();accounting.resolve(e);
        assertThatThrownBy(()->jdbc.update("delete from besu_ledger_receipt where execution_id=?",e.executionId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("update besu_ledger_receipt set correlation_id='OTHER' where execution_id=?",e.executionId())).isInstanceOf(RuntimeException.class);
    }
    @Test void directReceiptAcknowledgementCannotOmitRequiredJournals() {
        submitted();var evidence=BesuAccountingEvidence.allocation(e,"SIM-PG-1").withReceipt(receipt(true,3));
        assertThatThrownBy(()->new BesuAccountingStore(jdbc).acknowledge(e,new BesuAccountingStore.Acknowledgement(evidence,null,null)))
            .isInstanceOf(RuntimeException.class);assertThat(acknowledgements()).isZero();
    }
    static void await(CountDownLatch start) {try{start.await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
    // Minimal foreign-owner storage fixtures exercise the ledger guard, not cross-service migrations or authorization.
    BesuCompensationService recovery(){
        submitted();when(owners.receipt(eq(e),any())).thenReturn(receipt(false,3));accounting.resolve(e);
        jdbc.execute("create table if not exists payment(payment_id varchar(64) primary key,status varchar(30))");
        jdbc.execute("create table if not exists payment_besu_failure(execution_id uuid primary key,source_hash varchar(64),tx_hash varchar(66))");
        jdbc.execute("create table if not exists payment_besu_recovery_authorization(execution_id uuid primary key,payment_id varchar(64),correlation_id varchar(64),execution_document jsonb,execution_fingerprint varchar(64),source_hash varchar(64),actor_id varchar(255),action varchar(64))");
        var hash="a".repeat(64);var doc=com.cognizant.stablecoins.flow.SettlementBindingJson.write(e);var fp=com.cognizant.stablecoins.flow.SettlementBindingJson.fingerprint(e);
        jdbc.update("insert into payment values (?,'FAILED')",e.instruction().paymentId());
        jdbc.update("insert into payment_besu_failure values (?,?,?)",e.executionId(),hash,HASH);
        jdbc.update("insert into payment_besu_recovery_authorization values (?,?,?,?::jsonb,?,?,?,'RELEASE_REVERTED_TOKEN_IN_FLIGHT')",e.executionId(),e.instruction().paymentId(),e.instruction().correlationId(),doc,fp,hash,"approver");
        var original=ledger.getJournalsByPaymentId(e.instruction().paymentId()).stream().filter(j->"BLOCKCHAIN_SUBMISSION".equals(j.getJournalType())).findFirst().orElseThrow();
        var reads=mock(com.cognizant.stablecoins.flow.BesuRevertEvidence.class);
        when(reads.current(e)).thenAnswer(i->new com.cognizant.stablecoins.flow.BesuRevertEvidence.Proof(hash,null,
            new com.cognizant.stablecoins.flow.LifecycleReadModels.Journal(original.getJournalId(),original.getPaymentId(),original.getCorrelationId(),original.getJournalType(),original.getStatus(),original.getReference(),null,List.of()),
            jdbc.queryForObject("select count(*) from besu_ledger_compensation where execution_id=?",Integer.class,e.executionId())>0));
        when(reads.authorization(eq(e),any())).thenReturn(new com.cognizant.stablecoins.flow.BesuRevertEvidence.Authorization("approver",hash));
        return new BesuCompensationService(ledger,new BesuAccountingPolicy(accounts),reads,new BesuCompensationStore(jdbc));
    }
    @Autowired com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository accounts;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    BesuCompensationStore.Acknowledgement recover(BesuCompensationService service){
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(s->service.compensate(e));
    }
    @Test void compensationCommitsOnceWithImmutableAckAndOriginalsUntouched(){
        var service=recovery();var first=recover(service);assertThat(recover(service)).isEqualTo(first);assertThat(journals()).isEqualTo(6);
        assertThat(ledger.getJournal(first.originalJournal()).getStatus()).isEqualTo("POSTED");
        assertThatThrownBy(()->jdbc.update("delete from besu_ledger_compensation where execution_id=?",e.executionId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("update besu_ledger_compensation set actor_id='other' where execution_id=?",e.executionId())).isInstanceOf(RuntimeException.class);
    }
    @Test void compensationAckFailureRollsBackTheReleaseJournal(){
        var service=recovery();jdbc.execute("alter table besu_ledger_compensation add constraint reject_release_ack check(false) NOT VALID");
        try{assertThatThrownBy(()->recover(service)).isInstanceOf(RuntimeException.class);assertThat(journals()).isEqualTo(5);}
        finally{jdbc.execute("alter table besu_ledger_compensation drop constraint reject_release_ack");}
        assertThat(recover(service).releaseJournal()).isNotNull();assertThat(journals()).isEqualTo(6);
    }
    @Test void directJournalApiCannotCommitReleaseWithoutAuthorizedAck(){
        recovery();var original=ledger.getJournalsByPaymentId(e.instruction().paymentId()).stream().filter(j->"BLOCKCHAIN_SUBMISSION".equals(j.getJournalType())).findFirst().orElseThrow();
        var request=new com.cognizant.stablecoins.ledger.api.dto.PostJournalRequest();request.setPaymentId(e.instruction().paymentId());request.setCorrelationId(e.instruction().correlationId());
        request.setJournalType(com.cognizant.stablecoins.flow.BesuRevertEvidence.RELEASE);request.setReference(com.cognizant.stablecoins.flow.BesuRevertEvidence.reference(original.getJournalId()));request.setEvidence(original.getEvidence());
        var policy=new BesuAccountingPolicy(accounts);request.setEntries(List.of(policy.line(BesuAccountingPolicy.Role.TREASURY,e.instruction().tokenAmount(),true),policy.line(BesuAccountingPolicy.Role.TRANSIT,e.instruction().tokenAmount(),false)));
        assertThatThrownBy(()->ledger.postJournal(request)).isInstanceOf(RuntimeException.class);assertThat(journals()).isEqualTo(5);
    }

}
