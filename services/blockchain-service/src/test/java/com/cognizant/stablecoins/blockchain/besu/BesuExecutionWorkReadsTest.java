package com.cognizant.stablecoins.blockchain.besu;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuExecutionWorkReadsTest {
    @Test @SuppressWarnings({"unchecked","rawtypes"}) void discoveryIsBoundedReadOnlyAndRecoveryDoesNotDependOnFundingOrPaymentState() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var queries=new ArrayList<String>();var id=UUID.randomUUID();
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            String sql=c.getArgument(0);queries.add(sql);Object[] args=(Object[])c.getRawArguments()[2];
            assertThat(args).containsExactly(id,id,BesuExecutionWorkReads.BATCH_SIZE);
            var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn(id);when(rs.getString(2)).thenReturn("document");
            when(rs.getString(3)).thenReturn("fingerprint");when(rs.getString(4)).thenReturn("UNKNOWN");
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
        var reads=new BesuExecutionWorkReads(jdbc);assertThat(reads.recovering(id).getFirst().id()).isEqualTo(id);reads.funded(id);
        assertThat(queries.getFirst()).contains("order by execution_id limit ?","'SIGNED','ATTEMPTED','UNKNOWN','SUBMITTED'")
            .doesNotContain("payment", "signed_payload", "expires_at", "for update");
        assertThat(queries.getLast()).contains("payment_besu_funding", "p.status='FUNDED'", "not exists", "order by f.execution_id limit ?");
        assertThat(queries).allSatisfy(sql->assertThat(sql.stripLeading()).startsWith("select "));
    }
}
