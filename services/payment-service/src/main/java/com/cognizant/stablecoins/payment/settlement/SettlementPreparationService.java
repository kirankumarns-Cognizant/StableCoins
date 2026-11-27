package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.exception.PaymentAccessException;
import com.cognizant.stablecoins.payment.approval.ApprovalConflictException;
import com.cognizant.stablecoins.payment.event.DomainEvent;
import com.cognizant.stablecoins.payment.model.OutboxEvent;
import com.cognizant.stablecoins.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Payment-owned, read-only evidence preparation. It cannot propose a quote or claim execution. */
@Service
@ConditionalOnProperty(name="app.flow.enabled", havingValue="true")
@Transactional
public class SettlementPreparationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OutboxEventRepository outbox;
    private final SettlementPreparationReads reads;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public SettlementPreparationService(JdbcTemplate jdbc, ObjectMapper mapper, OutboxEventRepository outbox) {
        this(jdbc, mapper, outbox, new SettlementPreparationReads(jdbc, mapper), Clock.systemUTC());
    }
    SettlementPreparationService(JdbcTemplate jdbc, ObjectMapper mapper, OutboxEventRepository outbox,
            SettlementPreparationReads reads, Clock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.outbox=outbox; this.reads=reads; this.clock=clock;
    }
    public SettlementPreparation.Request prepare(String paymentId, UUID key, AuthorizedCustomer actor) {
        Objects.requireNonNull(key, "PREPARATION_ID_REQUIRED");
        lock(paymentId, actor);
        var instruction=reads.instruction(paymentId);
        if (!instruction.customerId().equals(actor.customerId())) throw denied();
        reads.requireInstruction(instruction);
        var existing=reads.request(key);
        if (existing.isPresent()) {
            if (!existing.get().instruction().equals(instruction)) throw conflict();
            return existing.get(); // Exact key replay never refreshes evidence or emits another command.
        }
        var request=new SettlementPreparation.Request(key,instruction,clock.instant());
        request.requireCurrent(clock.instant());
        jdbc.update("insert into payment_settlement_preparation(request_id,instruction_id,instruction_phase,payment_id,correlation_id,actor_id,document,fingerprint) values (?,?,'INSTRUCTION',?,?,?,cast(? as jsonb),?) on conflict(request_id) do nothing",
                key,instruction.intentId(),paymentId,instruction.correlationId(),actor.actorId(),
                SettlementBindingJson.write(request),SettlementBindingJson.fingerprint(request));
        if (reads.request(key).filter(request::equals).isEmpty()) throw conflict();
        var event=new DomainEvent<>(UUID.randomUUID().toString(),SettlementPreparation.EVENT,"1.0",
                paymentId,instruction.correlationId(),request);
        var row=new OutboxEvent();
        row.setEventId(event.getEventId()); row.setEventType(event.getEventType()); row.setEventVersion("1.0");
        row.setAggregateId(paymentId); row.setCorrelationId(instruction.correlationId());
        row.setPayload(FlowJson.json(mapper,event)); row.setStatus("PENDING"); outbox.save(row);
        return request;
    }
    public record View(SettlementPreparation.Request request, String status, String readinessGate,
            SettlementPreparation.Evidence evidence, SettlementAvailability availability) {}
    public View get(String paymentId, UUID key, AuthorizedCustomer actor) {
        lock(paymentId,actor);
        var request=reads.request(key).filter(r->r.instruction().paymentId().equals(paymentId)
                && r.instruction().customerId().equals(actor.customerId())).orElseThrow(SettlementPreparationService::conflict);
        reads.requireRequested(request);
        var documents=jdbc.query("select document::text,fingerprint from besu_preparation_observation where request_id=?",
                (rs,n)->{
                    var evidence=SettlementBindingJson.read(rs.getString(1),SettlementPreparation.Evidence.class);
                    if (!request.equals(evidence.request()) || !SettlementBindingJson.fingerprint(evidence).equals(rs.getString(2)))
                        throw new IllegalStateException("SETTLEMENT_OBSERVATION_INTEGRITY_FAILED");
                    return evidence;
                },key);
        if(documents.size()>1)throw new IllegalStateException("SETTLEMENT_OBSERVATION_INTEGRITY_FAILED");
        var evidence=documents.stream().findFirst().orElse(null);
        var availabilityRows=jdbc.query("select document::text,fingerprint from besu_preparation_availability where request_id=?",
                (rs,n)->{
                    var value=SettlementBindingJson.read(rs.getString(1),SettlementAvailability.class);
                    if(!value.preparation().equals(evidence) || !request.equals(value.preparation().request())
                            || !SettlementBindingJson.fingerprint(value).equals(rs.getString(2)))
                        throw new IllegalStateException("SETTLEMENT_AVAILABILITY_INTEGRITY_FAILED");
                    return value;
                },key);
        if(availabilityRows.size()>1)throw new IllegalStateException("SETTLEMENT_AVAILABILITY_INTEGRITY_FAILED");
        var availability=availabilityRows.stream().findFirst().orElse(null);
        String status=evidence==null?"PENDING":"OBSERVED_NOT_READY";
        try {
            request.requireCurrent(clock.instant());
            if(evidence!=null)evidence.requireFor(request,clock.instant());
            if(availability!=null)availability.requireFor(request,clock.instant());
        } catch(IllegalStateException expired) { status="EXPIRED"; }
        return new View(request,status,availability==null?"RESERVATION_INTEGRATION_PENDING":
                "RESERVATION_CLAIM_INTEGRATION_PENDING",evidence,availability);
    }
    private void lock(String paymentId, AuthorizedCustomer actor) {
        if(actor==null)throw denied();
        var owners=jdbc.queryForList("select a.customer_id from payment p join account a on a.account_id=p.sender_account_id where p.payment_id=? for update of p",paymentId);
        if(owners.size()!=1 || !actor.customerId().equals(owners.getFirst().get("customer_id")))throw denied();
    }
    private static PaymentAccessException denied(){return new PaymentAccessException("CUSTOMER_SCOPE_MISMATCH","Payment is outside customer scope");}
    private static ApprovalConflictException conflict(){return new ApprovalConflictException("SETTLEMENT_PREPARATION_KEY_CONFLICT");}
}
