# AI Agent Engineering Contract

## Purpose

This file defines mandatory engineering and architecture rules for any
AI agent modifying this repository.

It applies to:

- Claude Code
- GitHub Copilot
- OpenAI Codex
- other coding agents

Read this file BEFORE changing code.

---

## 1. System Objective

Build an AWS-native Financial Services POC demonstrating:

Cross-Border B2B Payment
→ Stablecoin Settlement
→ Real Blockchain Testnet Transaction
→ Optional Visa Payout
→ Financial Ledger
→ Reconciliation

Primary demonstration amount:

USD 10,000

---

## 2. Non-Negotiable Architecture

Frontend:

React + TypeScript

Backend:

Java 21
Spring Boot 3.x

Compute:

AWS ECS Fargate

API:

Amazon API Gateway

Database:

Aurora PostgreSQL

Asynchronous processing:

Amazon EventBridge + Amazon SQS

Infrastructure:

AWS CDK + TypeScript

Blockchain:

Base Sepolia preferred
Ethereum Sepolia fallback

Visa:

Visa Simulator behind VisaPaymentAdapter

---

## 3. Real versus Simulated

### REAL

- application
- APIs
- AWS infrastructure
- PostgreSQL
- event processing
- ledger
- reconciliation
- testnet wallet
- blockchain testnet
- blockchain transaction
- transaction hash
- transaction confirmation

### SIMULATED

- fiat funding
- fiat-to-USDC conversion
- FX
- KYC
- KYB
- AML
- sanctions screening
- Visa
- beneficiary fiat credit

Never represent a simulated operation as real.

---

## 4. Required Services

Initial deployable services:

1. payment-service
2. compliance-quote-service
3. blockchain-service
4. settlement-ledger-service
5. reconciliation-service
6. visa-simulator

Do NOT create additional microservices without justification.

---

## 5. Payment State Model

Expected high-level states:

CREATED
COMPLIANCE_PENDING
COMPLIANCE_APPROVED
COMPLIANCE_REJECTED
QUOTE_PENDING
QUOTE_ACCEPTED
RAIL_SELECTED
FUNDING_PENDING
FUNDED
BLOCKCHAIN_SUBMITTED
BLOCKCHAIN_CONFIRMED
SETTLEMENT_COMPLETED
VISA_PAYOUT_PENDING
VISA_PAYOUT_COMPLETED
RECONCILIATION_PENDING
RECONCILED
FAILED

State transitions must be explicit and auditable.

---

## 6. Event Contract

Important events include:

PaymentCreated
ComplianceCompleted
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

Every event MUST contain:

eventId
eventType
eventVersion
paymentId
correlationId
timestamp

Events must be idempotently processed.

---

## 7. Correlation

The same correlationId must propagate through:

HTTP request
Payment
Events
Ledger
Blockchain transaction
Visa transaction
Reconciliation
Logs

Blockchain txHash must be associated with paymentId and correlationId.

---

## 8. Financial Ledger

Blockchain is NOT the application's accounting ledger.

Implement double-entry accounting.

Posted financial entries must NOT be mutated.

Corrections require reversal or compensating entries.

Debits must equal credits for each balanced transaction.

---

## 9. Blockchain

The end-to-end integration test MUST execute against a real public testnet
when TESTNET mode is enabled.

Required capabilities:

getBalance()
estimateGas()
transferToken()
submitTransaction()
getTransactionReceipt()
waitForConfirmation()

Persist:

network
chainId
tokenContract
senderWallet
recipientWallet
tokenAmount
transactionHash
blockNumber
gasUsed
transactionStatus

---

## 10. Blockchain Security

Never:

- commit private keys
- store private keys in PostgreSQL
- log private keys
- put secrets in application.yml
- hardcode RPC credentials

POC secrets should use AWS Secrets Manager and KMS.

Document institutional MPC/HSM/custody as production evolution.

---

## 11. Visa Architecture

Business services MUST interact with:

VisaPaymentAdapter

Implement initially:

VisaSimulatorAdapter

Future:

VisaDirectAdapter

The Payment Orchestrator must not depend on Visa-specific implementation details.

---

## 12. Idempotency

Payment APIs and asynchronous consumers must be idempotent.

Use:

Idempotency-Key

for payment-creation operations.

Do not submit duplicate blockchain transactions when an event is redelivered.

---

## 13. Observability

Use structured JSON logging.

Never log:

- credentials
- private keys
- secrets
- full sensitive PII

Logs should contain when applicable:

timestamp
service
paymentId
correlationId
eventId
eventType
status
txHash
visaReference

---

## 14. Failure Handling

Implement:

retry
timeout
dead-letter queues
idempotent consumers
recoverable/non-recoverable error classification

Never blindly retry blockchain submissions.

Check previously submitted transaction state first.

---

## 15. Engineering Standards

Code must:

- compile
- have meaningful tests
- avoid unnecessary frameworks
- use typed models
- validate external input
- produce structured errors
- avoid dead code
- avoid placeholder implementations unless explicitly marked

Do not introduce Kubernetes.

Do not introduce Kafka/MSK for this POC.

Do not introduce a custom smart contract during initial implementation
unless explicitly requested.

---

## 16. Agent Behaviour

Before implementation:

1. Read this file.
2. Read relevant /docs files.
3. Read applicable ADRs.
4. Identify conflicts.
5. Do not silently alter architecture.

After implementation:

1. compile
2. run relevant tests
3. report failures honestly
4. describe files modified
5. list architectural assumptions
6. identify remaining TODOs

Never claim a test passed unless it actually executed successfully.

---

## 17. Architecture Changes

Existing accepted ADRs are authoritative.

If implementation requires an architecture change:

STOP.

Propose an ADR.

Do not silently implement a different architecture.
---

## 18. Operational Handover

Before continuing demo operations, read [the central SOP](docs/37-DEMO-OPERATIONS-SOP.md)
and [current activity status](docs/36-POC-ACTIVITY-STATUS.md). Read available ignored
.local/poc-status/ handover records for private run details; these records are
local-only and may not exist in another checkout. Do not publish their contents.
Record observed results and the next step after material demo activities. Do not
assume conversation history or a compaction summary contains all runtime evidence.
