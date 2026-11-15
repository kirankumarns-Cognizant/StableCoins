package com.cognizant.stablecoins.payment.approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoutingRecommendationRepository extends JpaRepository<RoutingRecommendation, UUID> {
    List<RoutingRecommendation> findByPaymentIdOrderByVersionDesc(String paymentId);
}
