package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.blockchain.besu.BesuLiquidityStoreTest.*;

class BesuReservationWorkerTest {
    final SettlementClaimWorkReads reads=mock(SettlementClaimWorkReads.class);
    final BesuExecutionReservationService reservations=mock(BesuExecutionReservationService.class);
    final BesuReservationWorker worker=new BesuReservationWorker(reads,reservations);
    final SettlementBinding.Execution e=execution(CONTEXT,"PAY-RESERVATION-WORKER","1","0.1");
    @Test void bothGatesAreRequired() {
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuReservationWorker.class)
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(BesuExecutionReservationService.class,()->reservations);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuReservationWorker.class));
        runner.withPropertyValues("app.besu.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuReservationWorker.class));
        runner.withPropertyValues("app.besu.funding-workers.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuReservationWorker.class));
        runner.withPropertyValues("app.besu.enabled=true","app.besu.funding-workers.enabled=true")
            .run(c->assertThat(c).hasSingleBean(BesuReservationWorker.class));
    }
    @Test void delegatesSameSavedExecutionToReservationOwner() {
        when(reads.awaitingReservation(null)).thenReturn(List.of(e.executionId()));when(reads.execution(e.executionId())).thenReturn(e);
        worker.poll();verify(reservations).reserve(e);
    }
    @Test void invalidClaimCannotReserveAndDoesNotBlockLaterClaim() {
        var bad=UUID.randomUUID();when(reads.awaitingReservation(null)).thenReturn(List.of(bad,e.executionId()));
        when(reads.execution(bad)).thenThrow(new IllegalStateException("INVALID_CLAIM"));when(reads.execution(e.executionId())).thenReturn(e);
        worker.poll();verify(reservations).reserve(e);verifyNoMoreInteractions(reservations);
    }
    @Test void unavailableNetworkDoesNotStopOtherReservations() {
        var next=execution(CONTEXT,"PAY-RESERVATION-NEXT","1","0.1");
        when(reads.awaitingReservation(null)).thenReturn(List.of(e.executionId(),next.executionId()));
        when(reads.execution(e.executionId())).thenReturn(e);when(reads.execution(next.executionId())).thenReturn(next);
        doThrow(new IllegalStateException("NETWORK_UNAVAILABLE")).when(reservations).reserve(e);worker.poll();verify(reservations).reserve(next);
    }
    @Test void fullDeferredPageAdvancesAndWrapsInsteadOfStarvingLaterClaims() {
        var page=new ArrayList<UUID>();for(int n=0;n<10;n++)page.add(UUID.randomUUID());
        when(reads.awaitingReservation(null)).thenReturn(page,List.of());when(reads.awaitingReservation(page.getLast())).thenReturn(List.of());
        doThrow(new IllegalStateException("PENDING")).when(reads).execution(any());worker.poll();worker.poll();worker.poll();
        var cursor=ArgumentCaptor.forClass(UUID.class);verify(reads,times(3)).awaitingReservation(cursor.capture());
        assertThat(cursor.getAllValues()).containsExactly(null,page.getLast(),null);
    }
    @Test void restartRetriesOriginalIdentityWithoutCreatingAnotherClaim() {
        when(reads.awaitingReservation(null)).thenReturn(List.of(e.executionId()));when(reads.execution(e.executionId())).thenReturn(e);
        worker.poll();new BesuReservationWorker(reads,reservations).poll();verify(reservations,times(2)).reserve(e);
    }
    @Test void scanFailureIsRetriedOnTheNextPoll() {
        when(reads.awaitingReservation(null)).thenThrow(new IllegalStateException("DATABASE")).thenReturn(List.of(e.executionId()));
        when(reads.execution(e.executionId())).thenReturn(e);worker.poll();worker.poll();verify(reservations).reserve(e);
    }
}
