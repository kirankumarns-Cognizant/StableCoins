package com.cognizant.stablecoins.ledger.event.consumer;

import com.cognizant.stablecoins.ledger.accounting.PaymentAccountingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class BlockchainConfirmedConsumer {
    private final PaymentAccountingService accounting;
    private final ObjectMapper mapper;
    public BlockchainConfirmedConsumer(PaymentAccountingService accounting,ObjectMapper mapper){this.accounting=accounting;this.mapper=mapper;}
    @Transactional
    @SqsListener("${app.sqs.blockchain-confirmed-queue-name}")
    public void consume(String body) {
        var event=LedgerInboundEvent.read(mapper,body,"BlockchainTransactionConfirmed",Set.of("1","1.0"));
        com.cognizant.stablecoins.ledger.accounting.PostingEvidence.Mode mode;
        try { mode=event.chainMode(); if(event.payload().path("blockNumber").asLong(0)<1) throw new IllegalArgumentException(); }
        catch(Exception error) { throw new IllegalArgumentException("Failed to deserialize BlockchainTransactionConfirmed message"); }
        var context=accounting.context(event.paymentId(),event.correlationId());
        if(event.payload().path("amount").decimalValue().compareTo(context.terms().transferUsdc())!=0) throw new IllegalArgumentException("CONFIRMED_AMOUNT_MISMATCH");
        accounting.confirmed(context,event.payload().path("txHash").asText(),mode);
        // A separate receipt-evidence command posts actual fees; never substitute a gas estimate.
    }
}
