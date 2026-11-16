package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.routing.*;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.approval.RoutingRecommendation.State;
/** Human approval boundary. No signing, reservation, payment-state mutation or model mutation tools. */
@Service @Transactional(noRollbackFor=ApprovalConflictException.class)
public class RecommendationService {
    private final PaymentRepository payments;
    private final RoutingRecommendationRepository recommendations;
    private final RoutingDecisionRepository decisions;
    private final RoutingAuditRepository audits;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final RoutingPolicyProperties policies;
    private final DeterministicRoutingAssessmentService assessor;
    private final Clock clock;
    private final AccountRepository accounts;
    private final BeneficiaryRepository beneficiaries;
    @Autowired
    public RecommendationService(PaymentRepository payments, RoutingRecommendationRepository recommendations,
            RoutingDecisionRepository decisions, RoutingAuditRepository audits, OutboxEventRepository outbox,
            ObjectMapper mapper, RoutingPolicyProperties policies, DeterministicRoutingAssessmentService assessor,
            AccountRepository accounts, BeneficiaryRepository beneficiaries) {
        this(payments,recommendations,decisions,audits,outbox,mapper,policies,assessor,accounts,beneficiaries,Clock.systemUTC());
    }
    RecommendationService(PaymentRepository payments, RoutingRecommendationRepository recommendations,
            RoutingDecisionRepository decisions, RoutingAuditRepository audits, OutboxEventRepository outbox,
            ObjectMapper mapper, RoutingPolicyProperties policies, DeterministicRoutingAssessmentService assessor,
            AccountRepository accounts, BeneficiaryRepository beneficiaries, Clock clock) {
        this.payments=payments; this.recommendations=recommendations; this.decisions=decisions;
        this.audits=audits; this.outbox=outbox; this.mapper=mapper; this.policies=policies; this.assessor=assessor; this.clock=clock; this.accounts=accounts; this.beneficiaries=beneficiaries;
    }
    /** Internal deterministic entry point; producers must supply trusted owner-service quote/evidence.
     * There is deliberately no public proposal/quote/evidence write API or fixture fallback. */
    public RecommendationView propose(UUID proposalId, AuthorizedCustomer actor, AssessmentContext context,
            RoutingEvidence.Snapshot evidence, RecommendationDocument.Quote quote) {
        Objects.requireNonNull(proposalId); Objects.requireNonNull(context);
        Payment payment=lock(context.paymentId(),actor);
        bind(payment,context);
        var existing=recommendations.findById(proposalId);
        if(existing.isPresent()) {
            var previous=existing.get(); authorize(previous,actor);
            if(!previous.paymentId.equals(payment.getPaymentId()) || !previous.document.context().equals(context)
                    || !previous.document.evidence().equals(evidence) || !Objects.equals(previous.document.quote(),quote))
                throw conflict("PROPOSAL_KEY_CONFLICT");
            expire(previous); return view(previous);
        }
        requirePolicy(payment);
        AssessmentResult assessment=assessor.assess(actor,context,payment.getIntent(),evidence);
        checkQuote(payment,context,assessment,quote);
        var history=recommendations.findByPaymentIdOrderByVersionDesc(payment.getPaymentId());
        // Validate the complete history before superseding any still-proposed row.
        for(var previous:history) {
            expire(previous);
            if(previous.state==State.APPROVED || previous.state==State.EXECUTED) throw conflict("PRIOR_APPROVAL_UNRESOLVED");
        }
        var instruction=instruction(payment);
        for(var previous:history) {
            if(previous.state==State.PROPOSED) change(previous,State.SUPERSEDED,actor.actorId(),"RoutingRecommendationSuperseded");
        }
        recommendations.flush(); // Flush old status before INSERT checks the one-active-row index.
        var r=new RoutingRecommendation(); r.id=proposalId; r.paymentId=payment.getPaymentId();
        r.correlationId=payment.getCorrelationId(); r.customerId=actor.customerId();
        r.version=history.isEmpty()?1:Math.addExact(history.getFirst().version,1);
        r.requiredApprovals=payment.getIntent().policy().requiredApprovals();
        r.state=State.DRAFT; r.createdAt=clock.instant();
        r.expiresAt=quote==null?assessment.expiresAt():minimum(assessment.expiresAt(),quote.expiresAt());
        String rationale=assessment.preferredCandidateId()==null ? "No approvable route: " + assessment.status()
            : "Deterministic priority " + assessment.priority() + " selected " + assessment.preferredCandidateId()
                + "; see itemized costs, eligibility findings and source references. Human approval required.";
        r.document=new RecommendationDocument(payment.getIntent(),context,evidence,assessment,quote,instruction,rationale,"DETERMINISTIC/"+assessment.ruleVersion());
        recommendations.save(r);
        change(r,State.PROPOSED,actor.actorId(),"RoutingRecommendationProposed"); expire(r);
        return view(r);
    }
    /** Select the matching immutable owner quote using the same deterministic assessor as approval. */
    public RecommendationView proposeCaptured(UUID id,AuthorizedCustomer actor,AssessmentContext context,RoutingEvidence.Snapshot evidence,List<RecommendationDocument.Quote> quotes){
        var payment=lock(context.paymentId(),actor);bind(payment,context);requirePolicy(payment);
        var assessment=assessor.assess(actor,context,payment.getIntent(),evidence);
        var quote=quotes.stream().filter(q->Objects.equals(q.candidateId(),assessment.preferredCandidateId())).findFirst().orElse(null);
        // The coordinator links this ID using JDBC in the same transaction. Flush JPA first
        // so the immediate PostgreSQL foreign key can see the recommendation; do not commit.
        var proposed=propose(id,actor,context,evidence,quote);
        recommendations.flush();
        return proposed;
    }
    public RecommendationView get(String paymentId,UUID id,AuthorizedCustomer actor) {
        return view(load(paymentId,id,actor));
    }
    public RecommendationView acceptQuote(String paymentId,UUID id,int version,UUID quoteId,int quoteVersion,AuthorizedCustomer actor) {
        var r=load(paymentId,id,actor); requireVersion(r,version); requireQuoteVersion(r,quoteId,quoteVersion);
        if(decision(r,actor,"QUOTE_ACCEPTED")) return view(r);
        requireProposed(r); revalidate(r,actor);
        record(r,actor,"QUOTE_ACCEPTED"); event(r,actor.actorId(),"RoutingQuoteAccepted"); return view(r);
    }
    public RecommendationView approve(String paymentId,UUID id,int version,UUID quoteId,int quoteVersion,AuthorizedCustomer actor) {
        var r=load(paymentId,id,actor); requireVersion(r,version); requireQuoteVersion(r,quoteId,quoteVersion);
        if(decision(r,actor,"APPROVED")) return view(r);
        requireProposed(r); revalidate(r,actor);
        if(!decision(r,actor,"QUOTE_ACCEPTED")) throw conflict("QUOTE_ACCEPTANCE_REQUIRED");
        record(r,actor,"APPROVED");
        long count=decisions.countByRecommendationIdAndAction(r.id,"APPROVED");
        // Each actor contributes once; payment row lock serializes approvals and replacement.
        int required=r.requiredApprovals;
        if(count>=required) change(r,State.APPROVED,actor.actorId(),"RoutingRecommendationApproved");
        else event(r,actor.actorId(),"RoutingApprovalRecorded");
        return view(r);
    }
    public RecommendationView reject(String paymentId,UUID id,int version,AuthorizedCustomer actor) {
        var r=load(paymentId,id,actor); requireVersion(r,version);
        if(r.state==State.REJECTED && decision(r,actor,"REJECTED")) return view(r);
        requireProposed(r); record(r,actor,"REJECTED"); change(r,State.REJECTED,actor.actorId(),"RoutingRecommendationRejected"); return view(r);
    }
    /** Execution is deterministic authority; agents cannot call these internal Java entry points. */
    public RecommendationView authorizeExecution(String paymentId,UUID id,int version,UUID quoteId,int quoteVersion,AuthorizedCustomer actor) {
        var r=load(paymentId,id,actor);requireVersion(r,version);requireQuoteVersion(r,quoteId,quoteVersion);
        if(r.state!=State.APPROVED || decisions.countByRecommendationIdAndAction(r.id,"APPROVED")<r.requiredApprovals)throw conflict("APPROVAL_REQUIRED");
        revalidate(r,actor);return view(r);
    }
    public void markExecuted(String paymentId,UUID id,AuthorizedCustomer actor) {
        var r=load(paymentId,id,actor);
        if(r.state!=State.APPROVED)throw conflict("APPROVAL_REQUIRED");
        revalidate(r,actor);change(r,State.EXECUTED,actor.actorId(),"RoutingRecommendationExecuted");
    }
    private Payment lock(String id,AuthorizedCustomer actor) {
        if(actor==null) throw new PaymentAccessException("AUTHENTICATION_REQUIRED","Verified customer required");
        var p=payments.lockByPaymentId(id).orElseThrow(()->new PaymentNotFoundException(id));
        if(p.getIntent()==null || !p.getIntent().customerId().equals(actor.customerId()))
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Payment is outside customer scope");
        return p;
    }
    private RoutingRecommendation load(String paymentId,UUID id,AuthorizedCustomer actor) {
        lock(paymentId,actor); // Lock before loading mutable recommendation state.
        var r=recommendations.findById(id).orElseThrow(()->conflict("RECOMMENDATION_NOT_FOUND"));
        authorize(r,actor);
        if(!r.paymentId.equals(paymentId)) throw conflict("RECOMMENDATION_PAYMENT_MISMATCH");
        expire(r); return r;
    }
    private void authorize(RoutingRecommendation r,AuthorizedCustomer actor) {
        if(!r.customerId.equals(actor.customerId())) throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Recommendation is outside customer scope");
    }
    private void requirePolicy(Payment p) {
        if(!p.getIntent().policy().equals(policies.getPolicies().get(p.getIntent().customerId()))) throw conflict("POLICY_CHANGED");
    }
    private void bind(Payment p,AssessmentContext c) {
        if(!p.getCorrelationId().equals(c.correlationId()) || !p.getIntent().customerId().equals(c.customerId())
                || !"USD".equals(p.getSourceCurrency()) || p.getAmount().compareTo(c.sourceAmountUsd())!=0
                || !p.getPaymentRail().equals(c.requestedRail().name())) throw conflict("PAYMENT_CONTEXT_MISMATCH");
    }
    private RecommendationDocument.InstructionBinding instruction(Payment p) {
        var account=accounts.findById(p.getSenderAccountId()).orElseThrow(()->conflict("ACCOUNT_UNAVAILABLE"));
        var beneficiary=beneficiaries.findById(p.getBeneficiaryId()).orElseThrow(()->conflict("BENEFICIARY_UNAVAILABLE"));
        if(!account.getCustomerId().equals(p.getIntent().customerId()) || !beneficiary.getCustomerId().equals(p.getIntent().customerId())
                || !"ACTIVE".equals(account.getStatus()) || !"ACTIVE".equals(beneficiary.getStatus())
                || !"USD".equals(account.getCurrency()) || beneficiary.getWalletAddress()==null
                || !beneficiary.getWalletAddress().matches("0x[0-9a-fA-F]{40}")
                || beneficiary.getWalletAddress().equalsIgnoreCase("0x"+"0".repeat(40))) throw conflict("ACCOUNT_OR_BENEFICIARY_INVALID");
        var outcomes=new java.util.HashSet<>(p.getIntent().instruction().allowedAlternatives());
        outcomes.add(p.getIntent().instruction().requiredOutcome());
        if(outcomes.stream().anyMatch(o->o!=BeneficiaryOutcome.USDC_WALLET && !o.payoutCurrency().equals(beneficiary.getCurrency())))
            throw conflict("PAYOUT_CURRENCY_CHANGED");
        return new RecommendationDocument.InstructionBinding(p.getSenderAccountId(),p.getBeneficiaryId(),
                beneficiary.getWalletAddress().toLowerCase(java.util.Locale.ROOT),beneficiary.getCurrency());
    }
    private void revalidate(RoutingRecommendation r,AuthorizedCustomer actor) {
        Payment p=payments.findById(r.paymentId).orElseThrow(()->new PaymentNotFoundException(r.paymentId));
        if(!"COMPLIANCE_APPROVED".equals(p.getStatus())) throw conflict("COMPLIANCE_OR_EXECUTION_STATE_INVALID");
        requirePolicy(p); bind(p,r.document.context());
        if(!instruction(p).equals(r.document.instruction())) throw conflict("BENEFICIARY_OR_ACCOUNT_CHANGED");
        var old=r.document.assessment();
        if(!p.getIntent().equals(r.document.intent())) throw conflict("INTENT_CHANGED");
        var current=assessor.assess(actor,r.document.context(),p.getIntent(),r.document.evidence());
        if(current.status()!=AssessmentResult.Status.ASSESSED || !Objects.equals(current.preferredCandidateId(),old.preferredCandidateId())
                || !current.ruleVersion().equals(old.ruleVersion()) || !current.candidates().equals(old.candidates())) throw conflict("REASSESSMENT_REQUIRED");
        checkQuote(p,r.document.context(),current,r.document.quote());
    }
    private void checkQuote(Payment p,AssessmentContext c,AssessmentResult assessment,RecommendationDocument.Quote q) {
        if(assessment.status()!=AssessmentResult.Status.ASSESSED) {
            if(q!=null) throw conflict("NO_ELIGIBLE_ROUTE"); return;
        }
        if(q==null) throw conflict("QUOTE_REQUIRED");
        var selected=assessment.candidates().stream().filter(a->a.candidate().candidateId().equals(assessment.preferredCandidateId())).findFirst().orElseThrow();
        if(q.quoteId()==null || q.version()<1 || !q.simulated() || q.createdAt()==null || q.expiresAt()==null
                || q.createdAt().isAfter(clock.instant()) || !q.expiresAt().isAfter(clock.instant()) || !q.expiresAt().isAfter(q.createdAt())
                || !p.getPaymentId().equals(q.paymentId()) || !p.getCorrelationId().equals(q.correlationId())
                || !c.customerId().equals(q.customerId()) || !selected.candidate().candidateId().equals(q.candidateId())
                || !selected.candidate().providerId().equals(q.providerId()) || q.sourceAmountUsd()==null || q.transferAmountUsdc()==null
                || q.totalFeeUsd()==null || q.sourceAmountUsd().compareTo(c.sourceAmountUsd())!=0
                || q.transferAmountUsdc().compareTo(c.transferAmountUsdc())!=0 || !q.payoutAmounts().equals(c.requiredPayoutAmounts())
                || q.totalFeeUsd().compareTo(selected.totalCostUsd())!=0) throw conflict("QUOTE_CONTEXT_MISMATCH");
    }
    private void requireQuoteVersion(RoutingRecommendation r,UUID id,int version) {
        var q=r.document.quote();
        if(q==null || !q.quoteId().equals(id) || q.version()!=version) throw conflict("QUOTE_VERSION_MISMATCH");
    }
    private void requireVersion(RoutingRecommendation r,int version) { if(r.version!=version) throw conflict("RECOMMENDATION_VERSION_MISMATCH"); }
    private void requireProposed(RoutingRecommendation r) { if(r.state!=State.PROPOSED) throw conflict("RECOMMENDATION_"+r.state); }
    private boolean decision(RoutingRecommendation r,AuthorizedCustomer actor,String action) {
        return decisions.findByRecommendationIdAndActorIdAndAction(r.id,actor.actorId(),action).isPresent();
    }
    private void record(RoutingRecommendation r,AuthorizedCustomer actor,String action) {
        var d=new RoutingDecision(); d.id=UUID.randomUUID(); d.recommendationId=r.id; d.actorId=actor.actorId(); d.action=action; d.decidedAt=clock.instant();
        decisions.saveAndFlush(d); audit(r,actor.actorId(),action);
    }
    private void expire(RoutingRecommendation r) {
        if((r.state==State.PROPOSED || r.state==State.APPROVED) && !r.expiresAt.isAfter(clock.instant()))
            change(r,State.EXPIRED,"payment-service","RoutingRecommendationExpired");
    }
    private void change(RoutingRecommendation r,State state,String actor,String type) {
        r.state=state; recommendations.save(r); audit(r,actor,state.name()); event(r,actor,type);
    }
    private void audit(RoutingRecommendation r,String actor,String action) {
        var a=new RoutingAudit(); a.id=UUID.randomUUID(); a.recommendationId=r.id; a.paymentId=r.paymentId;
        a.correlationId=r.correlationId; a.actorId=actor; a.action=action; a.recordedAt=clock.instant(); audits.save(a);
    }
    /** v1 payload: immutable binding plus actor/state. Consumers must not treat this as execution. */
    public record DecisionEvent(UUID recommendationId,int recommendationVersion,UUID intentId,int intentVersion,
            String policyVersion,UUID evidenceSnapshotId,UUID quoteId,Integer quoteVersion,String actorId,State state,Instant expiresAt) { }
    private void event(RoutingRecommendation r,String actor,String type) {
        var a=r.document.assessment(); var q=r.document.quote();
        var payload=new DecisionEvent(r.id,r.version,a.intentId(),a.intentVersion(),a.policyVersion(),a.evidenceSnapshotId(),
                q==null?null:q.quoteId(),q==null?null:q.version(),actor,r.state,r.expiresAt);
        var event=new DomainEvent<>(UUID.randomUUID().toString(),type,"1",r.paymentId,r.correlationId,payload);
        try {
            var row=new OutboxEvent(); row.setEventId(event.getEventId()); row.setEventType(type); row.setEventVersion("1");
            row.setAggregateId(r.paymentId); row.setCorrelationId(r.correlationId); row.setPayload(mapper.writeValueAsString(event));
            row.setStatus("PENDING"); outbox.save(row);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot serialize routing decision",e); }
    }
    private RecommendationView view(RoutingRecommendation r) {
        return new RecommendationView(r.id,r.version,r.paymentId,r.correlationId,r.state,r.createdAt,r.expiresAt,
                decisions.countByRecommendationIdAndAction(r.id,"APPROVED"),r.requiredApprovals,r.document);
    }
    private static Instant minimum(Instant a,Instant b) { return a.isBefore(b)?a:b; }
    private static ApprovalConflictException conflict(String code) { return new ApprovalConflictException(code); }
}
