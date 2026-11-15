package com.cognizant.stablecoins.visa.liquidity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
/** All balances, funding and consumption here are explicitly SIMULATED; no bank/Visa calls. */
@Service @Transactional
public class VisaSimulatorAdapter implements VisaPaymentAdapter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(VisaSimulatorAdapter.class);
    private final VisaLiquidityPocketRepository pockets;
    private final VisaLiquidityReservationRepository reservations;
    private final VisaLiquidityFundingRepository funding;
    private final VisaLiquidityAuditRepository audit;
    private final Set<String> allowedProviders;
    private final Duration ttl;
    public VisaSimulatorAdapter(VisaLiquidityPocketRepository pockets,VisaLiquidityReservationRepository reservations,
            VisaLiquidityFundingRepository funding,VisaLiquidityAuditRepository audit,
            @Value("${visa.simulator.allowed-provider-ids}") Set<String> providers,
            @Value("${visa.simulator.reservation-ttl}") Duration ttl) {
        this.pockets=pockets;this.reservations=reservations;this.funding=funding;this.audit=audit;
        allowedProviders=Set.copyOf(providers);this.ttl=ttl;
        if(providers.isEmpty() || providers.stream().anyMatch(p -> !p.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))
                || ttl==null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24))>0)
            throw new IllegalArgumentException("INVALID_SIMULATOR_CONFIGURATION");
    }
    @Override public Balance getBalance(String providerId,Currency currency) {
        String id=pocketKey(providerId,currency);
        var pocket=pockets.lock(id);
        if(pocket.isEmpty()) return new Balance(providerId,currency,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"SIMULATED",Instant.now());
        return balance(pocket.get());
    }
    @Override public Balance prefund(String operationId,String providerId,Currency currency,BigDecimal amount) {
        identifier(operationId);money(amount);
        var pocket=lockPocket(providerId,currency);
        var prior=funding.findById(operationId);
        if(prior.isPresent()) {
            if(!prior.get().pocketId.equals(pocket.pocketId) || prior.get().amount.compareTo(amount)!=0)
                throw new IllegalStateException("SIMULATED_PREFUND_OPERATION_CONFLICT");
            return balance(pocket);
        }
        BigDecimal total=pocket.total.add(amount);money(total);
        pocket.total=total;pockets.save(pocket);
        var row=new VisaLiquidityFunding();row.operationId=operationId;row.pocketId=pocket.pocketId;
        row.amount=amount;row.timestamp=Instant.now();funding.save(row);
        return balance(pocket);
    }
    @Override public Reservation reserve(String paymentId,String correlationId,String providerId,Currency currency,BigDecimal amount) {
        identifier(paymentId);identifier(correlationId);money(amount);
        var pocket=lockPocket(providerId,currency);
        var prior=reservations.findByPaymentId(paymentId);
        if(prior.isPresent()) {
            var r=prior.get();
            if(!r.correlationId.equals(correlationId) || !r.pocketId.equals(pocket.pocketId) || r.amount.compareTo(amount)!=0)
                throw new IllegalStateException("SIMULATED_PAYOUT_OPERATION_CONFLICT");
            expire(r);return r.view();
        }
        for(var r:reservations.findByPocketId(pocket.pocketId)) expire(r);
        if(balance(pocket).available().compareTo(amount)<0) throw new IllegalStateException("INSUFFICIENT_SIMULATED_FIAT");
        var r=new VisaLiquidityReservation();r.reservationId=UUID.randomUUID();r.paymentId=paymentId;r.correlationId=correlationId;
        r.providerId=providerId;r.currency=currency.name();r.pocketId=pocket.pocketId;r.amount=amount;r.status="HELD";
        r.createdAt=Instant.now();r.expiresAt=r.createdAt.plus(ttl);save(r);return r.view();
    }
    @Override public Reservation pin(UUID id) {
        var r=locked(id);expire(r);
        if("PINNED".equals(r.status)) return r.view();
        if(!"HELD".equals(r.status)) return r.view();
        r.status="PINNED";save(r);return r.view();
    }
    @Override public Reservation cancel(UUID id) {
        var r=locked(id);expire(r);
        if(Set.of("RELEASED","EXPIRED").contains(r.status)) return r.view();
        if(!"HELD".equals(r.status)) throw new IllegalStateException("SIMULATED_PAYOUT_REQUIRES_RESOLUTION");
        r.status="RELEASED";save(r);return r.view();
    }
    /** Authoritative simulator completion only; never call for an uncertain/timeout payout. */
    @Override public Reservation resolve(UUID id,boolean consumed) {
        var r=locked(id);String terminal=consumed ? "CONSUMED" : "RELEASED";
        if(terminal.equals(r.status)) return r.view();
        if(!"PINNED".equals(r.status)) throw new IllegalStateException("SIMULATED_PAYOUT_RESOLUTION_CONFLICT");
        if(consumed) {
            var pocket=pockets.lock(r.pocketId).orElseThrow();
            if(pocket.total.compareTo(r.amount)<0) throw new IllegalStateException("SIMULATED_FIAT_BALANCE_CONFLICT");
            pocket.total=pocket.total.subtract(r.amount);pockets.save(pocket);
        }
        r.status=terminal;save(r);return r.view();
    }
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.fasterxml.jackson.databind.ObjectMapper mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    @Override public com.cognizant.stablecoins.flow.LifecycleReadModels.Payout createPayout(PayoutRequest request) {
        if(request==null||request.executionId()==null||request.quoteId()==null||request.quoteVersion()<1||request.transactionHash()==null||!request.transactionHash().matches("0x[0-9a-fA-F]{64}")||!Set.of("SUCCEEDED","DECLINED","TIMEOUT").contains(request.scenario()))throw new IllegalArgumentException("INVALID_SIMULATED_PAYOUT");
        identifier(request.paymentId());identifier(request.correlationId());money(request.amount());
        // Same pocket-first lock order as existing reservation operations; serialize competing creates.
        lockPocket(request.providerId(),request.currency());
        var reads=new com.cognizant.stablecoins.flow.LifecycleReadModels(jdbc,mapper);var prior=reads.payout(request.paymentId());
        if(prior.isPresent()) {
            var p=prior.get();
            if(!p.executionId().equals(request.executionId())||!p.correlationId().equals(request.correlationId())||!p.providerId().equals(request.providerId())||!p.currency().equals(request.currency().name())||p.amount().compareTo(request.amount())!=0||!p.quoteId().equals(request.quoteId())||p.quoteVersion()!=request.quoteVersion()||!p.hash().equals(request.transactionHash()))throw new IllegalStateException("SIMULATED_PAYOUT_OPERATION_CONFLICT");
            return p; // A changed failure-injection setting cannot create another payout or change the captured scenario.
        }
        UUID id=UUID.nameUUIDFromBytes((request.executionId()+"/payout").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String reference="SIMULATED-VISA-"+id;
        jdbc.update("insert into visa_transaction(payout_id,execution_id,payment_id,correlation_id,provider_id,currency,amount,status,reference,quote_id,quote_version,transaction_hash,evidence_mode,scenario) values (?,?,?,?,?,?,?,'ACCEPTED',?,?,?,?,'SIMULATED',?)",id,request.executionId(),request.paymentId(),request.correlationId(),request.providerId(),request.currency().name(),request.amount(),reference,request.quoteId(),request.quoteVersion(),request.transactionHash(),request.scenario());
        auditPayout(id,"ACCEPTED");
        boolean alreadyPinned=reservations.findByPaymentId(request.paymentId()).map(r->r.status.equals("PINNED")).orElse(false);
        if(!alreadyPinned&&getBalance(request.providerId(),request.currency()).available().compareTo(request.amount())<0){payoutState(id,"FAILED");return reads.payout(request.paymentId()).orElseThrow();}
        var held=reserve(request.paymentId(),request.correlationId(),request.providerId(),request.currency(),request.amount());
        if(!Set.of("HELD","PINNED").contains(held.status()))throw new IllegalStateException("SIMULATED_PAYOUT_RESERVATION_UNAVAILABLE");
        var pinned=pin(held.reservationId());if(!"PINNED".equals(pinned.status()))throw new IllegalStateException("SIMULATED_PAYOUT_RESERVATION_UNAVAILABLE");
        jdbc.update("update visa_transaction set reservation_id=? where payout_id=?",pinned.reservationId(),id);
        payoutState(id,request.scenario().equals("TIMEOUT")?"UNKNOWN":"PROCESSING");return reads.payout(request.paymentId()).orElseThrow();
    }
    @Override public com.cognizant.stablecoins.flow.LifecycleReadModels.Payout finishPayout(UUID id) {
        var p=lockPayout(id);if(!p.status().equals("PROCESSING"))return p;
        resolve(p.reservationId(),p.scenario().equals("SUCCEEDED"));payoutState(id,p.scenario().equals("SUCCEEDED")?"SUCCEEDED":"FAILED");
        return new com.cognizant.stablecoins.flow.LifecycleReadModels(jdbc,mapper).payout(p.paymentId()).orElseThrow();
    }
    @Override public com.cognizant.stablecoins.flow.LifecycleReadModels.Payout resolvePayout(UUID id,UUID resolutionId,boolean succeeded) {
        Objects.requireNonNull(resolutionId);var p=lockPayout(id);
        UUID prior=jdbc.queryForObject("select resolution_id from visa_transaction where payout_id=?",UUID.class,id);
        String terminal=succeeded?"SUCCEEDED":"FAILED";
        if(prior!=null){if(!prior.equals(resolutionId)||!p.status().equals(terminal))throw new IllegalStateException("PAYOUT_RESOLUTION_CONFLICT");return p;}
        if(!p.status().equals("UNKNOWN"))throw new IllegalStateException("PAYOUT_NOT_UNKNOWN");
        resolve(p.reservationId(),succeeded);jdbc.update("update visa_transaction set resolution_id=? where payout_id=?",resolutionId,id);payoutState(id,terminal);
        return new com.cognizant.stablecoins.flow.LifecycleReadModels(jdbc,mapper).payout(p.paymentId()).orElseThrow();
    }
    private com.cognizant.stablecoins.flow.LifecycleReadModels.Payout lockPayout(UUID id){
        var rows=jdbc.queryForList("select provider_id,currency,payment_id from visa_transaction where payout_id=?",id);
        if(rows.size()!=1)throw new IllegalArgumentException("PAYOUT_NOT_FOUND");var row=rows.getFirst();
        lockPocket((String)row.get("provider_id"),Currency.valueOf((String)row.get("currency")));
        jdbc.queryForObject("select payout_id from visa_transaction where payout_id=? for update",UUID.class,id);
        return new com.cognizant.stablecoins.flow.LifecycleReadModels(jdbc,mapper).payout((String)row.get("payment_id")).orElseThrow();
    }
    private void payoutState(UUID id,String status){jdbc.update("update visa_transaction set status=?,updated_at=now() where payout_id=?",status,id);auditPayout(id,status);}
    private void auditPayout(UUID id,String status){jdbc.update("insert into visa_payout_audit(payout_id,status) values (?,?)",id,status);log.info("service=visa-simulator event=simulated_payout status={} payoutId={} evidenceMode=SIMULATED",status,id);}
    private Balance balance(VisaLiquidityPocket pocket) {
        BigDecimal reserved=BigDecimal.ZERO;Instant now=Instant.now();
        for(var r:reservations.findByPocketId(pocket.pocketId)) {
            if("PINNED".equals(r.status) || ("HELD".equals(r.status) && r.expiresAt.isAfter(now))) reserved=reserved.add(r.amount);
        }
        return new Balance(pocket.providerId,Currency.valueOf(pocket.currency),pocket.total,reserved,
                pocket.total.subtract(reserved).max(BigDecimal.ZERO),"SIMULATED",now);
    }
    private VisaLiquidityReservation locked(UUID id) {
        var first=reservations.findPocketLocation(id).orElseThrow(() -> new IllegalArgumentException("SIMULATED_RESERVATION_NOT_FOUND"));
        pocketKey(first.getProviderId(),Currency.valueOf(first.getCurrency()));
        pockets.lock(first.getPocketId()).orElseThrow();
        // JPA may have cached the first observation before waiting on the pocket lock.
        return reservations.lockReservation(id).orElseThrow();
    }
    private VisaLiquidityPocket lockPocket(String provider,Currency currency) {
        String id=pocketKey(provider,currency);pockets.ensure(id,provider,currency.name());return pockets.lock(id).orElseThrow();
    }
    private String pocketKey(String provider,Currency currency) {
        if(provider==null || !allowedProviders.contains(provider) || currency==null) throw new IllegalArgumentException("UNSUPPORTED_SIMULATED_PROVIDER_OR_CURRENCY");
        return provider+":"+currency.name();
    }
    private void expire(VisaLiquidityReservation r) {
        if("HELD".equals(r.status) && !r.expiresAt.isAfter(Instant.now())) { r.status="EXPIRED";save(r); }
    }
    private void save(VisaLiquidityReservation r) { r.updatedAt=Instant.now();reservations.save(r);audit.save(new VisaLiquidityAudit(r));
        log.info("service=visa-simulator paymentId={} correlationId={} status={} reservationId={} evidenceMode=SIMULATED",r.paymentId,r.correlationId,r.status,r.reservationId);
    }
    private static void identifier(String id) {
        if(id==null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) throw new IllegalArgumentException("INVALID_SIMULATOR_IDENTIFIER");
    }
    private static void money(BigDecimal amount) {
        if(amount==null || amount.signum()<=0 || amount.scale()>2 || amount.precision()-amount.scale()>22)
            throw new IllegalArgumentException("INVALID_SIMULATED_FIAT_AMOUNT");
    }
}
