package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BesuPaymentClosureWorkerTest {
    final BesuClosureWorkReads reads=mock(BesuClosureWorkReads.class);final BesuPaymentClosureService owner=mock(BesuPaymentClosureService.class);
    final SettlementBinding.Execution e=BesuPaymentClosureServiceTest.execution();
    BesuClosureWorkReads.Candidate row(){return new BesuClosureWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));}

    @Test void bothWorkerAndOwnerGatesAreRequired(){
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuPaymentClosureWorker.class).withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(BesuPaymentClosureService.class,()->owner);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuPaymentClosureWorker.class));
        runner.withPropertyValues("app.flow.enabled=true","app.besu.closure.enabled=true","app.besu.closure-workers.enabled=true").run(c->assertThat(c).hasSingleBean(BesuPaymentClosureWorker.class));
    }
    @Test void restartDelegatesOriginalExecutionToOwner(){
        when(reads.pending(null,false)).thenReturn(List.of(row()));new BesuPaymentClosureWorker(reads,owner).poll();new BesuPaymentClosureWorker(reads,owner).poll();verify(owner,times(2)).advance(e);
    }
    @Test void invalidRowAndScanFailuresDoNotInvokeOwner(){
        when(reads.pending(null,false)).thenThrow(new IllegalStateException("database")).thenReturn(List.of(new BesuClosureWorkReads.Candidate(e.executionId(),"bad","bad")));
        var worker=new BesuPaymentClosureWorker(reads,owner);worker.poll();worker.poll();verifyNoInteractions(owner);
    }
    @Test void deferredRowDoesNotBlockNextRow(){
        when(reads.pending(null,false)).thenReturn(List.of(row(),row()));doThrow(new IllegalStateException("deferred")).doNothing().when(owner).advance(e);
        new BesuPaymentClosureWorker(reads,owner).poll();verify(owner,times(2)).advance(e);
    }
    @Test void fullPageAdvancesAndEmptyPageWraps(){
        var page=new ArrayList<BesuClosureWorkReads.Candidate>();for(int n=0;n<10;n++)page.add(row());
        when(reads.pending(null,false)).thenReturn(page,List.of());when(reads.pending(e.executionId(),false)).thenReturn(List.of());
        var worker=new BesuPaymentClosureWorker(reads,owner);worker.poll();worker.poll();worker.poll();var cursor=ArgumentCaptor.forClass(UUID.class);
        verify(reads,times(3)).pending(cursor.capture(),eq(false));assertThat(cursor.getAllValues()).containsExactly(null,e.executionId(),null);
    }
}
