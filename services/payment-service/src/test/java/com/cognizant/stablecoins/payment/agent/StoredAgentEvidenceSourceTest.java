package com.cognizant.stablecoins.payment.agent;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static com.cognizant.stablecoins.payment.routing.SimulatedRoutingScenarios.Scenario.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StoredAgentEvidenceSourceTest {
    PaymentRepository payments=mock(PaymentRepository.class);RoutingRecommendationRepository recommendations=mock(RoutingRecommendationRepository.class);
    RoutingPolicyProperties policies=new RoutingPolicyProperties();CustomerAccessProperties access=new CustomerAccessProperties();
    Payment payment=new Payment();RoutingRecommendation r=new RoutingRecommendation();StoredAgentEvidenceSource source;
    @BeforeEach void setup(){var m=material(NORMAL,1);r.id=SEED;r.paymentId="PAY-AGENT";r.customerId=ACTOR.customerId();r.version=1;r.document=m.document();r.expiresAt=m.expiresAt();r.state=RoutingRecommendation.State.PROPOSED;
        payment.setPaymentId(r.paymentId);payment.setCorrelationId("CORR-AGENT");payment.setAmount(m.document().context().sourceAmountUsd());payment.setPaymentRail("AUTO");payment.setIntent(m.document().intent());payment.setStatus("COMPLIANCE_APPROVED");
        policies.setPolicies(Map.of(ACTOR.customerId(),payment.getIntent().policy()));access.setMemberships(Map.of(ACTOR.actorId(),new CustomerAccessProperties.Membership(ACTOR.customerId(),Set.of(CustomerAccessProperties.Role.PAYMENT_OPERATOR))));
        when(payments.findById(r.paymentId)).thenReturn(Optional.of(payment));when(recommendations.findById(SEED)).thenReturn(Optional.of(r));when(recommendations.findByPaymentIdOrderByVersionDesc(r.paymentId)).thenReturn(List.of(r));
        source=new StoredAgentEvidenceSource(payments,recommendations,policies,access);
    }
    AgentEvidenceSource.Material read(){return source.read("PAY-AGENT",SEED,ACTOR,false);}
    @Test void sourceReadsDoNotMutateLifecycleOrPublishEvents(){assertThat(read().document()).isEqualTo(r.document);assertThat(r.state).isEqualTo(RoutingRecommendation.State.PROPOSED);
        verify(payments,never()).save(any());verify(recommendations,never()).save(any());}
    @Test void priorApprovalBlocksAgentRerouting(){r.state=RoutingRecommendation.State.APPROVED;assertThatThrownBy(this::read).hasMessage("AGENT_PRIOR_APPROVAL_UNRESOLVED");}
    @Test void unknownSubmissionCannotCreateAnotherFinancialLeg(){payment.setStatus("BLOCKCHAIN_SUBMITTED");assertThatThrownBy(this::read).hasMessage("AGENT_PAYMENT_STATE_BLOCKED");}
    @Test void removedPolicyBlocksStaleEvidence(){policies.setPolicies(Map.of());assertThatThrownBy(this::read).hasMessage("AGENT_INTENT_OR_POLICY_CHANGED");}
    @Test void revokedMembershipBlocksToolRead(){access.setMemberships(Map.of());assertThatThrownBy(this::read).isInstanceOf(PaymentAccessException.class);}
    @Test void anotherCustomersRecommendationIsRejected(){r.customerId=UUID.randomUUID();assertThatThrownBy(this::read).isInstanceOf(PaymentAccessException.class);}
    @Test void contextMismatchIsRejected(){payment.setCorrelationId("CORR-OTHER");assertThatThrownBy(this::read).hasMessage("AGENT_SOURCE_SCOPE_INVALID");}
}
