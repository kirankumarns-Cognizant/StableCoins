package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.ledger.api.dto.PostJournalRequest;
import com.cognizant.stablecoins.ledger.service.LedgerPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingPolicy.Role.*;

/** Explicitly authorized control-position release. Never refunds USD, reverses gas or retries a chain transfer. */
@Service
@ConditionalOnProperty(name={"app.besu.accounting.enabled","app.besu.recovery.enabled"},havingValue="true")
public class BesuCompensationService {
    private final LedgerPostingService ledger;private final BesuAccountingPolicy policy;
    private final BesuRevertEvidence reads;private final BesuCompensationStore store;
    @Autowired
    public BesuCompensationService(LedgerPostingService ledger,BesuAccountingPolicy policy,JdbcTemplate jdbc,ObjectMapper mapper,Environment env,BesuCompensationStore store) {
        this(ledger,policy,new BesuRevertEvidence(jdbc,mapper,env),store);
    }
    public BesuCompensationService(LedgerPostingService ledger,BesuAccountingPolicy policy,BesuRevertEvidence reads,BesuCompensationStore store) {
        this.ledger=ledger;this.policy=policy;this.reads=reads;this.store=store;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public BesuCompensationStore.Acknowledgement compensate(SettlementBinding.Execution e) {
        ledger.lockPayment(e.instruction().paymentId(),e.instruction().correlationId());
        var proof=reads.current(e);var authorization=reads.authorization(e,proof);var prior=store.find(e);
        if(prior.isPresent()) {
            var a=prior.get();
            if(!proof.released() || !proof.sourceHash().equals(a.sourceHash()) || !authorization.actorId().equals(a.actorId())
                || !proof.submission().journalId().equals(a.originalJournal())
                || ledger.getJournalsByPaymentId(e.instruction().paymentId()).stream().noneMatch(j->
                    a.releaseJournal().equals(j.getJournalId()) && BesuRevertEvidence.RELEASE.equals(j.getJournalType())))throw conflict();
            return a;
        }
        if(proof.released())throw conflict();
        var original=ledger.getJournal(proof.submission().journalId());
        var request=new PostJournalRequest();request.setPaymentId(e.instruction().paymentId());request.setCorrelationId(e.instruction().correlationId());
        request.setJournalType(BesuRevertEvidence.RELEASE);request.setReference(BesuRevertEvidence.reference(original.getJournalId()));
        request.setDescription("Authorized release of reverted DEMOUSD in-flight control; gas and USD unchanged");
        request.setEvidence(original.getEvidence());
        request.setEntries(List.of(policy.line(TREASURY,e.instruction().tokenAmount(),true),policy.line(TRANSIT,e.instruction().tokenAmount(),false)));
        var release=ledger.postJournal(request);
        var a=new BesuCompensationStore.Acknowledgement(proof.sourceHash(),authorization.actorId(),original.getJournalId(),release.getJournalId());
        store.acknowledge(e,a);return a;
    }
    private static IllegalStateException conflict(){return new IllegalStateException("BESU_COMPENSATION_CONFLICT");}
}
