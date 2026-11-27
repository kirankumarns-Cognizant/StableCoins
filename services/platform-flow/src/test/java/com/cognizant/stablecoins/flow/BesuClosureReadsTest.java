package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.mock.env.MockEnvironment;
import java.math.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes each owner projection's row mapper with representative JDBC values; no database is claimed. */
class BesuClosureReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final SettlementBinding.Execution e=BesuClosureTestData.execution();
    final String hash="0x"+"a".repeat(64),block="0x"+"b".repeat(64);
    final List<String> queries=new ArrayList<>();
    final Map<String,Object> claim=new HashMap<>();
    boolean chainPresent=true,ackPresent=true,claimPresent=true;
    @BeforeEach @SuppressWarnings({"unchecked","rawtypes"}) void setup(){
        var i=e.instruction();var r=e.review();
        claim.put("execution_document",SettlementBindingJson.write(e));claim.put("execution_fingerprint",SettlementBindingJson.fingerprint(e));claim.put("execution_id",e.executionId());claim.put("payment_id",i.paymentId());claim.put("correlation_id",i.correlationId());claim.put("customer_id",i.customerId());claim.put("instruction_id",i.intentId());claim.put("recommendation_id",r.recommendationId());claim.put("recommendation_version",r.version());claim.put("quote_id",r.quoteId());claim.put("quote_version",r.quoteVersion());claim.put("preparation_id",r.preparationId());claim.put("claimed_at",Timestamp.from(r.assessedAt().plusSeconds(1)));
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);queries.add(sql);RowMapper rowMapper=call.getArgument(1);
            Map<?,?> values;
            if(sql.contains("payment_settlement_execution_claim")){if(!claimPresent)return List.of();values=claim;}
            else if(sql.contains("from payment p join account"))values=Map.of(1,i.paymentId(),2,i.correlationId(),3,i.customerId(),4,i.sourceUsd(),5,"USD");
            else if(sql.contains("from besu_signed_submission")){if(!chainPresent)return List.of();values=Map.of(1,SettlementBindingJson.write(e),2,SettlementBindingJson.fingerprint(e),3,e.context().nonceScope(),4,hash,5,"CONFIRMED",6,new BigDecimal("30000"),7,new BigDecimal("7"));}
            else if(sql.contains("from besu_final_receipt")){if(!chainPresent)return List.of();values=Map.of(1,e.context().nonceScope(),2,hash,3,block,4,101L,5,true,6,new BigDecimal("21000"),7,new BigDecimal("7"),8,new BigDecimal("147000"),9,"QBFT_COMMITTED");}
            else if(sql.contains("from besu_ledger_receipt")){if(!ackPresent)return List.of();values=Map.of(1,e.executionId(),2,i.paymentId(),3,i.correlationId(),4,"{}",5,UUID.fromString("11111111-1111-1111-1111-111111111111"),6,UUID.fromString("22222222-2222-2222-2222-222222222222"));}
            else if(sql.contains("from ledger_account")||sql.contains("from ledger_journal"))return List.of();
            else throw new AssertionError("UNEXPECTED_READ: "+sql);
            ResultSet rs=mock(ResultSet.class,answer->{if(answer.getArguments().length==0)return RETURNS_DEFAULTS.answer(answer);var key=answer.getArgument(0);return values.get(key);});
            return List.of(rowMapper.mapRow(rs,0));
        });
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),any(Object[].class))).thenReturn(false);
    }
    BesuClosureEvidence reader(){return new BesuClosureEvidence(jdbc,mapper,new MockEnvironment());}
    @Test void mapsExactClaimPaymentSubmissionReceiptAndAcknowledgement(){var s=reader().capture(e);assertThat(s.payment().currency()).isEqualTo("USD");assertThat(s.payment().customerId()).isEqualTo(e.instruction().customerId());assertThat(s.submission().execution()).isEqualTo(e);assertThat(s.receipt().totalFeeWei()).isEqualTo(BigInteger.valueOf(147000));assertThat(s.receipt().finality()).isEqualTo("QBFT_COMMITTED");assertThat(s.acknowledgement().executionId()).isEqualTo(e.executionId());}
    @Test void missingOwnerEvidenceRemainsAbsentRatherThanInvented(){chainPresent=false;ackPresent=false;var s=reader().capture(e);assertThat(s.submission()).isNull();assertThat(s.receipt()).isNull();assertThat(s.acknowledgement()).isNull();}
    @Test void unclaimedCallerCannotCaptureOtherOwnerEvidence(){claimPresent=false;assertThatThrownBy(()->reader().capture(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");assertThat(queries).hasSize(1);}
    @Test void alteredClaimFingerprintCannotAuthorizeReads(){claim.put("execution_fingerprint","0".repeat(64));assertThatThrownBy(()->reader().capture(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");assertThat(queries).hasSize(1);}
    @Test void readsOnlySelectedPublicEvidenceColumnsAndNeverSigningBytes(){reader().capture(e);assertThat(queries).allMatch(q->q.startsWith("select "));assertThat(queries).noneMatch(q->q.contains("signed_payload")||q.contains("password")||q.contains("private_key"));assertThat(queries).anyMatch(q->q.contains("p.source_currency"));}
}
