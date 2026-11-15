# Event Catalogue

## Common Envelope

{
  "eventId": "...",
  "eventType": "...",
  "eventVersion": "1.0",
  "paymentId": "PAY-10001",
  "correlationId": "CORR-10001",
  "timestamp": "...",
  "payload": {}
}

## Events

PaymentCreated

ComplianceCompleted

QuoteCreated

QuoteAccepted

RailSelected

StablecoinFundingCompleted

BlockchainTransactionSubmitted

BlockchainTransactionConfirmed

SettlementCompleted

VisaPayoutInitiated

VisaPayoutCompleted

ReconciliationCompleted

PaymentFailed

---

## PaymentCreated (Iteration 3 — IMPLEMENTED)

**Producer:** payment-service  
**Consumer:** compliance-quote-service  
**EventBridge source:** `stablecoins.payment-service`  
**Detail-type:** `PaymentCreated`  
**SQS target:** `stablecoin-payment-created-queue`  
**Version:** 1  

Written to OUTBOX_EVENT in the same DB transaction as PAYMENT row creation.
Payment transitions to COMPLIANCE_PENDING atomically with this event write.

```json
{
  "eventId": "uuid",
  "eventType": "PaymentCreated",
  "eventVersion": "1",
  "paymentId": "PAY-XXXXXXXXXXXX",
  "correlationId": "CORR-XXXXXXXXXXXX",
  "timestamp": "2026-11-10T12:00:00Z",
  "payload": {
    "senderAccountId": "uuid",
    "beneficiaryId": "uuid",
    "amount": 10000.00,
    "sourceCurrency": "USD",
    "destinationCurrency": "USD",
    "purpose": "SUPPLIER_INVOICE",
    "requestedRail": "AUTO"
  }
}
```

**Idempotency:** compliance-quote-service inserts `(eventId, "compliance-quote-service:PaymentCreatedConsumer")`
into PROCESSED_EVENT before processing. Duplicate delivery is silently skipped.

---

## ComplianceCompleted (Iteration 3 — IMPLEMENTED)

**Producer:** compliance-quote-service  
**Consumer:** payment-service  
**EventBridge source:** `stablecoins.compliance-quote-service`  
**Detail-type:** `ComplianceCompleted`  
**SQS target:** `stablecoin-compliance-completed-queue`  
**Version:** 1  

Written to OUTBOX_EVENT atomically with COMPLIANCE_CHECK rows.
`simulation=true` is ALWAYS set — compliance is never real.

```json
{
  "eventId": "uuid",
  "eventType": "ComplianceCompleted",
  "eventVersion": "1",
  "paymentId": "PAY-XXXXXXXXXXXX",
  "correlationId": "CORR-XXXXXXXXXXXX",
  "timestamp": "2026-11-10T12:00:05Z",
  "payload": {
    "overallResult": "APPROVED",
    "simulation": true,
    "checks": [
      { "checkType": "KYC_KYB",         "result": "PASS", "simulation": true, "reason": null },
      { "checkType": "SANCTIONS",        "result": "PASS", "simulation": true, "reason": null },
      { "checkType": "AML",              "result": "PASS", "simulation": true, "reason": null },
      { "checkType": "WALLET_SCREENING", "result": "PASS", "simulation": true, "reason": null }
    ]
  }
}
```

**State transition on consumption:**  
`COMPLIANCE_PENDING → COMPLIANCE_APPROVED` (all PASS)  
`COMPLIANCE_PENDING → COMPLIANCE_REJECTED` (any FAIL)

**Idempotency:** payment-service inserts `(eventId, "payment-service:ComplianceCompletedConsumer")`
into PROCESSED_EVENT before processing.

---

## Infrastructure (Iteration 3 — IMPLEMENTED)

| Resource | Name | Purpose |
|---|---|---|
| EventBridge Bus | `stablecoin-payment-bus` | Custom event bus |
| SQS Queue | `stablecoin-payment-created-queue` | PaymentCreated delivery to compliance |
| SQS Queue | `stablecoin-compliance-completed-queue` | ComplianceCompleted delivery to payment |
| SQS DLQ | `stablecoin-payment-created-dlq` | Undeliverable PaymentCreated (maxReceive=3) |
| SQS DLQ | `stablecoin-compliance-completed-dlq` | Undeliverable ComplianceCompleted (maxReceive=3) |

**Local dev:** LocalStack at `LOCALSTACK_URL`. Queues are auto-created by `LocalStackInitializer`
on application startup (`@Profile("local")`). OutboxDispatcher publishes directly to SQS (skips EventBridge).

**AWS production:** AwsEventBridgePublisher publishes to `stablecoin-payment-bus`.
CDK routing rules forward to SQS. Spring Cloud AWS `@SqsListener` consumes.

## StablecoinFundingCompleted

Publisher: payment-service

This event represents a SIMULATED fiat-to-stablecoin funding step.
It must never be represented as a real financial transaction.

```json
{
  "eventId": "EVT-10003",
  "eventType": "StablecoinFundingCompleted",
  "eventVersion": "1.0",
  "paymentId": "PAY-10001",
  "correlationId": "CORR-10001",
  "timestamp": "2026-11-10T12:00:00Z",
  "payload": {
    "fundingMode": "SIMULATED",
    "sourceAmount": 10000.00,
    "sourceCurrency": "USD",
    "destinationAmount": 10000.000000,
    "destinationAsset": "USDC",
    "rate": 1.0,
    "note": "Simulated USD-to-USDC conversion. No real funds moved."
  }
}
```

## Blockchain Confirmation Example

{
  "eventId": "EVT-10004",
  "eventType": "BlockchainTransactionConfirmed",
  "eventVersion": "1.0",
  "paymentId": "PAY-10001",
  "correlationId": "CORR-10001",
  "timestamp": "2026-11-10T12:00:00Z",
  "payload": {
    "network": "base-sepolia",
    "txHash": "REAL_TRANSACTION_HASH",
    "status": "CONFIRMED"
  }
}

## Delivery

EventBridge performs routing.

SQS provides durable service delivery.

Consumers must be idempotent.

Dead-letter queues must be configured where appropriate.

## Accounting integration contracts (ADR-015)

AWS EventBridge targets deliver only $.detail to SQS. Java and LocalStack consumers
therefore receive the same domain-event body, never an implicit EventBridge wrapper.
Every event still requires eventId, eventType, eventVersion, paymentId,
correlationId and an ISO-8601 timestamp. Queue source/routing is server-owned.

StablecoinFundingCompleted **2.0** (publisher: payment-service) adds trusted accepted
quote terms. The old amount-only 1.0 example above is historical and cannot
establish the new accounting binding. The new payload is:

```json
{
  "simulation": true,
  "fundingMode": "SIMULATED",
  "sourceCurrency": "USD",
  "destinationAsset": "USDC",
  "terms": {
    "conversionId": "00000000-0000-0000-0000-000000000101",
    "quoteId": "00000000-0000-0000-0000-000000000102",
    "quoteVersion": 1,
    "fundingReference": "SIMULATED-FUNDING-EXAMPLE",
    "sourceUsd": 10000.00,
    "transferUsdc": 10000.000000,
    "outcome": "USDC_WALLET",
    "payoutAmount": null
  }
}
```

This is a contract example, not proof of funding or a published event. For optional
SIMULATED_BANK_USD/INR outcomes, payoutAmount is the accepted net fiat amount.
LedgerFundingQueue delivers this event to StablecoinFundingConsumer, which reuses
atomic funding/conversion commands. Step 7 implements the approved wallet producer
and waits for all three bound POSTED funding/conversion journals before requesting
submission. This is application evidence of a simulated allocation, not real fiat
conversion. Fee-complete final settlement/payout/reconciliation remain step 8.

BlockchainTransactionSubmitted and BlockchainTransactionConfirmed 1.0 come only
from blockchain-service. Existing payloads include network, chainId, txHash,
amount, tokenSymbol and evidenceMode (LIVE or MOCK), plus transaction, token,
wallet and explorer fields. Confirmation additionally includes blockNumber and
receipt gas observations. Their separate ledger queues retain the bound allocation
and compare amounts; unknown networks/assets/modes cannot silently default to USDC
or real evidence. LIVE maps to REAL_TESTNET ledger provenance, never real fiat.
The confirmation contract is emitted only after successful required confirmations;
receipt fee completeness remains a separate evidence requirement.

Each of the three ledger delivery queues has its own configured DLQ, maxReceiveCount
3 and a 120-second visibility timeout. Out-of-order delivery must retry rather than
fabricate missing prior legs; exhausted messages require operational review. Queue
existence and source/target configuration do not prove end-to-end publication,
settlement, receipt fee extraction or downstream reconciliation.

## Step 7 owner-service workflow commands

All commands use the required domain envelope and eventVersion **1.0**. Their
publisher/source is payment-service (`stablecoins.payment-service`). EventBridge
forwards `$.detail`; local fanout sends the identical event body/ID to each target.

| Event type | Payload | Consumer / purpose |
|---|---|---|
| RoutingEvidenceRequested | Canonical typed FlowContract.Request, including request/payment/correlation/customer, USD/USDC, recipient, outcome, rail, corridor, deadline and provider IDs | compliance-quote captures immutable simulated provider quotes; blockchain captures treasury readiness |
| StablecoinExecutionRequested | `{ "executionId": "<UUID>" }` | compliance-quote validates the selected owner quote; blockchain prepares an existing-service treasury reservation |
| BlockchainSubmissionRequested | `{ "executionId": "<UUID>" }` | blockchain rechecks the claim, approval, reviewed execution bounds, compliance validation and three POSTED allocation legs before delegating submission |

Each request/claim is verified against owner tables; a command body cannot grant
approval or replace the canonical instruction. Command fanout adds five queues
and five DLQs. Each queue has 120-second visibility and maxReceiveCount 3. Together
with existing routes the CDK defines nine rules and 22 queues including DLQs.
Partial fanout retries preserve event identity; durable owner records make replay
safe. Queue configuration alone does not prove live delivery.

RoutingRecommendationExecuted **1.0** is an audited review notification on the
existing decision route. It is not a signing tool or money-movement command.
StablecoinFundingCompleted **2.0** is emitted atomically with the simulated account
debit and payment history; its conversionId binds the execution and quote terms.
Receipt recovery can emit a missing BlockchainTransactionSubmitted predecessor
before BlockchainTransactionConfirmed. Separate queues can reorder delivery, so
ledger consumers still retry missing prerequisites rather than fabricate them.

## Step 8 lifecycle events (version 1.0)

All retain the mandatory domain envelope and canonical payment/correlation IDs.

| Event | Publisher | Owner consumer / evidence |
|---|---|---|
| BlockchainReceiptObserved | blockchain outbox | Ledger actual receipt fee; also emitted for mined reverts with complete fees |
| SettlementRequested | payment outbox | Ledger verifies six journals and records final settlement |
| SettlementCompleted | payment outbox after ledger acknowledgement | Existing decision/notification stream |
| VisaPayoutInitiated | payment outbox | Separate Visa creation and ledger initiation queues |
| VisaPayoutCompleted | payment outbox after canonical simulated success | Ledger fiat completion queue |
| ReconciliationRequested | payment outbox | Reconciliation immutable owner report |
| ReconciliationCompleted | payment outbox after current matching report | Existing decision/notification stream |

StablecoinExecutionRequested additionally reaches the Visa owner to pin bank
provider capacity after canonical validation; wallet outcomes are a no-op there.
It now has three owner deliveries. Five new queue/DLQ pairs bring CDK totals to
14 rules and 32 queues including DLQs. $.detail targets preserve the domain envelope.
Out-of-order owner readiness causes retry, never fabricated preceding evidence.
See [step-8 delivery and recovery](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
