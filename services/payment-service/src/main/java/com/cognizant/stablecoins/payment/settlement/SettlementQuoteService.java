package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/**
 * Internal quote-draft preparation, deliberately without an HTTP/model tool or
 * execution event. The V13 approved-review foreign keys remain unchanged.
 */
@Service
@ConditionalOnProperty(name = "app.flow.enabled", havingValue = "true")
@Transactional
public class SettlementQuoteService {
    private final SettlementPreparationService preparations;
    private final SettlementQuoteStore store;
    private final SettlementQuoteProperties quotes;
    private final SettlementSelectionProperties selections;
    private final Clock clock;

    @Autowired
    public SettlementQuoteService(SettlementPreparationService preparations, JdbcTemplate jdbc,
            SettlementQuoteProperties quotes, SettlementSelectionProperties selections) {
        this(preparations, new SettlementQuoteStore(jdbc), quotes, selections, Clock.systemUTC());
    }
    SettlementQuoteService(SettlementPreparationService preparations, SettlementQuoteStore store,
            SettlementQuoteProperties quotes, SettlementSelectionProperties selections, Clock clock) {
        this.preparations = preparations; this.store = store; this.quotes = quotes;
        this.selections = selections; this.clock = clock;
    }

    /** Server resolves every term; caller supplies only identifiers and verified identity. */
    public SettlementQuote prepare(String paymentId, UUID preparationId, UUID reviewId, AuthorizedCustomer actor) {
        Objects.requireNonNull(preparationId, "PREPARATION_ID_REQUIRED");
        Objects.requireNonNull(reviewId, "REVIEW_ID_REQUIRED");
        if (actor == null) throw new PaymentAccessException("AUTHENTICATION_REQUIRED", "Verified customer required");
        // Authorize and lock the payment BEFORE consulting any quote ID.
        var view = preparations.get(paymentId, preparationId, actor);
        var request = view.request();
        if (!preparationId.equals(request.requestId()) || !paymentId.equals(request.instruction().paymentId())
                || !actor.customerId().equals(request.instruction().customerId()))
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Payment is outside customer scope");
        var prior = store.find(reviewId);
        if (prior.isPresent()) {
            var original = prior.get();
            if (!original.review().preparationId().equals(preparationId)
                    || !original.review().instruction().equals(request.instruction()))
                throw conflict("SETTLEMENT_QUOTE_KEY_CONFLICT");
            return original; // No refresh, changed terms or new quote on expired replay.
        }
        var instruction = request.instruction();
        if (!selections.getAllowedNetworks().getOrDefault(actor.customerId(), Set.of())
                .contains(instruction.context().token().chain().networkId()))
            throw conflict("SETTLEMENT_NETWORK_NOT_ALLOWED");
        var selected = new SettlementNetworkRegistry(selections.getNetworks())
                .forNewWork(instruction.context().token().chain().networkId());
        if (!selected.equals(instruction.context())) throw conflict("SETTLEMENT_CONTEXT_CHANGED");
        var terms = quotes.getTerms();
        if (terms == null) throw conflict("SETTLEMENT_QUOTE_TERMS_REQUIRED");
        if (view.evidence() == null || view.availability() == null)
            throw conflict("SETTLEMENT_AVAILABILITY_REQUIRED");
        if (!view.availability().preparation().equals(view.evidence())
                || !view.evidence().request().equals(request))
            throw conflict("SETTLEMENT_QUOTE_EVIDENCE_CHANGED");
        Instant now = clock.instant(); // Sample after owner lock and all owner reads.
        view.availability().requireFor(request, now);
        var review = new SettlementBinding.Review(reviewId, 1, preparationId, UUID.randomUUID(), 1,
                terms.providerId(), instruction, terms.simulatedFeeUsd(),
                terms.reserve(view.evidence().observation()), now, terms.expiry(instruction, now));
        var quote = new SettlementQuote(review, view.availability(), terms);
        store.capture(quote, actor.actorId());
        return quote;
    }

    /** Internal owner guard; an expired review must not replace an unresolved claimed execution. */
    public boolean executionClaimed(String paymentId) { return store.executionClaimed(paymentId); }

    /** Read immutable history, including expired quotes, without refreshing or authorizing it. */
    public SettlementQuote get(String paymentId, UUID preparationId, UUID reviewId, AuthorizedCustomer actor) {
        if (actor == null) throw new PaymentAccessException("AUTHENTICATION_REQUIRED", "Verified customer required");
        var view = preparations.get(paymentId, preparationId, actor);
        if (!preparationId.equals(view.request().requestId()) || !paymentId.equals(view.request().instruction().paymentId())
                || !actor.customerId().equals(view.request().instruction().customerId()))
            throw new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH", "Payment is outside customer scope");
        var quote = store.find(reviewId).orElseThrow(() -> conflict("SETTLEMENT_QUOTE_NOT_FOUND"));
        if (!quote.review().instruction().equals(view.request().instruction())
                || !quote.review().preparationId().equals(preparationId))
            throw conflict("SETTLEMENT_QUOTE_KEY_CONFLICT");
        return quote;
    }

    private static ApprovalConflictException conflict(String code) { return new ApprovalConflictException(code); }
}
