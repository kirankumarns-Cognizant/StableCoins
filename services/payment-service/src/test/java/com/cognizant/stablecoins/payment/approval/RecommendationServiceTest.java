package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.approval.ApprovalFixtures.*;
import static com.cognizant.stablecoins.payment.support.IntentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecommendationServiceTest {
    PaymentRepository payments; RoutingRecommendationRepository recommendations; RoutingDecisionRepository decisions;
    RoutingAuditRepository audits; OutboxEventRepository outbox; AccountRepository accounts; BeneficiaryRepository beneficiaries;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final Map<UUID,RoutingRecommendation> stored=new HashMap<>(); final List<RoutingDecision> accepted=new ArrayList<>();
    RoutingPolicyProperties config; Payment payment; AssessmentContext context; RoutingEvidence.Snapshot evidence;
    RecommendationDocument.Quote quote; RecommendationService service; Instant time; Beneficiary beneficiary;
    @BeforeEach void setup() {
        payments=mock(PaymentRepository.class);recommendations=mock(RoutingRecommendationRepository.class);decisions=mock(RoutingDecisionRepository.class);
        audits=mock(RoutingAuditRepository.class);outbox=mock(OutboxEventRepository.class);accounts=mock(AccountRepository.class);beneficiaries=mock(BeneficiaryRepository.class);
        config=policies();payment=payment();context=context(payment);evidence=evidence(context);quote=quote(context);time=NOW;beneficiary=beneficiary();
        Clock clock=new Clock() { public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return time;} };
        service=new RecommendationService(payments,recommendations,decisions,audits,outbox,mapper,config,
            new DeterministicRoutingAssessmentService(RULES,NETWORK,clock),accounts,beneficiaries,clock);
        when(payments.lockByPaymentId(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(payments.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(accounts.findById(payment.getSenderAccountId())).thenReturn(Optional.of(account()));
        when(beneficiaries.findById(payment.getBeneficiaryId())).thenReturn(Optional.of(beneficiary));
        when(recommendations.findById(any())).thenAnswer(i->Optional.ofNullable(stored.get(i.getArgument(0))));
        when(recommendations.findByPaymentIdOrderByVersionDesc(any())).thenAnswer(i->stored.values().stream().sorted(Comparator.comparingInt((RoutingRecommendation r)->r.version).reversed()).toList());
        when(recommendations.save(any())).thenAnswer(i->{RoutingRecommendation r=i.getArgument(0);stored.put(r.id,r);return r;});
        when(decisions.findByRecommendationIdAndActorIdAndAction(any(),any(),any())).thenAnswer(i->accepted.stream().filter(d->d.recommendationId.equals(i.getArgument(0))&&d.actorId.equals(i.getArgument(1))&&d.action.equals(i.getArgument(2))).findFirst());
        when(decisions.countByRecommendationIdAndAction(any(),any())).thenAnswer(i->accepted.stream().filter(d->d.recommendationId.equals(i.getArgument(0))&&d.action.equals(i.getArgument(1))).count());
        when(decisions.saveAndFlush(any())).thenAnswer(i->{RoutingDecision d=i.getArgument(0);accepted.add(d);return d;});
    }
    RecommendationView propose(){return service.propose(UUID.randomUUID(),ACTOR,context,evidence,quote);}
    RecommendationView accept(RecommendationView r,AuthorizedCustomer actor){return service.acceptQuote(payment.getPaymentId(),r.recommendationId(),r.version(),quote.quoteId(),1,actor);}
    RecommendationView approve(RecommendationView r,AuthorizedCustomer actor){return service.approve(payment.getPaymentId(),r.recommendationId(),r.version(),quote.quoteId(),1,actor);}
    void conflict(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOf(ApprovalConflictException.class).extracting("errorCode").isEqualTo(code);}
    @Test void persistsFullEvidenceAndLabelsDeterministicSimulation() throws Exception {
        var r=propose();assertThat(r.state()).isEqualTo(RoutingRecommendation.State.PROPOSED);
        assertThat(r.document().producerVersion()).isEqualTo("DETERMINISTIC/rules-v1");
        assertThat(r.document().quote().simulated()).isTrue();assertThat(r.expiresAt()).isEqualTo(quote.expiresAt());
        assertThat(mapper.readValue(mapper.writeValueAsString(r.document()),RecommendationDocument.class)).isEqualTo(r.document());
        var captor=org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).save(captor.capture());
        var event=mapper.readTree(captor.getValue().getPayload());assertThat(event.path("eventId").asText()).isEqualTo(captor.getValue().getEventId());
        assertThat(event.path("paymentId").asText()).isEqualTo(payment.getPaymentId());assertThat(event.path("correlationId").asText()).isEqualTo(payment.getCorrelationId());
    }
    @Test void requiresSeparateQuoteAcceptance(){var r=propose();conflict(()->approve(r,ACTOR),"QUOTE_ACCEPTANCE_REQUIRED");assertThat(accepted).isEmpty();}
    @Test void distinctApproversRequiredAndReplayCannotAddVotes(){var r=propose();accept(r,ACTOR);assertThat(approve(r,ACTOR).state()).isEqualTo(RoutingRecommendation.State.PROPOSED);
        approve(r,ACTOR);var second=new AuthorizedCustomer(CUSTOMER,"second");accept(r,second);assertThat(approve(r,second).state()).isEqualTo(RoutingRecommendation.State.APPROVED);
        assertThat(approve(r,ACTOR).approvalCount()).isEqualTo(2);assertThat(payment.getStatus()).isEqualTo("COMPLIANCE_APPROVED");verify(payments,never()).save(any());}
    @Test void replayRequiresExactRecommendationAndQuoteVersions(){var r=propose();conflict(()->service.acceptQuote(payment.getPaymentId(),r.recommendationId(),2,quote.quoteId(),1,ACTOR),"RECOMMENDATION_VERSION_MISMATCH");
        conflict(()->service.acceptQuote(payment.getPaymentId(),r.recommendationId(),1,UUID.randomUUID(),1,ACTOR),"QUOTE_VERSION_MISMATCH");assertThat(accepted).isEmpty();}
    @Test void expiresAtBoundaryAndRejectsAcceptance(){var r=propose();time=r.expiresAt();conflict(()->accept(r,ACTOR),"RECOMMENDATION_EXPIRED");assertThat(service.get(payment.getPaymentId(),r.recommendationId(),ACTOR).state()).isEqualTo(RoutingRecommendation.State.EXPIRED);}
    @Test void changedPolicyRequiresNewReview(){var r=propose();config.setPolicies(Map.of(CUSTOMER,policy("v2")));conflict(()->accept(r,ACTOR),"POLICY_CHANGED");}
    @Test void changedComplianceBlocksApproval(){var r=propose();payment.setStatus("COMPLIANCE_REJECTED");conflict(()->accept(r,ACTOR),"COMPLIANCE_OR_EXECUTION_STATE_INVALID");}
    @Test void downstreamPaymentCannotBeApprovedAgain(){var r=propose();payment.setStatus("BLOCKCHAIN_SUBMITTED");conflict(()->accept(r,ACTOR),"COMPLIANCE_OR_EXECUTION_STATE_INVALID");}
    @Test void changedBeneficiaryRequiresNewReview(){var r=propose();beneficiary.setWalletAddress("0x2222222222222222222222222222222222222222");conflict(()->accept(r,ACTOR),"BENEFICIARY_OR_ACCOUNT_CHANGED");}
    @Test void changedIntentVersionBlocksAcceptance(){var r=propose();var old=payment.getIntent();payment.setIntent(new PaymentIntentSnapshot(old.intentId(),2,old.customerId(),old.confirmedBy(),old.confirmedAt(),old.settlementAsset(),old.fundingSimulated(),old.fiatPayoutSimulated(),old.instruction(),old.policy()));conflict(()->accept(r,ACTOR),"INTENT_CHANGED");}
    @Test void supersedesOldProposalAndIncrementsVersion(){var first=propose();var second=propose();assertThat(second.version()).isEqualTo(2);conflict(()->accept(first,ACTOR),"RECOMMENDATION_SUPERSEDED");}
    @Test void cannotReplaceApprovedUnresolvedInstruction(){var r=propose();accept(r,ACTOR);approve(r,ACTOR);var second=new AuthorizedCustomer(CUSTOMER,"second");accept(r,second);approve(r,second);conflict(()->propose(),"PRIOR_APPROVAL_UNRESOLVED");}
    @Test void proposalIdIsIdempotentOnlyForSameMaterial(){UUID key=UUID.randomUUID();var r=service.propose(key,ACTOR,context,evidence,quote);assertThat(service.propose(key,ACTOR,context,evidence,quote).recommendationId()).isEqualTo(r.recommendationId());quote=quote(context);conflict(()->service.propose(key,ACTOR,context,evidence,quote),"PROPOSAL_KEY_CONFLICT");}
    @Test void deniesForeignCustomerBeforeReadingRecommendation(){var r=propose();clearInvocations(recommendations);assertThatThrownBy(()->service.get(payment.getPaymentId(),r.recommendationId(),new AuthorizedCustomer(UUID.randomUUID(),"foreign"))).isInstanceOf(PaymentAccessException.class);verifyNoInteractions(recommendations);}
    @Test void rejectsMissingAuthentication(){assertThatThrownBy(()->service.propose(UUID.randomUUID(),null,context,evidence,quote)).isInstanceOf(PaymentAccessException.class);verifyNoInteractions(recommendations);}
    @Test void noQuoteIsNeverAnApprovalFallback(){conflict(()->service.propose(UUID.randomUUID(),ACTOR,context,evidence,null),"QUOTE_REQUIRED");verify(recommendations,never()).save(any());}
    @Test void rejectsMismatchedQuoteFee(){var q=quote;quote=new RecommendationDocument.Quote(q.quoteId(),1,q.paymentId(),q.correlationId(),q.customerId(),q.candidateId(),q.providerId(),q.sourceAmountUsd(),q.transferAmountUsdc(),q.payoutAmounts(),java.math.BigDecimal.ZERO,q.createdAt(),q.expiresAt(),true);conflict(()->propose(),"QUOTE_CONTEXT_MISMATCH");}
    @Test void noEligibleRoutePersistsFindingsWithoutForcedChoice(){evidence=SimulatedRoutingScenarios.create(SimulatedRoutingScenarios.Scenario.NO_ELIGIBLE_ROUTE,context,BeneficiaryOutcome.SIMULATED_BANK_INR,NETWORK,NOW);quote=null;
        var r=propose();assertThat(r.document().assessment().preferredCandidateId()).isNull();assertThat(r.document().assessment().status()).isEqualTo(AssessmentResult.Status.NO_ELIGIBLE_ROUTE);}
    @Test void rejectedRecommendationCannotApproveAndRejectReplayIsIdempotent(){var r=propose();service.reject(payment.getPaymentId(),r.recommendationId(),1,ACTOR);service.reject(payment.getPaymentId(),r.recommendationId(),1,ACTOR);conflict(()->accept(r,ACTOR),"RECOMMENDATION_REJECTED");assertThat(accepted).hasSize(1);}

    void approveFully(RecommendationView r){accept(r,ACTOR);approve(r,ACTOR);var second=new AuthorizedCustomer(CUSTOMER,"second");accept(r,second);approve(r,second);}
    @Test void executionRequiresApprovalThreshold(){var r=propose();accept(r,ACTOR);approve(r,ACTOR);conflict(()->service.authorizeExecution(payment.getPaymentId(),r.recommendationId(),1,quote.quoteId(),1,ACTOR),"APPROVAL_REQUIRED");}
    @Test void approvedExecutionRevalidatesPolicy(){var r=propose();approveFully(r);config.setPolicies(Map.of(CUSTOMER,policy("v2")));conflict(()->service.authorizeExecution(payment.getPaymentId(),r.recommendationId(),1,quote.quoteId(),1,ACTOR),"POLICY_CHANGED");}
    @Test void approvedExecutionRevalidatesRecipient(){var r=propose();approveFully(r);beneficiary.setWalletAddress("0x"+"9".repeat(40));conflict(()->service.authorizeExecution(payment.getPaymentId(),r.recommendationId(),1,quote.quoteId(),1,ACTOR),"BENEFICIARY_OR_ACCOUNT_CHANGED");}
    @Test void executionMarksRecommendationOnceAndBlocksAnotherMoneyLeg(){var r=propose();approveFully(r);service.authorizeExecution(payment.getPaymentId(),r.recommendationId(),1,quote.quoteId(),1,ACTOR);service.markExecuted(payment.getPaymentId(),r.recommendationId(),ACTOR);assertThat(service.get(payment.getPaymentId(),r.recommendationId(),ACTOR).state()).isEqualTo(RoutingRecommendation.State.EXECUTED);conflict(()->service.markExecuted(payment.getPaymentId(),r.recommendationId(),ACTOR),"APPROVAL_REQUIRED");conflict(()->propose(),"PRIOR_APPROVAL_UNRESOLVED");}

    RecommendationDocument.Quote secondOwnerQuote(){var q=quote;return new RecommendationDocument.Quote(UUID.randomUUID(),1,q.paymentId(),q.correlationId(),q.customerId(),"SIMULATED-B","SIMULATED-PROVIDER-B",q.sourceAmountUsd(),q.transferAmountUsdc(),q.payoutAmounts(),new java.math.BigDecimal("8.30"),q.createdAt(),q.expiresAt(),true);}
    @Test void capturedOwnerQuotesChooseLowestCostIndependentlyOfListOrder(){var result=service.proposeCaptured(UUID.randomUUID(),ACTOR,context,evidence,List.of(secondOwnerQuote(),quote));assertThat(result.document().quote().quoteId()).isEqualTo(quote.quoteId());}
    @Test void capturedOwnerQuotesChooseFasterProviderForDeadlinePriority(){var i=payment.getIntent();var instruction=i.instruction();payment.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),i.customerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),true,true,new com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest(instruction.requiredOutcome(),instruction.deliverBy(),instruction.maximumFee(),instruction.feeCurrency(),RoutingPriority.MEET_DEADLINE,instruction.allowedAlternatives(),true),i.policy()));var faster=secondOwnerQuote();var result=service.proposeCaptured(UUID.randomUUID(),ACTOR,context,evidence,List.of(quote,faster));assertThat(result.document().quote().quoteId()).isEqualTo(faster.quoteId());}
}
