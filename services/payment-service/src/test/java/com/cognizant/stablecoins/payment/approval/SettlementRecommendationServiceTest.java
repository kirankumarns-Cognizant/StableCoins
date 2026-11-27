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
class SettlementRecommendationServiceTest {
    PaymentRepository payments; RoutingRecommendationRepository recommendations; RoutingDecisionRepository decisions;
    RoutingAuditRepository audits; OutboxEventRepository outbox; AccountRepository accounts; BeneficiaryRepository beneficiaries;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final Map<UUID,RoutingRecommendation> stored=new HashMap<>(); final List<RoutingDecision> accepted=new ArrayList<>();
    RoutingPolicyProperties config; Payment payment; AssessmentContext context; RoutingEvidence.Snapshot evidence;
    RecommendationDocument.Quote quote; RecommendationService service; Instant time; Beneficiary beneficiary;
    com.cognizant.stablecoins.payment.settlement.SettlementQuoteService quoteService;
    com.cognizant.stablecoins.payment.settlement.SettlementSelectionProperties selections;
    final Map<UUID,com.cognizant.stablecoins.flow.SettlementQuote> drafts=new HashMap<>();
    UUID draftId, preparationId;
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
        configurePrivate();
    }

    void configurePrivate() {
        time=Instant.parse("2026-11-25T10:00:00Z"); payment.setAmount(new java.math.BigDecimal("5")); payment.setPaymentRail("STABLECOIN");
        beneficiary.setWalletAddress("0x"+"e".repeat(40));
        var chain=new com.cognizant.stablecoins.flow.SettlementNetwork.Chain("besu-local",20261125,"0x"+"a".repeat(64),com.cognizant.stablecoins.flow.SettlementNetwork.Classification.PRIVATE_DEMO);
        var ctx=new com.cognizant.stablecoins.flow.SettlementNetwork.Context(
            new com.cognizant.stablecoins.flow.SettlementNetwork.Token(chain,"0x"+"b".repeat(40),"DEMOUSD",6,"0x"+"c".repeat(64)),
            new com.cognizant.stablecoins.flow.SettlementNetwork.NativeAsset(chain,"BESU_GAS",18),"0x"+"d".repeat(40),
            com.cognizant.stablecoins.flow.SettlementNetwork.FeeModel.EXECUTION_ONLY,"besu-demo-v1");
        var old=payment.getIntent();
        var policy=new CustomerRoutingPolicy("besu-customer-v1",Set.of(BeneficiaryOutcome.TOKEN_WALLET),Set.of("SIMULATED-BESU-DEMO"),new java.math.BigDecimal("3"),2);
        var instruction=new com.cognizant.stablecoins.payment.api.dto.PaymentIntentRequest(BeneficiaryOutcome.TOKEN_WALLET,time.plusSeconds(3600),new java.math.BigDecimal("3"),com.cognizant.stablecoins.payment.domain.SupportedCurrency.USD,RoutingPriority.LOWEST_COST,Set.of(),true,
            new com.cognizant.stablecoins.payment.settlement.SettlementSelection("besu-local",ctx.token().contract(),payment.getAmount()));
        payment.setIntent(new PaymentIntentSnapshot(old.intentId(),1,CUSTOMER,ACTOR.actorId(),time,SettlementAsset.DEMOUSD,true,false,instruction,policy,
            new com.cognizant.stablecoins.payment.settlement.ConfirmedSettlement(ctx,payment.getAmount(),beneficiary.getWalletAddress())));
        config.setPolicies(Map.of(CUSTOMER,policy));
        selections=new com.cognizant.stablecoins.payment.settlement.SettlementSelectionProperties();
        selections.setAllowedNetworks(Map.of(CUSTOMER,Set.of("besu-local")));
        selections.setNetworks(List.of(new com.cognizant.stablecoins.flow.SettlementNetworkRegistry.Entry(ctx,true)));
        quoteService=mock(com.cognizant.stablecoins.payment.settlement.SettlementQuoteService.class);
        when(quoteService.get(any(),any(),any(),any())).thenAnswer(i->drafts.get(i.getArgument(2)));
        service.settlementReviews(new com.cognizant.stablecoins.payment.settlement.SettlementReviewGuard(quoteService,selections));
        preparationId=UUID.randomUUID(); draftId=UUID.randomUUID(); addDraft(draftId);
    }
    com.cognizant.stablecoins.flow.SettlementQuote addDraft(UUID id) {
        var binding=com.cognizant.stablecoins.payment.settlement.ExplicitSettlementService.instruction(payment);
        var request=new com.cognizant.stablecoins.flow.SettlementPreparation.Request(preparationId,binding,time);
        var observation=new com.cognizant.stablecoins.flow.SettlementObservation(binding,100,java.math.BigDecimal.TEN,java.math.BigDecimal.ONE,
            java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,new java.math.BigDecimal("1e-18"),time,time.plusSeconds(15));
        var availability=new com.cognizant.stablecoins.flow.SettlementAvailability(new com.cognizant.stablecoins.flow.SettlementPreparation.Evidence(request,observation),java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,time);
        var terms=new com.cognizant.stablecoins.flow.SettlementQuote.Terms("besu-quote-v1","SIMULATED-BESU-DEMO",java.math.BigDecimal.ONE,900,java.math.BigDecimal.ONE);
        var q=new com.cognizant.stablecoins.flow.SettlementQuote(new com.cognizant.stablecoins.flow.SettlementBinding.Review(id,1,preparationId,UUID.randomUUID(),1,terms.providerId(),binding,terms.simulatedFeeUsd(),terms.reserve(observation),time,time.plusSeconds(900)),availability,terms);
        drafts.put(id,q);return q;
    }
    RecommendationView propose(){return service.proposeSettlement(payment.getPaymentId(),preparationId,draftId,ACTOR);}
    RecommendationView accept(RecommendationView r,AuthorizedCustomer actor){var q=r.document().settlementQuote().review();return service.acceptQuote(payment.getPaymentId(),r.recommendationId(),r.version(),q.quoteId(),q.quoteVersion(),actor);}
    RecommendationView approve(RecommendationView r,AuthorizedCustomer actor){var q=r.document().settlementQuote().review();return service.approve(payment.getPaymentId(),r.recommendationId(),r.version(),q.quoteId(),q.quoteVersion(),actor);}
    void conflict(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOf(ApprovalConflictException.class).extracting("errorCode").isEqualTo(code);}
    void approveFully(RecommendationView r){accept(r,ACTOR);approve(r,ACTOR);var second=new AuthorizedCustomer(CUSTOMER,"second");accept(r,second);approve(r,second);}
    @Test void canonicalTypedDocumentRoundTripsAndEventCannotBeMistakenForUsdc() throws Exception {
        var r=propose(); assertThat(r.state()).isEqualTo(RoutingRecommendation.State.PROPOSED);
        assertThat(r.document().quote()).isNull();assertThat(r.document().assessment()).isNull();
        assertThat(r.document().settlementQuote()).isEqualTo(drafts.get(draftId));
        assertThat(r.document().settlementReview(r.version()).instruction().context().token().symbol()).isEqualTo("DEMOUSD");
        assertThat(mapper.readValue(mapper.writeValueAsString(r.document()),RecommendationDocument.class)).isEqualTo(r.document());
        var captor=org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("SettlementRecommendationProposed");
        var event=mapper.readTree(captor.getValue().getPayload());assertThat(event.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(event.path("paymentId").asText()).isEqualTo(payment.getPaymentId());
        assertThat(event.path("correlationId").asText()).isEqualTo(payment.getCorrelationId());
        assertThat(event.toString()).contains("PRIVATE_DEMO","DEMOUSD","BESU_GAS","besu-customer-v1","besu-quote-v1");
        verify(payments,never()).save(any());
    }
    @Test void requiresOwnAcceptanceAndTwoDistinctApprovers(){var r=propose();conflict(()->approve(r,ACTOR),"QUOTE_ACCEPTANCE_REQUIRED");accept(r,ACTOR);assertThat(approve(r,ACTOR).state()).isEqualTo(RoutingRecommendation.State.PROPOSED);approve(r,ACTOR);assertThat(accepted).hasSize(2);var second=new AuthorizedCustomer(CUSTOMER,"second");conflict(()->approve(r,second),"QUOTE_ACCEPTANCE_REQUIRED");accept(r,second);assertThat(approve(r,second).state()).isEqualTo(RoutingRecommendation.State.APPROVED);assertThat(approve(r,ACTOR).approvalCount()).isEqualTo(2);}
    @Test void wrongVersionOrQuoteCannotVote(){var r=propose();var q=r.document().settlementQuote().review();conflict(()->service.acceptQuote(payment.getPaymentId(),draftId,2,q.quoteId(),1,ACTOR),"RECOMMENDATION_VERSION_MISMATCH");conflict(()->service.acceptQuote(payment.getPaymentId(),draftId,1,UUID.randomUUID(),1,ACTOR),"QUOTE_VERSION_MISMATCH");conflict(()->service.acceptQuote(payment.getPaymentId(),draftId,1,q.quoteId(),2,ACTOR),"QUOTE_VERSION_MISMATCH");assertThat(accepted).isEmpty();}
    @Test void foreignCustomerDeniedBeforeQuoteRead(){assertThatThrownBy(()->service.proposeSettlement(payment.getPaymentId(),preparationId,draftId,new AuthorizedCustomer(UUID.randomUUID(),"foreign"))).isInstanceOf(PaymentAccessException.class);verifyNoInteractions(quoteService,recommendations);}
    @Test void unavailableIntegrationFailsClosed(){service.settlementReviews(null);conflict(this::propose,"SETTLEMENT_REVIEW_NOT_CONFIGURED");verifyNoInteractions(outbox,decisions);}
    @Test void snapshotExpiryDoesNotPretendQuoteTermsExpired(){var r=propose();time=time.plusSeconds(16);accept(r,ACTOR);assertThat(approve(r,ACTOR).approvalCount()).isEqualTo(1);}
    @Test void quoteExpiresAtExactBoundary(){var r=propose();time=r.expiresAt();conflict(()->accept(r,ACTOR),"RECOMMENDATION_EXPIRED");}
    @Test void customerPolicyChangeBlocksApproval(){var r=propose();config.setPolicies(Map.of());conflict(()->accept(r,ACTOR),"POLICY_CHANGED");}
    @Test void disabledNetworkBlocksApproval(){var r=propose();selections.setNetworks(List.of(new com.cognizant.stablecoins.flow.SettlementNetworkRegistry.Entry(drafts.get(draftId).review().instruction().context(),false)));conflict(()->accept(r,ACTOR),"SETTLEMENT_NETWORK_UNAVAILABLE");}
    @Test void removedNetworkPermissionBlocksApproval(){var r=propose();selections.setAllowedNetworks(Map.of());conflict(()->accept(r,ACTOR),"SETTLEMENT_NETWORK_NOT_ALLOWED");}
    @Test void changedContextPolicyBlocksApproval(){var r=propose();var c=drafts.get(draftId).review().instruction().context();var changed=new com.cognizant.stablecoins.flow.SettlementNetwork.Context(c.token(),c.nativeAsset(),c.treasury(),c.feeModel(),"changed-v2");selections.setNetworks(List.of(new com.cognizant.stablecoins.flow.SettlementNetworkRegistry.Entry(changed,true)));conflict(()->accept(r,ACTOR),"SETTLEMENT_CONTEXT_CHANGED");}
    @Test void changedBeneficiaryBlocksApproval(){var r=propose();beneficiary.setWalletAddress("0x"+"f".repeat(40));conflict(()->accept(r,ACTOR),"BENEFICIARY_OR_ACCOUNT_CHANGED");}
    @Test void inactiveAccountBlocksApproval(){var r=propose();var a=account();a.setStatus("INACTIVE");when(accounts.findById(payment.getSenderAccountId())).thenReturn(Optional.of(a));conflict(()->accept(r,ACTOR),"ACCOUNT_OR_BENEFICIARY_INVALID");}
    @Test void changedComplianceBlocksApproval(){var r=propose();payment.setStatus("BLOCKCHAIN_SUBMITTED");conflict(()->accept(r,ACTOR),"COMPLIANCE_OR_EXECUTION_STATE_INVALID");}
    @Test void changedStoredDraftBlocksApproval(){var r=propose();addDraft(draftId);conflict(()->accept(r,ACTOR),"SETTLEMENT_QUOTE_CHANGED");}
    @Test void wrongPreparationCannotReuseProposal(){var r=propose();conflict(()->service.proposeSettlement(payment.getPaymentId(),UUID.randomUUID(),draftId,ACTOR),"PROPOSAL_KEY_CONFLICT");assertThat(stored).hasSize(1);}
    @Test void replayNeverRefreshesOrExtendsExpiredReview(){var r=propose();time=r.expiresAt();var replay=propose();assertThat(replay.state()).isEqualTo(RoutingRecommendation.State.EXPIRED);assertThat(replay.document()).isEqualTo(r.document());assertThat(stored).hasSize(1);}
    @Test void supersedingUsesCanonicalVersionWithoutRewritingDraft(){var first=propose();draftId=UUID.randomUUID();addDraft(draftId);var second=propose();assertThat(second.version()).isEqualTo(2);assertThat(second.document().settlementQuote().review().version()).isEqualTo(1);assertThat(second.document().settlementReview(second.version()).version()).isEqualTo(2);conflict(()->accept(first,ACTOR),"RECOMMENDATION_SUPERSEDED");}
    @Test void approvedWorkCannotBeReplacedOrSentToLegacyExecution(){var r=propose();approveFully(r);var q=r.document().settlementQuote().review();conflict(()->service.authorizeExecution(payment.getPaymentId(),r.recommendationId(),r.version(),q.quoteId(),q.quoteVersion(),ACTOR),"SETTLEMENT_EXECUTION_ACCEPTANCE_PENDING");conflict(()->service.markExecuted(payment.getPaymentId(),r.recommendationId(),ACTOR),"SETTLEMENT_EXECUTION_ACCEPTANCE_PENDING");draftId=UUID.randomUUID();addDraft(draftId);conflict(this::propose,"PRIOR_APPROVAL_UNRESOLVED");}
    @Test void rejectionIsAuditedAndCannotBeApproved(){var r=propose();service.reject(payment.getPaymentId(),draftId,1,ACTOR);conflict(()->accept(r,ACTOR),"RECOMMENDATION_REJECTED");assertThat(accepted).hasSize(1);}
    @Test void privatePolicyCannotReduceIndependentThresholdToOne(){var i=payment.getIntent();var old=i.policy();var policy=new CustomerRoutingPolicy(old.version(),old.allowedOutcomes(),old.allowedProviderIds(),old.maximumFeeUsd(),1);payment.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),i.customerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),true,false,i.instruction(),policy,i.settlement()));config.setPolicies(Map.of(CUSTOMER,policy));conflict(this::propose,"TWO_INDEPENDENT_APPROVERS_REQUIRED");}
    @Test void legacyAgentCannotTreatPrivateMaterialAsSevenSignalEvidence(){var r=propose();var access=new com.cognizant.stablecoins.payment.security.CustomerAccessProperties();access.setMemberships(Map.of(ACTOR.actorId(),new com.cognizant.stablecoins.payment.security.CustomerAccessProperties.Membership(CUSTOMER,Set.of(com.cognizant.stablecoins.payment.security.CustomerAccessProperties.Role.PAYMENT_OPERATOR))));var source=new com.cognizant.stablecoins.payment.agent.StoredAgentEvidenceSource(payments,recommendations,config,access);assertThatThrownBy(()->source.read(payment.getPaymentId(),r.recommendationId(),ACTOR,false)).hasMessage("AGENT_PRIVATE_REVIEW_UNSUPPORTED");}
    @Test void privateExecutionAuthorityRequiresExactApprovedCurrentMaterial(){
        var r=propose();var q=r.document().settlementQuote().review();
        conflict(()->service.authorizeSettlementExecution(payment.getPaymentId(),draftId,1,q.quoteId(),1,ACTOR),"APPROVAL_REQUIRED");
        approveFully(r);
        assertThat(service.authorizeSettlementExecution(payment.getPaymentId(),draftId,1,q.quoteId(),1,ACTOR).state())
                .isEqualTo(RoutingRecommendation.State.APPROVED);
        beneficiary.setWalletAddress("0x"+"f".repeat(40));
        conflict(()->service.authorizeSettlementExecution(payment.getPaymentId(),draftId,1,q.quoteId(),1,ACTOR),"BENEFICIARY_OR_ACCOUNT_CHANGED");
    }
    @Test void privateAuthorityRejectsExpiredApproval(){
        var r=propose();approveFully(r);var q=r.document().settlementQuote().review();time=r.expiresAt();
        conflict(()->service.authorizeSettlementExecution(payment.getPaymentId(),draftId,1,q.quoteId(),1,ACTOR),"APPROVAL_REQUIRED");
    }
    @Test void expiredClaimedReviewCannotBeReplacedByAnotherPrivateProposal(){
        var r=propose();approveFully(r);time=r.expiresAt();service.get(payment.getPaymentId(),draftId,ACTOR);
        when(quoteService.executionClaimed(payment.getPaymentId())).thenReturn(true);
        draftId=UUID.randomUUID();addDraft(draftId);
        conflict(this::propose,"SETTLEMENT_EXECUTION_ALREADY_CLAIMED");
    }
    @Test void privateDocumentRejectsMixedUsdcQuote(){var r=propose();assertThatThrownBy(()->new RecommendationDocument(r.document().intent(),context,null,null,quote,r.document().instruction(),"mixed","test",r.document().settlementQuote())).hasMessage("MIXED_SETTLEMENT_REVIEW_MATERIAL");}
    @Test void privateIntentChangeBlocksAcceptance(){var r=propose();var i=payment.getIntent();payment.setIntent(new PaymentIntentSnapshot(i.intentId(),2,i.customerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),true,false,i.instruction(),i.policy(),i.settlement()));conflict(()->accept(r,ACTOR),"INTENT_CHANGED");}
    @Test void providerMustBeExplicitlyAllowed(){var i=payment.getIntent();var old=i.policy();var policy=new CustomerRoutingPolicy(old.version(),old.allowedOutcomes(),Set.of("OTHER"),old.maximumFeeUsd(),2);payment.setIntent(new PaymentIntentSnapshot(i.intentId(),i.intentVersion(),i.customerId(),i.confirmedBy(),i.confirmedAt(),i.settlementAsset(),true,false,i.instruction(),policy,i.settlement()));config.setPolicies(Map.of(CUSTOMER,policy));conflict(this::propose,"SETTLEMENT_PROVIDER_NOT_ALLOWED");}
    com.cognizant.stablecoins.flow.SettlementBinding.Execution claimedExecution(RecommendationView r) {
        when(quoteService.executionClaimed(payment.getPaymentId())).thenReturn(true);
        service.settlementReviews(new com.cognizant.stablecoins.payment.settlement.SettlementReviewGuard(quoteService,selections,
                mock(com.cognizant.stablecoins.flow.SettlementExecutionReads.class)));
        return new com.cognizant.stablecoins.flow.SettlementBinding.Execution(UUID.randomUUID(),r.document().settlementReview(r.version()));
    }
    @Test void claimedApprovedPrivateReviewCanBeExecutedWithoutOpeningNewApproval() {
        var r=propose();approveFully(r);var e=claimedExecution(r);
        service.markSettlementExecuted(e,ACTOR);
        assertThat(stored.get(r.recommendationId()).state).isEqualTo(RoutingRecommendation.State.EXECUTED);
        conflict(()->service.markSettlementExecuted(e,ACTOR),"APPROVAL_REQUIRED");
    }
    @Test void claimedFundingRevalidatesBeneficiary() {
        var r=propose();approveFully(r);var e=claimedExecution(r);beneficiary.setWalletAddress("0x"+"f".repeat(40));
        conflict(()->service.markSettlementExecuted(e,ACTOR),"BENEFICIARY_OR_ACCOUNT_CHANGED");
    }
    @Test void claimDoesNotPermitAnotherPreparationOrReview() {
        var r=propose();approveFully(r);var e=claimedExecution(r);var q=e.review();
        var changed=new com.cognizant.stablecoins.flow.SettlementBinding.Review(q.recommendationId(),q.version(),UUID.randomUUID(),
                q.quoteId(),q.quoteVersion(),q.providerId(),q.instruction(),q.feeUsd(),q.nativeFeeReserve(),q.assessedAt(),q.expiresAt());
        assertThatThrownBy(()->service.markSettlementExecuted(new com.cognizant.stablecoins.flow.SettlementBinding.Execution(e.executionId(),changed),ACTOR))
                .hasMessage("SETTLEMENT_APPROVAL_BINDING_CHANGED");
        assertThat(stored.get(r.recommendationId()).state).isEqualTo(RoutingRecommendation.State.APPROVED);
    }
    @Test void missingCanonicalClaimFailsClosedEvenForApprovedReview() {
        var r=propose();approveFully(r);var e=claimedExecution(r);
        var reads=mock(com.cognizant.stablecoins.flow.SettlementExecutionReads.class);
        doThrow(new IllegalStateException("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED")).when(reads).requireClaimed(e);
        service.settlementReviews(new com.cognizant.stablecoins.payment.settlement.SettlementReviewGuard(quoteService,selections,reads));
        assertThatThrownBy(()->service.markSettlementExecuted(e,ACTOR)).hasMessage("CANONICAL_SETTLEMENT_EXECUTION_CLAIM_REQUIRED");
        assertThat(stored.get(r.recommendationId()).state).isEqualTo(RoutingRecommendation.State.APPROVED);
    }
    @Test void claimedFundingStillRejectsExpiredReview() {
        var r=propose();approveFully(r);var e=claimedExecution(r);time=r.expiresAt();
        conflict(()->service.markSettlementExecuted(e,ACTOR),"APPROVAL_REQUIRED");
    }
}
