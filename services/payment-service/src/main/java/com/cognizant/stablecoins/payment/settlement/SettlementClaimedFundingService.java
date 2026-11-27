package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Resumes a committed claim using its original operator, never identity supplied in a queue message. */
@Service
@ConditionalOnProperty(name={"app.flow.enabled","app.besu.funding.enabled","app.besu.funding-workers.enabled"},havingValue="true")
public class SettlementClaimedFundingService {
    private final SettlementExecutionClaimStore claims;
    private final SettlementFundingService funding;
    @Autowired
    public SettlementClaimedFundingService(JdbcTemplate jdbc,SettlementFundingService funding) {
        this(new SettlementExecutionClaimStore(jdbc),funding);
    }
    SettlementClaimedFundingService(SettlementExecutionClaimStore claims,SettlementFundingService funding) {
        this.claims=claims;this.funding=funding;
    }
    @Transactional
    public SettlementFundingService.Receipt fundClaimed(UUID executionId) {
        var claim=claims.byId(executionId).filter(c->c.execution().executionId().equals(executionId))
            .orElseThrow(()->new IllegalStateException("SETTLEMENT_EXECUTION_CLAIM_REQUIRED"));
        // Funding owner locks payment/account and rechecks current operator/approvers, compliance,
        // quote and fresh unsigned PINNED capacity. A historical claim is not fresh authority.
        return funding.fund(claim.execution(),new AuthorizedCustomer(claim.execution().instruction().customerId(),claim.requestedBy()));
    }
}
