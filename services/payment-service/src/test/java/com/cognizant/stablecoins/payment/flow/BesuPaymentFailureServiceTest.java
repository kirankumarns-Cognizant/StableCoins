package com.cognizant.stablecoins.payment.flow;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuPaymentFailureServiceTest {
    BesuPaymentClosureServiceTest f;BesuPaymentFailureService service;
    final BesuRevertEvidence reads=mock(BesuRevertEvidence.class);final BesuPaymentFailureStore store=mock(BesuPaymentFailureStore.class);
    final CustomerAccessProperties access=new CustomerAccessProperties();
    final String hash="a".repeat(64);BesuRevertEvidence.Proof proof;AuthorizedCustomer actor;
    @BeforeEach void setup(){
        f=new BesuPaymentClosureServiceTest();f.setup();
        var snapshot=mock(BesuClosureEvidence.Snapshot.class);var receipt=mock(BesuClosureEvidence.Receipt.class);
        when(snapshot.receipt()).thenReturn(receipt);when(receipt.transactionHash()).thenReturn("0x"+hash);
        proof=new BesuRevertEvidence.Proof(hash,snapshot,null,false);when(reads.current(f.e)).thenReturn(proof);
        when(store.failure(f.e)).thenReturn(Optional.empty());when(store.authorized(f.e)).thenReturn(Optional.empty());
        when(store.fundingActor(f.e)).thenReturn("operator");
        actor=new AuthorizedCustomer(f.e.instruction().customerId(),"approver");
        access.setMemberships(Map.of("approver",new Membership(actor.customerId(),Set.of(Role.PAYMENT_APPROVER))));
        service=new BesuPaymentFailureService(f.payments,f.flow,reads,store,access);
    }
    void failed(){f.payment.setStatus("FAILED");when(store.failure(f.e)).thenReturn(Optional.of(hash));}
    @Test void failureAndAuditAreRecordedOnlyAfterConclusiveEvidence(){service.advance(f.e);assertThat(f.payment.getStatus()).isEqualTo("FAILED");verify(f.history).save(any());verify(f.outbox).save(any());var order=inOrder(f.payments,store);order.verify(f.payments).flush();order.verify(store).record(f.e,proof);verify(store,never()).authorize(any(),any(),any());}
    @Test void replayAfterRestartDoesNotDuplicateFailure(){failed();service.advance(f.e);verifyNoInteractions(reads,f.history,f.outbox);verify(store,never()).record(any(),any());}
    @Test void missingRevertEvidenceCannotChangePayment(){when(reads.current(f.e)).thenThrow(new IllegalStateException("BESU_CONCLUSIVE_REVERT_REQUIRED"));assertThatThrownBy(()->service.advance(f.e)).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");assertThat(f.payment.getStatus()).isEqualTo("FUNDED");verifyNoInteractions(f.history,f.outbox);}
    @ParameterizedTest @ValueSource(strings={"RECONCILED","COMPLIANCE_APPROVED","FAILED"})
    void invalidOwnerStateCannotBecomeAFailedRecovery(String status){f.payment.setStatus(status);assertThatThrownBy(()->service.advance(f.e)).hasMessage("BESU_PAYMENT_FAILURE_CONFLICT");verifyNoInteractions(reads,f.history,f.outbox);}
    @Test void orphanFailureAcknowledgementCannotOverwriteState(){when(store.failure(f.e)).thenReturn(Optional.of(hash));assertThatThrownBy(()->service.advance(f.e)).hasMessage("BESU_PAYMENT_FAILURE_CONFLICT");}
    @Test void independentCurrentApproverCanAuthorizeExactFailedEvidence(){failed();service.authorizeRelease(f.e,actor,hash);verify(store).authorize(f.e,hash,"approver");verify(f.outbox).save(any());assertThat(f.payment.getStatus()).isEqualTo("FAILED");verifyNoInteractions(f.history);}
    @Test void authorizationReplayDoesNotEmitAnotherCommand(){failed();when(store.authorized(f.e)).thenReturn(Optional.of(hash));service.authorizeRelease(f.e,actor,hash);verify(store,never()).authorize(any(),any(),any());verifyNoInteractions(f.outbox);}
    @Test void changedEvidenceCannotBeAuthorized(){failed();assertThatThrownBy(()->service.authorizeRelease(f.e,actor,"b".repeat(64))).hasMessage("BESU_PAYMENT_FAILURE_CONFLICT");verify(store,never()).authorize(any(),any(),any());}
    @ParameterizedTest @ValueSource(strings={"revoked","operator","customer","self","null"})
    void authorizationRequiresCurrentIndependentCustomerApprover(String mode){
        failed();if(mode.equals("revoked"))access.setMemberships(Map.of());
        if(mode.equals("operator"))access.setMemberships(Map.of("approver",new Membership(actor.customerId(),Set.of(Role.PAYMENT_OPERATOR))));
        if(mode.equals("customer"))actor=new AuthorizedCustomer(UUID.randomUUID(),"approver");
        if(mode.equals("self"))when(store.fundingActor(f.e)).thenReturn("approver");
        var caller=mode.equals("null")?null:actor;
        assertThatThrownBy(()->service.authorizeRelease(f.e,caller,hash)).hasMessageContaining("BESU_RECOVERY_APPROVER_REQUIRED");verify(store,never()).authorize(any(),any(),any());verifyNoInteractions(f.outbox);
    }
    @Test void currentRoleIsCheckedOnReplayToo(){failed();when(store.authorized(f.e)).thenReturn(Optional.of(hash));access.setMemberships(Map.of());assertThatThrownBy(()->service.authorizeRelease(f.e,actor,hash)).hasMessageContaining("BESU_RECOVERY_APPROVER_REQUIRED");}
    @Test void manualReleaseCannotAcquireRetroactiveAuthorization(){failed();when(reads.current(f.e)).thenReturn(new BesuRevertEvidence.Proof(hash,proof.sources(),null,true));assertThatThrownBy(()->service.authorizeRelease(f.e,actor,hash)).hasMessage("BESU_PAYMENT_FAILURE_CONFLICT");verify(store,never()).authorize(any(),any(),any());}
    @Test void changedInstructionCannotBeFailedOrRecovered(){f.payment.setAmount(java.math.BigDecimal.ONE);assertThatThrownBy(()->service.advance(f.e)).hasMessage("BESU_PAYMENT_FAILURE_CONFLICT");verifyNoInteractions(reads,store);}
    @Test void ownersRemainOffUnlessBothGatesEnabled(){var r=new ApplicationContextRunner().withUserConfiguration(BesuPaymentFailureService.class);r.run(c->assertThat(c).doesNotHaveBean(BesuPaymentFailureService.class));r.withPropertyValues("app.flow.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuPaymentFailureService.class));r.withPropertyValues("app.besu.recovery.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuPaymentFailureService.class));}
}
