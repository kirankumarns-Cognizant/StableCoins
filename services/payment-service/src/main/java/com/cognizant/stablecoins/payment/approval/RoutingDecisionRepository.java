package com.cognizant.stablecoins.payment.approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoutingDecisionRepository extends JpaRepository<RoutingDecision, UUID> {
    Optional<RoutingDecision> findByRecommendationIdAndActorIdAndAction(UUID recommendationId, String actorId, String action);
    List<RoutingDecision> findByRecommendationIdOrderByDecidedAtAsc(UUID recommendationId);
    long countByRecommendationIdAndAction(UUID recommendationId, String action);
}
