# Service Catalogue

The six deployables below remain the architecture boundary. `services/platform-security`
is a shared Java library embedded in those applications, not a seventh service. It
reuses Cognito validation, membership, internal-client authorization and read-only
payment ownership checks. Responsibilities listed below include delivery targets;
consult the [delivery plan](21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md) for actual status.


## 1. Payment Service

Responsibilities:

- payment APIs
- validation
- orchestration
- state transitions
- idempotency
- rail selection

## 2. Compliance Quote Service

Responsibilities:

- simulated KYC/KYB
- sanctions simulation
- AML simulation
- wallet screening simulation
- FX quote
- fee quote

## 3. Blockchain Service

Responsibilities:

- wallet interaction
- token balances
- gas estimation
- signing
- transaction submission
- transaction monitoring
- blockchain confirmation

## 4. Settlement Ledger Service

Responsibilities:

- settlement
- balances
- double-entry accounting
- immutable posted entries
- reversals

## 5. Reconciliation Service

Compare:

Payment

versus

Ledger

versus

Blockchain

versus

Visa Simulator.

## 6. Visa Simulator

Simulate:

- prefunding
- payout
- payout status
- failure conditions

## Constraint

Do not split these services further during initial POC implementation
without an accepted ADR.

## Shared libraries used by the six services

platform-security reuses verified identity, membership and read-only ownership
checks. platform-flow provides typed workflow contracts, strict JSON parsing and
read-only owner-record gates. These are ordinary JAR libraries, not additional
microservices. Payment, compliance-quote and blockchain own the new workflow
writes; ledger posting and chain execution continue through existing services.
See [the owner-table inventory](30-PAYMENT-FLOW-INTEGRATION.md).

## Step 8 owner implementation

The same six deployables now cover closure. payment-service owns business state,
closure phases and commands; blockchain-service owns complete actual receipt fees;
settlement-ledger-service owns all postings/final settlement; visa-simulator owns
simulated payout and provider capacity; reconciliation-service owns immutable
reports/details and reads canonical source tables under ADR-008.
platform-flow is a shared Java library of contracts/read-only validation, not a
seventh microservice. See [owner sequence](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
