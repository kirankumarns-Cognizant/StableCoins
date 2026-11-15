package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;
import static org.assertj.core.api.Assertions.*;
class ControlLedgerEvidenceTest {
 final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule());
 final MockEnvironment env=new MockEnvironment().withProperty("app.ledger.accounting.version","adr015-v1");
 final UUID id=UUID.randomUUID(),prep=UUID.randomUUID(),quoteId=UUID.randomUUID(),txId=UUID.randomUUID();
 final String hash="0x"+"a".repeat(64),wallet="0x"+"1".repeat(40),token="0x"+"2".repeat(40);final Instant now=Instant.now();
 Execution e;Quote q;Chain c;Payout payout;List<Journal> journals;
 final Map<String,String> accounts=Map.ofEntries(Map.entry("corporate-usd","110001"),Map.entry("funding-usd","110002"),Map.entry("conversion-usd","110003"),Map.entry("treasury-usdc","120001"),Map.entry("allocation-usdc","220001"),Map.entry("transit-usdc","120002"),Map.entry("beneficiary-usdc","120003"),Map.entry("gas-expense-eth","510001"),Map.entry("gas-clearing-eth","130001"),Map.entry("provider-usd","110004"),Map.entry("payable-usd","210001"),Map.entry("provider-inr","140001"),Map.entry("payable-inr","240001"));
 @BeforeEach void setup(){accounts.forEach((k,v)->env.withProperty("app.ledger.accounting.accounts."+k,v));fixture("USDC_WALLET");}
 void fixture(String outcome){
  boolean bank=!outcome.equals("USDC_WALLET");String currency=bank?outcome.endsWith("INR")?"INR":"USD":null;BigDecimal amount=bank?new BigDecimal(currency.equals("INR")?"830000":"10000"):null;
  var request=new Request(prep,"PAY-1","CORR-1",UUID.randomUUID(),new BigDecimal("10000"),new BigDecimal("10000"),wallet,outcome,"AUTO","DEMO",now.plusSeconds(300),Set.of("A"));
  e=new Execution(id,UUID.randomUUID(),prep,"PAY-1","CORR-1","CONFIRMED",request,txId,now.plusSeconds(300));
  q=new Quote(prep,quoteId,1,"A","v1",request.sourceUsd(),request.transferUsdc(),BigDecimal.ONE,99,100,1000,BigDecimal.ONE,true,now,now.plusSeconds(300),currency,amount,bank?new BigDecimal(currency.equals("INR")?"83":"1"):null,bank?new BigDecimal("1000000"):null);
  c=new Chain(txId,"PAY-1","CORR-1","base-sepolia",84532,token,wallet,wallet,new BigDecimal("10000"),hash,"CONFIRMED",100L,new BigDecimal("42000"),new BigDecimal("1000000000"),new BigDecimal("100"),new BigDecimal("42000000000100"),true,null,"MOCK",3);
  payout=bank?new Payout(UUID.randomUUID(),id,"PAY-1","CORR-1","A",currency,amount,"SUCCEEDED","SIMULATED-VISA-1",UUID.randomUUID(),quoteId,1,hash,"SIMULATED","SUCCEEDED"):null;
  journals=new ArrayList<>();
  leg("FUNDING","USD",request.sourceUsd(),"funding-usd","corporate-usd","SIMULATED","SIMULATED-"+id,null);
  leg("CONVERSION_USD","USD",request.sourceUsd(),"conversion-usd","funding-usd","SIMULATED",id.toString(),null);
  leg("CONVERSION_USDC","USDC",c.amount(),"treasury-usdc","allocation-usdc","SIMULATED",id.toString(),null);
  leg("BLOCKCHAIN_SUBMISSION","USDC",c.amount(),"transit-usdc","treasury-usdc","MOCK",hash,null);
  leg("BLOCKCHAIN_CONFIRMATION","USDC",c.amount(),"beneficiary-usdc","transit-usdc","MOCK",hash,null);
  leg("NETWORK_FEE","ETH",c.totalFeeWei().movePointLeft(18),"gas-expense-eth","gas-clearing-eth","MOCK",hash,null);
  if(bank){leg("OFFRAMP_USDC","USDC",c.amount(),"allocation-usdc","beneficiary-usdc","SIMULATED",payout.reference(),payout.reference());leg("PAYOUT_INITIATION_"+currency,currency,amount,"provider-"+currency.toLowerCase(),"payable-"+currency.toLowerCase(),"SIMULATED",payout.reference(),payout.reference());leg("PAYOUT_COMPLETION_"+currency,currency,amount,"payable-"+currency.toLowerCase(),"provider-"+currency.toLowerCase(),"SIMULATED",payout.reference(),payout.reference());}
 }
 void leg(String type,String asset,BigDecimal amount,String debit,String credit,String mode,String reference,String payoutRef){
  var terms=new LinkedHashMap<String,Object>();terms.put("conversionId",id);terms.put("quoteId",quoteId);terms.put("quoteVersion",1);terms.put("fundingReference","SIMULATED-"+id);terms.put("sourceUsd",e.request().sourceUsd());terms.put("transferUsdc",c.amount());terms.put("outcome",e.request().outcome());terms.put("payoutAmount",q.payoutAmount());
  var ev=new LinkedHashMap<String,Object>();ev.put("mode",mode);ev.put("policyVersion","adr015-v1");ev.put("terms",terms);ev.put("transactionHash",type.startsWith("BLOCKCHAIN_")||type.equals("NETWORK_FEE")||payoutRef!=null?hash:null);ev.put("payoutReference",payoutRef);
  if(type.equals("NETWORK_FEE"))ev.put("receiptFee",Map.of("transactionHash",hash,"chainId",84532,"blockNumber",100,"gasUsed",42000,"effectiveGasPriceWei",new BigDecimal("1000000000"),"additionalFeeWei",100,"totalFeeWei",new BigDecimal("42000000000100"),"componentsComplete",true,"source","TRANSACTION_RECEIPT"));
  journals.add(new Journal(UUID.randomUUID(),e.paymentId(),e.correlationId(),type,"POSTED",reference,mapper.valueToTree(ev),List.of(new Entry(accounts.get(debit),asset,amount,BigDecimal.ZERO),new Entry(accounts.get(credit),asset,BigDecimal.ZERO,amount))));
 }
 List<String> inspect(){return ControlLedgerEvidence.inspect(e,q,c,payout,journals,env,ControlLedgerEvidence.Stage.COMPLETE);}
 @Test void exactWalletEvidencePasses(){assertThat(inspect()).isEmpty();}
 @Test void exactUsdBankEvidencePasses(){fixture("SIMULATED_BANK_USD");assertThat(inspect()).isEmpty();}
 @Test void exactInrBankEvidencePasses(){fixture("SIMULATED_BANK_INR");assertThat(inspect()).isEmpty();}
 @Test void missingNetworkFeeCannotReconcile(){journals.removeIf(j->j.type().equals("NETWORK_FEE"));assertThat(inspect()).contains("MISSING_OR_DUPLICATE_NETWORK_FEE");}
 @Test void duplicateJournalCannotReconcile(){journals.add(journals.getFirst());assertThat(inspect()).contains("MISSING_OR_DUPLICATE_FUNDING");}
 @Test void balancedButIncorrectAmountCannotReconcile(){var j=journals.getFirst();journals.set(0,new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),j.evidence(),List.of(new Entry("110002","USD",BigDecimal.ONE,BigDecimal.ZERO),new Entry("110001","USD",BigDecimal.ZERO,BigDecimal.ONE))));assertThat(inspect()).contains("ENTRIES_FUNDING");}
 @Test void crossAssetNettingCannotReconcile(){var j=journals.getFirst();journals.set(0,new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),j.status(),j.reference(),j.evidence(),List.of(j.entries().getFirst(),new Entry("110001","USDC",BigDecimal.ZERO,new BigDecimal("10000")))));assertThat(inspect()).contains("ENTRIES_FUNDING");}
 @Test void changedQuoteBindingCannotReconcile(){((com.fasterxml.jackson.databind.node.ObjectNode)journals.getFirst().evidence().path("terms")).put("quoteId",UUID.randomUUID().toString());assertThat(inspect()).contains("BINDING_FUNDING");}
 @Test void simulatedChainCannotBecomeRealByChangingLabel(){((com.fasterxml.jackson.databind.node.ObjectNode)journals.get(4).evidence()).put("mode","REAL_TESTNET");assertThat(inspect()).contains("BINDING_BLOCKCHAIN_CONFIRMATION");}
 @Test void gasEstimateCannotBecomeActualExpense(){((com.fasterxml.jackson.databind.node.ObjectNode)journals.get(5).evidence().path("receiptFee")).put("source","GAS_ESTIMATE");assertThat(inspect()).contains("NETWORK_FEE_RECEIPT_MISMATCH");}
 @Test void wrongReceiptFeeIsRejected(){((com.fasterxml.jackson.databind.node.ObjectNode)journals.get(5).evidence().path("receiptFee")).put("additionalFeeWei",0);assertThat(inspect()).contains("NETWORK_FEE_RECEIPT_MISMATCH");}
 @Test void partialBankPostingCannotReconcile(){fixture("SIMULATED_BANK_INR");journals.removeIf(j->j.type().equals("PAYOUT_COMPLETION_INR"));assertThat(inspect()).contains("MISSING_OR_DUPLICATE_PAYOUT_COMPLETION_INR");}
 @Test void unknownPayoutIsNotSuccess(){fixture("SIMULATED_BANK_USD");var p=payout;payout=new Payout(p.payoutId(),p.executionId(),p.paymentId(),p.correlationId(),p.providerId(),p.currency(),p.amount(),"UNKNOWN",p.reference(),p.reservationId(),p.quoteId(),p.quoteVersion(),p.hash(),p.mode(),"TIMEOUT");assertThat(inspect()).contains("PAYOUT_NOT_SUCCESSFUL");}
 @Test void changedPayoutReferenceIsDetected(){fixture("SIMULATED_BANK_USD");((com.fasterxml.jackson.databind.node.ObjectNode)journals.getLast().evidence()).put("payoutReference","ANOTHER-PAYOUT");assertThat(inspect()).contains("BINDING_PAYOUT_COMPLETION_USD");}
 @Test void compensationRequiresReviewRatherThanPretendingOriginalIsPosted(){var j=journals.getFirst();journals.set(0,new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),"REVERSED",j.reference(),j.evidence(),j.entries()));assertThat(inspect()).contains("BINDING_FUNDING");}
}
