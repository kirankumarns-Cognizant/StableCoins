package com.cognizant.stablecoins.ledger.integration;

import com.cognizant.stablecoins.ledger.accounting.*;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;

/** Actual PostgreSQL constraints/locks/rollback when Docker is available. */
@DataJpaTest @ActiveProfiles("test") @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({LedgerPostingService.class,AccountingProperties.class,AccountingPolicy.class,PaymentAccountingService.class})
class LedgerServiceIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired PaymentAccountingService accounting;
    @Autowired LedgerPostingService ledger;
    @Autowired JdbcTemplate jdbc;
    PaymentAccountingService.Context c;
    final String hash="0x"+"a".repeat(64);
    @BeforeEach void context(){String id=UUID.randomUUID().toString();c=new PaymentAccountingService.Context("PAY-"+id,"CORR-"+id,new Terms(UUID.randomUUID(),UUID.randomUUID(),1,"SIM-FUNDING-"+id,
            new BigDecimal("10000.00"),new BigDecimal("9995.125000"),Outcome.USDC_WALLET,null));}
    int count(){return jdbc.queryForObject("select count(*) from ledger_journal where payment_id=?",Integer.class,c.paymentId());}
    @Test void approvedMigrationsAndAtomicConversionRoundTrip(){var result=accounting.fundAndConvert(c);assertThat(result).hasSize(3);assertThat(count()).isEqualTo(3);
        var loaded=ledger.getJournal(result.get(2).getJournalId());assertThat(loaded.getEvidence().terms()).isEqualTo(c.terms());
        assertThat(jdbc.queryForObject("select count(*) from ledger_account",Integer.class)).isEqualTo(21);}
    @Test void conversionFailureRollsBackFundingAndBothAssets(){
        jdbc.execute("alter table ledger_journal add constraint test_reject_usdc check (journal_type <> 'CONVERSION_USDC') NOT VALID");
        try{assertThatThrownBy(()->accounting.fundAndConvert(c)).isInstanceOf(RuntimeException.class);assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from ledger_entry where payment_id=?",Integer.class,c.paymentId())).isZero();}
        finally{jdbc.execute("alter table ledger_journal drop constraint test_reject_usdc");}}
    @Test void concurrentDuplicateConversionCreatesExactlyThreeJournals() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {var start=new CountDownLatch(1);var a=executor.submit(()->{await(start);accounting.fundAndConvert(c);});var b=executor.submit(()->{await(start);accounting.fundAndConvert(c);});start.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}
        assertThat(count()).isEqualTo(3);assertThat(jdbc.queryForObject("select count(*) from ledger_entry where payment_id=?",Integer.class,c.paymentId())).isEqualTo(6);
    }
    @Test void changedReplayCannotModifyPostedRows(){accounting.fundAndConvert(c);var t=c.terms();var changed=new PaymentAccountingService.Context(c.paymentId(),c.correlationId(),new Terms(t.conversionId(),t.quoteId(),t.quoteVersion(),t.fundingReference(),new BigDecimal("9999"),t.transferUsdc(),t.outcome(),null));
        assertThatThrownBy(()->accounting.fundAndConvert(changed)).hasMessageContaining("JOURNAL_IDEMPOTENCY_CONFLICT");assertThat(count()).isEqualTo(3);}
    @Test void postedEntriesRejectDirectSqlUpdatesAndDeletes(){accounting.fundAndConvert(c);
        assertThatThrownBy(()->jdbc.update("update ledger_entry set debit=1 where payment_id=?",c.paymentId())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("delete from ledger_entry where payment_id=?",c.paymentId())).isInstanceOf(RuntimeException.class);}
    @Test void postedHeaderRejectsFinancialReferenceMutation(){accounting.fundAndConvert(c);assertThatThrownBy(()->jdbc.update("update ledger_journal set reference='ALTERED' where payment_id=?",c.paymentId())).isInstanceOf(RuntimeException.class);}
    @Test void postedJournalCannotReceiveExtraLines(){var j=accounting.fundAndConvert(c).getFirst();assertThatThrownBy(()->jdbc.update("insert into ledger_entry(entry_id,journal_id,ledger_account_id,payment_id,asset,debit,credit) select gen_random_uuid(),journal_id,ledger_account_id,payment_id,asset,debit,credit from ledger_entry where journal_id=?",j.getJournalId())).isInstanceOf(RuntimeException.class);}
    @Test void reversalCreatesCompensatingEntriesAndPreservesOriginalLines(){var original=accounting.fundAndConvert(c).getFirst();var before=ledger.getEntriesForJournal(original.getJournalId());var reversal=ledger.reverseJournal(original.getJournalId());assertThat(ledger.getJournal(original.getJournalId()).getStatus()).isEqualTo("REVERSED");
        assertThat(ledger.getEntriesForJournal(original.getJournalId())).extracting(e->e.getDebit()).containsExactlyElementsOf(before.stream().map(e->e.getDebit()).toList());
        assertThat(ledger.getEntriesForJournal(reversal.getJournalId())).allMatch(e->e.getReversalOf()!=null);}
    @Test void actualWeiExpenseIsNotRoundedToSixDecimals(){accounting.fundAndConvert(c);accounting.submitted(c,hash,Mode.MOCK);var r=new PaymentAccountingService.ReceiptFee(hash,84532,123,BigInteger.valueOf(21000),BigInteger.valueOf(3),BigInteger.ONE,BigInteger.valueOf(63001),true,PaymentAccountingService.FeeSource.TRANSACTION_RECEIPT);
        var journal=accounting.networkFee(c,r,Mode.MOCK);assertThat(ledger.getEntriesForJournal(journal.getJournalId())).anyMatch(e->e.getDebit().compareTo(new BigDecimal("0.000000000000063001"))==0);}
    @Test void inrPayoutKeepsExactQuotedAmountAndSimulationMode(){var t=c.terms();c=new PaymentAccountingService.Context(c.paymentId(),c.correlationId(),new Terms(t.conversionId(),t.quoteId(),1,t.fundingReference(),t.sourceUsd(),t.transferUsdc(),Outcome.SIMULATED_BANK_INR,new BigDecimal("830000.25")));
        accounting.fundAndConvert(c);accounting.submitted(c,hash,Mode.MOCK);accounting.confirmed(c,hash,Mode.MOCK);accounting.initiatePayout(c,hash,Mode.MOCK,"SIM-VISA-1");var done=accounting.completePayout(c,hash,"SIM-VISA-1",PaymentAccountingService.PayoutStatus.SUCCEEDED);
        assertThat(ledger.getEntriesForJournal(done.getJournalId())).allMatch(e->e.getAsset().equals("INR"));assertThat(done.getEvidence().mode()).isEqualTo(Mode.SIMULATED);}
    static void await(CountDownLatch start){try{start.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
