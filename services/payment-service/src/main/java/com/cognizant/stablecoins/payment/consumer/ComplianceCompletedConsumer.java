package com.cognizant.stablecoins.payment.consumer;

import com.cognizant.stablecoins.payment.domain.PaymentStatus;
import com.cognizant.stablecoins.payment.event.ComplianceCompletedPayload;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.model.Payment;
import com.cognizant.stablecoins.payment.model.PaymentStatusHistory;
import com.cognizant.stablecoins.payment.repository.PaymentRepository;
import com.cognizant.stablecoins.payment.repository.PaymentStatusHistoryRepository;
import com.cognizant.stablecoins.payment.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes ComplianceCompleted events from SQS.
 *
 * <p>Idempotency: inserts (eventId, consumerName) into PAYMENT_PROCESSED_EVENT atomically.
 * INSERT ON CONFLICT claims a new event; an existing row identifies a
 * duplicate and is silently skipped — the PK constraint is the authority, not a pre-check.
 *
 * <p>Parse failure: throws RuntimeException so Spring Cloud AWS redelivers; after
 * maxReceiveCount=3 the message is routed to the DLQ.
 *
 * <p>State transition:
 * COMPLIANCE_PENDING → COMPLIANCE_APPROVED (all checks PASS)
 * COMPLIANCE_PENDING → COMPLIANCE_REJECTED (any check FAIL)
 * Any other source state → warning logged, message ACKed (stale redelivery).
 */
@Component
public class ComplianceCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ComplianceCompletedConsumer.class);
    private static final String CONSUMER_NAME = "payment-service:ComplianceCompletedConsumer";
    private static final String ACTOR = "payment-service";

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public ComplianceCompletedConsumer(PaymentRepository paymentRepository,
                                        PaymentStatusHistoryRepository historyRepository,
                                        ProcessedEventRepository processedEventRepository,
                                        ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.historyRepository = historyRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @SqsListener("${app.sqs.compliance-completed-queue-name:stablecoin-compliance-completed-queue}")
    @Transactional
    public void onComplianceCompleted(String messageBody) {
        var envelope=com.cognizant.stablecoins.flow.FlowJson.read(objectMapper,messageBody,"ComplianceCompleted",java.util.Set.of("1","1.0"));
        var event=new DomainEvent<ComplianceCompletedPayload>(envelope.eventId(),envelope.eventType(),envelope.eventVersion(),envelope.paymentId(),envelope.correlationId(),com.cognizant.stablecoins.flow.FlowJson.convert(objectMapper,envelope.payload(),ComplianceCompletedPayload.class));
        if(!event.getPayload().isSimulation()||!java.util.Set.of("APPROVED","REJECTED").contains(event.getPayload().getOverallResult()))throw new IllegalArgumentException("INVALID_COMPLIANCE_RESULT");

        String eventId = event.getEventId();
        String paymentId = event.getPaymentId();

        // Idempotency: INSERT ON CONFLICT uses the PK constraint as the authority — no pre-check race condition.
        if(processedEventRepository.claim(eventId,CONSUMER_NAME)==0)return;

        ComplianceCompletedPayload payload = event.getPayload();
        String overallResult = payload.getOverallResult();
        boolean approved = "APPROVED".equals(overallResult);

        Payment payment = paymentRepository.lockByPaymentId(paymentId).orElse(null);
        if(payment==null)throw new IllegalStateException("COMPLIANCE_PAYMENT_NOT_FOUND");
        if(!payment.getCorrelationId().equals(event.getCorrelationId()))throw new IllegalArgumentException("COMPLIANCE_CORRELATION_MISMATCH");

        // State guard: only transition from COMPLIANCE_PENDING.
        if (!PaymentStatus.COMPLIANCE_PENDING.name().equals(payment.getStatus())) {
            log.warn("service={} event=compliance_completed_wrong_state paymentId={} currentStatus={} eventId={}",
                    ACTOR, paymentId, payment.getStatus(), eventId);
            return;
        }

        String oldStatus = payment.getStatus();
        String newStatus = approved
                ? PaymentStatus.COMPLIANCE_APPROVED.name()
                : PaymentStatus.COMPLIANCE_REJECTED.name();

        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setPaymentId(paymentId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason("Compliance result: " + overallResult + " (simulation=true)");
        history.setActorService("compliance-quote-service");
        historyRepository.save(history);

        log.info("service={} event=compliance_state_transitioned paymentId={} correlationId={} oldStatus={} newStatus={} simulation=true",
                ACTOR, paymentId, event.getCorrelationId(), oldStatus, newStatus);
    }
}
