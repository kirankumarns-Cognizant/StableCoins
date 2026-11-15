# API Specification

## Current implementation versus target

The endpoint lists below include planned contracts; they are not proof of implemented
controllers. Current protected evidence endpoints are:

- GET `/api/v1/journals/{journalId}`
- GET `/api/v1/payments/{paymentId}/ledger`
- GET `/api/v1/blockchain/transactions/{transactionId}` (application UUID, not txHash)
- GET `/api/v1/blockchain/payments/{paymentId}/transaction`

Customer evidence requires verified access tokens and server-owned operator membership
plus payment ownership. Foreign/missing payments return 404; ownership-store outage
returns structured 503. Blockchain routes remain behind `BLOCKCHAIN_EXECUTION_API_ENABLED`
(default false). POST blockchain commands additionally require the configured internal
client and scope. Manual ledger POST/reversal also require `LEDGER_MANUAL_API_ENABLED`
(default false); these are not model tools or a replacement for the approved lifecycle.
See [security](14-SECURITY.md), [runtime agent APIs](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md)
and [review APIs](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md). Deployment path routing must
include the actual implemented paths; provisioning remains pending.


## Payments

POST /api/v1/payments

GET /api/v1/payments/{paymentId}

GET /api/v1/payments/{paymentId}/status

GET /api/v1/payments/{paymentId}/journey

## Quotes

POST /api/v1/payments/{paymentId}/quotes

POST /api/v1/payments/{paymentId}/quotes/{quoteId}/accept

## Blockchain

GET /api/v1/payments/{paymentId}/blockchain

GET /api/v1/blockchain/transactions/{txHash}

## Reconciliation

GET /api/v1/payments/{paymentId}/reconciliation

## Visa Simulator

POST /visa-simulator/v1/prefund

POST /visa-simulator/v1/payouts

GET /visa-simulator/v1/payouts/{id}

GET /visa-simulator/v1/balances

## Payment Creation Example

POST /api/v1/payments

Headers:

Idempotency-Key: <UUID>

Body:

{
  "senderAccountId": "22222222-2222-2222-2222-222222222222",
  "beneficiaryId": "33333333-3333-3333-3333-333333333333",
  "amount": 10000,
  "currency": "USD",
  "purpose": "SUPPLIER_INVOICE",
  "requestedRail": "AUTO"
}

## Response

{
  "paymentId": "PAY-10001",
  "correlationId": "CORR-10001",
  "status": "COMPLIANCE_PENDING"
}

OpenAPI should eventually become the executable API specification.

## Payment intent extension (ADR-014)

The optional structured intent and server-controlled policy contracts are
specified in [22-PAYMENT-INTENT-CONTRACTS.md](22-PAYMENT-INTENT-CONTRACTS.md).
Existing requests without intent remain compatible. New intent-bearing HTTP
creation and reads fail closed until verified customer authentication is wired;
client-supplied identity headers or policy fields do not grant access.

## Step 7 implemented wallet-flow endpoints

These payment-service routes exist only when PAYMENT_FLOW_ENABLED=true. All require
verified PAYMENT_OPERATOR membership and matching payment/customer ownership.

| Method and path | Request / response |
|---|---|
| POST `/api/v1/payments/{paymentId}/recommendation-preparations` | UUID Idempotency-Key, no body; 202 durable preparation |
| GET `/api/v1/payments/{paymentId}/recommendation-preparations/{id}` | Preparation status and recommendation reference |
| POST `/api/v1/payments/{paymentId}/recommendations/{id}/execution` | UUID Idempotency-Key; `{ "expectedVersion": 1, "quoteId": "<UUID>", "quoteVersion": 1 }`; 202 durable execution claim |
| GET `/api/v1/payments/{paymentId}/execution` | Existing execution status/transaction reference |

Execution additionally needs PAYMENT_EXECUTION_ENABLED=true, the current accepted
quote and distinct approval threshold. An idempotent replay returns the same
operation; a changed binding conflicts. Execution is asynchronous: 202 is not a
transaction hash or confirmation. Current connected outcome is USDC_WALLET only;
bank-outcome preparation returns BANK_FLOW_PENDING_STEP_8. Owner quote/treasury
capture has no public caller-supplied-evidence endpoint. Existing agent, review,
ledger and blockchain APIs are reused; no additional service is introduced.
See [integration and runner](30-PAYMENT-FLOW-INTEGRATION.md).

## Step 8 lifecycle endpoints

The authoritative [lifecycle API table](31-SETTLEMENT-PAYOUT-RECONCILIATION.md#api-and-permissions)
adds ownership-protected payment closure, payout and reconciliation GET views;
internal simulated provider prefund, conclusive payout resolution and new
reconciliation runs; and refresh of incomplete receipt evidence for the original
mined transaction. Internal client/scope and default-off write gates apply.
There is no customer-facing arbitrary payout or ledger mutation added here.
Blockchain fee wei and ledger exactDebit/exactCredit are decimal strings to
preserve full precision in browser and Node consumers.

## Step 9 connected UI read APIs

The existing payment prefix now additionally provides authenticated operator-only
`GET /api/v1/payments/workbench` and
`GET /api/v1/payments/{paymentId}/evidence`. They are read-only customer-scoped
projections, not new command or private Visa endpoints. Payment ownership is
checked before cross-owner reads. Amounts/wei in evidence are exact decimal strings.
See [contracts, limits and security](32-CONNECTED-REACT-WORKSPACE.md).
