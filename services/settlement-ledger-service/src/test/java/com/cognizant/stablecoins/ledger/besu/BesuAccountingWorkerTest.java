package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.*;
import org.mockito.ArgumentCaptor;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingTestData.*;

class BesuAccountingWorkerTest {
    final BesuAccountingWorkReads reads=mock(BesuAccountingWorkReads.class);
    final BesuAccountingService accounting=mock(BesuAccountingService.class);
    final BesuAccountingWorker worker=new BesuAccountingWorker(reads,accounting);
    final SettlementBinding.Execution e=execution("PAY-WORKER");
    BesuAccountingWorkReads.Candidate row(SettlementBinding.Execution execution,String status) {
        return new BesuAccountingWorkReads.Candidate(execution.executionId(),SettlementBindingJson.write(execution),SettlementBindingJson.fingerprint(execution),status);
    }
    @Test void bothExplicitGatesAreRequired() {
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuAccountingWorker.class)
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(BesuAccountingService.class,()->accounting);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuAccountingWorker.class));
        runner.withPropertyValues("app.besu.accounting.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuAccountingWorker.class));
        runner.withPropertyValues("app.besu.accounting-workers.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuAccountingWorker.class));
        runner.withPropertyValues("app.besu.accounting.enabled=true","app.besu.accounting-workers.enabled=true")
            .run(c->assertThat(c).hasSingleBean(BesuAccountingWorker.class));
    }
    @ParameterizedTest @ValueSource(strings={"ATTEMPTED","UNKNOWN","SUBMITTED"})
    void uncertainTransferOnlyPostsInFlight(String status) {
        when(reads.pending(null)).thenReturn(List.of(row(e,status)));worker.poll();
        verify(accounting).submitted(e);verify(accounting,never()).resolve(any());
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","REVERTED"})
    void terminalReceiptAlwaysFollowsSubmissionAccounting(String status) {
        when(reads.pending(null)).thenReturn(List.of(row(e,status)));worker.poll();
        var order=inOrder(accounting);order.verify(accounting).submitted(e);order.verify(accounting).resolve(e);
    }
    @Test void failedSubmissionDoesNotResolveButDoesNotBlockNextPayment() {
        var next=execution("PAY-NEXT");when(reads.pending(null)).thenReturn(List.of(row(e,"CONFIRMED"),row(next,"REVERTED")));
        doThrow(new IllegalStateException("secret-test-marker")).when(accounting).submitted(e);worker.poll();
        verify(accounting,never()).resolve(e);verify(accounting).submitted(next);verify(accounting).resolve(next);
    }
    @Test void failedReceiptIsRediscoveredUsingOriginalIdentityAfterRestart() {
        when(reads.pending(null)).thenReturn(List.of(row(e,"CONFIRMED")));
        when(accounting.resolve(e)).thenThrow(new IllegalStateException("RECEIPT_PENDING")).thenReturn(null);
        worker.poll();new BesuAccountingWorker(reads,accounting).poll();
        verify(accounting,times(2)).submitted(e);verify(accounting,times(2)).resolve(e);
    }
    @Test void failedScanIsRetriedWithoutAnyPosting() {
        when(reads.pending(null)).thenThrow(new IllegalStateException("database")).thenReturn(List.of(row(e,"CONFIRMED")));
        worker.poll();verifyNoInteractions(accounting);worker.poll();verify(accounting).resolve(e);
    }
    @Test void changedIdentityFingerprintDocumentAndUnattemptedSignatureCannotPost() {
        when(reads.pending(null)).thenReturn(List.of(
            new BesuAccountingWorkReads.Candidate(UUID.randomUUID(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e),"CONFIRMED"),
            new BesuAccountingWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),"0".repeat(64),"CONFIRMED"),
            new BesuAccountingWorkReads.Candidate(e.executionId(),"invalid","invalid","CONFIRMED"),row(e,"SIGNED")));
        worker.poll();verifyNoInteractions(accounting);
    }
    @Test void boundedCursorAdvancesPastBlockedRowsThenWraps() {
        var page=new ArrayList<BesuAccountingWorkReads.Candidate>();
        for(int n=0;n<BesuAccountingWorkReads.BATCH_SIZE;n++)page.add(row(execution("PAY-PAGE-"+n),"CONFIRMED"));
        UUID last=page.getLast().id();when(reads.pending(null)).thenReturn(page,List.of());when(reads.pending(last)).thenReturn(List.of());
        doThrow(new IllegalStateException("ALLOCATION_PENDING")).when(accounting).submitted(any());
        worker.poll();worker.poll();worker.poll();var cursors=ArgumentCaptor.forClass(UUID.class);
        verify(reads,times(3)).pending(cursors.capture());assertThat(cursors.getAllValues()).containsExactly(null,last,null);
        verify(accounting,never()).resolve(any());
    }
    @Test @SuppressWarnings("unchecked") void discoveryUsesBoundedReadOnlyParametersAndExcludesAcknowledgedWork() throws Exception {
        var jdbc=mock(JdbcTemplate.class);UUID after=UUID.randomUUID();
        doAnswer(call->{
            String sql=call.getArgument(0);assertThat(sql).contains("'CONFIRMED','REVERTED'","not exists(select 1 from besu_ledger_receipt","order by s.execution_id limit ?")
                .doesNotContain("signed_bytes","payment p","expires_at","for update");
            RowMapper<Object> mapper=call.getArgument(1);var rs=mock(ResultSet.class);
            when(rs.getObject(1,UUID.class)).thenReturn(e.executionId());when(rs.getString(2)).thenReturn(SettlementBindingJson.write(e));
            when(rs.getString(3)).thenReturn(SettlementBindingJson.fingerprint(e));when(rs.getString(4)).thenReturn("CONFIRMED");
            return List.of(mapper.mapRow(rs,0));
        }).when(jdbc).query(anyString(),any(RowMapper.class),eq(after),eq(after),eq(BesuAccountingWorkReads.BATCH_SIZE));
        var rows=new BesuAccountingWorkReads(jdbc).pending(after);assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().execution()).isEqualTo(e);assertThat(rows.getFirst().terminal()).isTrue();
    }
}
