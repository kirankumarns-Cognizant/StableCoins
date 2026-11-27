package com.cognizant.stablecoins.payment.approval;
import java.util.Set;
/** Legacy v1 and private v1.0 audit notifications; never execution commands. */
public final class RoutingDecisionEvents {
    private RoutingDecisionEvents() { }
    public static final Set<String> TYPES=Set.of("RoutingRecommendationProposed", "RoutingQuoteAccepted",
        "RoutingApprovalRecorded", "RoutingRecommendationApproved", "RoutingRecommendationRejected",
        "RoutingRecommendationExpired", "RoutingRecommendationSuperseded", "RoutingRecommendationExecuted",
        "SettlementRecommendationProposed",
        "SettlementQuoteAccepted",
        "SettlementApprovalRecorded",
        "SettlementRecommendationApproved",
        "SettlementRecommendationRejected",
        "SettlementRecommendationExpired",
        "SettlementRecommendationSuperseded");
}
