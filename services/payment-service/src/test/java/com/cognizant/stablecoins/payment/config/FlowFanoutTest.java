package com.cognizant.stablecoins.payment.config;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.event.publisher.LocalSqsEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FlowFanoutTest {
 @Test void eachOwnerReceivesTheSameDomainEnvelope(){var sqs=mock(SqsClient.class);var mapper=new ObjectMapper().findAndRegisterModules();var publisher=new LocalSqsEventPublisher(sqs,mapper,Map.of()).withFanoutRoutes(Map.of("RoutingEvidenceRequested",List.of("quote","treasury")));var event=new DomainEvent<>(UUID.randomUUID().toString(),"RoutingEvidenceRequested","1.0","PAY-1","CORR-1",Map.of("requestId",UUID.randomUUID()));publisher.publish(event);var messages=ArgumentCaptor.forClass(SendMessageRequest.class);verify(sqs,times(2)).sendMessage(messages.capture());assertThat(messages.getAllValues()).extracting(SendMessageRequest::queueUrl).containsExactly("quote","treasury");assertThat(messages.getAllValues().get(0).messageBody()).isEqualTo(messages.getAllValues().get(1).messageBody());}
 @Test void partialDeliveryRetriesPreserveIdentityForIdempotentConsumers(){var sqs=mock(SqsClient.class);var first=new AtomicBoolean(true);when(sqs.sendMessage(any(SendMessageRequest.class))).thenAnswer(i->{SendMessageRequest message=i.getArgument(0);if(message.queueUrl().equals("treasury")&&first.getAndSet(false))throw new IllegalStateException("injected queue failure");return null;});var publisher=new LocalSqsEventPublisher(sqs,new ObjectMapper().findAndRegisterModules(),Map.of()).withFanoutRoutes(Map.of("RoutingEvidenceRequested",List.of("quote","treasury")));var event=new DomainEvent<>(UUID.randomUUID().toString(),"RoutingEvidenceRequested","1.0","PAY-1","CORR-1",Map.of());assertThatThrownBy(()->publisher.publish(event)).isInstanceOf(RuntimeException.class);publisher.publish(event);var messages=ArgumentCaptor.forClass(SendMessageRequest.class);verify(sqs,times(4)).sendMessage(messages.capture());assertThat(messages.getAllValues()).extracting(SendMessageRequest::messageBody).allMatch(body->body.contains(event.getEventId()));}
}
