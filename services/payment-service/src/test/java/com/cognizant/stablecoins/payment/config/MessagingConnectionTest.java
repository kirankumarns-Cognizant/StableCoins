package com.cognizant.stablecoins.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MessagingConnectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EventingConfig.class);

    @Test
    void localPublisherUsesConfiguredNamesAndAccount() {
        runner.withPropertyValues("connections.aws.endpoint=http://localhost:14566",
                "connections.aws.account-id=123456789012",
                "app.sqs.routing-decisions-queue-name=customer-routing",
                "app.sqs.payment-created-queue-name=customer-created",
                "app.sqs.compliance-completed-queue-name=customer-completed")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Object publisher = context.getBean("domainEventPublisher");
                    @SuppressWarnings("unchecked")
                    Map<String, String> queues = (Map<String, String>) ReflectionTestUtils.getField(publisher, "eventTypeToQueueUrl");
                    assertThat(queues).containsEntry("PaymentCreated", "http://localhost:14566/123456789012/customer-created")
                            .containsEntry("ComplianceCompleted", "http://localhost:14566/123456789012/customer-completed");
                    for(String type:com.cognizant.stablecoins.payment.approval.RoutingDecisionEvents.TYPES)
                        assertThat(queues).containsEntry(type,"http://localhost:14566/123456789012/customer-routing");
                });
    }

    @Test
    void privateClosureNotificationsUseAuditQueueOnlyWhenExplicitlyEnabled() {
        var local=runner.withPropertyValues("connections.aws.endpoint=http://localhost:14566",
            "app.sqs.routing-decisions-queue-name=private-audit",
            "app.sqs.payment-created-queue-name=created","app.sqs.compliance-completed-queue-name=compliance");
        for(boolean enabled:new boolean[]{false,true})local.withPropertyValues("app.besu.closure.enabled="+enabled).run(context->{
            assertThat(context).hasNotFailed();
            @SuppressWarnings("unchecked") var queues=(Map<String,String>)ReflectionTestUtils.getField(context.getBean("domainEventPublisher"),"eventTypeToQueueUrl");
            for(String type:java.util.List.of("BesuBlockchainTransactionSubmitted","BesuBlockchainTransactionConfirmed",
                    "BesuSettlementCompleted","BesuReconciliationRequested","BesuReconciliationCompleted")) {
                if(enabled)assertThat(queues).containsEntry(type,"http://localhost:14566/000000000000/private-audit");
                else assertThat(queues).doesNotContainKey(type);
            }
        });
    }

    @Test
    void awsUsesDefaultCredentialChainRatherThanDummyKeys() {
        runner.withPropertyValues("app.sqs.payment-created-queue-name=customer-created",
                "app.sqs.compliance-completed-queue-name=customer-completed").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SqsClient.class).serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(DefaultCredentialsProvider.class);
            assertThat(context.getBean(SqsClient.class).serviceClientConfiguration().endpointOverride()).isEmpty();
        });
    }
}
