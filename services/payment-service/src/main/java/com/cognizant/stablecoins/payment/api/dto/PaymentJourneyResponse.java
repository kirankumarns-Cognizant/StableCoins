package com.cognizant.stablecoins.payment.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class PaymentJourneyResponse {

    private final String paymentId;
    private final String correlationId;
    private final String currentStatus;
    private final List<StatusStep> steps;

    public PaymentJourneyResponse(String paymentId, String correlationId,
                                   String currentStatus, List<StatusStep> steps) {
        this.paymentId = paymentId;
        this.correlationId = correlationId;
        this.currentStatus = currentStatus;
        this.steps = steps;
    }

    public String getPaymentId() { return paymentId; }
    public String getCorrelationId() { return correlationId; }
    public String getCurrentStatus() { return currentStatus; }
    public List<StatusStep> getSteps() { return steps; }

    public static class StatusStep {
        private final String fromStatus;
        private final String toStatus;
        private final String reason;
        private final String actorService;
        private final OffsetDateTime timestamp;

        public StatusStep(String fromStatus, String toStatus, String reason,
                          String actorService, OffsetDateTime timestamp) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reason = reason;
            this.actorService = actorService;
            this.timestamp = timestamp;
        }

        public String getFromStatus() { return fromStatus; }
        public String getToStatus() { return toStatus; }
        public String getReason() { return reason; }
        public String getActorService() { return actorService; }
        public OffsetDateTime getTimestamp() { return timestamp; }
    }
}
