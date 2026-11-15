# Visa Simulator

## Objective

Represent the Visa integration boundary without requiring live Visa
commercial connectivity.

The simulator MUST never be presented as a real Visa system.

## Architecture

Payment Orchestrator
→ VisaPaymentAdapter
→ VisaSimulatorAdapter
→ Visa Simulator

Future:

Payment Orchestrator
→ VisaPaymentAdapter
→ Authorized Visa Adapter
→ Authorized Visa service

## Original target interface (not an inventory of implemented HTTP endpoints)

prefund(request)

createPayout(request)

getPayoutStatus(reference)

cancelPayout(reference)

getBalance()

## Original target APIs (some remain future scope)

POST /visa-simulator/v1/prefund

POST /visa-simulator/v1/payouts

GET /visa-simulator/v1/payouts/{id}

POST /visa-simulator/v1/payouts/{id}/cancel

GET /visa-simulator/v1/balances

## States

ACCEPTED
PROCESSING
SENT
COMPLETED
DECLINED
FAILED
TIMEOUT
COMPLIANCE_REVIEW
INSUFFICIENT_PREFUND

## Failure Injection

Support deterministic failure scenarios for automated testing.

Examples:

X-Simulator-Scenario: DECLINED

X-Simulator-Scenario: TIMEOUT

X-Simulator-Scenario: INSUFFICIENT_PREFUND

## Required Data

paymentId
correlationId
visaSimulatorReference
sourceAsset
sourceAmount
destinationCurrency
beneficiaryId
status
createdAt
updatedAt

## Step 4 implementation status

The existing visa-simulator now owns persistent SIMULATED USD/INR provider
liquidity behind `VisaPaymentAdapter` / `VisaSimulatorAdapter`: `getBalance`,
`prefund`, `reserve`, `pin`, `cancel`, `resolve`. Pockets start at zero;
idempotent funding/reservations and pocket row locks prevent double allocation.
Unsigned holds can expire; uncertain PINNED holds require conclusive resolution.
Funding receipts, terminal reservations and transition audits persist in shared
PostgreSQL under a service-owned Flyway history table.

These are internal Java operations. Payout APIs, payout references, failure
scenarios and beneficiary bank credit listed above remain planned; no HTTP write
endpoint or real Visa integration was added. See
[25-TREASURY-AND-EXECUTION-READINESS.md](25-TREASURY-AND-EXECUTION-READINESS.md).

## Step 8 implemented interface

The existing VisaPaymentAdapter remains the business boundary. Its simulator
implements balance/prefund/reserve/pin/cancel/resolution plus createPayout,
finishPayout and resolvePayout. Payout creation is an owner event operation,
not a customer-facing arbitrary payout POST. A bank payment pins the exact provider
fiat before funding and reuses that reservation for one payout/reference.
SUCCEEDED consumes once; DECLINED records FAILED; TIMEOUT records UNKNOWN with
capacity pinned until explicit conclusive internal resolution. Completion waits
for immutable off-ramp and fiat-payable accounting. All modes and bank credit
remain SIMULATED. The existing route enum is not a working Visa-only rail.

Implemented HTTP: POST /visa-simulator/v1/prefund,
POST /visa-simulator/v1/payouts/{id}/resolution and ownership-protected
GET /api/v1/payments/{paymentId}/payout. Write operations require internal service
identity/scope, UUID Idempotency-Key and the default-off operations gate.
See [complete contracts and demonstration](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
