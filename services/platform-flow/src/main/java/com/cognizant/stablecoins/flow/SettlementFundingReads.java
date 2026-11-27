package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;

/** Read-only owner evidence. Committed funding and complete allocation precede a new signature. */
public class SettlementFundingReads {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BesuClosureEvidence.Policy policy;
    public SettlementFundingReads(JdbcTemplate jdbc,ObjectMapper mapper,BesuClosureEvidence.Policy policy) {
        this.jdbc=Objects.requireNonNull(jdbc);this.mapper=Objects.requireNonNull(mapper).copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);this.policy=Objects.requireNonNull(policy);
    }
    public static String reference(SettlementBinding.Execution e){return "SIMULATED-BESU-"+e.executionId();}
    public void requireFunded(SettlementBinding.Execution e,String reference) {
        if(!reference(e).equals(reference) || e.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                || !"DEMOUSD".equals(e.context().token().symbol()) || e.context().token().decimals()!=6
                || !"BESU_GAS".equals(e.context().nativeAsset().symbol()) || e.context().nativeAsset().decimals()!=18
                || e.context().feeModel()!=SettlementNetwork.FeeModel.EXECUTION_ONLY
                || e.instruction().outcome()!=SettlementBinding.Outcome.TOKEN_WALLET) throw fundingConflict();
        var rows=jdbc.query("""
                select f.*,p.sender_account_id,a.customer_id account_customer_id,o.event_type,o.event_version,
                       o.aggregate_id,o.correlation_id event_correlation_id,o.payload::text event_document
                from payment_besu_funding f join payment p on p.payment_id=f.payment_id
                join account a on a.account_id=f.account_id join payment_outbox_event o on o.event_id=f.event_id
                where f.execution_id=?
                """,(rs,n)->{
            var i=e.instruction();
            var saved=SettlementBindingJson.read(rs.getString("execution_document"),SettlementBinding.Execution.class);
            var before=rs.getBigDecimal("balance_before");var after=rs.getBigDecimal("balance_after");
            var funded=rs.getTimestamp("funded_at").toInstant();
            if(!e.equals(saved) || !e.executionId().equals(rs.getObject("execution_id",UUID.class))
                    || !SettlementBindingJson.fingerprint(e).equals(rs.getString("execution_fingerprint"))
                    || !i.paymentId().equals(rs.getString("payment_id")) || !i.correlationId().equals(rs.getString("correlation_id"))
                    || !i.customerId().equals(rs.getObject("customer_id",UUID.class))
                    || !i.customerId().equals(rs.getObject("account_customer_id",UUID.class))
                    || !Objects.equals(rs.getObject("account_id",UUID.class),rs.getObject("sender_account_id",UUID.class))
                    || rs.getObject("account_id",UUID.class)==null || !equal(i.sourceUsd(),rs.getBigDecimal("source_usd"))
                    || before==null || after==null || after.signum()<0 || !equal(before.subtract(after),i.sourceUsd())
                    || before.stripTrailingZeros().scale()>2 || after.stripTrailingZeros().scale()>2
                    || rs.getString("actor_id")==null || rs.getString("actor_id").isBlank()
                    || !reference.equals(rs.getString("funding_reference"))
                    || !"BesuFundingAuthorized".equals(rs.getString("event_type")) || !"1.0".equals(rs.getString("event_version"))
                    || !i.paymentId().equals(rs.getString("aggregate_id")) || !i.correlationId().equals(rs.getString("event_correlation_id"))) throw fundingConflict();
            e.review().requireFresh(funded); // Historical authority at debit, not renewed by this read.
            var event=FlowJson.value(mapper,rs.getString("event_document"),JsonNode.class);
            var expected=mapper.createObjectNode();expected.put("eventId",UUID.fromString(rs.getString("event_id")).toString());
            expected.put("eventType","BesuFundingAuthorized");expected.put("eventVersion","1.0");
            expected.put("paymentId",i.paymentId());expected.put("correlationId",i.correlationId());
            expected.set("payload",command(e));
            if(!event.isObject() || !event.path("timestamp").isTextual()) throw fundingConflict();
            if(!Instant.parse(event.path("timestamp").textValue()).equals(funded)) throw fundingConflict();
            var actual=event.deepCopy();((ObjectNode)actual).remove("timestamp");
            if(!BesuClosureEvidence.canonical(expected).equals(BesuClosureEvidence.canonical(actual))) throw fundingConflict();
            return true;
        },e.executionId());
        if(rows.size()!=1)throw fundingConflict();
    }
    /** Transport is not authority: the exact command must already exist in the payment owner's outbox. */
    public SettlementBinding.Execution requireCommand(String document) {
        if(document==null || document.length()>131072)throw fundingConflict();
        var event=FlowJson.value(mapper,document,JsonNode.class);
        var e=SettlementBindingJson.read(event.path("payload").path("execution").toString(),SettlementBinding.Execution.class);
        requireFunded(e,event.path("payload").path("fundingReference").asText());
        var rows=jdbc.query("select o.payload::text from payment_besu_funding f join payment_outbox_event o on o.event_id=f.event_id where f.execution_id=?",
                (rs,n)->FlowJson.value(mapper,rs.getString(1),JsonNode.class),e.executionId());
        if(rows.size()!=1 || !BesuClosureEvidence.canonical(event).equals(BesuClosureEvidence.canonical(rows.getFirst())))throw fundingConflict();
        return e;
    }
    private JsonNode command(SettlementBinding.Execution e){
        var n=mapper.createObjectNode();n.set("execution",FlowJson.value(mapper,SettlementBindingJson.write(e),JsonNode.class));
        n.put("executionFingerprint",SettlementBindingJson.fingerprint(e));n.put("fundingReference",reference(e));n.put("simulated",true);return n;
    }
    public void requireAllocated(SettlementBinding.Execution e) {
        requireFunded(e,reference(e));
        validateAllocation(e,new LifecycleReadModels(jdbc,mapper).journals(e.instruction().paymentId()));
    }
    /** Three immutable POSTED journals are the durable ledger acknowledgement; no mutable flag suffices. */
    public void validateAllocation(SettlementBinding.Execution e,List<Journal> journals) {
        var legs=List.of(new Leg("FUNDING",policy.funding(),policy.corporate(),"USD",e.instruction().sourceUsd(),reference(e)),
                new Leg("CONVERSION_USD",policy.conversion(),policy.funding(),"USD",e.instruction().sourceUsd(),e.executionId().toString()),
                new Leg("CONVERSION_DEMOUSD","150001","250001","DEMOUSD",e.instruction().tokenAmount(),e.executionId().toString()));
        for(var leg:legs){
            var rows=journals.stream().filter(j->leg.type().equals(j.type())).toList();
            if(rows.size()!=1)throw allocationConflict();var j=rows.getFirst();
            if(j.journalId()==null || !"POSTED".equals(j.status()) || !e.instruction().paymentId().equals(j.paymentId())
                    || !e.instruction().correlationId().equals(j.correlationId()) || !leg.reference().equals(j.reference())
                    || !BesuClosureEvidence.canonical(posting(e)).equals(BesuClosureEvidence.canonical(j.evidence()))
                    || j.entries().size()!=2 || !line(j.entries(),leg.debit(),leg.asset(),leg.amount(),true)
                    || !line(j.entries(),leg.credit(),leg.asset(),leg.amount(),false))throw allocationConflict();
        }
    }
    private JsonNode posting(SettlementBinding.Execution e){
        var n=mapper.createObjectNode();n.put("mode","SIMULATED");n.put("policyVersion",policy.version());
        for(String key:List.of("terms","transactionHash","payoutReference","receiptFee"))n.putNull(key);
        var p=n.putObject("privateSettlement");p.put("executionDocument",SettlementBindingJson.write(e));
        p.put("executionFingerprint",SettlementBindingJson.fingerprint(e));p.put("fundingReference",reference(e));p.putNull("receipt");return n;
    }
    private record Leg(String type,String debit,String credit,String asset,BigDecimal amount,String reference){}
    private static boolean equal(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
    private static boolean line(List<Entry> lines,String code,String asset,BigDecimal amount,boolean debit){return lines.stream().filter(x->code.equals(x.accountCode())&&asset.equals(x.asset())&&equal(debit?x.debit():x.credit(),amount)&&equal(debit?x.credit():x.debit(),BigDecimal.ZERO)).count()==1;}
    private static IllegalStateException fundingConflict(){return new IllegalStateException("COMMITTED_BESU_FUNDING_REQUIRED");}
    private static IllegalStateException allocationConflict(){return new IllegalStateException("POSTED_BESU_ALLOCATION_REQUIRED");}
}
