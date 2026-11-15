package com.cognizant.stablecoins.payment.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

/** Client-side JSON decoding without constructor-name inference or Spring setup. */
class PaymentResponseJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void decodesCreationResponseWithTheExistingWireNames() throws Exception {
        String json = """
                {"paymentId":"PAY-123","correlationId":"CORR-123","status":"COMPLIANCE_PENDING"}
                """;
        var response = mapper.readValue(json, CreatePaymentResponse.class);
        assertThat(response.getPaymentId()).isEqualTo("PAY-123");
        assertThat(response.getCorrelationId()).isEqualTo("CORR-123");
        assertThat(response.getStatus()).isEqualTo("COMPLIANCE_PENDING");
        assertThat(mapper.readTree(mapper.writeValueAsString(response))).isEqualTo(mapper.readTree(json));
    }

    @Test
    void decodesStatusWithTimestamp() throws Exception {
        var response = mapper.readValue("""
                {"paymentId":"PAY-123","correlationId":"CORR-123","status":"FUNDED",
                 "updatedAt":"2026-11-15T12:00:00Z"}
                """, PaymentStatusResponse.class);
        assertThat(response.getPaymentId()).isEqualTo("PAY-123");
        assertThat(response.getCorrelationId()).isEqualTo("CORR-123");
        assertThat(response.getStatus()).isEqualTo("FUNDED");
        assertThat(response.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:00Z"));
    }

    @Test
    void decodesJourneyAndItsNestedStepsIncludingInitialNullStatus() throws Exception {
        var response = mapper.readValue("""
                {"paymentId":"PAY-123","correlationId":"CORR-123","currentStatus":"COMPLIANCE_PENDING",
                 "steps":[{"fromStatus":null,"toStatus":"CREATED","reason":"Payment instruction received",
                 "actorService":"payment-service","timestamp":"2026-11-15T12:00:00Z"}]}
                """, PaymentJourneyResponse.class);
        assertThat(response.getPaymentId()).isEqualTo("PAY-123");
        assertThat(response.getCorrelationId()).isEqualTo("CORR-123");
        assertThat(response.getCurrentStatus()).isEqualTo("COMPLIANCE_PENDING");
        assertThat(response.getSteps()).hasSize(1);
        var step = response.getSteps().getFirst();
        assertThat(step.getFromStatus()).isNull();
        assertThat(step.getToStatus()).isEqualTo("CREATED");
        assertThat(step.getReason()).isEqualTo("Payment instruction received");
        assertThat(step.getActorService()).isEqualTo("payment-service");
        assertThat(step.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-11-15T12:00:00Z"));
    }
}
