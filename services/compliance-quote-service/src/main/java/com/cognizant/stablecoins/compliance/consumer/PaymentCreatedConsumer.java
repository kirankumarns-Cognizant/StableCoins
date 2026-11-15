package com.cognizant.stablecoins.compliance.consumer;

import com.cognizant.stablecoins.compliance.event.DomainEvent;
import com.cognizant.stablecoins.compliance.event.PaymentCreatedPayload;
import com.cognizant.stablecoins.compliance.repository.ProcessedEventRepository;
import com.cognizant.stablecoins.compliance.service.ComplianceSimulatorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes PaymentCreated events from SQS.
 *
 * <p>For each event: runs simulated compliance checks, persists results,
 * writes ComplianceCompleted to the outbox — all in one transaction.
 *
 * <p>Idempotency: inserts (eventId, consumerName) into COMPLIANCE_PROCESSED_EVENT atomically.
 * INSERT ON CONFLICT identifies a duplicate without aborting the transaction — skipped without re-processing.
 *
 * <p>Parse failure: throws RuntimeException so Spring Cloud AWS redelivers; after
 * maxReceiveCount=3 the message is routed to the DLQ.
 */
@Component
public class PaymentCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedConsumer.class);
    private static final String CONSUMER_NAME = "compliance-quote-service:PaymentCreatedConsumer";
    private static final String SERVICE = "compliance-quote-service";

    private final ComplianceSimulatorService complianceSimulatorService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentCreatedConsumer(ComplianceSimulatorService complianceSimulatorService,
                                   ProcessedEventRepository processedEventRepository,
                                   ObjectMapper objectMapper) {
        this.complianceSimulatorService = complianceSimulatorService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @SqsListener("${app.sqs.payment-created-queue-name:stablecoin-payment-created-queue}")
    @Transactional
    public void onPaymentCreated(String messageBody) {
        DomainEvent<PaymentCreatedPayload> event;
        try {
            event = objectMapper.readValue(messageBody,
                    new TypeReference<DomainEvent<PaymentCreatedPayload>>() {});
        } catch (Exception e) {
            // Throw so Spring Cloud AWS redelivers; after maxReceiveCount the message goes to DLQ.
            throw new RuntimeException("Failed to deserialize PaymentCreated message", e);
        }

        String eventId = event.getEventId();
        String paymentId = event.getPaymentId();

        // Idempotency: INSERT ON CONFLICT uses the PK constraint as the authority — no pre-check race condition.
        if(processedEventRepository.claim(eventId,CONSUMER_NAME)==0)return;

        log.info("service={} event=compliance_check_started paymentId={} correlationId={} simulation=true",
                SERVICE, paymentId, event.getCorrelationId());

        complianceSimulatorService.runChecks(event);
    }
}
