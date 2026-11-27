package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.SettlementBinding;
import com.cognizant.stablecoins.ledger.accounting.*;
import com.cognizant.stablecoins.ledger.api.dto.*;
import com.cognizant.stablecoins.ledger.exception.JournalValidationException;
import com.cognizant.stablecoins.ledger.model.Journal;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingPolicy.Role.*;

/**
 * Internal ledger commands, gated off by default. No endpoint/listener/RPC/signing access.
 * Funding requires the exact committed payment-owner simulated debit and qualified outbox command.
 * These journals neither debit the application's customer account nor fund/mint a chain wallet.
 */
@Service
@ConditionalOnProperty(name="app.besu.accounting.enabled",havingValue="true")
public class BesuAccountingService {
    private final LedgerPostingService ledger;
    private final AccountingPolicy usdPolicy;
    private final BesuAccountingPolicy privatePolicy;
    private final BesuAccountingStore store;
    public BesuAccountingService(LedgerPostingService ledger,AccountingPolicy usdPolicy,
            BesuAccountingPolicy privatePolicy,BesuAccountingStore store) {
        this.ledger=ledger;this.usdPolicy=usdPolicy;this.privatePolicy=privatePolicy;this.store=store;
    }
    /** Caller is the trusted funding handler, never a browser/model or a claim-only automatic worker. */
    @Transactional
    public List<Journal> fundAndAllocate(SettlementBinding.Execution execution,String simulatedFundingReference) {
        var evidence=BesuAccountingEvidence.allocation(execution,simulatedFundingReference);lock(execution);
        store.requireFunded(execution,simulatedFundingReference);
        var existing=journals(execution);
        long legs=existing.stream().filter(j->Set.of("FUNDING","CONVERSION_USD","CONVERSION_DEMOUSD").contains(j.getJournalType())).count();
        if(legs!=0 && legs!=3) throw failure("BESU_PARTIAL_ALLOCATION_REVIEW_REQUIRED");
        var usd=execution.instruction().sourceUsd();
        var funding=post(execution,"FUNDING",simulatedFundingReference,
            List.of(usdPolicy.line(AccountingProperties.Role.FUNDING_USD,usd,true),usdPolicy.line(AccountingProperties.Role.CORPORATE_USD,usd,false)),
            evidence,PostingEvidence.Mode.SIMULATED,null);
        var conversion=post(execution,"CONVERSION_USD",execution.executionId().toString(),
            List.of(usdPolicy.line(AccountingProperties.Role.CONVERSION_USD,usd,true),usdPolicy.line(AccountingProperties.Role.FUNDING_USD,usd,false)),
            evidence,PostingEvidence.Mode.SIMULATED,null);
        var allocation=postPrivate(execution,"CONVERSION_DEMOUSD",execution.executionId().toString(),TREASURY,ALLOCATION,
            execution.instruction().tokenAmount(),evidence,PostingEvidence.Mode.SIMULATED,null);
        return List.of(funding,conversion,allocation);
    }
    @Transactional
    public Journal submitted(SettlementBinding.Execution execution) {
        lock(execution);var evidence=allocation(execution);var submission=store.submission(execution);
        return postPrivate(execution,"BLOCKCHAIN_SUBMISSION",submission.hash(),TRANSIT,TREASURY,
                execution.instruction().tokenAmount(),evidence,PostingEvidence.Mode.PRIVATE_DEMO,submission.hash());
    }
    /** Receipt acknowledgement and any confirmation/fee journals commit in the same ledger transaction. */
    @Transactional
    public BesuAccountingStore.Acknowledgement resolve(SettlementBinding.Execution execution) {
        lock(execution);var base=allocation(execution);var submission=store.submission(execution);
        require(execution,"BLOCKCHAIN_SUBMISSION",posting(base,PostingEvidence.Mode.PRIVATE_DEMO,submission.hash()));
        var receipt=store.receipt(execution,submission);var evidence=base.withReceipt(receipt);
        UUID confirmation=null,fee=null;
        if(receipt.success()) confirmation=postPrivate(execution,"BLOCKCHAIN_CONFIRMATION",submission.hash(),DELIVERED,TRANSIT,
            execution.instruction().tokenAmount(),evidence,PostingEvidence.Mode.PRIVATE_DEMO,submission.hash()).getJournalId();
        else if(journals(execution).stream().anyMatch(j->"BLOCKCHAIN_CONFIRMATION".equals(j.getJournalType())))
            throw failure("BESU_REVERTED_CONFIRMATION_CONFLICT");
        // A mined revert still consumes gas. A complete zero fee creates no fabricated positive/zero-value journal.
        if(receipt.totalFeeWei().signum()>0) fee=postPrivate(execution,"NETWORK_FEE",submission.hash(),GAS_EXPENSE,GAS_CLEARING,
            new BigDecimal(receipt.totalFeeWei(),18),evidence,PostingEvidence.Mode.PRIVATE_DEMO,submission.hash()).getJournalId();
        else if(journals(execution).stream().anyMatch(j->"NETWORK_FEE".equals(j.getJournalType())))
            throw failure("BESU_ZERO_FEE_JOURNAL_CONFLICT");
        var result=new BesuAccountingStore.Acknowledgement(evidence,confirmation,fee);
        var previous=store.acknowledgement(execution.executionId());
        if(previous.isPresent()) { if(!previous.get().equals(result)) throw failure("BESU_RECEIPT_ACCOUNTING_CONFLICT"); }
        else store.acknowledge(execution,result);
        return result;
    }
    private void lock(SettlementBinding.Execution e) {
        BesuAccountingEvidence.requirePrivate(e);ledger.lockPayment(e.instruction().paymentId(),e.instruction().correlationId());store.requireClaimed(e);
    }
    private List<Journal> journals(SettlementBinding.Execution e) {return ledger.getJournalsByPaymentId(e.instruction().paymentId());}
    private BesuAccountingEvidence allocation(SettlementBinding.Execution e) {
        var j=journals(e).stream().filter(x->"CONVERSION_DEMOUSD".equals(x.getJournalType())).findFirst()
                .orElseThrow(()->failure("BESU_ALLOCATION_REQUIRED"));
        var p=j.getEvidence();
        if(p==null || p.privateSettlement()==null) throw failure("BESU_ALLOCATION_REQUIRED");
        var evidence=BesuAccountingEvidence.allocation(e,p.privateSettlement().fundingReference());
        require(e,"CONVERSION_DEMOUSD",posting(evidence,PostingEvidence.Mode.SIMULATED,null));
        require(e,"FUNDING",posting(evidence,PostingEvidence.Mode.SIMULATED,null));
        require(e,"CONVERSION_USD",posting(evidence,PostingEvidence.Mode.SIMULATED,null));
        return evidence;
    }
    private void require(SettlementBinding.Execution e,String type,PostingEvidence expected) {
        var j=journals(e).stream().filter(x->type.equals(x.getJournalType())).findFirst().orElseThrow(()->failure("BESU_PRIOR_JOURNAL_REQUIRED"));
        if(!"POSTED".equals(j.getStatus()) || !e.instruction().correlationId().equals(j.getCorrelationId()) || !expected.equals(j.getEvidence()))
            throw failure("BESU_PRIOR_JOURNAL_MISMATCH");
    }
    private Journal postPrivate(SettlementBinding.Execution e,String type,String reference,BesuAccountingPolicy.Role debit,
            BesuAccountingPolicy.Role credit,BigDecimal amount,BesuAccountingEvidence evidence,PostingEvidence.Mode mode,String hash) {
        return post(e,type,reference,List.of(privatePolicy.line(debit,amount,true),privatePolicy.line(credit,amount,false)),evidence,mode,hash);
    }
    private Journal post(SettlementBinding.Execution e,String type,String reference,List<JournalEntryRequest> lines,
            BesuAccountingEvidence evidence,PostingEvidence.Mode mode,String hash) {
        var request=new PostJournalRequest();request.setPaymentId(e.instruction().paymentId());request.setCorrelationId(e.instruction().correlationId());
        request.setJournalType(type);request.setReference(reference);request.setDescription("ADR-016 private payment control ledger: "+type);
        request.setEvidence(posting(evidence,mode,hash));request.setEntries(lines);return ledger.postJournal(request);
    }
    private PostingEvidence posting(BesuAccountingEvidence evidence,PostingEvidence.Mode mode,String hash) {
        return new PostingEvidence(mode,BesuAccountingPolicy.VERSION+":"+usdPolicy.version(),null,hash,null,null,evidence);
    }
    private static JournalValidationException failure(String code) {return new JournalValidationException(code);}
}
