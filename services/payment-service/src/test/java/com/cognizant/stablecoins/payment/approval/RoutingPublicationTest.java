package com.cognizant.stablecoins.payment.approval;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.event.publisher.LocalSqsEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RoutingPublicationTest {
    @Test void unknownQueueMappingCannotBeAcknowledgedAsPublished() {
        var sqs=mock(SqsClient.class);var publisher=new LocalSqsEventPublisher(sqs,new ObjectMapper(),Map.of());
        var event=new DomainEvent<>("event-id","UnconfiguredType","1","PAY-X","CORR-X",Map.of());
        assertThatThrownBy(()->publisher.publish(event)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(sqs);
    }
}
