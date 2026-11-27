package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.cognizant.stablecoins.flow.SettlementNetwork.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SettlementExecutionReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final SettlementExecutionReads reads=new SettlementExecutionReads(jdbc);
    final Map<String,Object> row=new HashMap<>();
    static final Instant NOW=Instant.parse("2026-11-26T10:00:00Z");
    static SettlementBinding.Execution execution() {
        var chain=new Chain("besu-local",20261125,"0x"+"a".repeat(64),Classification.PRIVATE_DEMO);
        var context=new Context(new Token(chain,"0x"+"b".repeat(40),"DEMOUSD",6,"0x"+"c".repeat(64)),
                new NativeAsset(chain,"BESU_GAS",18),"0x"+"d".repeat(40),FeeModel.EXECUTION_ONLY,"besu-v1");
        var i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),"PAY-CLAIM","CORR-CLAIM",context,
                "0x"+"e".repeat(40),BigDecimal.ONE,BigDecimal.ONE,SettlementBinding.Outcome.TOKEN_WALLET,
                BigDecimal.ONE,NOW.plusSeconds(300));
        return new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),2,
                UUID.randomUUID(),UUID.randomUUID(),1,"PROVIDER",i,BigDecimal.ONE,new BigDecimal("0.1"),NOW,NOW.plusSeconds(60)));
    }
    final SettlementBinding.Execution e=execution();
    @BeforeEach @SuppressWarnings({"rawtypes","unchecked"}) void setup() {
        var r=e.review();var i=e.instruction();
        row.put("execution_document",SettlementBindingJson.write(e));
        row.put("execution_fingerprint",SettlementBindingJson.fingerprint(e));
        row.put("execution_id",e.executionId());row.put("payment_id",i.paymentId());row.put("correlation_id",i.correlationId());
        row.put("customer_id",i.customerId());row.put("instruction_id",i.intentId());
        row.put("recommendation_id",r.recommendationId());row.put("recommendation_version",r.version());
        row.put("quote_id",r.quoteId());row.put("quote_version",r.quoteVersion());row.put("preparation_id",r.preparationId());
        row.put("claimed_at",Timestamp.from(NOW));
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            if(row.isEmpty())return List.of();
            var rs=mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(a->(String)row.get(a.getArgument(0)));
            when(rs.getObject(anyString(),eq(UUID.class))).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getInt(anyString())).thenAnswer(a->(Integer)row.get(a.getArgument(0)));
            when(rs.getTimestamp(anyString())).thenAnswer(a->(Timestamp)row.get(a.getArgument(0)));
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
    }
    @Test void exactImmutableProjectionConfirmsOwnerClaim() {
        assertThatCode(()->reads.requireClaimed(e)).doesNotThrowAnyException();
    }
    @Test void missingClaimRejectsCallerSuppliedTerms() {
        row.clear();
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @Test void historicalClaimWithoutProjectionCannotGrantReservationAuthority() {
        row.remove("execution_document");row.remove("execution_fingerprint");
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("INVALID_SETTLEMENT_BINDING");
    }
    @ParameterizedTest
    @ValueSource(strings={"execution_id","customer_id","instruction_id","recommendation_id","quote_id","preparation_id"})
    void mismatchedScopedIdentityFailsClosed(String field) {
        row.put(field,UUID.randomUUID());
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @ParameterizedTest
    @ValueSource(strings={"payment_id","correlation_id","execution_fingerprint"})
    void mismatchedTextOrFingerprintFailsClosed(String field) {
        row.put(field,"changed");
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @ParameterizedTest
    @ValueSource(strings={"recommendation_version","quote_version"})
    void mismatchedVersionFailsClosed(String field) {
        row.put(field,99);
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @Test void ownerClaimMustHaveBeenCapturedWithinItsReviewWindow() {
        row.put("claimed_at",Timestamp.from(e.review().expiresAt()));
        assertThatThrownBy(()->reads.requireClaimed(e)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void projectionIsNotTransferableToAnotherExecution() {
        var other=new SettlementBinding.Execution(UUID.randomUUID(),e.review());
        assertThatThrownBy(()->reads.requireClaimed(other)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
}