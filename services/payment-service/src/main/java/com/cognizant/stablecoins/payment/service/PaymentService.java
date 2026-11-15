package com.cognizant.stablecoins.payment.service;

import com.cognizant.stablecoins.payment.api.dto.*;
import com.cognizant.stablecoins.payment.domain.PaymentStatus;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.intent.PaymentIntentService;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.event.PaymentCreatedPayload;
import com.cognizant.stablecoins.payment.exception.*;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String ACTOR = "payment-service";

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final PaymentIntentService intentService;

    public PaymentService(PaymentRepository paymentRepository,
                          AccountRepository accountRepository,
                          BeneficiaryRepository beneficiaryRepository,
                          PaymentStatusHistoryRepository historyRepository,
                          OutboxEventRepository outboxEventRepository,
                          ObjectMapper objectMapper,
                          PaymentIntentService intentService) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.historyRepository = historyRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.intentService = intentService;
    }

    /**
     * Creates a new payment or returns the existing one for a repeated idempotency key.
     *
     * <p>Idempotency rules (AGENTS.md Â§12):
     * <ul>
     *   <li>Same key + same fingerprint â†’ return existing payment (HTTP 200)
     *   <li>Same key + different fingerprint â†’ HTTP 409 Conflict
     *   <li>New key â†’ create and return new payment (HTTP 201)
     * </ul>
     */
    @Transactional
    public CreatePaymentResponse createPayment(String idempotencyKey, CreatePaymentRequest request) {
        return createPayment(idempotencyKey, request, null);
    }

    /** Internal boundary: caller must derive identity from verified authentication, never request JSON. */
    @Transactional
    public CreatePaymentResponse createPayment(String idempotencyKey, CreatePaymentRequest request,
            AuthorizedCustomer actor) {
        if (request.getIntent() != null) {
            intentService.requireContext(actor);
            intentService.validate(request);
        }
        String fingerprint = computeFingerprint(request);

        // Idempotency check
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    authorizeIntentRead(existing, actor);
                    if (existing.getRequestFingerprint().equals(fingerprint)) {
                        log.info("service={} event=idempotent_return paymentId={} correlationId={} idempotencyKey={}",
                                ACTOR, existing.getPaymentId(), existing.getCorrelationId(), idempotencyKey);
                        return new CreatePaymentResponse(existing.getPaymentId(),
                                existing.getCorrelationId(), existing.getStatus());
                    }
                    throw new IdempotencyConflictException(idempotencyKey);
                })
                .orElseGet(() -> doCreate(idempotencyKey, fingerprint, request, actor));
    }

    private CreatePaymentResponse doCreate(String idempotencyKey, String fingerprint,
                                            CreatePaymentRequest request, AuthorizedCustomer actor) {
        // Validate sender account
        Account account = accountRepository.findById(request.getSenderAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getSenderAccountId()));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new PaymentValidationException("ACCOUNT_NOT_ACTIVE",
                    "Sender account is not active: " + request.getSenderAccountId());
        }

        // Validate beneficiary
        Beneficiary beneficiary = beneficiaryRepository.findById(request.getBeneficiaryId())
                .orElseThrow(() -> new BeneficiaryNotFoundException(request.getBeneficiaryId()));
        if (!"ACTIVE".equals(beneficiary.getStatus())) {
            throw new PaymentValidationException("BENEFICIARY_NOT_ACTIVE",
                    "Beneficiary is not active: " + request.getBeneficiaryId());
        }

        if (actor != null && (!actor.customerId().equals(account.getCustomerId())
                || !actor.customerId().equals(beneficiary.getCustomerId()))) {
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Account and beneficiary are outside customer scope");
        }
        String paymentId = "PAY-" + UUID.randomUUID().toString().replace("-", "")
                                        .substring(0, 12).toUpperCase();
        String correlationId = "CORR-" + UUID.randomUUID().toString().replace("-", "")
                                             .substring(0, 12).toUpperCase();

        // â”€â”€ Atomic write: PAYMENT + HISTORY(CREATED) + HISTORY(COMPLIANCE_PENDING) + OUTBOX â”€â”€
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setCorrelationId(correlationId);
        payment.setSenderAccountId(request.getSenderAccountId());
        payment.setBeneficiaryId(request.getBeneficiaryId());
        payment.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        payment.setSourceCurrency(request.getCurrency().name());
        if (request.getIntent() != null) {
            payment.setIntent(intentService.prepare(request, account, beneficiary, actor));
            // Null for wallet-only settlement: USDC is an asset, not a fiat currency.
            payment.setDestinationCurrency(request.getIntent().requiredOutcome().payoutCurrency());
        } else {
            // Preserve the legacy API and historical request fingerprints.
            payment.setDestinationCurrency(request.getCurrency().name());
        }
        payment.setPaymentRail(request.getRequestedRail().name());
        payment.setPurpose(request.getPurpose().name());
        payment.setStatus(PaymentStatus.COMPLIANCE_PENDING.name());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestFingerprint(fingerprint);
        paymentRepository.save(payment);

        // History: CREATED entry
        PaymentStatusHistory createdHistory = new PaymentStatusHistory();
        createdHistory.setPaymentId(paymentId);
        createdHistory.setOldStatus(null);
        createdHistory.setNewStatus(PaymentStatus.CREATED.name());
        createdHistory.setReason("Payment created");
        createdHistory.setActorService(ACTOR);
        historyRepository.save(createdHistory);

        // History: COMPLIANCE_PENDING entry
        PaymentStatusHistory complianceHistory = new PaymentStatusHistory();
        complianceHistory.setPaymentId(paymentId);
        complianceHistory.setOldStatus(PaymentStatus.CREATED.name());
        complianceHistory.setNewStatus(PaymentStatus.COMPLIANCE_PENDING.name());
        complianceHistory.setReason("Compliance check initiated");
        complianceHistory.setActorService(ACTOR);
        historyRepository.save(complianceHistory);

        // Outbox: PaymentCreated event
        writePaymentCreatedOutbox(payment);

        log.info("service={} event=payment_created paymentId={} correlationId={} amount={} currency={} rail={} status={}",
                ACTOR, paymentId, correlationId, payment.getAmount(), payment.getSourceCurrency(),
                payment.getPaymentRail(), payment.getStatus());

        return new CreatePaymentResponse(paymentId, correlationId, PaymentStatus.COMPLIANCE_PENDING.name());
    }

    private void writePaymentCreatedOutbox(Payment payment) {
        PaymentCreatedPayload payload = new PaymentCreatedPayload(
                payment.getSenderAccountId(),
                payment.getBeneficiaryId(),
                payment.getAmount(),
                payment.getSourceCurrency(),
                payment.getDestinationCurrency(),
                payment.getPurpose(),
                payment.getPaymentRail()
        );
        DomainEvent<PaymentCreatedPayload> event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "PaymentCreated",
                "1",
                payment.getPaymentId(),
                payment.getCorrelationId(),
                payload
        );
        try {
            String json = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setEventId(event.getEventId());
            outbox.setEventType("PaymentCreated");
            outbox.setEventVersion("1");
            outbox.setAggregateId(payment.getPaymentId());
            outbox.setCorrelationId(payment.getCorrelationId());
            outbox.setPayload(json);
            outbox.setStatus("PENDING");
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write PaymentCreated outbox event for " + payment.getPaymentId(), e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        return getPayment(paymentId, null);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId, AuthorizedCustomer actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        authorizeIntentRead(payment, actor);
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        return getPaymentStatus(paymentId, null);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String paymentId, AuthorizedCustomer actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        authorizeIntentRead(payment, actor);
        return new PaymentStatusResponse(payment.getPaymentId(), payment.getCorrelationId(),
                payment.getStatus(), payment.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public PaymentJourneyResponse getPaymentJourney(String paymentId) {
        return getPaymentJourney(paymentId, null);
    }

    @Transactional(readOnly = true)
    public PaymentJourneyResponse getPaymentJourney(String paymentId, AuthorizedCustomer actor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        authorizeIntentRead(payment, actor);

        List<PaymentJourneyResponse.StatusStep> steps =
                historyRepository.findByPaymentIdOrderByTimestampAsc(paymentId)
                        .stream()
                        .map(h -> new PaymentJourneyResponse.StatusStep(
                                h.getOldStatus(), h.getNewStatus(),
                                h.getReason(), h.getActorService(), h.getTimestamp()))
                        .collect(Collectors.toList());

        return new PaymentJourneyResponse(payment.getPaymentId(), payment.getCorrelationId(),
                payment.getStatus(), steps);
    }

    /**
     * Deterministic SHA-256 fingerprint of normalized business fields.
     * Excludes non-business variability such as JSON property ordering.
     */
    String computeFingerprint(CreatePaymentRequest request) {
        String canonical = String.join("|",
                request.getSenderAccountId().toString(),
                request.getBeneficiaryId().toString(),
                request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                request.getCurrency().name(),
                request.getPurpose().name(),
                request.getRequestedRail().name()
        );
        if (request.getIntent() != null) {
            PaymentIntentRequest intent = request.getIntent();
            canonical += "|intent-v1|" + String.join("|",
                    intent.requiredOutcome().name(), intent.deliverBy().toString(),
                    intent.maximumFee().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    intent.feeCurrency().name(), intent.priority().name(),
                    intent.allowedAlternatives().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                    intent.confirmed().toString());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void authorizeIntentRead(Payment payment, AuthorizedCustomer actor) {
        if (actor != null) {
            Account account = accountRepository.findById(payment.getSenderAccountId())
                    .orElseThrow(() -> new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Payment account unavailable"));
            if (!actor.customerId().equals(account.getCustomerId()))
                throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Payment is outside customer scope");
        }
        if (payment.getIntent() != null) {
            intentService.requireContext(actor);
            intentService.authorizeReplay(payment.getIntent(), actor);
        }
    }

    private PaymentResponse toPaymentResponse(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.setIntent(p.getIntent());
        r.setPaymentId(p.getPaymentId());
        r.setCorrelationId(p.getCorrelationId());
        r.setSenderAccountId(p.getSenderAccountId());
        r.setBeneficiaryId(p.getBeneficiaryId());
        r.setAmount(p.getAmount());
        r.setSourceCurrency(p.getSourceCurrency());
        r.setDestinationCurrency(p.getDestinationCurrency());
        r.setPaymentRail(p.getPaymentRail());
        r.setPurpose(p.getPurpose());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
