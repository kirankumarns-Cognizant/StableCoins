package com.cognizant.stablecoins.payment.flow;
import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class BesuPaymentFailureWorkerTest {
    @Test void deferredOrMalformedWorkCannotStarveLaterValidCandidate(){
        var reads=mock(BesuRevertWorkReads.class);var owner=mock(BesuPaymentFailureService.class);var e=BesuPaymentClosureServiceTest.execution();
        var malformed=new BesuClosureWorkReads.Candidate(UUID.randomUUID(),"{}","wrong");
        var valid=new BesuClosureWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        when(reads.pending(null,false)).thenReturn(List.of(malformed,valid));
        new BesuPaymentFailureWorker(reads,owner).poll();verify(owner).advance(e);
    }
    @Test void transientScanFailureIsRetriedWithoutOwnerSideEffects(){
        var reads=mock(BesuRevertWorkReads.class);var owner=mock(BesuPaymentFailureService.class);when(reads.pending(null,false)).thenThrow(new IllegalStateException("DB_UNAVAILABLE")).thenReturn(List.of());
        var worker=new BesuPaymentFailureWorker(reads,owner);worker.poll();worker.poll();verifyNoInteractions(owner);verify(reads,times(2)).pending(null,false);
    }
}
