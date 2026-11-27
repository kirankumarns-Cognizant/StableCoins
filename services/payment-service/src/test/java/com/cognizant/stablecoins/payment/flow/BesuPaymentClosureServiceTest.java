package com.cognizant.stablecoins.payment.flow;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.settlement.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuPaymentClosureServiceTest {
    final PaymentRepository payments=mock(PaymentRepository.class);final OutboxEventRepository outbox=mock(OutboxEventRepository.class);
    final PaymentStatusHistoryRepository history=mock(PaymentStatusHistoryRepository.class);
    final SettlementExecutionReads claims=mock(SettlementExecutionReads.class);final BesuSettlementClosureReads reads=mock(BesuSettlementClosureReads.class);
    final BesuPaymentClosureStore store=mock(BesuPaymentClosureStore.class);final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final PaymentFlowService flow=new PaymentFlowService(mock(JdbcTemplate.class),mapper,new MockEnvironment(),payments,null,null,null,outbox,history,null);
    final SettlementBinding.Execution e=execution();final Payment payment=new Payment();
    final BesuClosureEvidence.Comparison comparison=new BesuClosureEvidence.Comparison(BesuClosureEvidence.KIND,BesuClosureEvidence.GATE,
        new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003"),mapper.createObjectNode(),List.of(),"MATCHED");
    final BesuSettlementClosureReads.Report report=new BesuSettlementClosureReads.Report(UUID.randomUUID(),"a".repeat(64));
    final AtomicReference<BesuSettlementClosureReads.Report> completed=new AtomicReference<>();
    BesuPaymentClosureService service;
    static SettlementBinding.Execution execution() {
        var chain=new SettlementNetwork.Chain("besu-local",20261125,"0x"+"1".repeat(64),SettlementNetwork.Classification.PRIVATE_DEMO);
        var c=new SettlementNetwork.Context(new SettlementNetwork.Token(chain,"0x"+"2".repeat(40),"DEMOUSD",6,"0x"+"3".repeat(64)),
            new SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"4".repeat(40),SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-v1");
        var now=Instant.parse("2026-11-26T10:00:00Z");var i=new SettlementBinding.Instruction(UUID.randomUUID(),1,UUID.randomUUID(),"PAY-CLOSURE","CORR-CLOSURE",c,
            "0x"+"5".repeat(40),BigDecimal.TEN,BigDecimal.TEN,SettlementBinding.Outcome.TOKEN_WALLET,new BigDecimal("3"),now.plusSeconds(3600));
        return new SettlementBinding.Execution(UUID.randomUUID(),new SettlementBinding.Review(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),1,"PRIVATE-PROVIDER",i,BigDecimal.ONE,BigDecimal.ZERO,now,now.plusSeconds(900)));
    }
    @BeforeEach void setup() {
        var i=e.instruction();var request=new PaymentIntentRequest(BeneficiaryOutcome.TOKEN_WALLET,i.deliverBy(),i.maximumFeeUsd(),SupportedCurrency.USD,
            RoutingPriority.LOWEST_COST,Set.of(),true,new SettlementSelection("besu-local",i.context().token().contract(),i.tokenAmount()));
        var policy=new CustomerRoutingPolicy("private-v1",Set.of(BeneficiaryOutcome.TOKEN_WALLET),Set.of(e.review().providerId()),new BigDecimal("3"),2);
        payment.setIntent(new PaymentIntentSnapshot(i.intentId(),1,i.customerId(),"operator",e.review().assessedAt(),SettlementAsset.DEMOUSD,true,false,
            request,policy,new ConfirmedSettlement(i.context(),i.tokenAmount(),i.recipient())));
        payment.setPaymentId(i.paymentId());payment.setCorrelationId(i.correlationId());payment.setAmount(i.sourceUsd());payment.setSourceCurrency("USD");payment.setPaymentRail("STABLECOIN");payment.setStatus("FUNDED");
        when(payments.lockByPaymentId(i.paymentId())).thenReturn(Optional.of(payment));when(reads.current(e)).thenReturn(comparison);
        when(reads.matchingReport(e,comparison)).thenReturn(Optional.empty());when(store.find(e)).thenAnswer(x->Optional.ofNullable(completed.get()));
        doAnswer(x->{completed.set(x.getArgument(1));return null;}).when(store).complete(eq(e),any());
        service=new BesuPaymentClosureService(payments,flow,claims,reads,store);
    }
    @Test void completeLedgerEvidenceAdvancesToPendingButNeverInfersReconciliation() throws Exception {
        service.advance(e);assertThat(payment.getStatus()).isEqualTo("RECONCILIATION_PENDING");
        var states=ArgumentCaptor.forClass(PaymentStatusHistory.class);verify(history,times(4)).save(states.capture());
        assertThat(states.getAllValues()).extracting(PaymentStatusHistory::getNewStatus).containsExactly("BLOCKCHAIN_SUBMITTED","BLOCKCHAIN_CONFIRMED","SETTLEMENT_COMPLETED","RECONCILIATION_PENDING");
        var events=ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox,times(4)).save(events.capture());
        for(var event:events.getAllValues()) {
            var d=mapper.readTree(event.getPayload());assertThat(d.path("eventVersion").asText()).isEqualTo("1.0");
            assertThat(d.path("paymentId").asText()).isEqualTo(e.instruction().paymentId());assertThat(d.path("correlationId").asText()).isEqualTo(e.instruction().correlationId());
            assertThat(d.hasNonNull("timestamp")).isTrue();assertThat(d.path("payload").path("executionId").asText()).isEqualTo(e.executionId().toString());
        }
        verify(store,never()).complete(any(),any());
    }
    @Test void exactReportCompletesAndRestartReplayCreatesNoDuplicateHistoryOrEvents() {
        service.advance(e);when(reads.matchingReport(e,comparison)).thenReturn(Optional.of(report));service.advance(e);
        new BesuPaymentClosureService(payments,flow,claims,reads,store).advance(e);
        assertThat(payment.getStatus()).isEqualTo("RECONCILED");assertThat(completed.get()).isEqualTo(report);
        verify(history,times(5)).save(any());verify(outbox,times(5)).save(any());verify(store).complete(e,report);verify(reads,times(2)).current(e);
        var ordered=inOrder(payments,store);ordered.verify(payments).flush();ordered.verify(store).complete(e,report);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","MISMATCH"})
    void missingOrRevertedOrUnbalancedEvidenceDoesNotAdvance(String status) {
        when(reads.current(e)).thenReturn(new BesuClosureEvidence.Comparison(comparison.kind(),comparison.closureGate(),comparison.policy(),comparison.sources(),List.of("TRANSFER_REVERTED_REQUIRES_REVIEW"),status));
        service.advance(e);assertThat(payment.getStatus()).isEqualTo("FUNDED");verifyNoInteractions(history,outbox);verify(reads,never()).matchingReport(any(),any());
    }
    @Test void changedOrForgedReportCannotAdvanceAnyState() {
        doThrow(new IllegalStateException("BESU_FRESH_RECONCILIATION_REQUIRED")).when(reads).matchingReport(e,comparison);
        assertThatThrownBy(()->service.advance(e)).hasMessage("BESU_FRESH_RECONCILIATION_REQUIRED");verifyNoInteractions(history,outbox);
    }
    @Test void missingCommittedFundingCannotAdvanceState() {
        doThrow(new IllegalStateException("FUNDING_REQUIRED")).when(reads).current(e);
        assertThatThrownBy(()->service.advance(e)).hasMessage("FUNDING_REQUIRED");verifyNoInteractions(history,outbox);
    }
    @Test void changedIntentCannotCloseAnotherInstruction() {
        payment.setAmount(BigDecimal.ONE);assertThatThrownBy(()->service.advance(e)).hasMessage("BESU_PAYMENT_CLOSURE_CONFLICT");verifyNoInteractions(reads,history,outbox);
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","COMPLIANCE_APPROVED","RECONCILED"})
    void unexpectedOwnerStateCannotBeOverwritten(String status) {
        payment.setStatus(status);assertThatThrownBy(()->service.advance(e)).hasMessage("BESU_PAYMENT_CLOSURE_CONFLICT");verifyNoInteractions(reads,history,outbox);
    }
    @Test void orphanTerminalAcknowledgementFailsClosed() {
        completed.set(report);assertThatThrownBy(()->service.advance(e)).hasMessage("BESU_PAYMENT_CLOSURE_CONFLICT");verifyNoInteractions(reads,history,outbox);
    }
    @Test void unclaimedExecutionCannotReadFinancialEvidence() {
        doThrow(new IllegalStateException("CLAIM_REQUIRED")).when(claims).requireClaimed(e);
        assertThatThrownBy(()->service.advance(e)).hasMessage("CLAIM_REQUIRED");verifyNoInteractions(reads,history,outbox,store);
    }
    @Test void disabledByDefaultAndEitherGateAloneCannotCreateOwner() {
        var runner=new ApplicationContextRunner().withUserConfiguration(BesuPaymentClosureService.class);
        runner.run(c->assertThat(c).doesNotHaveBean(BesuPaymentClosureService.class));
        runner.withPropertyValues("app.flow.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuPaymentClosureService.class));
        runner.withPropertyValues("app.besu.closure.enabled=true").run(c->assertThat(c).doesNotHaveBean(BesuPaymentClosureService.class));
    }
}
