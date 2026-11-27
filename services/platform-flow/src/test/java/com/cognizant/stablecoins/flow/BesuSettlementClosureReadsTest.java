package com.cognizant.stablecoins.flow;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BesuSettlementClosureReadsTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final BesuClosureEvidence checker=mock(BesuClosureEvidence.class);final SettlementFundingReads funding=mock(SettlementFundingReads.class);
    final BesuSettlementClosureReads reads=new BesuSettlementClosureReads(jdbc,mapper,checker,funding);
    final SettlementBinding.Execution e=BesuClosureTestData.execution();
    final BesuClosureEvidence.Comparison comparison=new BesuClosureEvidence.Comparison(BesuClosureEvidence.KIND,BesuClosureEvidence.GATE,
        new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"),mapper.createObjectNode(),List.of(),"MATCHED");
    final Map<Integer,Object> row=new HashMap<>();boolean absent;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        var document=BesuReconciliationDocuments.document(mapper,e,comparison);
        row.putAll(Map.of(1,e.executionId(),2,e.instruction().paymentId(),3,e.instruction().correlationId(),4,"MATCHED",5,BesuReconciliationDocuments.hash(mapper,document),6,document.toString()));
        doAnswer(call->{
            if(absent)return List.of();RowMapper<Object> rm=call.getArgument(1);ResultSet rs=mock(ResultSet.class);
            when(rs.getObject(1,UUID.class)).thenAnswer(i->row.get(1));when(rs.getString(anyInt())).thenAnswer(i->row.get(i.getArgument(0)));
            return List.of(rm.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),eq(BesuReconciliationDocuments.id(mapper,document)));
    }
    @Test void freshCaptureRequiresExactCommittedFundingAndAllocationFirst() {
        when(checker.compare(any())).thenReturn(comparison);assertThat(reads.current(e)).isEqualTo(comparison);
        var order=inOrder(funding,checker);order.verify(funding).requireAllocated(e);order.verify(checker).capture(e);order.verify(checker).compare(any());
    }
    @Test void missingFundingCannotReachComparison() {
        doThrow(new IllegalStateException("FUNDING_REQUIRED")).when(funding).requireAllocated(e);
        assertThatThrownBy(()->reads.current(e)).hasMessage("FUNDING_REQUIRED");verifyNoInteractions(checker);
    }
    @Test void exactMatchedReportCarriesStableIdentity() {
        var report=reads.matchingReport(e,comparison).orElseThrow();assertThat(report.sourceHash()).isEqualTo(row.get(5));
        assertThat(report.id()).isEqualTo(BesuReconciliationDocuments.id(mapper,BesuReconciliationDocuments.document(mapper,e,comparison)));
    }
    @Test void missingReportIsPending() {absent=true;assertThat(reads.matchingReport(e,comparison)).isEmpty();}
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6})
    void crossExecutionScopeStatusFingerprintAndDocumentCannotClose(int field) {
        row.put(field,field==1?UUID.randomUUID():field==6?"{}":"changed");
        assertThatThrownBy(()->reads.matchingReport(e,comparison)).isInstanceOf(RuntimeException.class);
    }
    @Test void nonmatchedComparisonCannotReadReport() {
        var pending=new BesuClosureEvidence.Comparison(comparison.kind(),comparison.closureGate(),comparison.policy(),comparison.sources(),List.of("MISSING_BESU_RECEIPT"),"PENDING");
        assertThatThrownBy(()->reads.matchingReport(e,pending)).hasMessage("BESU_COMPLETE_EVIDENCE_REQUIRED");verifyNoInteractions(jdbc);
    }
    @Test void changedSourcesRequireAnotherReportAndNumericRepresentationDoesNot() throws Exception {
        var original=BesuReconciliationDocuments.document(mapper,e,comparison);var equivalent=original.deepCopy();
        original.withObject("sources").put("value",1);equivalent.withObject("sources").put("value",new java.math.BigDecimal("1.00"));
        assertThat(BesuReconciliationDocuments.id(mapper,original)).isEqualTo(BesuReconciliationDocuments.id(mapper,equivalent));
        equivalent.withObject("sources").put("value",2);assertThat(BesuReconciliationDocuments.id(mapper,original)).isNotEqualTo(BesuReconciliationDocuments.id(mapper,equivalent));
    }
    @Test @SuppressWarnings("unchecked") void discoveryIsBoundedTerminalAccountedAndSeparatelyFiltersReportWork() throws Exception {
        UUID after=UUID.randomUUID();
        doAnswer(call->{
            String sql=call.getArgument(0);assertThat(sql).contains("join besu_ledger_receipt","s.status='CONFIRMED'","RECONCILIATION_PENDING","order by f.execution_id limit ?").doesNotContain("signed_bytes","expires_at");
            RowMapper<Object> rm=call.getArgument(1);var rs=mock(ResultSet.class);when(rs.getObject(1,UUID.class)).thenReturn(e.executionId());
            when(rs.getString(2)).thenReturn(SettlementBindingJson.write(e));when(rs.getString(3)).thenReturn(SettlementBindingJson.fingerprint(e));return List.of(rm.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),eq(true),eq(after),eq(after),eq(10));
        assertThat(new BesuClosureWorkReads(jdbc).pending(after,true).getFirst().execution()).isEqualTo(e);
    }
    @Test void discoveryRejectsChangedExecution() {
        var row=new BesuClosureWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),"0".repeat(64));
        assertThatThrownBy(row::execution).hasMessage("BESU_CLOSURE_WORK_CONFLICT");
    }
}
