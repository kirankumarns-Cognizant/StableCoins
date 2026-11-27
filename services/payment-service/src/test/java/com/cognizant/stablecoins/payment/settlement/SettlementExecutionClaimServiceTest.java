package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest;
import com.cognizant.stablecoins.payment.domain.SupportedCurrency;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementExecutionClaimServiceTest {
    final PaymentRepository payments=mock(PaymentRepository.class);
    final RecommendationService recommendations=mock(RecommendationService.class);
    final RoutingDecisionRepository decisions=mock(RoutingDecisionRepository.class);
    final SettlementExecutionClaimStore store=mock(SettlementExecutionClaimStore.class);
    final CustomerAccessProperties access=new CustomerAccessProperties();
    final SettlementBinding.Instruction instruction=instruction();
    final UUID executionId=UUID.randomUUID();
    final Instant assessed=Instant.parse("2026-11-25T10:00:00Z");
    final Instant now=assessed.plusSeconds(5);
    final AuthorizedCustomer actor=new AuthorizedCustomer(instruction.customerId(),"operator");
    Payment payment;
    RecommendationView view;
    List<RoutingDecision> rows;
    SettlementExecutionClaimService service;
    @BeforeEach void setup(){
        var policy=new CustomerRoutingPolicy("besu-customer-v1",Set.of(BeneficiaryOutcome.TOKEN_WALLET),
                Set.of("SIMULATED-PROVIDER-A"),new BigDecimal("3"),2);
        var request=new PaymentIntentRequest(BeneficiaryOutcome.TOKEN_WALLET,instruction.deliverBy(),
                instruction.maximumFeeUsd(),SupportedCurrency.USD,RoutingPriority.LOWEST_COST,Set.of(),true,
                new SettlementSelection("besu-local",instruction.context().token().contract(),instruction.tokenAmount()));
        var intent=new PaymentIntentSnapshot(instruction.intentId(),1,instruction.customerId(),actor.actorId(),assessed,
                SettlementAsset.DEMOUSD,true,false,request,policy,
                new ConfirmedSettlement(instruction.context(),instruction.tokenAmount(),instruction.recipient()));
        payment=new Payment();payment.setPaymentId(instruction.paymentId());payment.setCorrelationId(instruction.correlationId());
        payment.setAmount(instruction.sourceUsd());payment.setSourceCurrency("USD");payment.setPaymentRail("STABLECOIN");
        payment.setStatus("COMPLIANCE_APPROVED");payment.setIntent(intent);
        var observation=new SettlementObservation(instruction,100,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE,
                BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("1e-18"),assessed,assessed.plusSeconds(15));
        var preparation=new SettlementPreparation.Request(UUID.randomUUID(),instruction,assessed);
        var availability=new SettlementAvailability(new SettlementPreparation.Evidence(preparation,observation),
                BigDecimal.ZERO,BigDecimal.ZERO,assessed);
        var terms=new SettlementQuote.Terms("quote-v1","SIMULATED-PROVIDER-A",new BigDecimal("2"),900,BigDecimal.ONE);
        var review=new SettlementBinding.Review(UUID.randomUUID(),1,preparation.requestId(),UUID.randomUUID(),1,
                terms.providerId(),instruction,terms.simulatedFeeUsd(),terms.reserve(observation),assessed,assessed.plusSeconds(900));
        var quote=new SettlementQuote(review,availability,terms);
        var document=new RecommendationDocument(intent,null,null,null,null,
                new RecommendationDocument.InstructionBinding(UUID.randomUUID(),UUID.randomUUID(),instruction.recipient(),"USD"),
                "Private review","test",quote);
        view=new RecommendationView(review.recommendationId(),2,instruction.paymentId(),instruction.correlationId(),
                RoutingRecommendation.State.APPROVED,assessed,review.expiresAt(),2,2,document);
        access.setMemberships(Map.of("operator",new Membership(instruction.customerId(),Set.of(Role.PAYMENT_OPERATOR)),
                "a",new Membership(instruction.customerId(),Set.of(Role.PAYMENT_APPROVER)),
                "b",new Membership(instruction.customerId(),Set.of(Role.PAYMENT_APPROVER))));
        rows=new ArrayList<>(List.of(decision("a","QUOTE_ACCEPTED",1),decision("a","APPROVED",2),
                decision("b","QUOTE_ACCEPTED",2),decision("b","APPROVED",3)));
        when(payments.lockByPaymentId(instruction.paymentId())).thenReturn(Optional.of(payment));
        when(store.byPayment(anyString())).thenReturn(Optional.empty());when(store.byId(any())).thenReturn(Optional.empty());
        when(recommendations.authorizeSettlementExecution(anyString(),any(),anyInt(),any(),anyInt(),any())).thenAnswer(c->view);
        when(decisions.findByRecommendationIdOrderByDecidedAtAsc(any())).thenAnswer(c->rows);
        service=service(now);
    }
    SettlementExecutionClaimService service(Instant instant){
        return new SettlementExecutionClaimService(payments,recommendations,decisions,access,store,Clock.fixed(instant,ZoneOffset.UTC));
    }
    RoutingDecision decision(String subject,String action,int seconds){
        var d=new RoutingDecision();d.id=UUID.randomUUID();d.recommendationId=view.recommendationId();d.actorId=subject;
        d.action=action;d.decidedAt=assessed.plusSeconds(seconds);return d;
    }
    SettlementExecutionClaim claim(){
        var r=view.document().settlementQuote().review();
        return service.claim(payment.getPaymentId(),executionId,view.recommendationId(),view.version(),r.quoteId(),r.quoteVersion(),actor);
    }
    void conflict(Runnable action,String code){
        assertThatThrownBy(action::run).isInstanceOf(ApprovalConflictException.class).extracting("errorCode").isEqualTo(code);
        verify(store,never()).capture(any());
    }
    @Test void capturesCanonicalVersionAndIndependentAcceptedVotesWithoutPaymentMutation(){
        var claim=claim();
        assertThat(claim.execution().review().version()).isEqualTo(2);
        assertThat(claim.execution().review().instruction()).isEqualTo(instruction);
        assertThat(claim.approvals()).extracting(SettlementExecutionClaim.Approval::actorId).containsExactly("a","b");
        assertThat(claim.phase()).isEqualTo("CLAIMED_NOT_SUBMITTED");
        assertThat(claim.claimedAt()).isEqualTo(now);
        verify(store).capture(claim);verify(payments,never()).save(any());
        assertThat(payment.getStatus()).isEqualTo("COMPLIANCE_APPROVED");
        assertThat(SettlementExecutionClaimJson.read(SettlementExecutionClaimJson.write(claim),SettlementExecutionClaim.class)).isEqualTo(claim);
    }
    @Test void operatorMembershipIsCheckedBeforeAnyOwnerLookup(){
        access.setMemberships(Map.of());
        assertThatThrownBy(this::claim).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
        verifyNoInteractions(payments,store,recommendations,decisions);
    }
    @Test void missingIdentityIsRejectedBeforeReads(){
        var r=view.document().settlementQuote().review();
        assertThatThrownBy(()->service.claim(payment.getPaymentId(),executionId,view.recommendationId(),2,r.quoteId(),1,null))
                .hasMessageContaining("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(payments,store,recommendations,decisions);
    }
    @Test void foreignCustomerIsDeniedBeforeClaimRead(){
        var other=new AuthorizedCustomer(UUID.randomUUID(),"foreign");
        access.setMemberships(Map.of("foreign",new Membership(other.customerId(),Set.of(Role.PAYMENT_OPERATOR))));
        var r=view.document().settlementQuote().review();
        assertThatThrownBy(()->service.claim(payment.getPaymentId(),executionId,view.recommendationId(),2,r.quoteId(),1,other))
                .hasMessageContaining("CUSTOMER_SCOPE_MISMATCH");
        verifyNoInteractions(store,recommendations,decisions);
    }
    @Test void authorityFailureCannotPersistClaim(){
        when(recommendations.authorizeSettlementExecution(anyString(),any(),anyInt(),any(),anyInt(),any()))
                .thenThrow(new ApprovalConflictException("APPROVAL_REQUIRED"));
        conflict(this::claim,"APPROVAL_REQUIRED");verifyNoInteractions(decisions);
    }
    @Test void twoCountsWithoutActualIndependentRowsAreInsufficient(){
        rows.clear();assertThatThrownBy(this::claim).hasMessage("INDEPENDENT_SETTLEMENT_APPROVALS_REQUIRED");
        verify(store,never()).capture(any());
    }
    @Test void duplicateActorCannotCountTwice(){rows.add(rows.getLast());conflict(this::claim,"SETTLEMENT_APPROVAL_EVIDENCE_INVALID");}
    @Test void missingOwnQuoteAcceptanceBlocksClaim(){rows.removeFirst();conflict(this::claim,"QUOTE_ACCEPTANCE_REQUIRED");}
    @Test void acceptanceCannotFollowApproval(){
        rows.getFirst().decidedAt=now;
        assertThatThrownBy(this::claim).hasMessage("APPROVAL_BEFORE_ACCEPTANCE");verify(store,never()).capture(any());
    }
    @Test void futureVoteIsNotAuthority(){
        rows.getLast().decidedAt=now.plusSeconds(1);
        assertThatThrownBy(this::claim).hasMessage("SETTLEMENT_APPROVAL_WINDOW_INVALID");verify(store,never()).capture(any());
    }
    @Test void rejectedEvidenceCannotBeHiddenByApprovedView(){rows.add(decision("c","REJECTED",2));conflict(this::claim,"SETTLEMENT_APPROVAL_EVIDENCE_INVALID");}
    @Test void revokedApproverCannotAuthorizeNewClaim(){
        var members=new HashMap<>(access.getMemberships());members.remove("b");access.setMemberships(members);
        assertThatThrownBy(this::claim).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");verify(store,never()).capture(any());
    }
    @Test void otherCustomerApproverIsRejected(){
        var members=new HashMap<>(access.getMemberships());members.put("b",new Membership(UUID.randomUUID(),Set.of(Role.PAYMENT_APPROVER)));
        access.setMemberships(members);assertThatThrownBy(this::claim).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
        verify(store,never()).capture(any());
    }
    @Test void expiredReviewCannotCreateClaim(){
        service=service(view.expiresAt());assertThatThrownBy(this::claim).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
        verify(store,never()).capture(any());
    }
    @Test void exactHistoricalReplayPreservesClaimAndDoesNotReauthorizeOrExtendExpiry(){
        var original=claim();clearInvocations(store,recommendations,decisions);
        when(store.byPayment(payment.getPaymentId())).thenReturn(Optional.of(original));
        service=service(view.expiresAt().plusSeconds(100));
        assertThat(claim()).isEqualTo(original);verifyNoInteractions(recommendations,decisions);verify(store,never()).capture(any());
    }
    @Test void samePaymentCannotReceiveAnotherExecutionId(){
        var original=claim();clearInvocations(store);
        when(store.byPayment(payment.getPaymentId())).thenReturn(Optional.of(original));
        var r=view.document().settlementQuote().review();
        conflict(()->service.claim(payment.getPaymentId(),UUID.randomUUID(),view.recommendationId(),2,r.quoteId(),1,actor),
                "SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
    }
    @Test void claimedPaymentRejectsChangedQuoteVersion(){
        var original=claim();clearInvocations(store);
        when(store.byPayment(payment.getPaymentId())).thenReturn(Optional.of(original));
        var r=view.document().settlementQuote().review();
        conflict(()->service.claim(payment.getPaymentId(),executionId,view.recommendationId(),2,r.quoteId(),2,actor),
                "SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
    }
    @Test void executionIdAlreadyUsedElsewhereCannotReachNewAuthority(){
        when(store.byId(executionId)).thenReturn(Optional.of(new SettlementExecutionClaim(
                new SettlementBinding.Execution(executionId,view.document().settlementReview(2)),"operator","policy-v1",2,
                List.of(new SettlementExecutionClaim.Approval("a",UUID.randomUUID(),assessed,UUID.randomUUID(),assessed),
                        new SettlementExecutionClaim.Approval("b",UUID.randomUUID(),assessed,UUID.randomUUID(),assessed)),now)));
        conflict(this::claim,"SETTLEMENT_EXECUTION_CLAIM_CONFLICT");verifyNoInteractions(recommendations,decisions);
    }
    @Test void storeRequiresActualTransactionBeforeAnySql(){
        var claim=claim();var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        assertThatThrownBy(()->new SettlementExecutionClaimStore(jdbc).capture(claim)).hasMessage("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        verifyNoInteractions(jdbc);
    }
    SettlementExecutionClaim fundingClaim() {
        var c=claim();when(store.byId(executionId)).thenReturn(Optional.of(c));return c;
    }
    @Test void fundingRevalidatesOriginalVotesWithoutCreatingAnotherClaim() {
        var c=fundingClaim();clearInvocations(store);
        assertThat(service.requireFundingAuthority(c.execution(),actor)).isEqualTo(c);
        verify(store,never()).capture(any());
    }
    @Test void expiredHistoricalClaimCannotAuthorizeFunding() {
        var c=fundingClaim();service=service(view.expiresAt());
        assertThatThrownBy(()->service.requireFundingAuthority(c.execution(),actor)).hasMessage("SETTLEMENT_REVIEW_NOT_CURRENT");
    }
    @Test void revokedApproverCannotAuthorizeFunding() {
        var c=fundingClaim();var members=new HashMap<>(access.getMemberships());members.remove("b");access.setMemberships(members);
        assertThatThrownBy(()->service.requireFundingAuthority(c.execution(),actor)).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
    }
    @Test void changedVoteIdCannotAuthorizeFunding() {
        var c=fundingClaim();rows.getLast().id=UUID.randomUUID();
        assertThatThrownBy(()->service.requireFundingAuthority(c.execution(),actor)).extracting("errorCode").isEqualTo("SETTLEMENT_APPROVAL_EVIDENCE_CHANGED");
    }
    @Test void missingClaimCannotAuthorizeFunding() {
        var c=claim();assertThatThrownBy(()->service.requireFundingAuthority(c.execution(),actor))
                .extracting("errorCode").isEqualTo("SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
    }
    @Test void revokedOperatorCannotAuthorizeFunding() {
        var c=fundingClaim();access.setMemberships(Map.of());
        assertThatThrownBy(()->service.requireFundingAuthority(c.execution(),actor)).hasMessageContaining("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
    }
}