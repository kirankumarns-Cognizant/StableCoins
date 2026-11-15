package com.cognizant.stablecoins.ledger.event.consumer;

import com.cognizant.stablecoins.ledger.accounting.PaymentAccountingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class BlockchainSubmittedConsumer {
    private final PaymentAccountingService accounting;
    private final ObjectMapper mapper;
    public BlockchainSubmittedConsumer(PaymentAccountingService accounting,ObjectMapper mapper){this.accounting=accounting;this.mapper=mapper;}
    @Transactional
    @SqsListener("${app.sqs.blockchain-submitted-queue-name}")
    public void consume(String body) {
        var e=LedgerInboundEvent.read(mapper,body,"BlockchainTransactionSubmitted",Set.of("1","1.0"));
        var mode=e.chainMode();
        var context=accounting.context(e.paymentId(),e.correlationId());
        if(e.payload().path("amount").decimalValue().compareTo(context.terms().transferUsdc())!=0) throw new IllegalArgumentException("SUBMITTED_AMOUNT_MISMATCH");
        accounting.submitted(context,e.payload().path("txHash").asText(),mode);
    }
}
