package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.flow.LifecycleReadModels.*;

class SettlementFundingReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper();
    final SettlementBinding.Execution e=SettlementExecutionReadsTest.execution();
    final SettlementFundingReads reads=new SettlementFundingReads(jdbc,mapper,new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"));
    final Map<String,Object> row=new HashMap<>();
    final UUID account=UUID.randomUUID(),eventId=UUID.randomUUID();
    ObjectNode event;
    @BeforeEach @SuppressWarnings({"unchecked","rawtypes"}) void setup(){
        row.put("execution_document",SettlementBindingJson.write(e));row.put("execution_fingerprint",SettlementBindingJson.fingerprint(e));
        row.put("execution_id",e.executionId());row.put("payment_id",e.instruction().paymentId());row.put("correlation_id",e.instruction().correlationId());
        row.put("customer_id",e.instruction().customerId());row.put("account_customer_id",e.instruction().customerId());
        row.put("account_id",account);row.put("sender_account_id",account);row.put("source_usd",BigDecimal.ONE);
        row.put("balance_before",BigDecimal.TEN);row.put("balance_after",new BigDecimal("9.00"));row.put("actor_id","operator");
        row.put("funded_at",Timestamp.from(e.review().assessedAt().plusSeconds(1)));
        row.put("funding_reference",SettlementFundingReads.reference(e));row.put("event_type","BesuFundingAuthorized");row.put("event_version","1.0");
        row.put("aggregate_id",e.instruction().paymentId());row.put("event_correlation_id",e.instruction().correlationId());row.put("event_id",eventId.toString());
        event=mapper.createObjectNode();event.put("eventId",eventId.toString());event.put("eventType","BesuFundingAuthorized");event.put("eventVersion","1.0");
        event.put("paymentId",e.instruction().paymentId());event.put("correlationId",e.instruction().correlationId());event.put("timestamp",e.review().assessedAt().plusSeconds(1).toString());
        var payload=event.putObject("payload");payload.set("execution",FlowJson.value(mapper,SettlementBindingJson.write(e),JsonNode.class));
        payload.put("executionFingerprint",SettlementBindingJson.fingerprint(e));payload.put("fundingReference",SettlementFundingReads.reference(e));payload.put("simulated",true);
        row.put("event_document",event.toString());
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            if(row.isEmpty())return List.of();var rs=mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getString(1)).thenAnswer(a->row.get("event_document"));
            when(rs.getObject(anyString(),eq(UUID.class))).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getBigDecimal(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getTimestamp(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
    }
    void funded(){reads.requireFunded(e,SettlementFundingReads.reference(e));}
    @Test void exactCommittedDebitAndEventAreRequired(){assertThatCode(this::funded).doesNotThrowAnyException();assertThat(reads.requireCommand(event.toString())).isEqualTo(e);}
    @Test void missingFundingFailsClosed(){row.clear();assertThatThrownBy(this::funded).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"payment_id","correlation_id","funding_reference","execution_fingerprint","actor_id","event_type","event_version","aggregate_id","event_correlation_id"})
    void alteredProjectionFailsClosed(String key){row.put(key,"");assertThatThrownBy(this::funded).isInstanceOf(IllegalStateException.class);}
    @ParameterizedTest @ValueSource(strings={"customer_id","account_customer_id","sender_account_id","account_id","execution_id"})
    void alteredOwnerFailsClosed(String key){row.put(key,UUID.randomUUID());assertThatThrownBy(this::funded).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");}
    @Test void arithmeticMismatchFailsClosed(){row.put("balance_after",new BigDecimal("8"));assertThatThrownBy(this::funded).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");}
    @Test void historicalExpiredReviewIsNotRenewedByFundingRead(){row.put("funded_at",Timestamp.from(e.review().expiresAt()));assertThatThrownBy(this::funded).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");}
    @Test void eventCannotClaimRealFiat(){((ObjectNode)event.path("payload")).put("simulated",false);row.put("event_document",event.toString());assertThatThrownBy(this::funded).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");}
    @Test void transportCannotInventAnotherEvent(){event.put("eventId",UUID.randomUUID().toString());assertThatThrownBy(()->reads.requireCommand(event.toString())).hasMessage("COMMITTED_BESU_FUNDING_REQUIRED");}
    @Test void duplicateJsonKeysRejected(){String body=event.toString().replaceFirst("\\{","{\"eventType\":\"Other\",");assertThatThrownBy(()->reads.requireCommand(body)).isInstanceOf(IllegalArgumentException.class);}
    JsonNode evidence(){
        var n=mapper.createObjectNode();n.put("mode","SIMULATED");n.put("policyVersion","adr016-control-v1:adr015-v1");
        for(String key:List.of("terms","transactionHash","payoutReference","receiptFee"))n.putNull(key);
        var p=n.putObject("privateSettlement");p.put("executionDocument",SettlementBindingJson.write(e));p.put("executionFingerprint",SettlementBindingJson.fingerprint(e));
        p.put("fundingReference",SettlementFundingReads.reference(e));p.putNull("receipt");return n;
    }
    Journal journal(String type,String debit,String credit,String asset){return new Journal(UUID.randomUUID(),e.instruction().paymentId(),e.instruction().correlationId(),type,"POSTED",
            type.equals("FUNDING")?SettlementFundingReads.reference(e):e.executionId().toString(),evidence(),List.of(new Entry(debit,asset,BigDecimal.ONE,BigDecimal.ZERO),new Entry(credit,asset,BigDecimal.ZERO,BigDecimal.ONE)));}
    List<Journal> journals(){return new ArrayList<>(List.of(journal("FUNDING","110002","110001","USD"),journal("CONVERSION_USD","110003","110002","USD"),journal("CONVERSION_DEMOUSD","150001","250001","DEMOUSD")));}
    @Test void allThreeExactPostedJournalsAcknowledgeAllocation(){assertThatCode(()->reads.validateAllocation(e,journals())).doesNotThrowAnyException();}
    @Test void missingLegCannotAuthorizeSigning(){var js=journals();js.removeLast();assertThatThrownBy(()->reads.validateAllocation(e,js)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");}
    @Test void duplicateLegCannotAuthorizeSigning(){var js=journals();js.add(js.getFirst());assertThatThrownBy(()->reads.validateAllocation(e,js)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");}
    @Test void wrongAssetCannotAuthorizeSigning(){var js=journals();js.set(2,journal("CONVERSION_DEMOUSD","150001","250001","USDC"));assertThatThrownBy(()->reads.validateAllocation(e,js)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");}
    @Test void journalEvidenceCannotClaimRealFiat(){var js=journals();((ObjectNode)js.getFirst().evidence()).put("mode","LIVE");assertThatThrownBy(()->reads.validateAllocation(e,js)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");}
    @Test void reversedJournalCannotAuthorizeSigning(){var js=journals();var j=js.getFirst();js.set(0,new Journal(j.journalId(),j.paymentId(),j.correlationId(),j.type(),"REVERSED",j.reference(),j.evidence(),j.entries()));assertThatThrownBy(()->reads.validateAllocation(e,js)).hasMessage("POSTED_BESU_ALLOCATION_REQUIRED");}
}
