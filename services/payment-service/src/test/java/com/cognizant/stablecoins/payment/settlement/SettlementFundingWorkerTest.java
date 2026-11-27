package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.SettlementClaimWorkReads;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementFundingWorkerTest {
    final SettlementClaimWorkReads reads=mock(SettlementClaimWorkReads.class);
    final SettlementClaimedFundingService funding=mock(SettlementClaimedFundingService.class);
    final SettlementFundingWorker worker=new SettlementFundingWorker(reads,funding);
    @Test void allThreeGatesAreRequired() {
        var runner=new ApplicationContextRunner().withUserConfiguration(SettlementFundingWorker.class)
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(SettlementClaimedFundingService.class,()->funding);
        String[] keys={"app.flow.enabled","app.besu.funding.enabled","app.besu.funding-workers.enabled"};
        for(int mask=0;mask<8;mask++) {
            var values=new ArrayList<String>();for(int n=0;n<3;n++)values.add(keys[n]+"="+((mask&(1<<n))!=0));
            final boolean enabled=mask==7;
            runner.withPropertyValues(values.toArray(String[]::new)).run(c->{
                if(enabled)assertThat(c).hasSingleBean(SettlementFundingWorker.class);
                else assertThat(c).doesNotHaveBean(SettlementFundingWorker.class);
            });
        }
    }
    @Test void usesClaimIdOnlyWithoutInventingAnActorOrExecution() {
        var id=UUID.randomUUID();when(reads.awaitingFunding(null)).thenReturn(List.of(id));worker.poll();verify(funding).fundClaimed(id);
        verify(reads,never()).execution(any());
    }
    @Test void rejectedFundingDoesNotStopTheNextClaim() {
        var bad=UUID.randomUUID();var next=UUID.randomUUID();when(reads.awaitingFunding(null)).thenReturn(List.of(bad,next));
        doThrow(new IllegalStateException("ROLE_REVOKED")).when(funding).fundClaimed(bad);worker.poll();verify(funding).fundClaimed(next);
    }
    @Test void fullDeferredPageAdvancesAndWraps() {
        var page=new ArrayList<UUID>();for(int n=0;n<10;n++)page.add(UUID.randomUUID());
        when(reads.awaitingFunding(null)).thenReturn(page,List.of());when(reads.awaitingFunding(page.getLast())).thenReturn(List.of());
        doThrow(new IllegalStateException("PENDING")).when(funding).fundClaimed(any());worker.poll();worker.poll();worker.poll();
        var cursor=ArgumentCaptor.forClass(UUID.class);verify(reads,times(3)).awaitingFunding(cursor.capture());
        assertThat(cursor.getAllValues()).containsExactly(null,page.getLast(),null);
    }
    @Test void restartReusesTheSameClaimId() {
        var id=UUID.randomUUID();when(reads.awaitingFunding(null)).thenReturn(List.of(id));worker.poll();
        new SettlementFundingWorker(reads,funding).poll();verify(funding,times(2)).fundClaimed(id);
    }
    @Test void scanFailureDoesNotEndFuturePolling() {
        var id=UUID.randomUUID();when(reads.awaitingFunding(null)).thenThrow(new IllegalStateException("DATABASE")).thenReturn(List.of(id));
        worker.poll();worker.poll();verify(funding).fundClaimed(id);
    }
}
