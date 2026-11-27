package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.math.*;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingTestData.*;

class BesuAccountingStoreTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final BesuAccountingStore store=new BesuAccountingStore(jdbc);
    final SettlementBinding.Execution e=execution("PAY-READS");
    final Map<Integer,Object> submission=new HashMap<>(),receipt=new HashMap<>();
    boolean absent;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        submission.putAll(Map.of(1,SettlementBindingJson.write(e),2,SettlementBindingJson.fingerprint(e),3,e.context().nonceScope(),
            4,HASH,5,"CONFIRMED",6,new BigDecimal("25000"),7,new BigDecimal("3")));
        receipt.putAll(Map.of(1,HASH,2,BLOCK,3,101L,4,true,5,new BigDecimal("21000"),6,new BigDecimal("3"),7,new BigDecimal("63000"),8,e.context().nonceScope(),9,"QBFT_COMMITTED"));
        doAnswer(call->{
            if(absent) return List.of();
            String sql=call.getArgument(0);RowMapper<Object> mapper=call.getArgument(1);
            var row=sql.contains("besu_final_receipt")?receipt:submission;ResultSet rs=mock(ResultSet.class);
            when(rs.getString(anyInt())).thenAnswer(i->row.get(i.getArgument(0)));
            when(rs.getBigDecimal(anyInt())).thenAnswer(i->row.get(i.getArgument(0)));
            when(rs.getLong(anyInt())).thenAnswer(i->row.get(i.getArgument(0)));
            when(rs.getBoolean(anyInt())).thenAnswer(i->row.get(i.getArgument(0)));
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),eq(e.executionId()));
    }
    @Test void canonicalOwnerReadNeverFetchesSignedPayload() {
        var s=store.submission(e);assertThat(store.receipt(e,s)).isEqualTo(receipt(true,3));
        verify(jdbc).query(contains("select document::text,fingerprint,scope_key,tx_hash,status,gas_limit,gas_price"),any(RowMapper.class),eq(e.executionId()));
    }
    @ParameterizedTest @ValueSource(strings={"execution","fingerprint","scope","hash","unattempted","limit","price"})
    void rejectsNoncanonicalSubmissionProjection(String defect) {
        switch(defect) {
            case "execution"->submission.put(1,SettlementBindingJson.write(execution("PAY-OTHER")));
            case "fingerprint"->submission.put(2,"f".repeat(64));case "scope"->submission.put(3,"OTHER");case "hash"->submission.put(4,"bad");
            case "unattempted"->submission.put(5,"SIGNED");case "limit"->submission.put(6,new BigDecimal("30000001"));case "price"->submission.put(7,new BigDecimal("-1"));
        }
        assertThatThrownBy(()->store.submission(e)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings={"scope","hash","finality","status","gas","price","total","block"})
    void rejectsReceiptMismatchOrIncompleteFees(String defect) {
        var saved=store.submission(e);
        switch(defect) {
            case "scope"->receipt.put(8,"OTHER");case "hash"->receipt.put(1,"0x"+"c".repeat(64));case "finality"->receipt.put(9,"PENDING");
            case "status"->receipt.put(4,false);case "gas"->{receipt.put(5,new BigDecimal("26000"));receipt.put(7,new BigDecimal("78000"));}
            case "price"->{receipt.put(6,new BigDecimal("4"));receipt.put(7,new BigDecimal("84000"));}
            case "total"->receipt.put(7,BigDecimal.ONE);case "block"->receipt.put(3,0L);
        }
        assertThatThrownBy(()->store.receipt(e,saved)).isInstanceOf(RuntimeException.class);
    }
    @Test void missingOwnerReceiptOrSubmissionFailsClosed() {
        var saved=store.submission(e);absent=true;
        assertThatThrownBy(()->store.submission(e)).hasMessage("BESU_LEDGER_OWNER_EVIDENCE_REQUIRED");
        assertThatThrownBy(()->store.receipt(e,saved)).hasMessage("BESU_LEDGER_OWNER_EVIDENCE_REQUIRED");
    }
    @Test void missingOrTrailingReceiptFieldsCannotBecomeZeroFeeEvidence() {
        var evidence=BesuAccountingEvidence.allocation(e,"SIM-1").withReceipt(receipt(false,0));
        assertThat(BesuAccountingEvidence.read(evidence.json())).isEqualTo(evidence);
        assertThatThrownBy(()->BesuAccountingEvidence.read(evidence.json().replace("\"success\":false,",""))).hasMessage("BESU_ACCOUNTING_EVIDENCE_INVALID");
        assertThatThrownBy(()->BesuAccountingEvidence.read(evidence.json()+" {} ")).hasMessage("BESU_ACCOUNTING_EVIDENCE_INVALID");
    }
}
