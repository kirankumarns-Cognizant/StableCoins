package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.ledger.besu.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuCompensationServiceTest {
    BesuAccountingServiceTest f;BesuCompensationService service;
    final BesuRevertEvidence reads=mock(BesuRevertEvidence.class);final BesuCompensationStore store=mock(BesuCompensationStore.class);
    final AtomicReference<BesuCompensationStore.Acknowledgement> ack=new AtomicReference<>();
    BesuRevertEvidence.Proof proof;UUID original;
    @BeforeEach void setup(){
        f=new BesuAccountingServiceTest();f.setup();f.submitted();
        when(f.store.receipt(eq(f.e),any())).thenReturn(BesuAccountingTestData.receipt(false,3));f.service.resolve(f.e);
        var j=f.f.saved.values().stream().filter(x->x.getJournalType().equals("BLOCKCHAIN_SUBMISSION")).findFirst().orElseThrow();original=j.getJournalId();
        proof=new BesuRevertEvidence.Proof("a".repeat(64),null,new LifecycleReadModels.Journal(original,j.getPaymentId(),j.getCorrelationId(),j.getJournalType(),j.getStatus(),j.getReference(),null,List.of()),false);
        when(reads.current(f.e)).thenAnswer(i->new BesuRevertEvidence.Proof(proof.sourceHash(),null,proof.submission(),ack.get()!=null));
        when(reads.authorization(eq(f.e),any())).thenReturn(new BesuRevertEvidence.Authorization("approver",proof.sourceHash()));
        when(store.find(f.e)).thenAnswer(i->Optional.ofNullable(ack.get()));doAnswer(i->{ack.set(i.getArgument(1));return null;}).when(store).acknowledge(eq(f.e),any());
        service=new BesuCompensationService(f.f.ledger,f.policy,reads,store);
    }
    @Test void appendOnlyReleasePreservesGasFundingAndOriginalPostedJournal(){
        var before=new ArrayList<>(f.f.saved.values());var gas=before.stream().filter(j->j.getJournalType().equals("NETWORK_FEE")).findFirst().orElseThrow();
        var a=service.compensate(f.e);assertThat(a.originalJournal()).isEqualTo(original);assertThat(a.releaseJournal()).isNotEqualTo(original);
        assertThat(f.f.saved).hasSize(before.size()+1);assertThat(before).allMatch(j->j.getStatus().equals("POSTED"));assertThat(f.f.saved.get(gas.getJournalId())).isSameAs(gas);
        var release=f.f.saved.get(a.releaseJournal());assertThat(release.getReference()).isEqualTo(BesuRevertEvidence.reference(original));
        assertThat(release.getEvidence()).isEqualTo(f.f.saved.get(original).getEvidence());
        var lines=f.f.lines.stream().filter(x->a.releaseJournal().equals(x.getJournalId())).toList();
        assertThat(lines).hasSize(2).allMatch(x->x.getAsset().equals("DEMOUSD"));
        assertThat(lines).anyMatch(x->x.getLedgerAccountId().equals(f.f.chart.get("150001").getLedgerAccountId()) && x.getDebit().compareTo(f.e.instruction().tokenAmount())==0);
        assertThat(lines).anyMatch(x->x.getLedgerAccountId().equals(f.f.chart.get("150002").getLedgerAccountId()) && x.getCredit().compareTo(f.e.instruction().tokenAmount())==0);
        assertThat(f.f.saved.values()).noneMatch(j->j.getJournalType().equals("BLOCKCHAIN_CONFIRMATION") || j.getJournalType().contains("REFUND"));
    }
    @Test void restartedWorkerReturnsSameAcknowledgementWithoutAnotherJournal(){var a=service.compensate(f.e);assertThat(new BesuCompensationService(f.f.ledger,f.policy,reads,store).compensate(f.e)).isEqualTo(a);verify(store).acknowledge(eq(f.e),any());assertThat(f.f.saved.values()).filteredOn(j->BesuRevertEvidence.RELEASE.equals(j.getJournalType())).hasSize(1);}
    @Test void missingAuthorizationCannotMoveBalances(){when(reads.authorization(eq(f.e),any())).thenThrow(new IllegalStateException("BESU_COMPENSATION_AUTHORIZATION_REQUIRED"));assertThatThrownBy(()->service.compensate(f.e)).hasMessage("BESU_COMPENSATION_AUTHORIZATION_REQUIRED");assertThat(f.f.saved).hasSize(5);verify(store,never()).acknowledge(any(),any());}
    @Test void unknownOrSuccessfulOutcomeCannotCompensate(){when(reads.current(f.e)).thenThrow(new IllegalStateException("BESU_CONCLUSIVE_REVERT_REQUIRED"));assertThatThrownBy(()->service.compensate(f.e)).hasMessage("BESU_CONCLUSIVE_REVERT_REQUIRED");assertThat(f.f.saved).hasSize(5);}
    @Test void unacknowledgedExistingReleaseFailsClosed(){when(reads.current(f.e)).thenReturn(new BesuRevertEvidence.Proof(proof.sourceHash(),null,proof.submission(),true));assertThatThrownBy(()->service.compensate(f.e)).hasMessage("BESU_COMPENSATION_CONFLICT");assertThat(f.f.saved).hasSize(5);}
    @Test void changedAcknowledgementCannotAuthorizeDuplicateRelease(){service.compensate(f.e);var a=ack.get();ack.set(new BesuCompensationStore.Acknowledgement("b".repeat(64),a.actorId(),a.originalJournal(),a.releaseJournal()));assertThatThrownBy(()->service.compensate(f.e)).hasMessage("BESU_COMPENSATION_CONFLICT");assertThat(f.f.saved).hasSize(6);}
    @Test void disabledByDefaultAndSingleGateDoesNotActivate(){var r=new ApplicationContextRunner().withUserConfiguration(BesuCompensationService.class);r.run(c->assertThat(c).doesNotHaveBean(BesuCompensationService.class));r.withPropertyValues("app.besu.accounting.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuCompensationService.class));r.withPropertyValues("app.besu.recovery.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuCompensationService.class));}
}
