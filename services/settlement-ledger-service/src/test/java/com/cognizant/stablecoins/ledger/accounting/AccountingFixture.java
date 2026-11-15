package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.ledger.model.*;
import com.cognizant.stablecoins.ledger.repository.*;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;

class AccountingFixture {
    final JournalRepository journals=mock(JournalRepository.class);
    final LedgerEntryRepository entries=mock(LedgerEntryRepository.class);
    final LedgerAccountRepository accounts=mock(LedgerAccountRepository.class);
    final LedgerPaymentLockRepository locks=mock(LedgerPaymentLockRepository.class);
    final Map<UUID,Journal> saved=new LinkedHashMap<>();
    final List<LedgerEntry> lines=new ArrayList<>();
    final Map<String,LedgerAccount> chart=new HashMap<>();
    final AccountingProperties properties=new AccountingProperties();
    final AccountingPolicy policy=new AccountingPolicy(properties,accounts);
    final LedgerPostingService ledger=new LedgerPostingService(journals,entries,accounts,locks);
    final PaymentAccountingService service=new PaymentAccountingService(ledger,policy);
    final String hash="0x"+"a".repeat(64);
    AccountingFixture() {
        for(var role:AccountingProperties.Role.values()) { var account=new LedgerAccount();
            account.setLedgerAccountId(UUID.randomUUID()); account.setAccountCode(role.defaultCode);
            account.setAsset(role.asset); account.setAccountType(role.type); account.setActive(true); chart.put(role.defaultCode,account); }
        when(accounts.findByAccountCode(anyString())).thenAnswer(i->Optional.ofNullable(chart.get(i.getArgument(0))));
        when(journals.saveAndFlush(any())).thenAnswer(i->{ Journal j=i.getArgument(0); saved.put(j.getJournalId(),j); return j; });
        when(entries.save(any())).thenAnswer(i->{ LedgerEntry e=i.getArgument(0); lines.add(e); return e; });
        when(journals.findByPaymentIdAndJournalType(anyString(),anyString())).thenAnswer(i->saved.values().stream()
                .filter(j->j.getPaymentId().equals(i.getArgument(0)) && j.getJournalType().equals(i.getArgument(1))).findFirst());
        when(journals.findByPaymentIdOrderByCreatedAtAsc(anyString())).thenAnswer(i->saved.values().stream().filter(j->j.getPaymentId().equals(i.getArgument(0))).toList());
        when(entries.findByJournalIdOrderByCreatedAtAsc(any())).thenAnswer(i->lines.stream().filter(e->e.getJournalId().equals(i.getArgument(0))).toList());
        when(journals.findById(any())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(0))));
        when(journals.lockById(any())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(0))));
        when(journals.findScope(any())).thenAnswer(i->Optional.ofNullable(saved.get(i.getArgument(0))).map(j->new JournalRepository.Scope() {
            public String getPaymentId(){return j.getPaymentId();} public String getCorrelationId(){return j.getCorrelationId();} }));
        var correlations=new HashMap<String,String>();
        doAnswer(i->{ correlations.putIfAbsent(i.getArgument(0),i.getArgument(1)); return null; }).when(locks).ensure(anyString(),anyString());
        when(locks.lock(anyString())).thenAnswer(i->Optional.of(new LedgerPaymentLock(i.getArgument(0),correlations.get(i.getArgument(0)))));
    }
    PaymentAccountingService.Context context(Outcome outcome) {
        return new PaymentAccountingService.Context("PAY-ACCOUNTING","CORR-ACCOUNTING",new Terms(UUID.randomUUID(),UUID.randomUUID(),1,"SIMULATED-FUNDING-1",
                new BigDecimal("10000.00"),new BigDecimal("9995.125000"),outcome,
                outcome==Outcome.USDC_WALLET?null:new BigDecimal(outcome==Outcome.SIMULATED_BANK_INR?"830000.00":"9990.00")));
    }
    void submitted(PaymentAccountingService.Context c) { service.fundAndConvert(c); service.submitted(c,hash,Mode.MOCK); }
    void confirmed(PaymentAccountingService.Context c) { submitted(c); service.confirmed(c,hash,Mode.MOCK); }
}
