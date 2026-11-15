package com.cognizant.stablecoins.ledger.event.consumer;

import com.cognizant.stablecoins.ledger.accounting.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

/** Version 2 requires accepted quote terms; legacy amount-only funding events cannot invent accounting bindings. */
@Service
public class StablecoinFundingConsumer {
    private final PaymentAccountingService accounting;
    private final ObjectMapper mapper;
    public StablecoinFundingConsumer(PaymentAccountingService accounting,ObjectMapper mapper){this.accounting=accounting;this.mapper=mapper;}
    @Transactional
    @SqsListener("${app.sqs.ledger-funding-queue-name}")
    public void consume(String body) {
        var e=LedgerInboundEvent.read(mapper,body,"StablecoinFundingCompleted",Set.of("2.0"));
        var p=e.payload();
        if(!p.path("simulation").isBoolean() || !p.path("simulation").booleanValue() || !"SIMULATED".equals(p.path("fundingMode").asText())
                || !"USD".equals(p.path("sourceCurrency").asText()) || !"USDC".equals(p.path("destinationAsset").asText()))
            throw new IllegalArgumentException("SIMULATED_FUNDING_EVIDENCE_REQUIRED");
        if(!p.path("terms").isObject()) throw new IllegalArgumentException("ACCEPTED_QUOTE_TERMS_REQUIRED");
        PostingEvidence.Terms terms;
        try { terms=mapper.treeToValue(p.path("terms"),PostingEvidence.Terms.class); }
        catch(Exception error) { throw new IllegalArgumentException("ACCEPTED_QUOTE_TERMS_REQUIRED"); }
        accounting.fundAndConvert(new PaymentAccountingService.Context(e.paymentId(),e.correlationId(),terms));
    }
}
