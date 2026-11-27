package com.cognizant.stablecoins.ledger.besu;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Separate private funding transport. Handler transaction commits before successful acknowledgement. */
@Service
@ConditionalOnProperty(name={"app.besu.accounting.enabled", "app.besu.funding-events.enabled"}, havingValue="true")
public class BesuFundingConsumer {
    private final BesuFundingHandler handler;
    public BesuFundingConsumer(BesuFundingHandler handler) { this.handler = handler; }

    @SqsListener("${app.sqs.besu-ledger-funding-queue-name}")
    public void consume(String body) {
        // Do not swallow failures: SQS retries and eventually redrives to the dedicated DLQ.
        // The handler verifies the committed envelope and reuses immutable journal references.
        handler.handle(body);
    }
}
