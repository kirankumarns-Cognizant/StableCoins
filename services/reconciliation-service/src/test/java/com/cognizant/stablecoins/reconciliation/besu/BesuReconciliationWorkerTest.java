package com.cognizant.stablecoins.reconciliation.besu;
import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BesuReconciliationWorkerTest {
    final BesuClosureWorkReads reads=mock(BesuClosureWorkReads.class);final BesuReconciliationService owner=mock(BesuReconciliationService.class);
    final SettlementBinding.Execution e=execution();
    BesuClosureWorkReads.Candidate row(){return new BesuClosureWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));}
    static com.cognizant.stablecoins.flow.SettlementBinding.Execution execution(){var f=new BesuReconciliationServiceTest();f.setup();return f.e;}
    @Test void bothWorkerAndOwnerGatesAreRequired(){
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuReconciliationWorker.class).withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(BesuReconciliationService.class,()->owner);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuReconciliationWorker.class));
        runner.withPropertyValues("app.besu.reconciliation.enabled=true","app.besu.closure-workers.enabled=true").run(c->assertThat(c).hasSingleBean(BesuReconciliationWorker.class));
    }
    @Test void restartDelegatesOriginalExecutionToOwner(){
        when(reads.pending(null,true)).thenReturn(List.of(row()));new BesuReconciliationWorker(reads,owner).poll();new BesuReconciliationWorker(reads,owner).poll();verify(owner,times(2)).runCurrent(e);
    }
    @Test void invalidRowAndScanFailuresDoNotInvokeOwner(){
        when(reads.pending(null,true)).thenThrow(new IllegalStateException("database")).thenReturn(List.of(new BesuClosureWorkReads.Candidate(e.executionId(),"bad","bad")));
        var worker=new BesuReconciliationWorker(reads,owner);worker.poll();worker.poll();verifyNoInteractions(owner);
    }
    @Test void deferredRowDoesNotBlockNextRow(){
        when(reads.pending(null,true)).thenReturn(List.of(row(),row()));when(owner.runCurrent(e)).thenThrow(new IllegalStateException("deferred")).thenReturn(null);
        new BesuReconciliationWorker(reads,owner).poll();verify(owner,times(2)).runCurrent(e);
    }
    @Test void fullPageAdvancesAndEmptyPageWraps(){
        var page=new ArrayList<BesuClosureWorkReads.Candidate>();for(int n=0;n<10;n++)page.add(row());
        when(reads.pending(null,true)).thenReturn(page,List.of());when(reads.pending(e.executionId(),true)).thenReturn(List.of());
        var worker=new BesuReconciliationWorker(reads,owner);worker.poll();worker.poll();worker.poll();var cursor=ArgumentCaptor.forClass(UUID.class);
        verify(reads,times(3)).pending(cursor.capture(),eq(true));assertThat(cursor.getAllValues()).containsExactly(null,e.executionId(),null);
    }
}
