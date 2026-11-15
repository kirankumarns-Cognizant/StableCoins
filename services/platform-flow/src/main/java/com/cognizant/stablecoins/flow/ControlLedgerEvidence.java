package com.cognizant.stablecoins.flow;
import java.math.*;
import java.util.*;
import org.springframework.core.env.Environment;
import static com.cognizant.stablecoins.flow.FlowContract.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;
/** Verifies approved ADR-015 templates; never posts or fixes a journal. */
public final class ControlLedgerEvidence {
 private ControlLedgerEvidence(){}
 public enum Stage { SETTLEMENT, PAYOUT_INITIATED, COMPLETE }
 private record Leg(String asset,BigDecimal amount,String debit,String credit,String mode,String reference,String payout){}
 public static List<String> inspect(Execution e,Quote q,Chain c,Payout p,List<Journal> journals,Environment env,Stage stage){
  var findings=new ArrayList<String>();String chainMode="LIVE".equals(c.mode())?"REAL_TESTNET":"MOCK";
  if(!"CONFIRMED".equals(c.status()))findings.add("CHAIN_NOT_CONFIRMED");
  if(c.blockNumber()==null||c.blockNumber()<1||c.confirmations()<env.getProperty("blockchain.confirmations-required",Integer.class,1))findings.add("CONFIRMATIONS_INCOMPLETE");
  BigDecimal fee=null;
  if(!c.feeComplete()||c.gasUsed()==null||c.effectiveGasPrice()==null||c.additionalFeeWei()==null||c.totalFeeWei()==null)findings.add("FEE_EVIDENCE_INCOMPLETE");
  else if(c.gasUsed().signum()<=0||c.effectiveGasPrice().signum()<0||c.additionalFeeWei().signum()<0||c.totalFeeWei().signum()<=0||c.gasUsed().multiply(c.effectiveGasPrice()).add(c.additionalFeeWei()).compareTo(c.totalFeeWei())!=0)findings.add("FEE_EVIDENCE_INCONSISTENT");
  else fee=c.totalFeeWei().movePointLeft(18);
  var expected=new LinkedHashMap<String,Leg>();
  add(expected,"FUNDING","USD",e.request().sourceUsd(),"funding-usd","corporate-usd","SIMULATED","SIMULATED-"+e.executionId(),null);
  add(expected,"CONVERSION_USD","USD",e.request().sourceUsd(),"conversion-usd","funding-usd","SIMULATED",e.executionId().toString(),null);
  add(expected,"CONVERSION_USDC","USDC",e.request().transferUsdc(),"treasury-usdc","allocation-usdc","SIMULATED",e.executionId().toString(),null);
  add(expected,"BLOCKCHAIN_SUBMISSION","USDC",c.amount(),"transit-usdc","treasury-usdc",chainMode,c.hash(),null);
  add(expected,"BLOCKCHAIN_CONFIRMATION","USDC",c.amount(),"beneficiary-usdc","transit-usdc",chainMode,c.hash(),null);
  add(expected,"NETWORK_FEE","ETH",fee,"gas-expense-eth","gas-clearing-eth",chainMode,c.hash(),null);
  boolean bank=!e.request().outcome().equals("USDC_WALLET");
  if(bank&&stage!=Stage.SETTLEMENT){
   if(p==null)findings.add("PAYOUT_EVIDENCE_MISSING");
   else {
    String a=q.payoutCurrency();
    if(!Set.of("PROCESSING","SUCCEEDED").contains(p.status())||stage==Stage.COMPLETE&&!p.status().equals("SUCCEEDED"))findings.add("PAYOUT_NOT_SUCCESSFUL");
    add(expected,"OFFRAMP_USDC","USDC",c.amount(),"allocation-usdc","beneficiary-usdc","SIMULATED",p.reference(),p.reference());
    add(expected,"PAYOUT_INITIATION_"+a,a,q.payoutAmount(),"provider-"+a.toLowerCase(),"payable-"+a.toLowerCase(),"SIMULATED",p.reference(),p.reference());
    if(stage==Stage.COMPLETE)add(expected,"PAYOUT_COMPLETION_"+a,a,q.payoutAmount(),"payable-"+a.toLowerCase(),"provider-"+a.toLowerCase(),"SIMULATED",p.reference(),p.reference());
   }
  }
  if(!bank&&p!=null)findings.add("UNEXPECTED_WALLET_PAYOUT");
  for(var item:expected.entrySet()){
   var matches=journals.stream().filter(j->item.getKey().equals(j.type())).toList();
   if(matches.size()!=1){findings.add("MISSING_OR_DUPLICATE_"+item.getKey());continue;}
   var j=matches.getFirst();var leg=item.getValue();var ev=j.evidence();var t=ev.path("terms");
   boolean chain=item.getKey().startsWith("BLOCKCHAIN_")||item.getKey().equals("NETWORK_FEE")||leg.payout()!=null;
   if(!j.paymentId().equals(e.paymentId())||!j.correlationId().equals(e.correlationId())||!j.status().equals("POSTED")||!Objects.equals(j.reference(),leg.reference())||!leg.mode().equals(ev.path("mode").asText())||!env.getRequiredProperty("app.ledger.accounting.version").equals(ev.path("policyVersion").asText())||!e.executionId().toString().equals(t.path("conversionId").asText())||!q.quoteId().toString().equals(t.path("quoteId").asText())||q.version()!=t.path("quoteVersion").asInt()||!("SIMULATED-"+e.executionId()).equals(t.path("fundingReference").asText())||!number(t.path("sourceUsd"),e.request().sourceUsd())||!number(t.path("transferUsdc"),c.amount())||!e.request().outcome().equals(t.path("outcome").asText())||(bank?!number(t.path("payoutAmount"),q.payoutAmount()):!t.path("payoutAmount").isNull())||!Objects.equals(chain?c.hash():null,ev.path("transactionHash").isNull()?null:ev.path("transactionHash").asText())||!Objects.equals(leg.payout(),ev.path("payoutReference").isNull()?null:ev.path("payoutReference").asText()))findings.add("BINDING_"+item.getKey());
   String debit=env.getRequiredProperty("app.ledger.accounting.accounts."+leg.debit()),credit=env.getRequiredProperty("app.ledger.accounting.accounts."+leg.credit());
   if(leg.amount()==null||j.entries().size()!=2||j.entries().stream().filter(x->x.asset().equals(leg.asset())&&x.accountCode().equals(debit)&&equal(x.debit(),leg.amount())&&equal(x.credit(),BigDecimal.ZERO)).count()!=1||j.entries().stream().filter(x->x.asset().equals(leg.asset())&&x.accountCode().equals(credit)&&equal(x.credit(),leg.amount())&&equal(x.debit(),BigDecimal.ZERO)).count()!=1)findings.add("ENTRIES_"+item.getKey());
   if(item.getKey().equals("NETWORK_FEE")){
    var r=ev.path("receiptFee");if(!r.path("componentsComplete").asBoolean()||!r.path("source").asText().equals("TRANSACTION_RECEIPT")||!c.hash().equals(r.path("transactionHash").asText())||r.path("chainId").asLong()!=c.chainId()||!Objects.equals(c.blockNumber(),r.path("blockNumber").asLong())||!number(r.path("gasUsed"),c.gasUsed())||!number(r.path("effectiveGasPriceWei"),c.effectiveGasPrice())||!number(r.path("additionalFeeWei"),c.additionalFeeWei())||!number(r.path("totalFeeWei"),c.totalFeeWei()))findings.add("NETWORK_FEE_RECEIPT_MISMATCH");
   }
  }
  if(journals.stream().anyMatch(j->!expected.containsKey(j.type())))findings.add("UNEXPECTED_LEDGER_LEG");
  return List.copyOf(findings);
 }
 private static boolean number(com.fasterxml.jackson.databind.JsonNode n,BigDecimal v){return n.isNumber()&&equal(n.decimalValue(),v);}
 private static boolean equal(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
 private static void add(Map<String,Leg> m,String type,String asset,BigDecimal amount,String debit,String credit,String mode,String reference,String payout){m.put(type,new Leg(asset,amount,debit,credit,mode,reference,payout));}
}
