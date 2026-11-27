package com.cognizant.stablecoins.payment.config;

import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.event.publisher.DomainEventPublisher;
import com.cognizant.stablecoins.payment.model.OutboxEvent;
import com.cognizant.stablecoins.payment.outbox.OutboxDispatcher;
import com.cognizant.stablecoins.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BesuFundingTransportTest {
    final SqsClient sqs=mock(SqsClient.class);
    final EventBridgeClient bridge=mock(EventBridgeClient.class);
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(EventingConfig.class)
        .withBean(SqsClient.class,()->sqs).withBean(EventBridgeClient.class,()->bridge)
        .withPropertyValues("connections.aws.region=us-west-2", "app.sqs.payment-created-queue-name=created",
            "app.sqs.compliance-completed-queue-name=compliance", "app.sqs.ledger-funding-queue-name=public-funding");
    DomainEvent<?> event(String type){return new DomainEvent<>("event-1",type,"1.0","PAY-1","CORR-1",Map.of("simulated",true));}
    @Test void defaultLocalRouteCannotAcknowledgePrivateFunding(){
        runner.withPropertyValues("connections.aws.endpoint=http://localhost:4566").run(c->{
            assertThatThrownBy(()->c.getBean(DomainEventPublisher.class).publish(event("BesuFundingAuthorized")))
                .hasMessageContaining("No configured queue");verifyNoInteractions(sqs);
        });
    }
    @Test void defaultAwsRouteCannotLosePrivateFundingWithoutRule(){
        runner.run(c->{assertThatThrownBy(()->c.getBean(DomainEventPublisher.class).publish(event("BesuFundingAuthorized")))
            .hasMessageContaining("disabled");verifyNoInteractions(bridge);});
    }
    @Test void enabledPrivateRoutePreservesEnvelopeAndPublicDestination() throws Exception {
        runner.withPropertyValues("connections.aws.endpoint=http://localhost:4566", "app.besu.funding-events.enabled=true",
            "app.sqs.besu-ledger-funding-queue-name=private-funding").run(c->{
            var publisher=c.getBean(DomainEventPublisher.class);var event=event("BesuFundingAuthorized");publisher.publish(event);
            publisher.publish(event("StablecoinFundingCompleted"));
            var requests=ArgumentCaptor.forClass(SendMessageRequest.class);verify(sqs,times(2)).sendMessage(requests.capture());
            assertThat(requests.getAllValues()).extracting(SendMessageRequest::queueUrl).containsExactly(
                "http://localhost:4566/000000000000/private-funding","http://localhost:4566/000000000000/public-funding");
            var mapper=c.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            assertThat(mapper.readTree(requests.getAllValues().getFirst().messageBody())).isEqualTo(mapper.valueToTree(event));
        });
    }
    @Test void enabledAwsDeliveryPreservesDomainDetailAndSource(){
        when(bridge.putEvents(any(software.amazon.awssdk.services.eventbridge.model.PutEventsRequest.class)))
            .thenReturn(software.amazon.awssdk.services.eventbridge.model.PutEventsResponse.builder().failedEntryCount(0).build());
        runner.withPropertyValues("app.besu.funding-events.enabled=true").run(c->{
            var event=event("BesuFundingAuthorized");c.getBean(DomainEventPublisher.class).publish(event);
            var requests=ArgumentCaptor.forClass(software.amazon.awssdk.services.eventbridge.model.PutEventsRequest.class);
            verify(bridge).putEvents(requests.capture());var entries=requests.getValue().entries();assertThat(entries).hasSize(1);
            assertThat(entries.getFirst().source()).isEqualTo("stablecoins.payment-service");
            assertThat(entries.getFirst().detailType()).isEqualTo("BesuFundingAuthorized");
            var mapper=c.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
            assertThat(mapper.readTree(entries.getFirst().detail())).isEqualTo(mapper.valueToTree(event));verifyNoInteractions(sqs);
        });
    }
    @Test void privateQueueMustBeExplicitAndSeparate(){
        for(String queue:new String[]{"", "public-funding"}) runner.withPropertyValues("connections.aws.endpoint=http://localhost:4566",
            "app.besu.funding-events.enabled=true", "app.sqs.besu-ledger-funding-queue-name="+queue)
            .run(c->assertThat(c).hasFailed());
    }
    @Test void queueFailureRetainsOutboxUntilSuccessfulRetry(){
        runner.withPropertyValues("connections.aws.endpoint=http://localhost:4566", "app.besu.funding-events.enabled=true",
            "app.sqs.besu-ledger-funding-queue-name=private-funding").run(c->{
            var mapper=c.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);var event=event("BesuFundingAuthorized");
            var row=new OutboxEvent();row.setEventId(event.getEventId());row.setEventType(event.getEventType());
            row.setAggregateId("PAY-1");row.setPayload(mapper.writeValueAsString(event));
            var repository=mock(OutboxEventRepository.class);var dispatcher=new OutboxDispatcher(repository,c.getBean(DomainEventPublisher.class),mapper);
            when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException("injected delivery failure"))
                .thenReturn(SendMessageResponse.builder().messageId("accepted").build());
            dispatcher.publishOne(row);assertThat(row.getStatus()).isEqualTo("PENDING");assertThat(row.getRetryCount()).isEqualTo(1);
            assertThat(row.getPublishedAt()).isNull();dispatcher.publishOne(row);assertThat(row.getStatus()).isEqualTo("PUBLISHED");
            var messages=ArgumentCaptor.forClass(SendMessageRequest.class);verify(sqs,times(2)).sendMessage(messages.capture());
            assertThat(messages.getAllValues().get(0).messageBody()).isEqualTo(messages.getAllValues().get(1).messageBody());
        });
    }
}
