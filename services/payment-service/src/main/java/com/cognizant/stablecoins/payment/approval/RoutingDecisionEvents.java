package com.cognizant.stablecoins.payment.approval;
import java.util.Set;
/** v1 audit notifications, never commands to fund, sign, submit or pay out. */
public final class RoutingDecisionEvents {
    private RoutingDecisionEvents() { }
    public static final Set<String> TYPES=Set.of("RoutingRecommendationProposed", "RoutingQuoteAccepted",
        "RoutingApprovalRecorded", "RoutingRecommendationApproved", "RoutingRecommendationRejected",
        "RoutingRecommendationExpired", "RoutingRecommendationSuperseded", "RoutingRecommendationExecuted");
}
