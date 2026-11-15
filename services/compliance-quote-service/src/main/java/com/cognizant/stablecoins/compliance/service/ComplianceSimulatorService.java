package com.cognizant.stablecoins.compliance.service;

import com.cognizant.stablecoins.compliance.event.ComplianceCheckResult;
import com.cognizant.stablecoins.compliance.event.ComplianceCompletedPayload;
import com.cognizant.stablecoins.compliance.event.DomainEvent;
import com.cognizant.stablecoins.compliance.event.PaymentCreatedPayload;
import com.cognizant.stablecoins.compliance.model.ComplianceCheck;
import com.cognizant.stablecoins.compliance.model.OutboxEvent;
import com.cognizant.stablecoins.compliance.repository.ComplianceCheckRepository;
import com.cognizant.stablecoins.compliance.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic compliance simulation (AGENTS.md, README §Simulated).
 *
 * <p>All checks carry simulation=true. The UI and logs must never represent
 * simulated functions as real.
 *
 * <p>Default outcome for a standard USD payment:
 * KYC_KYB=PASS, SANCTIONS=PASS, AML=PASS, WALLET_SCREENING=PASS → APPROVED.
 *
 * <p>Rejection triggers (deterministic, not random):
 * <ul>
 *   <li>amount > 1,000,000 USD → AML FAIL
 *   <li>purpose == "REJECTED_TEST" → SANCTIONS FAIL
 * </ul>
 */
@Service
public class ComplianceSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceSimulatorService.class);
    private static final String SERVICE = "compliance-quote-service";
    private static final BigDecimal AML_THRESHOLD = new BigDecimal("1000000.00");
    private static final String[] CHECK_TYPES = {"KYC_KYB", "SANCTIONS", "AML", "WALLET_SCREENING"};

    private final ComplianceCheckRepository complianceCheckRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ComplianceSimulatorService(ComplianceCheckRepository complianceCheckRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       ObjectMapper objectMapper) {
        this.complianceCheckRepository = complianceCheckRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs the 4 simulated compliance checks and writes ComplianceCompleted to the outbox.
     * All DB writes are in the caller's transaction (PaymentCreatedConsumer).
     */
    @Transactional
    public void runChecks(DomainEvent<PaymentCreatedPayload> inboundEvent) {
        String paymentId = inboundEvent.getPaymentId();
        String correlationId = inboundEvent.getCorrelationId();
        PaymentCreatedPayload p = inboundEvent.getPayload();

        List<ComplianceCheckResult> results = new ArrayList<>();
        boolean allPass = true;

        for (String checkType : CHECK_TYPES) {
            String result = evaluate(checkType, p);
            String reason = "PASS".equals(result) ? null : buildRejectionReason(checkType, p);
            if ("FAIL".equals(result)) allPass = false;

            ComplianceCheck row = new ComplianceCheck();
            row.setPaymentId(paymentId);
            row.setCorrelationId(correlationId);
            row.setCheckType(checkType);
            row.setResult(result);
            row.setSimulation(true);
            row.setReason(reason);
            complianceCheckRepository.save(row);

            results.add(new ComplianceCheckResult(checkType, result, true, reason));
            log.info("service={} event=compliance_check checkType={} result={} paymentId={} simulation=true",
                    SERVICE, checkType, result, paymentId);
        }

        String overallResult = allPass ? "APPROVED" : "REJECTED";
        log.info("service={} event=compliance_completed paymentId={} overallResult={} simulation=true",
                SERVICE, paymentId, overallResult);

        writeOutboxEvent(paymentId, correlationId, overallResult, results);
    }

    private String evaluate(String checkType, PaymentCreatedPayload p) {
        return switch (checkType) {
            case "AML" -> p.getAmount() != null && p.getAmount().compareTo(AML_THRESHOLD) > 0
                    ? "FAIL" : "PASS";
            case "SANCTIONS" -> "REJECTED_TEST".equals(p.getPurpose()) ? "FAIL" : "PASS";
            default -> "PASS";
        };
    }

    private String buildRejectionReason(String checkType, PaymentCreatedPayload p) {
        return switch (checkType) {
            case "AML" -> "Amount " + p.getAmount() + " exceeds AML threshold (simulation)";
            case "SANCTIONS" -> "Purpose flagged for sanctions screening test (simulation)";
            default -> "Simulated rejection";
        };
    }

    private void writeOutboxEvent(String paymentId, String correlationId,
                                   String overallResult, List<ComplianceCheckResult> checks) {
        ComplianceCompletedPayload payload = new ComplianceCompletedPayload(overallResult, true, checks);
        DomainEvent<ComplianceCompletedPayload> event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "ComplianceCompleted",
                "1",
                paymentId,
                correlationId,
                payload
        );

        try {
            String json = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setEventId(event.getEventId());
            outbox.setEventType("ComplianceCompleted");
            outbox.setEventVersion("1");
            outbox.setAggregateId(paymentId);
            outbox.setCorrelationId(correlationId);
            outbox.setPayload(json);
            outbox.setStatus("PENDING");
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write ComplianceCompleted outbox event for payment " + paymentId, e);
        }
    }
}
