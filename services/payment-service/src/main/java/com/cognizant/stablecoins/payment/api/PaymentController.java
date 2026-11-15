package com.cognizant.stablecoins.payment.api;

import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.service.PaymentService;
import jakarta.validation.Valid;
import com.cognizant.stablecoins.payment.security.CustomerAccess;
import org.springframework.security.core.Authentication;
import static com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    private final CustomerAccess access;

    public PaymentController(PaymentService paymentService, CustomerAccess access) {
        this.paymentService = paymentService;
        this.access = access;
    }

    /**
     * POST /api/v1/payments
     *
     * Requires Idempotency-Key header.
     * Returns 201 for new payments and idempotent replays (same paymentId returned on replay).
     * Returns 409 when the same key is reused with a different payload.
     */
    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request, Authentication authentication) {

        CreatePaymentResponse response = paymentService.createPayment(idempotencyKey, request, access.require(authentication, PAYMENT_OPERATOR));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/v1/payments/{paymentId} */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, access.require(authentication, PAYMENT_OPERATOR)));
    }

    /** GET /api/v1/payments/{paymentId}/status */
    @GetMapping("/{paymentId}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable String paymentId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(paymentId, access.require(authentication, PAYMENT_OPERATOR)));
    }

    /** GET /api/v1/payments/{paymentId}/journey */
    @GetMapping("/{paymentId}/journey")
    public ResponseEntity<PaymentJourneyResponse> getPaymentJourney(@PathVariable String paymentId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPaymentJourney(paymentId, access.require(authentication, PAYMENT_OPERATOR)));
    }
}
