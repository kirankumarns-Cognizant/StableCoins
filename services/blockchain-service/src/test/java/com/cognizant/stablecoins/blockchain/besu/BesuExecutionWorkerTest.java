package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuLiquidityStoreTest.*;

class BesuExecutionWorkerTest {
    final BesuExecutionWorkReads reads=mock(BesuExecutionWorkReads.class);
    final SettlementFundingReads funding=mock(SettlementFundingReads.class);
    final BesuSubmissionService submissions=mock(BesuSubmissionService.class);
    final BesuReceiptService receipts=mock(BesuReceiptService.class);
    final MockEnvironment env=new MockEnvironment();
    final BesuExecutionWorker worker=new BesuExecutionWorker(reads,funding,submissions,receipts,env);
    final SettlementBinding.Execution e=execution(CONTEXT,"PAY-WORKER","1","0.1");
    BesuExecutionWorkReads.Candidate row(SettlementBinding.Execution value,String status){
        return new BesuExecutionWorkReads.Candidate(value.executionId(),SettlementBindingJson.write(value),SettlementBindingJson.fingerprint(value),status);
    }
    @Test void workerRequiresBothExplicitGates(){
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuExecutionWorker.class)
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,()->new com.fasterxml.jackson.databind.ObjectMapper())
            .withBean(BesuSubmissionService.class,()->submissions).withBean(BesuReceiptService.class,()->receipts);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuExecutionWorker.class));
        runner.withPropertyValues("app.besu.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuExecutionWorker.class));
        runner.withPropertyValues("app.besu.workers.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuExecutionWorker.class));
        runner.withPropertyValues("app.besu.enabled=true","app.besu.workers.enabled=true")
            .run(c->assertThat(c).hasSingleBean(BesuExecutionWorker.class));
    }
    @Test void submissionDefaultsOffWithoutScanningOrSigningFreshFunding(){
        when(reads.recovering(null)).thenReturn(List.of(row(e,"SIGNED")));worker.poll();
        verify(reads,never()).funded(any());verifyNoInteractions(funding,submissions,receipts);
    }
    @Test void newWorkWaitsForCommittedAllocationBeforeSubmissionAndReceipt(){
        env.withProperty("app.besu.workers.submission-enabled","true");
        when(reads.funded(null)).thenReturn(List.of(row(e,"FUNDED")));worker.poll();
        var order=inOrder(funding,submissions,receipts);order.verify(funding).requireAllocated(e);
        order.verify(submissions).submit(e);order.verify(receipts).confirm(e);verify(submissions,never()).observeSubmission(any());
    }
    @Test void missingAllocationCannotReachSignatureOrReceipt(){
        env.withProperty("app.besu.workers.submission-enabled","true");when(reads.funded(null)).thenReturn(List.of(row(e,"FUNDED")));
        doThrow(new IllegalStateException("ALLOCATION_PENDING")).when(funding).requireAllocated(e);worker.poll();
        verifyNoInteractions(submissions,receipts);
    }
    @Test void everyAttemptedStateRecoversWithNewSubmissionsOff(){
        for(String status:List.of("ATTEMPTED","UNKNOWN","SUBMITTED")){
            when(reads.recovering(null)).thenReturn(List.of(row(e,status)));worker.poll();
        }
        verify(submissions,times(3)).observeSubmission(e);verify(receipts,times(3)).confirm(e);
        verify(submissions,never()).submit(any());verifyNoInteractions(funding);
    }
    @Test void savedUnattemptedSignatureRequiresSubmissionFlagAndAllocation(){
        env.withProperty("app.besu.workers.submission-enabled","true");when(reads.recovering(null)).thenReturn(List.of(row(e,"SIGNED")));
        worker.poll();verify(funding).requireAllocated(e);verify(submissions).submit(e);verify(receipts).confirm(e);
    }
    @Test void aFailedExecutionDoesNotBlockTheNextOne(){
        var next=execution(CONTEXT,"PAY-NEXT","1","0.1");when(reads.recovering(null)).thenReturn(List.of(row(e,"UNKNOWN"),row(next,"SUBMITTED")));
        doThrow(new IllegalStateException("provider-secret-test-marker")).when(submissions).observeSubmission(e);worker.poll();
        verify(receipts,never()).confirm(e);verify(receipts).confirm(next);
    }
    @Test void scanFailureDoesNotBlockTheIndependentLane(){
        env.withProperty("app.besu.workers.submission-enabled","true");when(reads.recovering(null)).thenThrow(new IllegalStateException("database"));
        when(reads.funded(null)).thenReturn(List.of(row(e,"FUNDED")));worker.poll();verify(submissions).submit(e);
    }
    @Test void malformedOrChangedDiscoveryCannotInvokeOwners(){
        var invalid=new BesuExecutionWorkReads.Candidate(e.executionId(),"invalid","bad","UNKNOWN");
        var changed=new BesuExecutionWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),"0".repeat(64),"UNKNOWN");
        var wrongId=new BesuExecutionWorkReads.Candidate(UUID.randomUUID(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e),"UNKNOWN");
        when(reads.recovering(null)).thenReturn(List.of(invalid,changed,wrongId,row(e,"CONFIRMED")));worker.poll();
        verifyNoInteractions(funding,submissions,receipts);
    }
    @Test void cursorAdvancesPastFullBlockedPageAndWrapsAfterEnd(){
        var page=new ArrayList<BesuExecutionWorkReads.Candidate>();
        for(int n=0;n<BesuExecutionWorkReads.BATCH_SIZE;n++)page.add(row(execution(CONTEXT,"PAY-PAGE-"+n,"1","0.1"),"SIGNED"));
        UUID last=page.getLast().id();when(reads.recovering(null)).thenReturn(page,List.of());
        when(reads.recovering(last)).thenReturn(List.of());worker.poll();worker.poll();worker.poll();
        var cursors=ArgumentCaptor.forClass(UUID.class);verify(reads,times(3)).recovering(cursors.capture());
        assertThat(cursors.getAllValues()).containsExactly(null,last,null);
    }
    @Test void restartStartsDiscoveryAgainWithoutRecreatingExecutionIdentity(){
        when(reads.recovering(null)).thenReturn(List.of(row(e,"UNKNOWN")));
        worker.poll();new BesuExecutionWorker(reads,funding,submissions,receipts,env).poll();
        verify(submissions,times(2)).observeSubmission(e);verify(submissions,never()).submit(any());
    }
    @Test void actualSubmissionOwnerAcrossWorkerRestartSignsAndBroadcastsOnce(){
        var f=new BesuSubmissionServiceTest();f.setup();env.withProperty("app.besu.workers.submission-enabled","true");
        when(reads.funded(null)).thenReturn(List.of(row(f.e,"FUNDED")),List.of());
        when(reads.recovering(null)).thenReturn(List.of(),List.of(row(f.e,"SUBMITTED")));
        new BesuExecutionWorker(reads,f.funding,f.service,receipts,env).poll();
        new BesuExecutionWorker(reads,f.funding,f.service,receipts,env).poll();
        verify(f.adapter,times(1)).signRawTransaction(anyLong(),anyString(),anyString(),any(),anyInt(),any(),any());
        verify(f.adapter,times(1)).submitRawTransaction(any());verify(receipts,times(2)).confirm(f.e);
    }
    @Test void failedFundingPageDoesNotStarveLaterExecutions(){
        env.withProperty("app.besu.workers.submission-enabled","true");var page=new ArrayList<BesuExecutionWorkReads.Candidate>();
        for(int n=0;n<BesuExecutionWorkReads.BATCH_SIZE;n++)page.add(row(execution(CONTEXT,"PAY-FUND-PAGE-"+n,"1","0.1"),"FUNDED"));
        doThrow(new IllegalStateException("ALLOCATION_PENDING")).when(funding).requireAllocated(any());
        UUID last=page.getLast().id();when(reads.funded(null)).thenReturn(page,List.of());when(reads.funded(last)).thenReturn(List.of());
        worker.poll();worker.poll();worker.poll();var cursors=ArgumentCaptor.forClass(UUID.class);
        verify(reads,times(3)).funded(cursors.capture());assertThat(cursors.getAllValues()).containsExactly(null,last,null);
        verifyNoInteractions(submissions,receipts);
    }
}
