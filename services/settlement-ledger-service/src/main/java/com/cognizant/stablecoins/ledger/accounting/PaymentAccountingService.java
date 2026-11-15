package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.ledger.api.dto.PostJournalRequest;
import com.cognizant.stablecoins.ledger.exception.JournalValidationException;
import com.cognizant.stablecoins.ledger.model.Journal;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import static com.cognizant.stablecoins.ledger.accounting.AccountingProperties.Role.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;

/** Trusted internal commands. No public HTTP/model write tool exposes these methods. */
@Service
public class PaymentAccountingService {
    public record Context(String paymentId,String correlationId,Terms terms) {}
    public enum PayoutStatus { SUCCEEDED, FAILED, UNKNOWN }
    public enum FeeSource { TRANSACTION_RECEIPT, GAS_ESTIMATE }
    public record ReceiptFee(String transactionHash,long chainId,long blockNumber,BigInteger gasUsed,
            BigInteger effectiveGasPriceWei,BigInteger additionalFeeWei,BigInteger totalFeeWei,
            boolean componentsComplete,FeeSource source) {}
    private final LedgerPostingService ledger;
    private final AccountingPolicy policy;
    public PaymentAccountingService(LedgerPostingService ledger,AccountingPolicy policy) { this.ledger=ledger; this.policy=policy; }

    @Transactional
    public List<Journal> fundAndConvert(Context context) {
        validate(context); lock(context);
        var initial=ledger.getJournalsByPaymentId(context.paymentId());
        long legs=initial.stream().filter(j->Set.of("FUNDING","CONVERSION_USD","CONVERSION_USDC").contains(j.getJournalType())).count();
        if(legs!=0 && legs!=3) throw failure("PARTIAL_COMPOUND_JOURNAL_REVIEW_REQUIRED");
        var simulated=evidence(context,Mode.SIMULATED,null,null);
        Journal funding=post(context,"FUNDING",context.terms().fundingReference(),FUNDING_USD,CORPORATE_USD,context.terms().sourceUsd(),simulated);
        Journal usd=post(context,"CONVERSION_USD",context.terms().conversionId().toString(),CONVERSION_USD,FUNDING_USD,context.terms().sourceUsd(),simulated);
        Journal usdc=post(context,"CONVERSION_USDC",context.terms().conversionId().toString(),TREASURY_USDC,ALLOCATION_USDC,context.terms().transferUsdc(),simulated);
        return List.of(funding,usd,usdc);
    }
    @Transactional
    public Journal submitted(Context context,String hash,Mode mode) {
        validate(context); hash(hash); chainMode(mode); lock(context);
        require(context,"CONVERSION_USDC",Mode.SIMULATED,null,null);
        return post(context,"BLOCKCHAIN_SUBMISSION",hash,TRANSIT_USDC,TREASURY_USDC,context.terms().transferUsdc(),evidence(context,mode,hash,null));
    }
    @Transactional
    public Journal confirmed(Context context,String hash,Mode mode) {
        validate(context); hash(hash); chainMode(mode); lock(context);
        if(ledger.getJournalsByPaymentId(context.paymentId()).stream().anyMatch(j->"BLOCKCHAIN_SETTLEMENT".equals(j.getJournalType())))
            throw failure("LEGACY_SETTLEMENT_REVIEW_REQUIRED");
        require(context,"BLOCKCHAIN_SUBMISSION",mode,hash,null);
        return post(context,"BLOCKCHAIN_CONFIRMATION",hash,BENEFICIARY_USDC,TRANSIT_USDC,context.terms().transferUsdc(),evidence(context,mode,hash,null));
    }
    @Transactional
    public Journal networkFee(Context context,ReceiptFee receipt,Mode mode) {
        validate(context); chainMode(mode); lock(context);
        if(receipt==null) throw failure("FEE_EVIDENCE_INCOMPLETE");
        hash(receipt.transactionHash());
        require(context,"BLOCKCHAIN_SUBMISSION",mode,receipt.transactionHash(),null);
        if(receipt.source()!=FeeSource.TRANSACTION_RECEIPT || !receipt.componentsComplete() || receipt.blockNumber()<1
                || (receipt.chainId()!=84532 && receipt.chainId()!=11155111) || receipt.gasUsed()==null || receipt.gasUsed().signum()<=0
                || receipt.effectiveGasPriceWei()==null || receipt.effectiveGasPriceWei().signum()<0 || receipt.totalFeeWei()==null
                || receipt.additionalFeeWei()==null || receipt.additionalFeeWei().signum()<0) throw failure("FEE_EVIDENCE_INCOMPLETE");
        var computed=receipt.gasUsed().multiply(receipt.effectiveGasPriceWei()).add(receipt.additionalFeeWei());
        if(computed.signum()<=0 || !computed.equals(receipt.totalFeeWei())) throw failure("FEE_EVIDENCE_INCONSISTENT");
        // Also valid for a reverted mined receipt: gas is spent even when USDC settlement fails.
        return post(context,"NETWORK_FEE",receipt.transactionHash(),GAS_EXPENSE_ETH,GAS_CLEARING_ETH,
                new BigDecimal(computed,18),new PostingEvidence(mode,policy.version(),context.terms(),receipt.transactionHash(),null,receipt));
    }
    @Transactional
    public List<Journal> initiatePayout(Context context,String hash,Mode settlementMode,String payoutReference) {
        validate(context); bank(context); reference(payoutReference); hash(hash); chainMode(settlementMode); lock(context);
        require(context,"BLOCKCHAIN_CONFIRMATION",settlementMode,hash,null);
        String asset=payoutAsset(context);
        requirePair(context,"OFFRAMP_USDC","PAYOUT_INITIATION_"+asset);
        var simulated=evidence(context,Mode.SIMULATED,hash,payoutReference);
        Journal offramp=post(context,"OFFRAMP_USDC",payoutReference,ALLOCATION_USDC,BENEFICIARY_USDC,context.terms().transferUsdc(),simulated);
        Journal payable=post(context,"PAYOUT_INITIATION_"+asset,payoutReference,
                asset.equals("USD")?PROVIDER_USD:PROVIDER_INR,asset.equals("USD")?PAYABLE_USD:PAYABLE_INR,context.terms().payoutAmount(),simulated);
        return List.of(offramp,payable);
    }
    @Transactional
    public Journal completePayout(Context context,String hash,String payoutReference,PayoutStatus status) {
        validate(context); bank(context); reference(payoutReference); hash(hash); lock(context);
        if(status!=PayoutStatus.SUCCEEDED) throw failure("PAYOUT_NOT_CONFIRMED_SUCCESSFUL");
        String asset=payoutAsset(context);
        require(context,"PAYOUT_INITIATION_"+asset,Mode.SIMULATED,hash,payoutReference);
        return post(context,"PAYOUT_COMPLETION_"+asset,payoutReference,asset.equals("USD")?PAYABLE_USD:PAYABLE_INR,
                asset.equals("USD")?PROVIDER_USD:PROVIDER_INR,context.terms().payoutAmount(),evidence(context,Mode.SIMULATED,hash,payoutReference));
    }
    /** Read the immutable ledger-owned allocation binding; never infer a quote from chain amounts. */
    @Transactional
    public Context context(String paymentId,String correlationId) {
        ledger.lockPayment(paymentId,correlationId);
        Journal conversion=ledger.getJournalsByPaymentId(paymentId).stream().filter(j->"CONVERSION_USDC".equals(j.getJournalType())).findFirst()
                .orElseThrow(()->failure("ALLOCATION_EVIDENCE_REQUIRED"));
        if(!correlationId.equals(conversion.getCorrelationId()) || conversion.getEvidence()==null) throw failure("ALLOCATION_EVIDENCE_REQUIRED");
        Context context=new Context(paymentId,correlationId,conversion.getEvidence().terms()); validate(context); return context;
    }
    private Journal post(Context c,String type,String reference,AccountingProperties.Role debit,AccountingProperties.Role credit,BigDecimal amount,PostingEvidence evidence) {
        var request=new PostJournalRequest(); request.setPaymentId(c.paymentId()); request.setCorrelationId(c.correlationId());
        request.setJournalType(type); request.setReference(reference); request.setDescription("ADR-015 payment control ledger: "+type);
        request.setEvidence(evidence); request.setEntries(List.of(policy.line(debit,amount,true),policy.line(credit,amount,false)));
        return ledger.postJournal(request);
    }
    private void require(Context c,String type,Mode mode,String hash,String payout) {
        var journal=ledger.getJournalsByPaymentId(c.paymentId()).stream().filter(j->type.equals(j.getJournalType())).findFirst()
                .orElseThrow(()->failure("PRIOR_ACCOUNTING_LEG_REQUIRED"));
        if(!"POSTED".equals(journal.getStatus()) || !c.correlationId().equals(journal.getCorrelationId())
                || !evidence(c,mode,hash,payout).equals(journal.getEvidence())) throw failure("PRIOR_ACCOUNTING_LEG_MISMATCH");
    }
    private void requirePair(Context c,String a,String b) {
        var existing=ledger.getJournalsByPaymentId(c.paymentId());
        boolean one=existing.stream().anyMatch(j->a.equals(j.getJournalType())),two=existing.stream().anyMatch(j->b.equals(j.getJournalType()));
        if(one!=two) throw failure("PARTIAL_COMPOUND_JOURNAL_REVIEW_REQUIRED");
    }
    private PostingEvidence evidence(Context c,Mode mode,String hash,String payout) { return new PostingEvidence(mode,policy.version(),c.terms(),hash,payout,null); }
    private void lock(Context c) { ledger.lockPayment(c.paymentId(),c.correlationId()); }
    private static void validate(Context c) {
        if(c==null || c.terms()==null || c.terms().conversionId()==null || c.terms().quoteId()==null || c.terms().quoteVersion()<1
                || c.terms().outcome()==null) throw failure("ALLOCATION_EVIDENCE_REQUIRED");
        reference(c.terms().fundingReference()); amount(c.terms().sourceUsd(),2); amount(c.terms().transferUsdc(),6);
        if(c.terms().outcome()==Outcome.USDC_WALLET) { if(c.terms().payoutAmount()!=null) throw failure("WALLET_OUTCOME_HAS_NO_FIAT_PAYOUT"); }
        else amount(c.terms().payoutAmount(),2);
    }
    private static void amount(BigDecimal amount,int scale) {
        if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>scale || amount.precision()-amount.scale()>18)
            throw failure("ACCOUNTING_AMOUNT_INVALID");
    }
    private static void bank(Context c) { if(c.terms().outcome()==Outcome.USDC_WALLET) throw failure("WALLET_OUTCOME_HAS_NO_FIAT_PAYOUT"); }
    private static String payoutAsset(Context c) { return c.terms().outcome()==Outcome.SIMULATED_BANK_USD?"USD":"INR"; }
    private static void chainMode(Mode mode) { if(mode!=Mode.REAL_TESTNET && mode!=Mode.MOCK) throw failure("CHAIN_EVIDENCE_MODE_REQUIRED"); }
    private static void hash(String hash) { if(hash==null || !hash.matches("0x[0-9a-fA-F]{64}")) throw failure("TRANSACTION_HASH_REQUIRED"); }
    private static void reference(String reference) { if(reference==null || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) throw failure("EVIDENCE_REFERENCE_REQUIRED"); }
    private static JournalValidationException failure(String code) { return new JournalValidationException(code); }
}
