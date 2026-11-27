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

## 19. Additive Besu implementation

Owner-accepted [ADR-016](adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md)
authorizes the separate local private-demo Besu network and narrow DEMOUSD
contract exception. Base Sepolia remains the default public path. Besu setup
is isolated under infrastructure/besu; runtime integration uses a dedicated
besu package inside blockchain-service, not another business microservice.
The orchestrator selects a pre-initialized network; it never deploys per payment
or changes chains after signing/submission. Do not enable Besu selection until
its complete approval, accounting and reconciliation path passes acceptance.

## 20. One implementation backlog

Read the [consolidated implementation backlog](docs/40-IMPLEMENTATION-BACKLOG.md) before planning work.
Record every new TODO, deferred decision and acceptance gap there with a stable ID,
status and closure criteria. Update it after implementation and validation. Other
documents may describe designs and dated evidence, but must reference backlog IDs
instead of maintaining separate current queues. Never close an item solely on
compilation, skipped tests or historical evidence for another revision.

## 21. Conversation knowledge capture

When project conversations contain substantive learning or conceptual explanations,
capture the useful content in [knowledge notes](docs/41-KNOWLEDGE-NOTES.md) before
ending the turn. The owner requested this ongoing local record on 26 November 2026.
The owner clarified that this is a personal learning knowledgebase: questions such
as "what is EVM?" and "how do ERC-20, ERC-721 and ERC-1155 differ?", with accessible
answers, examples and comparison tables. Use the question as a heading, record the
date learned and sources, and update existing topics with useful follow-ups and
corrections. Capture conceptual learning embedded in other discussions as well.
Exclude implementation coverage assessments, completion percentages, test results,
task lists, routine status chatter, secrets, sensitive PII and private handovers.
Keep project examples short and clearly labelled. Current TODOs, deferred decisions
and acceptance gaps stay in the central backlog; operational evidence stays in the
activity history. Knowledge capture does not authorize new scope or override ADRs.
No note is required for a purely operational exchange.

### Standing authorization for conversation extracts (26 November 2026)

The owner explicitly authorizes ongoing knowledge capture without asking again.
Review every project conversation for useful functional-domain, technology and
tool concepts, including learning embedded in implementation discussions. Summarize
those concepts in docs/41-KNOWLEDGE-NOTES.md before ending the turn; update an
existing topic rather than duplicate it. Capture useful explanations and examples,
not a verbatim transcript. The exclusions and source/date requirements above still
apply; a purely operational or progress-count exchange needs no learning entry.

### Knowledge scope clarification (26 November 2026)

Also capture learning about what POC functionality means and how it participates
in the stablecoin value chain: domain workflows, treasury, compliance, accounting,
reconciliation, tools, technology choices, deployment and enterprise foundations.
Explain actual functionality with dated source references and distinguish
implemented behavior, simulated behavior and future design. Keep commands,
execution procedures, coverage percentages, task status and test results in the
SOP/activity/backlog instead. Knowledge notes are a learning reference, not only
a glossary and not a competing operational or implementation register.

## 22. Enterprise foundations: deferred review

The owner requested reusable foundations for logging, auditing, exception/error
handling, analytics, model token-usage visibility in an app corner, caching,
notifications and enterprise integration. Reuse existing components and preserve
service ownership, authorization, idempotency and financial evidence boundaries.
On 26 November the owner explicitly deferred this review/refactoring until the
end, to preserve working functionality and continue Besu first. Track this as
E01-E06 in the central backlog. Do not refactor or enable these features during
Besu work merely because this principle has been recorded. Evaluate any required
architecture change through the existing ADR process.