package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.sql.ResultSet;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SettlementFundedOwnerReadsTest {
    @ParameterizedTest @ValueSource(strings={"COMPLIANCE_APPROVED","FUNDED","FUNDING_PENDING","RECONCILED","FAILED"})
    @SuppressWarnings({"unchecked","rawtypes"}) void preparationAndSigningHaveSeparateStateRequirements(String state)throws Exception {
        var jdbc=mock(JdbcTemplate.class);var i=SettlementExecutionReadsTest.execution().instruction();
        var reads=new SettlementPreparationReads(jdbc,new ObjectMapper());
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            var rs=mock(ResultSet.class);String sql=c.getArgument(0);
            if(sql.contains("phase='INSTRUCTION'")){
                when(rs.getString(1)).thenReturn(SettlementBindingJson.write(i));when(rs.getString(2)).thenReturn(SettlementBindingJson.fingerprint(i));
            }else {
                var values=Map.of("correlation_id",i.correlationId(),"source_currency","USD","payment_rail","STABLECOIN","status",state,
                    "account_status","ACTIVE","beneficiary_status","ACTIVE","wallet_address",i.recipient(),"intent_snapshot",SettlementPreparationTest.intent(i).toString());
                when(rs.getString(anyString())).thenAnswer(a->values.get(a.getArgument(0)));
                when(rs.getBigDecimal("amount")).thenReturn(i.sourceUsd());when(rs.getObject(anyString(),eq(UUID.class))).thenReturn(i.customerId());
            }
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
        if(state.equals("FUNDED"))assertThatCode(()->reads.requireFundedInstruction(i)).doesNotThrowAnyException();
        else assertThatThrownBy(()->reads.requireFundedInstruction(i)).hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
        if(state.equals("COMPLIANCE_APPROVED"))assertThatCode(()->reads.requireInstruction(i)).doesNotThrowAnyException();
        else assertThatThrownBy(()->reads.requireInstruction(i)).hasMessage("SETTLEMENT_OWNER_INSTRUCTION_CHANGED");
    }
}
