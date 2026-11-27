package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Internal owner claim. No HTTP/model tool, submission event, reservation or financial journal. */
@Service
@ConditionalOnProperty(name="app.flow.enabled", havingValue="true")
@Transactional
public class SettlementExecutionClaimService {
    private final PaymentRepository payments;
    private final RecommendationService recommendations;
    private final RoutingDecisionRepository decisions;
    private final CustomerAccessProperties access;
    private final SettlementExecutionClaimStore store;
    private final Clock clock;

    @Autowired
    public SettlementExecutionClaimService(PaymentRepository payments, RecommendationService recommendations,
            RoutingDecisionRepository decisions, CustomerAccessProperties access, JdbcTemplate jdbc) {
        this(payments,recommendations,decisions,access,new SettlementExecutionClaimStore(jdbc),Clock.systemUTC());
    }
    SettlementExecutionClaimService(PaymentRepository payments, RecommendationService recommendations,
            RoutingDecisionRepository decisions, CustomerAccessProperties access,
            SettlementExecutionClaimStore store, Clock clock) {
        this.payments=payments;this.recommendations=recommendations;this.decisions=decisions;
        this.access=access;this.store=store;this.clock=clock;
    }

    public SettlementExecutionClaim claim(String paymentId, UUID executionId, UUID recommendationId,
            int version, UUID quoteId, int quoteVersion, AuthorizedCustomer actor) {
        if (actor==null) throw denied("AUTHENTICATION_REQUIRED");
        requireMember(actor.actorId(),actor.customerId(),Role.PAYMENT_OPERATOR);
        if(executionId==null || recommendationId==null || quoteId==null || version<1 || quoteVersion<1)
            throw conflict("SETTLEMENT_EXECUTION_IDENTIFIERS_REQUIRED");
        var payment=payments.lockByPaymentId(paymentId).orElseThrow(()->new PaymentNotFoundException(paymentId));
        if(payment.getIntent()==null || !actor.customerId().equals(payment.getIntent().customerId()))
            throw denied("CUSTOMER_SCOPE_MISMATCH");
        var prior=store.byPayment(paymentId);
        if(prior.isPresent()) {
            var c=prior.get();var r=c.execution().review();
            if(!c.execution().executionId().equals(executionId) || !r.recommendationId().equals(recommendationId)
                    || r.version()!=version || !r.quoteId().equals(quoteId) || r.quoteVersion()!=quoteVersion)
                throw conflict("SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
            return c; // Historical replay, never refreshes expiry or grants new submission authority.
        }
        if(store.byId(executionId).isPresent()) throw conflict("SETTLEMENT_EXECUTION_CLAIM_CONFLICT");
        var authorized=recommendations.authorizeSettlementExecution(paymentId,recommendationId,version,quoteId,quoteVersion,actor);
        var document=authorized.document();
        var review=document.settlementReview(authorized.version());
        if(!paymentId.equals(authorized.paymentId()) || !recommendationId.equals(authorized.recommendationId())
                || authorized.version()!=version || !review.quoteId().equals(quoteId) || review.quoteVersion()!=quoteVersion
                || !review.instruction().customerId().equals(actor.customerId())
                || !review.instruction().equals(ExplicitSettlementService.instruction(payment))
                || authorized.requiredApprovals()!=payment.getIntent().policy().requiredApprovals())
            throw conflict("SETTLEMENT_EXECUTION_BINDING_CHANGED");
        var approvals=approvals(recommendationId,actor.customerId());
        // Check time after locking and authority reads. Original RPC observation is not refreshed.
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        review.requireFresh(now);
        var claim=new SettlementExecutionClaim(new SettlementBinding.Execution(executionId,review),
                actor.actorId(),payment.getIntent().policy().version(),authorized.requiredApprovals(),approvals,now);
        store.capture(claim);
        return claim;
    }

    /** Fresh funding authority, unlike historical claim replay. Payment lock is retained by the caller transaction. */
    public SettlementExecutionClaim requireFundingAuthority(SettlementBinding.Execution execution,AuthorizedCustomer actor) {
        if(actor==null) throw denied("AUTHENTICATION_REQUIRED");
        requireMember(actor.actorId(),actor.customerId(),Role.PAYMENT_OPERATOR);
        var i=execution.instruction();
        var payment=payments.lockByPaymentId(i.paymentId()).orElseThrow(()->new PaymentNotFoundException(i.paymentId()));
        if(payment.getIntent()==null || !actor.customerId().equals(payment.getIntent().customerId()))
            throw denied("CUSTOMER_SCOPE_MISMATCH");
        var claim=store.byId(execution.executionId()).orElseThrow(()->conflict("SETTLEMENT_EXECUTION_CLAIM_REQUIRED"));
        if(!claim.execution().equals(execution) || !i.equals(ExplicitSettlementService.instruction(payment))
                || !claim.customerPolicyVersion().equals(payment.getIntent().policy().version())
                || claim.requiredApprovals()!=payment.getIntent().policy().requiredApprovals())
            throw conflict("SETTLEMENT_EXECUTION_BINDING_CHANGED");
        if(!claim.approvals().equals(approvals(execution.review().recommendationId(),actor.customerId())))
            throw conflict("SETTLEMENT_APPROVAL_EVIDENCE_CHANGED");
        execution.review().requireFresh(clock.instant());
        return claim;
    }

    private List<SettlementExecutionClaim.Approval> approvals(UUID reviewId, UUID customerId) {
        var rows=decisions.findByRecommendationIdOrderByDecidedAtAsc(reviewId);
        var acceptances=new HashMap<String,RoutingDecision>();
        var votes=new HashMap<String,RoutingDecision>();
        for(var row:rows) {
            if(!reviewId.equals(row.recommendationId) || row.id==null || row.actorId==null || row.decidedAt==null)
                throw conflict("SETTLEMENT_APPROVAL_EVIDENCE_INVALID");
            var target="QUOTE_ACCEPTED".equals(row.action)?acceptances:"APPROVED".equals(row.action)?votes:null;
            if(target==null || target.putIfAbsent(row.actorId,row)!=null)
                throw conflict("SETTLEMENT_APPROVAL_EVIDENCE_INVALID");
        }
        var result=new ArrayList<SettlementExecutionClaim.Approval>();
        for(var vote:votes.values()) {
            var accepted=acceptances.get(vote.actorId);
            if(accepted==null) throw conflict("QUOTE_ACCEPTANCE_REQUIRED");
            requireMember(vote.actorId,customerId,Role.PAYMENT_APPROVER);
            result.add(new SettlementExecutionClaim.Approval(vote.actorId,accepted.id,accepted.decidedAt,vote.id,vote.decidedAt));
        }
        result.sort(Comparator.comparing(SettlementExecutionClaim.Approval::actorId));
        return List.copyOf(result);
    }
    private void requireMember(String subject, UUID customerId, Role role) {
        var member=access.getMemberships().get(subject);
        if(member==null || !member.customerId().equals(customerId) || !member.roles().contains(role))
            throw denied("SETTLEMENT_EXECUTION_ROLE_REQUIRED");
    }
    private static PaymentAccessException denied(String code){return new PaymentAccessException(code,code);}
    private static ApprovalConflictException conflict(String code){return new ApprovalConflictException(code);}
}