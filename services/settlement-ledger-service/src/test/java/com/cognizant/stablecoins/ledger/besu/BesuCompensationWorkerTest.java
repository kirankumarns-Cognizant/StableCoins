package com.cognizant.stablecoins.ledger.besu;
import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
class BesuCompensationWorkerTest {
    @Test void deferredOrMalformedWorkCannotStarveLaterValidCandidate(){
        var reads=mock(BesuRevertWorkReads.class);var owner=mock(BesuCompensationService.class);var e=BesuAccountingTestData.execution("PAY-RECOVERY");
        var malformed=new BesuClosureWorkReads.Candidate(UUID.randomUUID(),"{}","wrong");
        var valid=new BesuClosureWorkReads.Candidate(e.executionId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e));
        when(reads.pending(null,true)).thenReturn(List.of(malformed,valid));
        new BesuCompensationWorker(reads,owner).poll();verify(owner).compensate(e);
    }
    @Test void transientScanFailureIsRetriedWithoutOwnerSideEffects(){
        var reads=mock(BesuRevertWorkReads.class);var owner=mock(BesuCompensationService.class);when(reads.pending(null,true)).thenThrow(new IllegalStateException("DB_UNAVAILABLE")).thenReturn(List.of());
        var worker=new BesuCompensationWorker(reads,owner);worker.poll();worker.poll();verifyNoInteractions(owner);verify(reads,times(2)).pending(null,true);
    }
}
