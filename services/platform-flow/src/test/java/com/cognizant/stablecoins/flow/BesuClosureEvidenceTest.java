package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.*;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.flow.BesuClosureEvidence.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;
import static org.assertj.core.api.Assertions.*;

class BesuClosureEvidenceTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final Policy policy=new Policy("adr015-v1","110002","110001","110003");
    final String hash="0x"+"a".repeat(64),block="0x"+"b".repeat(64);
    SettlementBinding.Execution e;Snapshot s;
    @BeforeEach void setup(){
        e=BesuClosureTestData.execution();
        fixture(true,7);
    }
    void fixture(boolean success,long price){
        var i=e.instruction();var r=new Receipt(e.context().nonceScope(),hash,block,101,success,BigInteger.valueOf(21000),BigInteger.valueOf(price),BigInteger.valueOf(21000*price),"QBFT_COMMITTED");
        var js=new ArrayList<Journal>();
        leg(js,"FUNDING","110002","110001","USD",i.sourceUsd(),"SIM-FUND","SIMULATED",null);
        leg(js,"CONVERSION_USD","110003","110002","USD",i.sourceUsd(),e.executionId().toString(),"SIMULATED",null);
        leg(js,"CONVERSION_DEMOUSD","150001","250001","DEMOUSD",i.tokenAmount(),e.executionId().toString(),"SIMULATED",null);
        leg(js,"BLOCKCHAIN_SUBMISSION","150002","150001","DEMOUSD",i.tokenAmount(),hash,"PRIVATE_DEMO",null);
        if(success)leg(js,"BLOCKCHAIN_CONFIRMATION","150003","150002","DEMOUSD",i.tokenAmount(),hash,"PRIVATE_DEMO",r);
        if(price>0)leg(js,"NETWORK_FEE","550001","150004","BESU_GAS",new BigDecimal(r.totalFeeWei(),18),hash,"PRIVATE_DEMO",r);
        var accounts=List.of(new Account("110001","USD","ASSET"),new Account("110002","USD","ASSET"),new Account("110003","USD","ASSET"),new Account("150001","DEMOUSD","ASSET"),new Account("150002","DEMOUSD","ASSET"),new Account("150003","DEMOUSD","ASSET"),new Account("250001","DEMOUSD","LIABILITY"),new Account("550001","BESU_GAS","EXPENSE"),new Account("150004","BESU_GAS","ASSET"));
        s=new Snapshot(e,new Payment(i.paymentId(),i.correlationId(),i.customerId(),i.sourceUsd(),"USD"),new Submission(e,SettlementBindingJson.fingerprint(e),e.context().nonceScope(),hash,success?"CONFIRMED":"REVERTED",BigInteger.valueOf(30000),BigInteger.valueOf(price)),r,new Acknowledgement(e.executionId(),i.paymentId(),i.correlationId(),privateEvidence(r),id(js,"BLOCKCHAIN_CONFIRMATION"),id(js,"NETWORK_FEE")),js,accounts,false);
    }
    UUID id(List<Journal> js,String type){return js.stream().filter(j->j.type().equals(type)).map(Journal::journalId).findFirst().orElse(null);}
    ObjectNode privateEvidence(Receipt r){
        var n=mapper.createObjectNode();n.put("executionDocument",SettlementBindingJson.write(e));n.put("executionFingerprint",SettlementBindingJson.fingerprint(e));n.put("fundingReference","SIM-FUND");
        if(r==null)n.putNull("receipt");else {var receipt=mapper.<ObjectNode>valueToTree(r);receipt.remove(List.of("scope","finality"));n.set("receipt",receipt);}return n;
    }
    void leg(List<Journal> js,String type,String dr,String cr,String asset,BigDecimal amount,String reference,String mode,Receipt r){
        var n=mapper.createObjectNode();n.put("mode",mode);n.put("policyVersion",policy.version());n.putNull("terms");n.put("transactionHash",mode.equals("SIMULATED")?null:hash);n.putNull("payoutReference");n.putNull("receiptFee");n.set("privateSettlement",privateEvidence(r));
        js.add(new Journal(UUID.randomUUID(),"PAY-BESU","CORR-BESU",type,"POSTED",reference,n,List.of(new Entry(dr,asset,amount,BigDecimal.ZERO),new Entry(cr,asset,BigDecimal.ZERO,amount))));
    }
    Snapshot snapshot(Payment p,Submission sub,Receipt r,Acknowledgement ack,List<Journal> js,List<Account> accounts,boolean payout){return new Snapshot(e,p,sub,r,ack,js,accounts,payout);}
    void journals(List<Journal> js){s=snapshot(s.payment(),s.submission(),s.receipt(),s.acknowledgement(),js,s.accounts(),false);}
    Comparison check(){return BesuClosureEvidence.compare(mapper,policy,s);}
    void mismatch(String finding){assertThat(check().status()).isEqualTo("MISMATCH");assertThat(check().findings()).contains(finding);}
    @Test void serviceDateSerializationPreferencesCannotChangeSourceIdentity() {
        var timestampMapper=mapper.copy().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var isoMapper=mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var first=BesuReconciliationDocuments.document(timestampMapper,e,BesuClosureEvidence.compare(timestampMapper,policy,s));
        var second=BesuReconciliationDocuments.document(isoMapper,e,BesuClosureEvidence.compare(isoMapper,policy,s));
        assertThat(BesuReconciliationDocuments.id(timestampMapper,first)).isEqualTo(BesuReconciliationDocuments.id(isoMapper,second));
        assertThat(BesuReconciliationDocuments.hash(timestampMapper,first)).isEqualTo(BesuReconciliationDocuments.hash(isoMapper,second));
    }
    @Test void exactPrivateChainAndControlLedgerMatchWithoutClaimingPaymentClosure(){assertThat(check().findings()).isEmpty();assertThat(check().status()).isEqualTo("MATCHED");assertThat(check().closureGate()).isEqualTo(GATE);}
    @Test void completeZeroFeeRequiresAcknowledgementAndNoFeeJournal(){fixture(true,0);assertThat(check().status()).isEqualTo("MATCHED");assertThat(s.journals()).noneMatch(j->j.type().equals("NETWORK_FEE"));s=snapshot(s.payment(),s.submission(),s.receipt(),null,s.journals(),s.accounts(),false);assertThat(check().status()).isEqualTo("PENDING");}
    @Test void revertedTransferWithCorrectFeeJournalNeverMatches(){fixture(false,7);mismatch("TRANSFER_REVERTED_REQUIRES_REVIEW");}
    @Test void zeroFeeRevertNeverMatchesEither(){fixture(false,0);mismatch("TRANSFER_REVERTED_REQUIRES_REVIEW");}
    @ParameterizedTest @ValueSource(strings={"FUNDING","CONVERSION_USD","CONVERSION_DEMOUSD","BLOCKCHAIN_SUBMISSION","BLOCKCHAIN_CONFIRMATION","NETWORK_FEE"})
    void everyMissingLegIsPending(String type){journals(s.journals().stream().filter(j->!j.type().equals(type)).toList());assertThat(check().findings()).contains("MISSING_"+type);assertThat(check().status()).isIn("PENDING","MISMATCH");}
    @ParameterizedTest @ValueSource(strings={"FUNDING","CONVERSION_USD","CONVERSION_DEMOUSD","BLOCKCHAIN_SUBMISSION","BLOCKCHAIN_CONFIRMATION","NETWORK_FEE"})
    void everyDuplicateLegIsMismatch(String type){var js=new ArrayList<>(s.journals());js.add(js.stream().filter(j->j.type().equals(type)).findFirst().orElseThrow());journals(js);mismatch("DUPLICATE_"+type);}
    @ParameterizedTest @ValueSource(strings={"FUNDING","CONVERSION_USD","CONVERSION_DEMOUSD","BLOCKCHAIN_SUBMISSION","BLOCKCHAIN_CONFIRMATION","NETWORK_FEE"})
    void balancedButWrongAmountIsRejectedForEveryLeg(String type){journals(s.journals().stream().map(j->!j.type().equals(type)?j:new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),j.evidence(),j.entries().stream().map(x->new Entry(x.accountCode(),x.asset(),x.debit().signum()>0?x.debit().add(BigDecimal.ONE):BigDecimal.ZERO,x.credit().signum()>0?x.credit().add(BigDecimal.ONE):BigDecimal.ZERO)).toList())).toList());mismatch("JOURNAL_ENTRIES_"+type);}
    @ParameterizedTest @ValueSource(strings={"mode","policyVersion","transactionHash","terms","payoutReference","receiptFee","privateSettlement"})
    void forgedPostingProvenanceIsRejected(String field){var js=new ArrayList<>(s.journals());var j=js.getLast();var n=j.evidence().deepCopy();((ObjectNode)n).put(field,"forged");js.set(js.size()-1,new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),n,j.entries()));journals(js);mismatch("JOURNAL_EVIDENCE_NETWORK_FEE");}
    @Test void wrongSourceCustomerIsRejected(){var p=s.payment();s=snapshot(new Payment(p.paymentId(),p.correlationId(),UUID.randomUUID(),p.amount(),p.currency()),s.submission(),s.receipt(),s.acknowledgement(),s.journals(),s.accounts(),false);mismatch("PAYMENT_BINDING_MISMATCH");}
    @Test void submissionMustMatchExactApprovedExecution(){var sub=s.submission();s=snapshot(s.payment(),new Submission(e,"0".repeat(64),sub.scope(),sub.hash(),sub.status(),sub.gasLimit(),sub.gasPrice()),s.receipt(),s.acknowledgement(),s.journals(),s.accounts(),false);mismatch("BESU_SUBMISSION_BINDING_MISMATCH");}
    @Test void otherGenesisCannotSupplyReceipt(){var r=s.receipt();s=snapshot(s.payment(),s.submission(),new Receipt("other-chain",r.transactionHash(),r.blockHash(),r.blockNumber(),r.success(),r.gasUsed(),r.gasPriceWei(),r.totalFeeWei(),r.finality()),s.acknowledgement(),s.journals(),s.accounts(),false);mismatch("BESU_RECEIPT_BINDING_MISMATCH");}
    @Test void missingReceiptStaysPendingUnlessContradictedByClaimedFinalJournals(){s=snapshot(s.payment(),s.submission(),null,null,s.journals().subList(0,4),s.accounts(),false);assertThat(check().status()).isEqualTo("PENDING");}
    @Test void unknownSubmissionCannotBeDeclaredMatched(){var sub=s.submission();s=snapshot(s.payment(),new Submission(e,sub.fingerprint(),sub.scope(),hash,"UNKNOWN",sub.gasLimit(),sub.gasPrice()),null,null,s.journals().subList(0,4),s.accounts(),false);assertThat(check().status()).isEqualTo("PENDING");}
    @Test void accountDefinitionMustMatchEntryAssetAndType(){var a=new ArrayList<>(s.accounts());a.set(0,new Account("110001","USD","LIABILITY"));s=snapshot(s.payment(),s.submission(),s.receipt(),s.acknowledgement(),s.journals(),a,false);mismatch("JOURNAL_ACCOUNT_FUNDING");}
    @Test void acknowledgementMustPointToExactFeeJournal(){var a=s.acknowledgement();s=snapshot(s.payment(),s.submission(),s.receipt(),new Acknowledgement(a.executionId(),a.paymentId(),a.correlationId(),a.evidence(),a.confirmationJournal(),UUID.randomUUID()),s.journals(),s.accounts(),false);mismatch("BESU_LEDGER_RECEIPT_MISMATCH");}
    @Test void privateWalletPayoutIsNotImplicitlyAccepted(){s=snapshot(s.payment(),s.submission(),s.receipt(),s.acknowledgement(),s.journals(),s.accounts(),true);mismatch("UNEXPECTED_PAYOUT");}
    @Test void reversedOrAdditionalJournalIsNotIgnored(){var js=new ArrayList<>(s.journals());var j=js.getFirst();js.add(new Journal(UUID.randomUUID(),j.paymentId(),j.correlationId(),"REVERSAL", "POSTED",j.reference(),j.evidence(),j.entries()));journals(js);mismatch("UNEXPECTED_JOURNAL");}
    @Test void fingerprintSurvivesRelationalOrderingAndJsonbNumericScale(){var original=fingerprint(mapper,check());var js=new ArrayList<>(s.journals());Collections.reverse(js);js.replaceAll(j->{var entries=new ArrayList<>(j.entries());Collections.reverse(entries);entries.replaceAll(x->new Entry(x.accountCode(),x.asset(),x.debit().setScale(18),x.credit().setScale(18)));return new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),j.evidence(),entries);});var accounts=new ArrayList<>(s.accounts());Collections.reverse(accounts);s=snapshot(s.payment(),s.submission(),s.receipt(),s.acknowledgement(),js,accounts,false);assertThat(fingerprint(mapper,check())).isEqualTo(original);}
    @Test void changedReceiptChangesFingerprint(){var original=fingerprint(mapper,check());var r=s.receipt();s=snapshot(s.payment(),s.submission(),new Receipt(r.scope(),r.transactionHash(),r.blockHash(),102,r.success(),r.gasUsed(),r.gasPriceWei(),r.totalFeeWei(),r.finality()),s.acknowledgement(),s.journals(),s.accounts(),false);assertThat(fingerprint(mapper,check())).isNotEqualTo(original);}
    @ParameterizedTest @ValueSource(strings={"hash","block","height","gas","price","total","finality","outcome"})
    void receiptMustMatchAllPersistedSubmissionAndFinalityTerms(String field){
        var r=s.receipt();
        var changed=new Receipt(r.scope(),field.equals("hash")?block:r.transactionHash(),field.equals("block")?"0x"+"0".repeat(64):r.blockHash(),field.equals("height")?0:r.blockNumber(),field.equals("outcome")?!r.success():r.success(),field.equals("gas")?BigInteger.valueOf(31000):r.gasUsed(),field.equals("price")?BigInteger.valueOf(8):r.gasPriceWei(),field.equals("total")?BigInteger.ONE:r.totalFeeWei(),field.equals("finality")?"UNCONFIRMED":r.finality());
        s=snapshot(s.payment(),s.submission(),changed,s.acknowledgement(),s.journals(),s.accounts(),false);mismatch("BESU_RECEIPT_BINDING_MISMATCH");
    }
    @Test void zeroFeeCannotHaveInventedFeeJournal(){fixture(true,0);var js=new ArrayList<>(s.journals());leg(js,"NETWORK_FEE","550001","150004","BESU_GAS",BigDecimal.ONE,hash,"PRIVATE_DEMO",s.receipt());journals(js);mismatch("UNEXPECTED_JOURNAL");}
    @Test void legacyUsdcCannotReplacePrivateTokenAccounting(){journals(s.journals().stream().map(j->!j.type().equals("BLOCKCHAIN_CONFIRMATION")?j:new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),j.evidence(),j.entries().stream().map(x->new Entry(x.accountCode(),"USDC",x.debit(),x.credit())).toList())).toList());mismatch("JOURNAL_ENTRIES_BLOCKCHAIN_CONFIRMATION");}
    @Test void numericJsonbReceiptRepresentationIsEquivalent(){var original=fingerprint(mapper,check());var js=new ArrayList<>(s.journals());js.replaceAll(j->{var n=(ObjectNode)j.evidence().deepCopy();var receipt=n.path("privateSettlement").path("receipt");if(receipt.isObject())((ObjectNode)receipt).put("gasUsed",new BigDecimal("21000.000"));return new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),n,j.entries());});journals(js);assertThat(check().status()).isEqualTo("MATCHED");assertThat(fingerprint(mapper,check())).isEqualTo(original);}

}
