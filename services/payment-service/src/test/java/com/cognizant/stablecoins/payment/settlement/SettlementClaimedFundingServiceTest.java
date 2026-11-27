package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementClaimedFundingServiceTest {
    final SettlementExecutionClaimStore claims=mock(SettlementExecutionClaimStore.class);
    final SettlementFundingService funding=mock(SettlementFundingService.class);
    final SettlementClaimedFundingService service=new SettlementClaimedFundingService(claims,funding);
    SettlementExecutionClaim claim(SettlementFundingServiceTest f) {
        var at=f.e.review().assessedAt();
        var approvals=List.of(new SettlementExecutionClaim.Approval("approver-a",UUID.randomUUID(),at.plusSeconds(1),UUID.randomUUID(),at.plusSeconds(2)),
            new SettlementExecutionClaim.Approval("approver-b",UUID.randomUUID(),at.plusSeconds(1),UUID.randomUUID(),at.plusSeconds(2)));
        return new SettlementExecutionClaim(f.e,f.actor.actorId(),"private-v1",2,approvals,at.plusSeconds(3));
    }
    @Test void rehydratesOriginalOperatorOnlyFromCanonicalStoredClaim() {
        var f=new SettlementFundingServiceTest();f.setup();var claim=claim(f);when(claims.byId(f.e.executionId())).thenReturn(Optional.of(claim));
        service.fundClaimed(f.e.executionId());verify(funding).fund(f.e,new AuthorizedCustomer(f.e.instruction().customerId(),claim.requestedBy()));
    }
    @Test void missingClaimCannotCallFunding() {
        assertThatThrownBy(()->service.fundClaimed(UUID.randomUUID())).hasMessage("SETTLEMENT_EXECUTION_CLAIM_REQUIRED");verifyNoInteractions(funding);
    }
    @Test void discoveredIdCannotBeSubstitutedForAnotherStoredClaim() {
        var f=new SettlementFundingServiceTest();f.setup();var other=UUID.randomUUID();when(claims.byId(other)).thenReturn(Optional.of(claim(f)));
        assertThatThrownBy(()->service.fundClaimed(other)).hasMessage("SETTLEMENT_EXECUTION_CLAIM_REQUIRED");verifyNoInteractions(funding);
    }
    @Test void realFundingBoundaryPreservesOneDebitAndOneEventAcrossRestart() {
        var f=new SettlementFundingServiceTest();f.setup();when(claims.byId(f.e.executionId())).thenReturn(Optional.of(claim(f)));
        var receipt=new SettlementClaimedFundingService(claims,f.service).fundClaimed(f.e.executionId());
        when(f.store.find(f.e)).thenReturn(Optional.of(receipt));
        assertThat(new SettlementClaimedFundingService(claims,f.service).fundClaimed(f.e.executionId())).isSameAs(receipt);
        assertThat(f.account.getBalance()).isEqualByComparingTo("15");verify(f.accounts,times(1)).save(any());verify(f.outbox,times(1)).saveAndFlush(any());
    }
    @Test void revokedAuthorityStillReachesExistingRecheckAndCannotDebit() {
        var f=new SettlementFundingServiceTest();f.setup();when(claims.byId(f.e.executionId())).thenReturn(Optional.of(claim(f)));
        doThrow(new IllegalStateException("ROLE_REVOKED")).when(f.authority).requireFundingAuthority(f.e,f.actor);
        assertThatThrownBy(()->new SettlementClaimedFundingService(claims,f.service).fundClaimed(f.e.executionId())).hasMessage("ROLE_REVOKED");f.noDebit();
    }
    @Test void staleReservationStillCannotDebit() {
        var f=new SettlementFundingServiceTest();f.setup();when(claims.byId(f.e.executionId())).thenReturn(Optional.of(claim(f)));
        doThrow(new IllegalStateException("RESERVATION_STALE")).when(f.reservations).requireFreshPinned(f.e,f.now);
        assertThatThrownBy(()->new SettlementClaimedFundingService(claims,f.service).fundClaimed(f.e.executionId())).hasMessage("RESERVATION_STALE");f.noDebit();
    }
    @Test void helperRequiresAllGatesAndOwnsATransaction() throws Exception {
        var runner=new ApplicationContextRunner().withUserConfiguration(SettlementClaimedFundingService.class)
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class)).withBean(SettlementFundingService.class,()->funding);
        runner.run(c->assertThat(c).doesNotHaveBean(SettlementClaimedFundingService.class));
        runner.withPropertyValues("app.flow.enabled=true","app.besu.funding.enabled=true")
            .run(c->assertThat(c).doesNotHaveBean(SettlementClaimedFundingService.class));
        runner.withPropertyValues("app.flow.enabled=true","app.besu.funding.enabled=true","app.besu.funding-workers.enabled=true")
            .run(c->assertThat(c).hasSingleBean(SettlementClaimedFundingService.class));
        assertThat(SettlementClaimedFundingService.class.getMethod("fundClaimed",UUID.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }
}
