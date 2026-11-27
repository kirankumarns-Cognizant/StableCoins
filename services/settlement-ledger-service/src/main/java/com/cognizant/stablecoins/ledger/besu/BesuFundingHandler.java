package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import com.cognizant.stablecoins.ledger.model.Journal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Validates committed funding before atomic, idempotent ledger allocation. */
@Service
@ConditionalOnProperty(name="app.besu.accounting.enabled",havingValue="true")
public class BesuFundingHandler {
    private final SettlementFundingReads funding;
    private final BesuAccountingService accounting;
    @org.springframework.beans.factory.annotation.Autowired
    public BesuFundingHandler(JdbcTemplate jdbc,ObjectMapper mapper,BesuAccountingService accounting) {
        this(new SettlementFundingReads(jdbc,mapper,new BesuClosureEvidence.Policy("adr015-v1","110002","110001","110003")),accounting);
    }
    BesuFundingHandler(SettlementFundingReads funding,BesuAccountingService accounting){this.funding=funding;this.accounting=accounting;}
    @Transactional
    public List<Journal> handle(String document) {
        var e=funding.requireCommand(document);
        return accounting.fundAndAllocate(e,SettlementFundingReads.reference(e));
    }
}
