package com.cognizant.stablecoins.payment.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.time.Duration;

@Configuration
public class RoutingAssessmentConfiguration {
    @Bean
    AssessmentRules assessmentRules(
            @Value("${app.routing.assessment.version}") String version,
            @Value("${app.routing.assessment.maximum-evidence-age}") Duration maximumAge,
            @Value("${app.routing.assessment.historical-window}") Duration window,
            @Value("${app.routing.assessment.minimum-sample-size}") long sample,
            @Value("${app.routing.assessment.latency-percentile}") int percentile,
            @Value("${app.routing.assessment.minimum-success-rate}") BigDecimal success,
            @Value("${app.routing.assessment.maximum-fraud-score}") BigDecimal fraud,
            @Value("${app.routing.assessment.maximum-price-deviation-bps}") BigDecimal deviation,
            @Value("${app.routing.assessment.maximum-candidates}") int candidates,
            @Value("${app.routing.assessment.maximum-observations}") int observations) {
        return new AssessmentRules(version, maximumAge, window, sample, percentile, success, fraud, deviation, candidates, observations);
    }
    @Bean
    RoutingEvidence.Network routingNetwork(@Value("${blockchain.network}") String name,
            @Value("${blockchain.chain-id}") long chainId, @Value("${blockchain.token-contract}") String contract) {
        return new RoutingEvidence.Network(name, chainId, contract, true);
    }
}
