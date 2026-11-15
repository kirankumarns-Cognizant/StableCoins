# Implementation Plan — Stablecoin Intelligent Payment Orchestration POC

Architecture review completed: 2026-11-10
Owner decisions recorded: 2026-11-10

Reviewed as: Principal AWS Architect, Payments Architect, Blockchain Architect,
Application Architect, Security Architect.

Sources read: CLAUDE.md, AGENTS.md, README.md, docs/01–16, adr/ADR-001–006,
prompts/00-REVIEW.md.

---

## Section 0: Owner Decisions — All Approved

### DEC-01: Token Contract — APPROVED

Use Circle's official test USDC deployed on Base Sepolia where available.

Do not deploy a custom ERC-20 contract in Version 1.

Token contract address, token symbol and token decimals must be configurable
environment variables. Never hardcode chain-specific values in business logic
(consistent with ADR-002).

Ethereum Sepolia remains the fallback network. Fallback also uses Circle's
official test USDC on Ethereum Sepolia.

If Circle's testnet USDC is unavailable at time of implementation, the
implementation must report BLOCKED and propose a new ADR. Silent substitution
of another token is not permitted.

See ADR-011.

### DEC-02: Database Schema Ownership — APPROVED

One Aurora PostgreSQL cluster, one database, one shared schema.

Logical table ownership:

| Service | Owned Tables |
|---|---|
| payment-service | CUSTOMER, ACCOUNT, BENEFICIARY, PAYMENT, PAYMENT_STATUS_HISTORY |
| compliance-quote-service | FX_QUOTE, COMPLIANCE_CHECK |
| blockchain-service | WALLET, BLOCKCHAIN_TRANSACTION |
| settlement-ledger-service | SETTLEMENT, LEDGER_ACCOUNT, JOURNAL, LEDGER_ENTRY |
| visa-simulator | VISA_TRANSACTION |
| reconciliation-service | RECONCILIATION, RECONCILIATION_DETAIL |

A service may read from tables it does not own.
A service may not INSERT, UPDATE or DELETE rows in another service's tables.

See ADR-007.

### DEC-03: Reconciliation Data Access — APPROVED

The Reconciliation Service queries the shared Aurora database directly.

Read access: PAYMENT, COMPLIANCE_CHECK, FX_QUOTE, BLOCKCHAIN_TRANSACTION,
SETTLEMENT, LEDGER_ENTRY, VISA_TRANSACTION.

Write access: RECONCILIATION, RECONCILIATION_DETAIL only.

Production evolution: event-projected read models per reconciliation leg.
Document this in reconciliation service design notes; do not implement for POC.

See ADR-008.

### DEC-04: API Gateway to Fargate Network Path — APPROVED

CloudFront → AWS WAF → API Gateway HTTP API → VPC Link →
internal shared ALB → ECS Fargate.

ALB uses path-based routing to route to the correct Fargate service.

Visa Simulator is kept private (internal VPC only) unless explicitly required
for external testing.

See ADR-009.

### DEC-05: ACCOUNT Table Ownership — APPROVED (overriding recommendation)

payment-service owns the customer ACCOUNT table.

Customer ACCOUNT (payment account) and LEDGER_ACCOUNT (chart of accounts
entry) are separate concepts. They must not be confused in naming or schema
design.

settlement-ledger-service owns: LEDGER_ACCOUNT, JOURNAL, LEDGER_ENTRY,
SETTLEMENT.

See ADR-012.

### DEC-06: Local Development Strategy — APPROVED

Testcontainers (PostgreSQL) for database in integration tests.
LocalStack for EventBridge and SQS in integration tests.
Mocks/stubs for unit tests.
Real Base Sepolia only when both BLOCKCHAIN_MODE=REAL_TESTNET and
TESTNET_ENABLED=true are set simultaneously.

Spring profiles:

| Profile | Purpose |
|---|---|
| test | Unit tests; mocks only |
| local | Local development; Testcontainers + LocalStack |
| aws-dev | Shared dev AWS account |
| testnet | Real Base Sepolia execution |

See ADR-010.

---

## Section 0B: Additional Approved Decisions

### Chart of Accounts — APPROVED

The following chart of accounts is the authoritative account list for the POC.
Seed data must be inserted by settlement-ledger-service Flyway migration.

| Code   | Name                          | Type    | Asset |
|--------|-------------------------------|---------|-------|
| 110001 | Corporate USD Control         | ASSET   | USD   |
| 110002 | Stablecoin Funding Clearing   | ASSET   | USD   |
| 120001 | USDC Treasury                 | ASSET   | USDC  |
| 120002 | USDC In Transit               | ASSET   | USDC  |
| 120003 | Beneficiary USDC Settlement   | ASSET   | USDC  |
| 210001 | Beneficiary Settlement Payable| LIABILITY| USD  |
| 410001 | Payment Fee Revenue           | REVENUE | USD   |
| 510001 | Blockchain Gas Expense        | EXPENSE | ETH   |
| 510002 | Payment Fee Expense           | EXPENSE | USD   |

Ledger balancing rule: validate SUM(debits) = SUM(credits) separately for each
asset (USD, USDC, ETH). Never offset USD against USDC or ETH to claim balance.

### Synchronous / Asynchronous Interaction Boundaries — APPROVED

| Payment Step | Mode | Notes |
|---|---|---|
| Payment creation | Synchronous HTTP | Returns paymentId immediately |
| Payment query | Synchronous HTTP | |
| Quote creation | Synchronous HTTP | compliance-quote-service |
| Quote acceptance | Synchronous HTTP | User action |
| Compliance check | Asynchronous | PaymentCreated → ComplianceCompleted |
| Rail selection | Internal logic | Within payment-service |
| Stablecoin funding | Asynchronous | StablecoinFundingCompleted |
| Blockchain submission | Asynchronous | BlockchainTransactionSubmitted |
| Blockchain confirmation | Asynchronous | BlockchainTransactionConfirmed |
| Settlement/ledger posting | Asynchronous | SettlementCompleted |
| Visa payout | Asynchronous | VisaPayoutCompleted |
| Reconciliation | Asynchronous | ReconciliationCompleted |
| UI status update | HTTP polling every 3 seconds | While payment is in-flight |

### StablecoinFundingCompleted Publisher — APPROVED

payment-service publishes StablecoinFundingCompleted.

The event payload must include fundingMode: "SIMULATED" to clearly distinguish
this step from a real fiat-to-stablecoin conversion. Consumers must not treat
this event as evidence of a real financial transaction.

### Cognito Authentication Model — APPROVED

Amazon Cognito User Pool with Hosted UI.
OAuth 2.0 Authorization Code flow with PKCE.
API Gateway HTTP API JWT authorizer validates Cognito-issued JWTs.
Spring Boot services configured as OAuth 2.0 resource servers using
Spring Security JWT validation.

Application roles mapped via Cognito custom user attributes or groups:

| Role | Description |
|---|---|
| TREASURY_USER | Initiates and monitors payments |
| OPERATIONS_USER | Views payments, triggers reconciliation |
| ADMIN | Full access including configuration |

### Blockchain Java Library — APPROVED

Use web3j for all Java EVM interactions in the blockchain-service.

Version selection: use the latest stable release compatible with Java 21 and
Spring Boot 3.x. Verify compatibility before pinning in the Maven BOM.
Do not invent a version number; check the web3j release page at time of
implementation.

---

## Architecture Review Summary

### Main Payment Flow (3 bullets)

- Corporate user authenticates → creates payment → compliance simulation →
  FX quote acceptance → stablecoin rail selected → funding simulated.
- Blockchain Service submits a REAL ERC-20 token transfer to Base Sepolia →
  stores txHash immediately → polls for confirmation → emits
  BlockchainTransactionConfirmed event.
- Settlement Ledger posts double-entry accounting entries → optional Visa
  payout simulated → Reconciliation Service compares payment / ledger /
  blockchain / Visa records → payment reaches RECONCILED.

### Six Microservices

| Service | Primary Responsibility |
|---|---|
| payment-service | APIs, state machine, orchestration, idempotency, rail selection |
| compliance-quote-service | Simulated KYC/AML/sanctions, FX quote, fee quote |
| blockchain-service | Wallet interaction, gas, signing, submission, confirmation |
| settlement-ledger-service | Double-entry ledger, settlement, immutable entries |
| reconciliation-service | Cross-leg comparison: payment / ledger / blockchain / Visa |
| visa-simulator | Simulated Visa prefund, payout, failure injection |

### AWS Services

Messaging: Amazon EventBridge + Amazon SQS (per-service queues with DLQs).
Compute: Amazon ECS Fargate for all six Spring Boot services.

---

## Section 1: Contradictions — Resolved

### C1 — Blockchain library specified as Node.js/Python [RESOLVED]

docs/06-BLOCKCHAIN.md corrected to specify web3j (Java). See Section 0B
(Blockchain Java Library) and task C.

### C2 — Service naming inconsistency [RESOLVED]

docs/08-MICROSERVICES.md already reflects the canonical six-service names
from AGENTS.md. No further change required.

---

## Section 2: Implementation Issues and Missing Requirements — Resolved

All findings from the architecture review are addressed by the decisions in
Sections 0 and 0B. Key resolutions:

| Finding | Resolution |
|---|---|
| D1 — Java library not specified | web3j (Section 0B) |
| D2 — Token contract not specified | Circle testnet USDC (DEC-01) |
| D3 — Testnet wallet process | Pre-demo checklist in Iteration 10 |
| D4 — Cognito not configured | Section 0B Cognito model |
| D5 — Schema ownership | DEC-02 logical ownership table |
| D6 — Service-to-service auth | VPC-only internal routing for POC |
| D7 — OpenAPI spec | Future; not blocking initial iterations |
| D8 — Chart of accounts | Section 0B approved CoA |
| D9 — Per-asset balancing | Section 0B balancing rule |
| D10 — Reversal workflow | JOURNAL.reversed_by_journal_id; detail in Iteration 4 |
| D11 — Sync/async boundary | Section 0B interaction table |
| D12 — StablecoinFundingCompleted | Section 0B: payment-service, SIMULATED |
| D13 — API Gateway path | DEC-04 |
| D14 — ACCOUNT ownership | DEC-05 |
| D15 — Local dev strategy | DEC-06 |
| D16 — .env.example | Populated (task H) |
| D17 — Nonce management | Pessimistic lock in Iteration 5 |
| D18 — UI polling | 3-second HTTP polling (Section 0B) |

---

## Section 3: Architecture Decisions — All Approved

| ID | Decision | Status | Blocks |
|---|---|---|---|
| DEC-01 | Circle testnet USDC on Base Sepolia | APPROVED | Iteration 5 |
| DEC-02 | Shared schema, logical ownership | APPROVED | Iteration 2 |
| DEC-03 | Reconciliation reads Aurora directly | APPROVED | Iteration 7 |
| DEC-04 | HTTP API GW → VPC Link → ALB → Fargate | APPROVED | Iteration 9 |
| DEC-05 | ACCOUNT owned by payment-service | APPROVED | Iteration 2 |
| DEC-06 | Testcontainers + LocalStack + Spring profiles | APPROVED | Iteration 1 |

---

## Section 4: Implementation Iterations

Each iteration produces a testable increment. No iteration should attempt to
build the entire system.

---

### Iteration 0 — Architecture Validation COMPLETE

Deliverable: This document.
All decisions resolved. Pre-implementation checklist complete.

---

### Iteration 1 — Repository Bootstrap

Goal: Compilable, runnable skeleton. No business logic.

Scope:
- Maven multi-module project under services/ with one module per service:
  payment-service, compliance-quote-service, blockchain-service,
  settlement-ledger-service, reconciliation-service, visa-simulator.
- Each module: Spring Boot 3.x, Java 21, one GET /health endpoint.
- React TypeScript app under frontend/ (Vite, one placeholder screen).
- AWS CDK TypeScript project under infrastructure/ (cdk init only).
- Dockerfile per service (multi-stage build: build stage + runtime stage).
- docker-compose.yml for local development (Postgres, LocalStack).
- .env.example with all required variables.
- .gitignore covering secrets, build outputs, node_modules, .env files.
- Skeleton CI configuration.
- Spring profiles: test, local, aws-dev, testnet.

Exit criteria:
- mvn clean install passes for all modules.
- Each service starts locally and returns 200 on GET /health.
- npm run build passes for frontend.
- cdk synth runs without error.
- No secrets in source control.

Not in scope: business logic, event publishing, database tables.

---

### Iteration 2 — Payment Domain and PostgreSQL

Goal: Payment creation, state machine, and persistence end-to-end.

Scope:
- Flyway migration scripts (V1__) for: CUSTOMER, ACCOUNT, BENEFICIARY,
  PAYMENT, PAYMENT_STATUS_HISTORY.
- POST /api/v1/payments with Idempotency-Key enforcement.
- GET /api/v1/payments/{paymentId}, /status, /journey.
- Payment state machine: CREATED → COMPLIANCE_PENDING.
- payment-service owns CUSTOMER, ACCOUNT, BENEFICIARY tables (DEC-05).
- Input validation on all API fields.
- Structured JSON logging: timestamp, service, paymentId, correlationId.
- LocalStack and Testcontainers via local Spring profile.
- Unit tests for state machine transitions.
- Integration tests with Testcontainers PostgreSQL.

Exit criteria:
- POST /payments creates a payment record.
- Duplicate Idempotency-Key returns same paymentId without duplicate record.
- GET /payments/{id} returns correct state and history.
- All unit and integration tests pass.

Not in scope: events, blockchain, compliance simulation.

---

### Iteration 3 — EventBridge/SQS Event Model

Goal: Domain events published and consumed reliably.

Scope:
- Spring Boot AWS SDK v2 EventBridge publisher (common library module).
- SQS consumer base class: idempotency check on eventId, DLQ configuration,
  structured error logging.
- PaymentCreated event published by payment-service after Iteration 2 state.
- compliance-quote-service: consumes PaymentCreated, runs simulated compliance,
  emits ComplianceCompleted.
- payment-service: consumes ComplianceCompleted, transitions to
  COMPLIANCE_APPROVED.
- COMPLIANCE_CHECK Flyway migration in compliance-quote-service.
- LocalStack SQS and EventBridge for local profile.
- Event envelope validates: eventId, eventType, eventVersion, paymentId,
  correlationId, timestamp, payload.

Exit criteria:
- Full publish → consume → state transition cycle works end-to-end locally.
- Duplicate eventId delivered twice handled idempotently (single state change).
- Failed message lands in DLQ after max retries.
- Integration test drives the full event cycle with LocalStack.

Not in scope: FX quote, blockchain, Visa.

---

### Iteration 4 — Double-Entry Ledger

Goal: settlement-ledger-service posts balanced double-entry journal entries.

Scope:
- Flyway migrations for: LEDGER_ACCOUNT (seeded with approved chart of accounts),
  JOURNAL, LEDGER_ENTRY, SETTLEMENT.
- Chart of accounts seed data (codes 110001–510002) inserted by migration.
- JournalService: validates SUM(debits) = SUM(credits) per asset before
  posting; rejects unbalanced transactions.
- Immutability: no UPDATE or DELETE on POSTED LEDGER_ENTRY or JOURNAL rows.
- Reversal: POST /ledger/journals/{journalId}/reverse creates mirror journal
  with reversed debit/credit signs; sets original JOURNAL.reversed_by_journal_id.
- GET /api/v1/payments/{paymentId}/ledger returns journal and entry summary.
- Idempotent posting: re-delivery of an event with the same paymentId and
  journal type produces no duplicate journal.

Exit criteria:
- Posting a balanced journal (all four legs from docs/10-LEDGER.md example)
  succeeds.
- Posting an unbalanced journal is rejected with a structured error.
- USD entries, USDC entries, and ETH entries each balance independently.
- Reversal creates correctly signed mirror entries; reversed journal
  cannot be reversed again.
- Unit tests for all four ledger entry legs.
- Integration test with Testcontainers.

Not in scope: event-driven wiring from blockchain or Visa.

---

### Iteration 5 — Blockchain Service

Goal: REAL token transfer submitted and confirmed on Base Sepolia testnet.

Prerequisite check: verify that Circle's testnet USDC contract address on
Base Sepolia is obtainable. If not, report BLOCKED and propose ADR before
writing any code.

Scope:
- web3j dependency added to blockchain-service Maven module (latest stable
  version compatible with Java 21 and Spring Boot 3.x; verify at time of
  implementation).
- RPC URL and wallet private key loaded from Secrets Manager secret or .env
  for local testing (never from source code or hardcoded).
- Implement full BlockchainService interface from docs/06-BLOCKCHAIN.md.
- Nonce management: pessimistic service-level lock (one in-flight submission
  per sender wallet) prevents nonce collision.
- WALLET and BLOCKCHAIN_TRANSACTION Flyway migrations.
- Idempotency: before submitting, query BLOCKCHAIN_TRANSACTION for an
  existing SUBMITTED or CONFIRMED record for the same paymentId. Never
  resubmit if one exists.
- txHash stored immediately after submission, before confirmation poll.
- BlockchainTransactionSubmitted event published on submission.
- BlockchainTransactionConfirmed event published after confirmation.
- Blockchain explorer URL constructed from txHash (never fabricated).
- Token contract address, symbol and decimals read from configuration.
- Integration tests tagged @TestnetIntegration, skipped by default unless
  BLOCKCHAIN_MODE=REAL_TESTNET and TESTNET_ENABLED=true.

Exit criteria:
- transferToken() submits a real transaction to Base Sepolia (testnet profile).
- Real txHash verifiable at https://sepolia.basescan.org.
- waitForConfirmation() returns when mined.
- Idempotency: timeout after submission does not cause duplicate submission.
- Unit tests with mocked RPC provider pass without network access.

Not in scope: wiring into the full payment flow; blockchain tested in isolation.

---

### Iteration 6 — Visa Simulator

Goal: Visa Simulator operational with deterministic failure injection.

Scope:
- visa-simulator Spring Boot service (internal VPC only per DEC-04).
- VISA_TRANSACTION Flyway migration.
- Implement all APIs from docs/07-VISA-SIMULATOR.md:
  POST /visa-simulator/v1/prefund
  POST /visa-simulator/v1/payouts
  GET /visa-simulator/v1/payouts/{id}
  POST /visa-simulator/v1/payouts/{id}/cancel
  GET /visa-simulator/v1/balances
- Payout state machine: ACCEPTED → PROCESSING → SENT → COMPLETED.
- Failure injection via X-Simulator-Scenario header:
  DECLINED, TIMEOUT, INSUFFICIENT_PREFUND.
- VisaPaymentAdapter interface.
- VisaSimulatorAdapter implementation.
- payment-service wired to VisaPaymentAdapter for the Visa payout step.
- VisaPayoutInitiated and VisaPayoutCompleted events published.
- All simulator responses include simulatedVisa: true in metadata.

Exit criteria:
- Happy-path payout reaches COMPLETED.
- Each failure scenario (DECLINED, TIMEOUT, INSUFFICIENT_PREFUND) testable
  via X-Simulator-Scenario header.
- Visa step in full payment journey emits VisaPayoutCompleted event.
- Unit tests cover all simulator scenarios.

Not in scope: reconciliation wiring.

---

### Iteration 7 — Reconciliation Service

Goal: Reconciliation Service compares all four legs; produces MATCHED or MISMATCH.

Scope:
- RECONCILIATION and RECONCILIATION_DETAIL Flyway migrations.
- POST /api/v1/payments/{paymentId}/reconciliation (trigger).
- GET /api/v1/payments/{paymentId}/reconciliation (result).
- Reconciliation logic (DEC-03: direct Aurora read):
  Compare PAYMENT.amount vs LEDGER_ENTRY totals (per asset) vs
  BLOCKCHAIN_TRANSACTION.amount vs VISA_TRANSACTION.amount.
- RECONCILIATION_DETAIL: one row per leg (PAYMENT, LEDGER, BLOCKCHAIN, VISA)
  with expected_amount, actual_amount, asset, status.
- Statuses: MATCHED, PARTIALLY_MATCHED, MISMATCH, PENDING.
- ReconciliationCompleted event published; payment-service transitions to RECONCILED.
- Idempotent: delivering ReconciliationCompleted twice does not duplicate records.

Exit criteria:
- Full happy-path payment produces MATCHED reconciliation with correct
  RECONCILIATION_DETAIL rows.
- Injected amount discrepancy in BLOCKCHAIN_TRANSACTION produces MISMATCH
  with RECONCILIATION_DETAIL showing the mismatched leg.
- Unit and integration tests pass.

Not in scope: UI, AWS deployment.

---

### Iteration 8 — React UI

Goal: All five UI screens functional against the running backend.

Scope:
- Screen 1: Treasury Dashboard — simulated USD treasury balance, test token
  balance, network, treasury wallet, pending/completed payments, reconciliation
  status. Simulated balances clearly labelled.
- Screen 2: Initiate Payment — sender, beneficiary, amount, currency, purpose,
  payment rail (AUTO, STABLECOIN, VISA, STABLECOIN_PLUS_VISA).
- Screen 3: Payment Journey — progression display for all states from CREATED
  to RECONCILED; polls GET /payments/{id}/status every 3 seconds while in-flight.
- Screen 4: Blockchain Transaction — network, token, sender, recipient, amount,
  txHash, block number, gas used, status, explorer link (BaseScan).
  Prominent label: REAL TESTNET TRANSACTION.
- Screen 5: Reconciliation — payment, ledger, blockchain, Visa legs; per-leg
  MATCHED/MISMATCH status. Visa leg labelled SIMULATED VISA.
- Cognito Hosted UI login flow (Authorization Code + PKCE).
- UI never labels any simulated function as real.
- npm run build produces deployable static assets.

Exit criteria:
- Full payment journey can be initiated and tracked through the UI.
- Real txHash appears on Screen 4 with working BaseScan link.
- Reconciliation screen shows MATCHED for completed payment.
- Simulated functions carry correct labels throughout UI.

Not in scope: CloudFront hosting (Iteration 9).

---

### Iteration 9 — AWS Infrastructure Deployment

Goal: All services deployed on AWS; infrastructure reproducible with cdk deploy
and removable with cdk destroy.

Scope (CDK stacks):
- NetworkStack: VPC (2 AZs), public/private subnets, NAT Gateway, internal
  shared ALB with path-based routing.
- DataStack: Aurora Serverless v2 PostgreSQL cluster, Secrets Manager entries
  for DB credentials, KMS key.
- MessagingStack: EventBridge custom bus, SQS queues per consumer service,
  DLQs configured.
- SecurityStack: Cognito User Pool, App Client, Hosted UI, IAM task roles
  (least privilege per service), WAF, Security Groups.
- ComputeStack: ECS Cluster, task definitions (one per service), Fargate
  services, ECR repositories.
- ApiStack: API Gateway HTTP API, VPC Link, ALB integration, JWT authorizer
  referencing Cognito User Pool (DEC-04).
- FrontendStack: CloudFront distribution, S3 bucket for React SPA assets,
  WAF on CloudFront.
- ObservabilityStack: CloudWatch log groups (per service), CloudWatch dashboard,
  CloudTrail.
- Secrets Manager entry for blockchain wallet private key; not in CDK source.

Exit criteria:
- All six services return 200 on GET /health when deployed.
- cdk deploy completes without manual console steps.
- cdk destroy leaves no billable resources running.
- No secrets appear in CDK source or Git history.
- S3 static assets served via CloudFront.

---

### Iteration 10 — End-to-End Integration

Goal: Complete demonstration scenario verified end-to-end on AWS.

Scope:
- Pre-demo checklist: verify testnet wallet ETH balance (gas), verify testnet
  USDC balance; fail fast with a clear message if either is insufficient.
- Automated E2E test: USD 10,000 → compliance PASS → quote accepted →
  stablecoin rail → real testnet tx confirmed → Visa payout COMPLETED →
  ledger POSTED (all four journals balanced) → reconciliation MATCHED.
- Idempotency test: same Idempotency-Key submitted twice → single payment record.
- Duplicate event test: BlockchainTransactionConfirmed delivered twice →
  single ledger journal.
- Blockchain safety test: simulated post-submission timeout → system reads
  BLOCKCHAIN_TRANSACTION, finds existing SUBMITTED record, does NOT resubmit.
- Ledger balance test: for every payment, SUM(debits) = SUM(credits) per asset.
- Reconciliation mismatch test: injected discrepancy → MISMATCH with
  correct detail.
- Capture observed metrics (actual values only; never invent numbers):
  - Blockchain submission to confirmation (seconds)
  - Full payment creation to RECONCILED (seconds)

Exit criteria: all tests from docs/15-TEST-PLAN.md critical scenario pass.
Real txHash verifiable on https://sepolia.basescan.org.

---

## Appendix: Pre-Implementation Checklist

| Item | Status |
|---|---|
| DEC-01 Circle testnet USDC | APPROVED |
| DEC-02 Shared schema ownership | APPROVED |
| DEC-03 Reconciliation direct read | APPROVED |
| DEC-04 API GW → ALB → Fargate | APPROVED |
| DEC-05 ACCOUNT owned by payment-service | APPROVED |
| DEC-06 LocalStack + Testcontainers | APPROVED |
| Chart of accounts | APPROVED |
| Sync/async interaction boundaries | APPROVED |
| StablecoinFundingCompleted publisher | APPROVED (payment-service, SIMULATED) |
| Cognito model | APPROVED |
| web3j library selection | APPROVED |
| docs/06-BLOCKCHAIN.md corrected | DONE (task C) |
| docs/09-DATA-MODEL.md updated | DONE (task D) |
| docs/10-LEDGER.md updated | DONE (task E) |
| docs/11-EVENT-CATALOG.md updated | DONE (task F) |
| docs/14-SECURITY.md updated | DONE (task G) |
| .env.example populated | DONE (task H) |
| ADR-007 through ADR-012 created | DONE (task I) |
| AWS account and region selected | PENDING — confirm before Iteration 9 |
| Testnet wallet address provisioned | PENDING — required before Iteration 5 |
| Developer environment requirements | PENDING — document in scripts/README |

## Iteration 1 Readiness

**READY.**

All architecture decisions are resolved. All specification contradictions are
corrected. .env.example, ADRs, and documentation are complete.

Iteration 1 (Repository Bootstrap) may begin.
