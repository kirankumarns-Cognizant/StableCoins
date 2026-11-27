package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementClaimWorkReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final SettlementClaimWorkReads reads=new SettlementClaimWorkReads(jdbc);
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void boundedDiscoverySeparatesReservationAndFundingWithoutOwnerWrites() throws Exception {
        var id=UUID.randomUUID();var queries=new ArrayList<String>();
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            queries.add(c.getArgument(0));assertThat((Object[])c.getRawArguments()[2]).containsExactly(id,id,10);
            var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn(id);
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
        assertThat(reads.awaitingReservation(id)).containsExactly(id);assertThat(reads.awaitingFunding(id)).containsExactly(id);
        assertThat(queries).allSatisfy(sql->assertThat(sql).startsWith("select ").contains("p.status='COMPLIANCE_APPROVED'",
            "not exists(select 1 from payment_besu_funding", "not exists(select 1 from besu_signed_submission",
            "order by c.execution_id limit ?").doesNotContain("for update","signed_payload"));
        assertThat(queries.getFirst()).contains("not exists(select 1 from besu_execution_reservation");
        assertThat(queries.getLast()).contains("join besu_liquidity_commitment", "l.status='PINNED'");
    }
    @SuppressWarnings({"unchecked","rawtypes"}) void projection(String doc,String fingerprint) {
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            var rs=mock(ResultSet.class);when(rs.getString(1)).thenReturn(doc);when(rs.getString(2)).thenReturn(fingerprint);
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
    }
    @Test void exactProjectionLoadsSameExecution() {
        var e=SettlementExecutionReadsTest.execution();projection(SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        assertThat(reads.execution(e.executionId())).isEqualTo(e);
    }
    @Test void missingProjectionFailsClosed() {
        assertThatThrownBy(()->reads.execution(UUID.randomUUID())).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @Test void changedFingerprintCannotBecomeReservationWork() {
        var e=SettlementExecutionReadsTest.execution();projection(SettlementBindingJson.write(e),"0".repeat(64));
        assertThatThrownBy(()->reads.execution(e.executionId())).hasMessage("BESU_CLAIM_WORK_BINDING_CONFLICT");
    }
    @Test void projectionCannotBeMovedToAnotherExecution() {
        var e=SettlementExecutionReadsTest.execution();projection(SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        assertThatThrownBy(()->reads.execution(UUID.randomUUID())).hasMessage("BESU_CLAIM_WORK_BINDING_CONFLICT");
    }
}
