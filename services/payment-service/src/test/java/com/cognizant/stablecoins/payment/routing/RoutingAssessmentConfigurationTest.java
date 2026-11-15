package com.cognizant.stablecoins.payment.routing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class RoutingAssessmentConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(c -> c.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(RoutingAssessmentConfiguration.class);
    @Test void readsServerOwnedRulesFromSharedConfiguration() {
        runner.withPropertyValues("ROUTING_ASSESSMENT_VERSION=customer-baseline-v2", "ROUTING_MINIMUM_SAMPLE_SIZE=200",
                "ROUTING_MAXIMUM_EVIDENCE_AGE=PT2M").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AssessmentRules.class).version()).isEqualTo("customer-baseline-v2");
            assertThat(context.getBean(AssessmentRules.class).minimumSampleSize()).isEqualTo(200);
            assertThat(context.getBean(AssessmentRules.class).maximumEvidenceAge()).isEqualTo(Duration.ofMinutes(2));
            assertThat(context.getBean(RoutingEvidence.Network.class).chainId()).isEqualTo(84532);
        });
    }
    @Test void rejectsInvalidRiskConfigurationInsteadOfRelaxingControls() {
        runner.withPropertyValues("ROUTING_MAXIMUM_FRAUD_SCORE=101").run(context -> assertThat(context).hasFailed());
    }
}
