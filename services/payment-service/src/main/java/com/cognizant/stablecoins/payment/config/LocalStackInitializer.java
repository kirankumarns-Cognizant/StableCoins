package com.cognizant.stablecoins.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.*;

/** Local-only queue bootstrap. Never creates or changes real AWS queues. CDK owns AWS resources. */
@Component @Profile("local")
public class LocalStackInitializer {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.core.env.Environment environment;
    private final SqsClient sqs;
    private final String endpoint;
    private final List<String[]> routes;
    public LocalStackInitializer(SqsClient sqs,
            @Value("${connections.aws.endpoint:}") String endpoint,
            @Value("${app.sqs.payment-created-queue-name}") String created,@Value("${app.sqs.payment-created-dlq-name}") String createdDlq,
            @Value("${app.sqs.compliance-completed-queue-name}") String compliance,@Value("${app.sqs.compliance-completed-dlq-name}") String complianceDlq,
            @Value("${app.sqs.routing-decisions-queue-name}") String review,@Value("${app.sqs.routing-decisions-dlq-name}") String reviewDlq,
            @Value("${app.sqs.ledger-funding-queue-name}") String funding,@Value("${app.sqs.ledger-funding-dlq-name}") String fundingDlq,
            @Value("${app.sqs.blockchain-submitted-queue-name}") String submitted,@Value("${app.sqs.blockchain-submitted-dlq-name}") String submittedDlq,
            @Value("${app.sqs.blockchain-confirmed-queue-name}") String confirmed,@Value("${app.sqs.blockchain-confirmed-dlq-name}") String confirmedDlq) {
        this.sqs=sqs;this.endpoint=endpoint;
        routes=List.of(new String[]{created,createdDlq},new String[]{compliance,complianceDlq},new String[]{review,reviewDlq},
                new String[]{funding,fundingDlq},new String[]{submitted,submittedDlq},new String[]{confirmed,confirmedDlq});
    }
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if(endpoint==null || endpoint.isBlank()) throw new IllegalStateException("Local profile requires LOCALSTACK_URL; refusing AWS queue creation");
        var allRoutes=new ArrayList<>(routes);
        if(environment!=null && environment.getProperty("app.flow.enabled",Boolean.class,false)){
            for(String name:List.of("flow-quote","flow-treasury","flow-validation","flow-reservation","flow-submission","receipt-observed","settlement-requested","visa-payout","ledger-payout","reconciliation-requested"))
                allRoutes.add(new String[]{environment.getRequiredProperty("app.sqs."+name+"-queue-name"),environment.getRequiredProperty("app.sqs."+name+"-dlq-name")});
        }
        for(var route:allRoutes) {
            String deadUrl=create(route[1]);
            String arn=sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(deadUrl).attributeNames(QueueAttributeName.QUEUE_ARN).build())
                    .attributes().get(QueueAttributeName.QUEUE_ARN);
            if(arn==null || !arn.matches("arn:[a-z0-9-]+:sqs:[a-z0-9-]+:[0-9]{12}:[A-Za-z0-9_-]+")) throw new IllegalStateException("Invalid local DLQ reference");
            String url=create(route[0]);
            // Set after creation: listeners may already have created a queue without redrive attributes.
            sqs.setQueueAttributes(SetQueueAttributesRequest.builder().queueUrl(url).attributes(Map.of(
                    QueueAttributeName.REDRIVE_POLICY,"{\"deadLetterTargetArn\":\""+arn+"\",\"maxReceiveCount\":\"3\"}",
                    QueueAttributeName.VISIBILITY_TIMEOUT,"120")).build());
        }
    }
    private String create(String name) { return sqs.createQueue(CreateQueueRequest.builder().queueName(name).build()).queueUrl(); }
}
