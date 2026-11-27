package com.cognizant.stablecoins.flow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementReservationReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final SettlementBinding.Execution e=SettlementExecutionReadsTest.execution();
    final SettlementReservationReads reads=new SettlementReservationReads(jdbc);
    final Instant now=e.review().assessedAt().plusSeconds(5);
    final Map<String,Object> row=new HashMap<>();
    @BeforeEach @SuppressWarnings({"rawtypes","unchecked"}) void setup(){
        var o=new SettlementObservation(e.instruction(),100,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
                BigDecimal.ONE,new BigDecimal("1e-18"),now.minusSeconds(1),now.plusSeconds(10));
        row.put("execution_document",SettlementBindingJson.write(e));row.put("execution_fingerprint",SettlementBindingJson.fingerprint(e));
        row.put("payment_id",e.instruction().paymentId());row.put("scope_key",e.context().nonceScope());row.put("token_contract",e.context().token().contract());
        row.put("status","PINNED");row.put("expires_at",Timestamp.from(e.review().expiresAt()));row.put("pinned_at",Timestamp.from(now));
        row.put("observation_document",SettlementBindingJson.write(o));row.put("observation_fingerprint",SettlementBindingJson.fingerprint(o));
        row.put("signed",false);row.put("resolved",false);
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{
            if(row.isEmpty())return List.of();var rs=mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getTimestamp(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            when(rs.getBoolean(anyString())).thenAnswer(a->row.get(a.getArgument(0)));
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs,0));
        });
    }
    @Test void exactFreshUnsignedPinnedCapacityIsReadable(){assertThatCode(()->reads.requireFreshPinned(e,now)).doesNotThrowAnyException();}
    @Test void missingReservationFailsClosed(){row.clear();assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"signed","resolved"}) void startedOrResolvedExecutionCannotAuthorizeFirstFunding(String key){row.put(key,true);assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"HELD","RELEASED"}) void onlyPinnedCapacityIsAccepted(String status){row.put("status",status);assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @ParameterizedTest @ValueSource(strings={"payment_id","scope_key","token_contract","execution_fingerprint","observation_fingerprint"}) void alteredProjectionFailsClosed(String key){row.put(key,"changed");assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @Test void expiredObservationCannotFund(){assertThatThrownBy(()->reads.requireFreshPinned(e,now.plusSeconds(10))).hasMessage("SETTLEMENT_OBSERVATION_NOT_CURRENT");}
    @Test void futurePinCannotFund(){row.put("pinned_at",Timestamp.from(now.plusSeconds(1)));assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @Test void pinBeforeObservationCannotFund(){row.put("pinned_at",Timestamp.from(now.minusSeconds(2)));assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @Test void missingPinCannotFund(){row.put("pinned_at",null);assertThatThrownBy(()->reads.requireFreshPinned(e,now)).hasMessage("FRESH_UNSIGNED_BESU_RESERVATION_REQUIRED");}
    @Test void expiredReviewCannotFund(){assertThatThrownBy(()->reads.requireFreshPinned(e,e.review().expiresAt())).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");verifyNoInteractions(jdbc);}
}
