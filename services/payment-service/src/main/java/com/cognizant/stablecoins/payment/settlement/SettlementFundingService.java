package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.approval.*;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.model.*;
import com.cognizant.stablecoins.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Internal payment-owner funding boundary. No endpoint/listener; no mint, chain submission or ledger writes. */
@Service
@ConditionalOnProperty(name="app.besu.funding.enabled",havingValue="true")
@Transactional
public class SettlementFundingService {
    private final PaymentRepository payments;
    private final AccountRepository accounts;
    private final SettlementExecutionClaimService authority;
    private final RecommendationService recommendations;
    private final SettlementExecutionReads claims;
    private final SettlementReservationReads reservations;
    private final SettlementFundingStore store;
    private final OutboxEventRepository outbox;
    private final PaymentStatusHistoryRepository history;
    private final ObjectMapper mapper;
    private final Clock clock;
    @Autowired
    public SettlementFundingService(PaymentRepository payments,AccountRepository accounts,
            SettlementExecutionClaimService authority,RecommendationService recommendations,JdbcTemplate jdbc,
            OutboxEventRepository outbox,PaymentStatusHistoryRepository history,ObjectMapper mapper) {
        this(payments,accounts,authority,recommendations,new SettlementExecutionReads(jdbc),
                new SettlementReservationReads(jdbc),new SettlementFundingStore(jdbc),outbox,history,mapper,Clock.systemUTC());
    }
    SettlementFundingService(PaymentRepository payments,AccountRepository accounts,SettlementExecutionClaimService authority,
            RecommendationService recommendations,SettlementExecutionReads claims,SettlementReservationReads reservations,
            SettlementFundingStore store,OutboxEventRepository outbox,PaymentStatusHistoryRepository history,ObjectMapper mapper,Clock clock) {
        this.payments=payments;this.accounts=accounts;this.authority=authority;this.recommendations=recommendations;
        this.claims=claims;this.reservations=reservations;this.store=store;this.outbox=outbox;this.history=history;this.mapper=mapper;this.clock=clock;
    }
    public Receipt fund(SettlementBinding.Execution e,AuthorizedCustomer actor) {
        Objects.requireNonNull(e,"EXECUTION_REQUIRED");
        if(actor==null) throw new IllegalArgumentException("AUTHENTICATION_REQUIRED");
        var i=e.instruction();
        if(!actor.customerId().equals(i.customerId())) throw new IllegalArgumentException("CUSTOMER_SCOPE_MISMATCH");
        var p=payments.lockByPaymentId(i.paymentId()).orElseThrow(()->conflict("PAYMENT_NOT_FOUND"));
        if(p.getIntent()==null || !actor.customerId().equals(p.getIntent().customerId())) throw conflict("CUSTOMER_SCOPE_MISMATCH");
        claims.requireClaimed(e);
        var prior=store.find(e);
        if(prior.isPresent()) return prior.get(); // Historical acknowledgement only, no debit or new signing authority.
        var account=accounts.lockByAccountId(p.getSenderAccountId()).orElseThrow(()->conflict("ACCOUNT_UNAVAILABLE"));
        authority.requireFundingAuthority(e,actor); // Membership, independent votes and immutable owner claim after lock waits.
        if(!i.equals(ExplicitSettlementService.instruction(p)) || !"COMPLIANCE_APPROVED".equals(p.getStatus())
                || !"USD".equals(p.getSourceCurrency()) || !"STABLECOIN".equals(p.getPaymentRail())
                || !account.getCustomerId().equals(i.customerId()) || !"USD".equals(account.getCurrency())
                || !"ACTIVE".equals(account.getStatus()) || !"DEMO_SIMULATED_USD".equals(account.getAccountType()))
            throw conflict("SIMULATED_FUNDING_BINDING_CHANGED");
        if(account.getBalance()==null || account.getBalance().compareTo(i.sourceUsd())<0) throw conflict("INSUFFICIENT_SIMULATED_USD");
        recommendations.markSettlementExecuted(e,actor); // Current quote, policy, beneficiary and compliance; same DB transaction.
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        reservations.requireFreshPinned(e,now); // Last evidence check, after every payment/account lock and approval lookup.
        var receipt=new Receipt(e,account.getAccountId(),account.getBalance(),account.getBalance().subtract(i.sourceUsd()),
                actor.actorId(),UUID.randomUUID(),now);
        var event=new DomainEvent<>(receipt.eventId().toString(),"BesuFundingAuthorized","1.0",i.paymentId(),i.correlationId(),
                new FundingCommand(e,SettlementBindingJson.fingerprint(e),receipt.fundingReference(),true));
        event.setTimestamp(now.atOffset(ZoneOffset.UTC));
        String payload;
        try { payload=mapper.writeValueAsString(event); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("BESU_FUNDING_EVENT_SERIALIZATION_FAILED",error);}
        account.setBalance(receipt.balanceAfter());accounts.save(account);
        for(String state:List.of("QUOTE_ACCEPTED","RAIL_SELECTED","FUNDING_PENDING","FUNDED")) {
            var h=new PaymentStatusHistory();h.setPaymentId(i.paymentId());h.setOldStatus(p.getStatus());h.setNewStatus(state);
            h.setReason("Approved private DEMOUSD instruction; USD funding/conversion simulated");h.setActorService("payment-service");
            history.save(h);p.setStatus(state);payments.save(p);
        }
        var row=new OutboxEvent();row.setEventId(event.getEventId());row.setEventType(event.getEventType());row.setEventVersion(event.getEventVersion());
        row.setAggregateId(i.paymentId());row.setCorrelationId(i.correlationId());row.setPayload(payload);row.setStatus("PENDING");
        outbox.saveAndFlush(row); // Flush owner JPA writes before the JDBC receipt FK; rollback remains atomic.
        store.capture(receipt);
        return receipt;
    }
    public record FundingCommand(SettlementBinding.Execution execution,String executionFingerprint,String fundingReference,boolean simulated) {}
    public record Receipt(SettlementBinding.Execution execution,UUID accountId,BigDecimal balanceBefore,BigDecimal balanceAfter,
            String actorId,UUID eventId,Instant fundedAt) {
        public Receipt {
            Objects.requireNonNull(execution);Objects.requireNonNull(accountId);Objects.requireNonNull(eventId);Objects.requireNonNull(fundedAt);
            if(execution.context().token().chain().classification()!=SettlementNetwork.Classification.PRIVATE_DEMO
                    || !"DEMOUSD".equals(execution.context().token().symbol()) || actorId==null || actorId.isBlank()
                    || balanceBefore==null || balanceAfter==null || balanceAfter.signum()<0
                    || balanceBefore.subtract(balanceAfter).compareTo(execution.instruction().sourceUsd())!=0
                    || balanceBefore.stripTrailingZeros().scale()>2 || balanceAfter.stripTrailingZeros().scale()>2)
                throw new IllegalArgumentException("INVALID_SIMULATED_BESU_FUNDING");
            execution.review().requireFresh(fundedAt);
        }
        public String fundingReference(){return "SIMULATED-BESU-"+execution.executionId();}
    }
    private static ApprovalConflictException conflict(String code){return new ApprovalConflictException(code);}
}
