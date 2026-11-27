package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;

/** ADR-007/016 read-only chain/ledger comparison. This is not payment closure authority. */
public final class BesuClosureEvidence {
    public static final String KIND="BESU_CHAIN_LEDGER_V1";
    public static final String GATE="PAYMENT_OWNER_INTEGRATION_PENDING";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Policy policy;
    public BesuClosureEvidence(JdbcTemplate jdbc,ObjectMapper mapper,Environment env) {
        this.jdbc=jdbc;this.mapper=mapper;this.policy=new Policy(
            env.getProperty("app.ledger.accounting.version","adr015-v1"),
            env.getProperty("app.ledger.accounting.accounts.funding-usd","110002"),
            env.getProperty("app.ledger.accounting.accounts.corporate-usd","110001"),
            env.getProperty("app.ledger.accounting.accounts.conversion-usd","110003"));
    }
    public record Policy(String usdVersion,String funding,String corporate,String conversion) {
        public Policy {
            if(usdVersion==null || usdVersion.isBlank() || funding==null || corporate==null || conversion==null
                || Set.of(funding,corporate,conversion).size()!=3) throw new IllegalArgumentException("INVALID_RECONCILIATION_POLICY");
        }
        public String version(){return "adr016-control-v1:"+usdVersion;}
    }
    public record Payment(String paymentId,String correlationId,UUID customerId,BigDecimal amount,String currency){}
    public record Submission(SettlementBinding.Execution execution,String fingerprint,String scope,String hash,
                             String status,BigInteger gasLimit,BigInteger gasPrice){}
    public record Receipt(String scope,String transactionHash,String blockHash,long blockNumber,boolean success,
                          BigInteger gasUsed,BigInteger gasPriceWei,BigInteger totalFeeWei,String finality){}
    public record Acknowledgement(UUID executionId,String paymentId,String correlationId,JsonNode evidence,
                                  UUID confirmationJournal,UUID feeJournal){}
    public record Account(String code,String asset,String type){}
    public record Snapshot(SettlementBinding.Execution execution,Payment payment,Submission submission,Receipt receipt,
                           Acknowledgement acknowledgement,List<Journal> journals,List<Account> accounts,boolean payoutPresent){
        public Snapshot {Objects.requireNonNull(execution);journals=List.copyOf(journals);accounts=List.copyOf(accounts);}
    }
    public record Comparison(String kind,String closureGate,Policy policy,JsonNode sources,List<String> findings,String status){}

    /** Read inside the caller's REPEATABLE_READ transaction. Never read signed transaction bytes or secrets. */
    public Snapshot capture(SettlementBinding.Execution e) {
        new SettlementExecutionReads(jdbc).requireClaimed(e);
        var i=e.instruction();
        var payment=jdbc.query("select p.payment_id,p.correlation_id,a.customer_id,p.amount,p.source_currency from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=?",
            (r,n)->new Payment(r.getString(1),r.getString(2),r.getObject(3,UUID.class),r.getBigDecimal(4),r.getString(5)),i.paymentId()).stream().findFirst().orElse(null);
        var submission=jdbc.query("select document,fingerprint,scope_key,tx_hash,status,gas_limit,gas_price from besu_signed_submission where execution_id=?",
            (r,n)->new Submission(SettlementBindingJson.read(r.getString(1),SettlementBinding.Execution.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6).toBigIntegerExact(),r.getBigDecimal(7).toBigIntegerExact()),e.executionId()).stream().findFirst().orElse(null);
        var receipt=jdbc.query("select scope_key,tx_hash,block_hash,block_number,success,gas_used,gas_price,total_fee_wei,finality_policy from besu_final_receipt where execution_id=?",
            (r,n)->new Receipt(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getBoolean(5),r.getBigDecimal(6).toBigIntegerExact(),r.getBigDecimal(7).toBigIntegerExact(),r.getBigDecimal(8).toBigIntegerExact(),r.getString(9)),e.executionId()).stream().findFirst().orElse(null);
        var ack=jdbc.query("select execution_id,payment_id,correlation_id,evidence,confirmation_journal,fee_journal from besu_ledger_receipt where execution_id=?",
            (r,n)->new Acknowledgement(r.getObject(1,UUID.class),r.getString(2),r.getString(3),FlowJson.value(mapper,r.getString(4),JsonNode.class),r.getObject(5,UUID.class),r.getObject(6,UUID.class)),e.executionId()).stream().findFirst().orElse(null);
        var accounts=jdbc.query("select distinct a.account_code,a.asset,a.account_type from ledger_account a join ledger_entry x on x.ledger_account_id=a.ledger_account_id join ledger_journal j on j.journal_id=x.journal_id where j.payment_id=? order by a.account_code",
            (r,n)->new Account(r.getString(1),r.getString(2),r.getString(3)),i.paymentId());
        boolean payout=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from visa_transaction where payment_id=?)",Boolean.class,i.paymentId()));
        return new Snapshot(e,payment,submission,receipt,ack,new LifecycleReadModels(jdbc,mapper).journals(i.paymentId()),accounts,payout);
    }
    public Comparison compare(Snapshot s){return compare(mapper,policy,s);}
    public static Comparison compare(ObjectMapper mapperInput,Policy policy,Snapshot s) {
        // Service-local Jackson date preferences must not change cross-owner source identity.
        var mapper=mapperInput.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var f=new TreeSet<String>();var e=s.execution();var i=e.instruction();var c=i.context();
        if(c.token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
            || !c.token().symbol().equals("DEMOUSD") || c.token().decimals()!=6
            || !c.nativeAsset().symbol().equals("BESU_GAS") || c.nativeAsset().decimals()!=18
            || c.feeModel()!=SettlementNetwork.FeeModel.EXECUTION_ONLY || i.outcome()!=SettlementBinding.Outcome.TOKEN_WALLET)
            throw new IllegalArgumentException("PRIVATE_WALLET_SETTLEMENT_REQUIRED");
        var p=s.payment();
        if(p==null) f.add("MISSING_PAYMENT");
        else if(!i.paymentId().equals(p.paymentId()) || !i.correlationId().equals(p.correlationId())
            || !i.customerId().equals(p.customerId()) || !equal(i.sourceUsd(),p.amount()) || !"USD".equals(p.currency())) f.add("PAYMENT_BINDING_MISMATCH");
        var sub=s.submission();var r=s.receipt();
        if(sub==null) f.add("MISSING_BESU_SUBMISSION");
        else {
            if(!e.equals(sub.execution()) || !SettlementBindingJson.fingerprint(e).equals(sub.fingerprint())
                || !c.nonceScope().equals(sub.scope()) || !hash(sub.hash()) || !positive(sub.gasLimit())
                || sub.gasLimit().compareTo(BigInteger.valueOf(30_000_000))>0 || !nonnegative(sub.gasPrice())) f.add("BESU_SUBMISSION_BINDING_MISMATCH");
            if(!Arrays.asList("CONFIRMED","REVERTED").contains(sub.status())) {
                if(Arrays.asList("SIGNED","ATTEMPTED","SUBMITTED","UNKNOWN").contains(sub.status())) f.add("BESU_SUBMISSION_INCOMPLETE");
                else f.add("BESU_SUBMISSION_STATUS_MISMATCH");
            }
        }
        if(r==null) f.add("MISSING_BESU_RECEIPT");
        else {
            if(!c.nonceScope().equals(r.scope()) || !hash(r.transactionHash()) || !hash(r.blockHash()) || r.blockNumber()<=0
                || !"QBFT_COMMITTED".equals(r.finality()) || !positive(r.gasUsed()) || r.gasUsed().compareTo(BigInteger.valueOf(30_000_000))>0
                || !nonnegative(r.gasPriceWei()) || !nonnegative(r.totalFeeWei())
                || (positive(r.gasUsed()) && nonnegative(r.gasPriceWei()) && !r.gasUsed().multiply(r.gasPriceWei()).equals(r.totalFeeWei()))
                || sub==null || !Objects.equals(sub.hash(),r.transactionHash()) || !Objects.equals(sub.gasPrice(),r.gasPriceWei())
                || sub.gasLimit()==null || r.gasUsed()==null || r.gasUsed().compareTo(sub.gasLimit())>0
                || !Objects.equals(sub.status(),r.success()?"CONFIRMED":"REVERTED")) f.add("BESU_RECEIPT_BINDING_MISMATCH");
            if(!r.success()) f.add("TRANSFER_REVERTED_REQUIRES_REVIEW");
        }
        if(s.payoutPresent()) f.add("UNEXPECTED_PAYOUT");
        String funding=null;
        var allocations=s.journals().stream().filter(j->"CONVERSION_DEMOUSD".equals(j.type())).toList();
        if(allocations.size()==1) {
            var value=canonical(allocations.getFirst().evidence()).path("privateSettlement").path("fundingReference");
            if(value.isTextual() && (value.textValue().matches("SIM-[A-Za-z0-9._:-]{1,96}") || SettlementFundingReads.reference(e).equals(value.textValue()))) funding=value.textValue();
        }
        if(funding==null) f.add("BESU_FUNDING_PROVENANCE_MISSING");
        var legs=new LinkedHashMap<String,Leg>();
        legs.put("FUNDING",new Leg(policy.funding(),policy.corporate(),"USD",i.sourceUsd(),funding,"SIMULATED",false));
        legs.put("CONVERSION_USD",new Leg(policy.conversion(),policy.funding(),"USD",i.sourceUsd(),e.executionId().toString(),"SIMULATED",false));
        legs.put("CONVERSION_DEMOUSD",new Leg("150001","250001","DEMOUSD",i.tokenAmount(),e.executionId().toString(),"SIMULATED",false));
        String tx=sub==null?null:sub.hash();
        legs.put("BLOCKCHAIN_SUBMISSION",new Leg("150002","150001","DEMOUSD",i.tokenAmount(),tx,"PRIVATE_DEMO",false));
        if(r!=null && r.success()) legs.put("BLOCKCHAIN_CONFIRMATION",new Leg("150003","150002","DEMOUSD",i.tokenAmount(),tx,"PRIVATE_DEMO",true));
        if(r!=null && positive(r.totalFeeWei())) legs.put("NETWORK_FEE",new Leg("550001","150004","BESU_GAS",new BigDecimal(r.totalFeeWei(),18),tx,"PRIVATE_DEMO",true));
        var expectedAccounts=new HashMap<String,Account>();
        expectedAccounts.put(policy.funding(),new Account(policy.funding(),"USD","ASSET"));
        expectedAccounts.put(policy.corporate(),new Account(policy.corporate(),"USD","ASSET"));
        expectedAccounts.put(policy.conversion(),new Account(policy.conversion(),"USD","ASSET"));
        for(String code:List.of("150001","150002","150003","250001","550001","150004")) expectedAccounts.put(code,new Account(code,code.equals("550001")||code.equals("150004")?"BESU_GAS":"DEMOUSD",code.equals("250001")?"LIABILITY":code.equals("550001")?"EXPENSE":"ASSET"));
        for(var entry:legs.entrySet()) {
            String type=entry.getKey();var leg=entry.getValue();var js=s.journals().stream().filter(j->type.equals(j.type())).toList();
            if(js.isEmpty()){f.add("MISSING_"+type);continue;}
            if(js.size()!=1){f.add("DUPLICATE_"+type);continue;}
            var j=js.getFirst();
            if(!"POSTED".equals(j.status()) || !i.paymentId().equals(j.paymentId()) || !i.correlationId().equals(j.correlationId())
                || !Objects.equals(leg.reference(),j.reference())) f.add("JOURNAL_BINDING_"+type);
            JsonNode expected=posting(mapper,e,funding,leg.mode(),leg.mode().equals("SIMULATED")?null:tx,leg.receipt()?r:null,policy.version());
            if(!canonical(expected).equals(canonical(j.evidence()))) f.add("JOURNAL_EVIDENCE_"+type);
            if(j.entries().size()!=2 || !line(j.entries(),leg.debit(),leg.asset(),leg.amount(),true)
                || !line(j.entries(),leg.credit(),leg.asset(),leg.amount(),false)) f.add("JOURNAL_ENTRIES_"+type);
            for(var code:List.of(leg.debit(),leg.credit())) if(s.accounts().stream().filter(a->a.code().equals(code)).count()!=1
                || !s.accounts().contains(expectedAccounts.get(code))) f.add("JOURNAL_ACCOUNT_"+type);
        }
        if(s.journals().stream().anyMatch(j->!legs.containsKey(j.type()))) f.add("UNEXPECTED_JOURNAL");
        var ack=s.acknowledgement();
        if(ack==null) f.add("MISSING_BESU_LEDGER_RECEIPT");
        else if(r==null || !e.executionId().equals(ack.executionId()) || !i.paymentId().equals(ack.paymentId())
            || !i.correlationId().equals(ack.correlationId()) || !canonical(privateEvidence(mapper,e,funding,r)).equals(canonical(ack.evidence()))
            || !Objects.equals(ack.confirmationJournal(),r.success()?journalId(s,"BLOCKCHAIN_CONFIRMATION"):null)
            || !Objects.equals(ack.feeJournal(),positive(r.totalFeeWei())?journalId(s,"NETWORK_FEE"):null)) f.add("BESU_LEDGER_RECEIPT_MISMATCH");
        var sources=mapper.<ObjectNode>valueToTree(s);
        // Relational row order and JSONB object key/number representations do not change source identity.
        sources.set("journals",sorted(mapper,s.journals().stream().map(j->{var node=mapper.<ObjectNode>valueToTree(j);node.set("entries",sorted(mapper,j.entries()));return node;}).toList()));
        sources.set("accounts",sorted(mapper,s.accounts()));
        var findings=List.copyOf(f);
        String status=findings.isEmpty()?"MATCHED":findings.stream().allMatch(x->x.startsWith("MISSING_")||x.endsWith("_INCOMPLETE")||x.endsWith("_MISSING"))?"PENDING":"MISMATCH";
        return new Comparison(KIND,GATE,policy,canonical(sources),findings,status);
    }
    private record Leg(String debit,String credit,String asset,BigDecimal amount,String reference,String mode,boolean receipt){}
    private static UUID journalId(Snapshot s,String type){var rows=s.journals().stream().filter(j->type.equals(j.type())).toList();return rows.size()==1?rows.getFirst().journalId():null;}
    private static boolean line(List<Entry> entries,String code,String asset,BigDecimal amount,boolean debit){return entries.stream().filter(x->code.equals(x.accountCode()) && asset.equals(x.asset()) && equal(debit?x.debit():x.credit(),amount) && equal(debit?x.credit():x.debit(),BigDecimal.ZERO)).count()==1;}
    private static boolean equal(BigDecimal a,BigDecimal b){return a!=null && b!=null && a.compareTo(b)==0;}
    private static boolean positive(BigInteger x){return x!=null && x.signum()>0;}
    private static boolean nonnegative(BigInteger x){return x!=null && x.signum()>=0;}
    private static boolean hash(String x){try{return SettlementNetwork.hash(x).equals(x);}catch(IllegalArgumentException ex){return false;}}
    private static JsonNode privateEvidence(ObjectMapper m,SettlementBinding.Execution e,String funding,Receipt r){
        var n=m.createObjectNode();n.put("executionDocument",SettlementBindingJson.write(e));n.put("executionFingerprint",SettlementBindingJson.fingerprint(e));n.put("fundingReference",funding);
        if(r==null)n.putNull("receipt");else {var receipt=m.<ObjectNode>valueToTree(r);receipt.remove(List.of("scope","finality"));n.set("receipt",receipt);}return n;
    }
    private static JsonNode posting(ObjectMapper m,SettlementBinding.Execution e,String funding,String mode,String hash,Receipt r,String version){
        var n=m.createObjectNode();n.put("mode",mode);n.put("policyVersion",version);n.putNull("terms");n.put("transactionHash",hash);n.putNull("payoutReference");n.putNull("receiptFee");n.set("privateSettlement",privateEvidence(m,e,funding,r));return n;
    }
    private static ArrayNode sorted(ObjectMapper m,List<?> values){var nodes=values.stream().map(x->m.<JsonNode>valueToTree(x)).map(BesuClosureEvidence::canonical).sorted(Comparator.comparing(JsonNode::toString)).toList();var out=m.createArrayNode();nodes.forEach(out::add);return out;}
    public static JsonNode canonical(JsonNode value){
        if(value==null||value.isNull())return NullNode.instance;
        if(value.isObject()){var out=JsonNodeFactory.instance.objectNode();var names=new TreeSet<String>();value.fieldNames().forEachRemaining(names::add);names.forEach(k->out.set(k,canonical(value.get(k))));return out;}
        if(value.isArray()){var out=JsonNodeFactory.instance.arrayNode();value.forEach(x->out.add(canonical(x)));return out;}
        if(value.isNumber())return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());return value.deepCopy();
    }
    public static String fingerprint(ObjectMapper mapper,Comparison comparison){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(mapper.valueToTree(comparison)).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
