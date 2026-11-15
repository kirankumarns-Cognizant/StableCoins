package com.cognizant.stablecoins.payment.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LocalStackInitializerTest {
    LocalStackInitializer initializer(SqsClient sqs,String endpoint){return new LocalStackInitializer(sqs,endpoint,"created","created-dlq","compliance","compliance-dlq","review","review-dlq","funding","funding-dlq","submitted","submitted-dlq","confirmed","confirmed-dlq");}
    @Test void localBootstrapConfiguresEveryDlqEvenWhenQueueAlreadyExists() throws Exception {
        var sqs=mock(SqsClient.class);
        when(sqs.createQueue(any(CreateQueueRequest.class))).thenAnswer(i->CreateQueueResponse.builder().queueUrl("http://localhost:4566/000000000000/"+((CreateQueueRequest)i.getArgument(0)).queueName()).build());
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenAnswer(i->{String url=((GetQueueAttributesRequest)i.getArgument(0)).queueUrl();return GetQueueAttributesResponse.builder().attributes(java.util.Map.of(QueueAttributeName.QUEUE_ARN,"arn:aws:sqs:us-east-1:000000000000:"+url.substring(url.lastIndexOf('/')+1))).build();});
        initializer(sqs,"http://localhost:4566").init();
        var capture=ArgumentCaptor.forClass(SetQueueAttributesRequest.class);verify(sqs,times(6)).setQueueAttributes(capture.capture());
        for(var request:capture.getAllValues()){var policy=new ObjectMapper().readTree(request.attributes().get(QueueAttributeName.REDRIVE_POLICY));assertThat(policy.path("maxReceiveCount").asInt()).isEqualTo(3);assertThat(policy.path("deadLetterTargetArn").asText()).endsWith("-dlq");assertThat(request.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("120");}
    }
    @Test void missingLocalEndpointNeverCallsRealAws(){var sqs=mock(SqsClient.class);assertThatThrownBy(()->initializer(sqs,"").init()).hasMessageContaining("refusing AWS queue creation");verifyNoInteractions(sqs);}
    @Test void bootstrapFailureDoesNotReportReady(){var sqs=mock(SqsClient.class);when(sqs.createQueue(any(CreateQueueRequest.class))).thenThrow(SqsException.builder().message("test-only-unavailable").build());assertThatThrownBy(()->initializer(sqs,"http://localhost:4566").init()).isInstanceOf(SqsException.class);}
}
