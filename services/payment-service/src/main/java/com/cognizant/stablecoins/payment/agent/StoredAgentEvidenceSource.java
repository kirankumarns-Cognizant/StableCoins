package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.intent.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Captured server-owned evidence only. No live feed or fixture substitution is implied. */
@Service @Transactional(readOnly=true)
public class StoredAgentEvidenceSource implements AgentEvidenceSource {
    private final PaymentRepository payments;private final RoutingRecommendationRepository recommendations;
    private final RoutingPolicyProperties policies;private final CustomerAccessProperties access;
    public StoredAgentEvidenceSource(PaymentRepository p,RoutingRecommendationRepository r,RoutingPolicyProperties policies,CustomerAccessProperties access){this.payments=p;this.recommendations=r;this.policies=policies;this.access=access;}
    public Material read(String paymentId,UUID recommendationId,AuthorizedCustomer actor,boolean latest){
        if(actor==null)throw new PaymentAccessException("AUTHENTICATION_REQUIRED","Verified customer required");
        var membership=access.getMemberships().get(actor.actorId());
        if(membership==null||!membership.customerId().equals(actor.customerId())||!membership.roles().contains(CustomerAccessProperties.Role.PAYMENT_OPERATOR))
            throw new PaymentAccessException("CUSTOMER_ACCESS_DENIED","Customer membership is not authorized");
        var payment=payments.findById(paymentId).orElseThrow(()->new PaymentNotFoundException(paymentId));
        if(payment.getIntent()==null||!payment.getIntent().customerId().equals(actor.customerId()))
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Payment is outside customer scope");
        var original=recommendations.findById(recommendationId).orElseThrow(()->new AgentFailure("AGENT_RECOMMENDATION_NOT_FOUND"));
        if(!original.paymentId.equals(paymentId)||!original.customerId.equals(actor.customerId()))
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Recommendation is outside customer scope");
        var rows=recommendations.findByPaymentIdOrderByVersionDesc(paymentId);
        var r=latest&&!rows.isEmpty()?rows.getFirst():original;
        if(!r.customerId.equals(actor.customerId()))throw new AgentFailure("AGENT_SOURCE_SCOPE_INVALID");
        if(rows.stream().anyMatch(row->row.state==RoutingRecommendation.State.APPROVED||row.state==RoutingRecommendation.State.EXECUTED))
            throw new AgentFailure("AGENT_PRIOR_APPROVAL_UNRESOLVED");
        if(!Set.of("COMPLIANCE_PENDING","COMPLIANCE_APPROVED","QUOTE_PENDING","CREATED").contains(payment.getStatus()))
            throw new AgentFailure("AGENT_PAYMENT_STATE_BLOCKED");
        if(r.state!=RoutingRecommendation.State.PROPOSED&&r.state!=RoutingRecommendation.State.EXPIRED)
            throw new AgentFailure("AGENT_SOURCE_SUPERSEDED");
        if(!payment.getIntent().equals(r.document.intent())||!payment.getIntent().policy().equals(policies.getPolicies().get(actor.customerId())))
            throw new AgentFailure("AGENT_INTENT_OR_POLICY_CHANGED");
        if(r.document.settlementQuote()!=null)throw new AgentFailure("AGENT_PRIVATE_REVIEW_UNSUPPORTED");
        var c=r.document.context();
        if(!c.paymentId().equals(paymentId)||!c.correlationId().equals(payment.getCorrelationId())||!c.customerId().equals(actor.customerId())
                ||c.sourceAmountUsd().compareTo(payment.getAmount())!=0||!c.requestedRail().name().equals(payment.getPaymentRail()))
            throw new AgentFailure("AGENT_SOURCE_SCOPE_INVALID");
        return new Material(r.id,r.version,r.expiresAt,r.document);
    }
}
