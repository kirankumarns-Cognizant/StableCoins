# ADR-007: Shared Schema with Logical Table Ownership

Status: ACCEPTED

## Context

The POC uses six microservices sharing one Aurora PostgreSQL cluster.
The team must decide between per-service schemas and a single shared schema.

## Decision

Use one Aurora PostgreSQL cluster, one database, and one shared schema for
the POC.

Assign logical table ownership to each service. A service may read from any
table. A service may only INSERT, UPDATE or DELETE rows in its own tables.

Ownership:

| Service | Owned Tables |
|---|---|
| payment-service | CUSTOMER, ACCOUNT, BENEFICIARY, PAYMENT, PAYMENT_STATUS_HISTORY |
| compliance-quote-service | FX_QUOTE, COMPLIANCE_CHECK |
| blockchain-service | WALLET, BLOCKCHAIN_TRANSACTION |
| settlement-ledger-service | SETTLEMENT, LEDGER_ACCOUNT, JOURNAL, LEDGER_ENTRY |
| visa-simulator | VISA_TRANSACTION |
| reconciliation-service | RECONCILIATION, RECONCILIATION_DETAIL |

Each service owns its Flyway migration scripts for its tables.

## Rationale

- Eliminates cross-service HTTP calls for the reconciliation join query
  (see ADR-008).
- Reduces operational complexity for a POC (single connection pool, single
  schema to manage, single Flyway state table per service namespace).
- Acceptable for a POC where the team can enforce the write-ownership rule
  by convention and code review.

## Consequences

- Schema coupling: a table schema change by one service may break another
  service that reads from it. Changes require cross-service awareness.
- Production evolution: migrate to per-service schemas or event-projected
  read models as the system matures.

## Production Evolution

Introduce per-service schemas or event-sourced read models when:
- service teams become independent
- deployment cadences diverge
- data ownership needs stronger enforcement

## Step 7 implementation note (2026-11-14)

Within this accepted shared-schema decision, payment-service owns
payment_flow_preparation/payment_execution; compliance-quote-service owns
compliance_flow_quote/compliance_execution_validation; blockchain-service owns
blockchain_flow_observation. Each has an owner Flyway migration. The shared
platform-flow library performs parameterized read-only joins to verify current
instructions, accepted recommendations, captured quote/treasury bindings and
ledger acknowledgements. It has no financial write API. Each owner still writes
only its own records. Production schema isolation/read-model evolution remains
as described above; live PostgreSQL integration has not yet been executed.

## Implementation note - step 8 (2026-11-15)

Step 8 adds payment-owned payment_closure, blockchain-owned receipt-fee fields, ledger-owned settlement evidence links, Visa-owned visa_transaction/visa_payout_audit, and reconciliation-owned reconciliation/reconciliation_detail. Canonical cross-owner reads are confined to read models; writes remain with each existing owner.

See [lifecycle implementation and limitations](../docs/31-SETTLEMENT-PAYOUT-RECONCILIATION.md). Accepted decision and status are unchanged.

## Step 9 implementation note - 2026-11-15

The connected React workspace reuses existing owner command APIs and adds a
customer-authorized, read-only payment-service evidence projection. Shared-schema
reads follow the accepted ownership boundary; the private Visa endpoint stays
private. UI decimal-string/BigInt display checks preserve per-asset precision,
while the existing ledger, agent authority and approval/execution services remain
authoritative. No architectural decision or accepted status changes. See
[connected UI design](../docs/32-CONNECTED-REACT-WORKSPACE.md).

## Dedicated local bootstrap note (2026-11-15)

Compose explicitly enables owner Flyway baseline-on-migrate at version 0 so V1
runs in the dedicated local shared schema even when another owner created tables
first. Central defaults keep automatic baseline disabled outside that local stack.
No migration checksum repair or financial data reset is performed. This matches
the existing cross-owner integration-test setup and preserves separate owner
history tables. Live PostgreSQL startup remains an acceptance gate.
See [local validation](../docs/33-LOCAL-FIRST-VALIDATION.md); accepted decision unchanged.

## Preparation handoff implementation note - 2026-11-16

The payment-owned recommendation is explicitly flushed through its existing JPA
repository before a JDBC update links it from the payment-owned preparation row.
Both writes retain the same transaction and rollback boundary. No cross-owner
write, schema constraint relaxation or additional service is introduced. Added
PostgreSQL cases cover immediate FK visibility and rollback after flush; local
compilation is verified, live database execution remains pending. See the dated
[UI recovery notes](../docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## Besu preparation implementation note - 2026-11-25

The additive read-only private preparation handoff introduces payment-owned
payment_settlement_preparation (V14) and blockchain-owned
besu_preparation_observation (V7). Payment requests publish through the existing
payment outbox; only the blockchain owner writes observations. Shared canonical
request/current-intent checks and evidence reads perform no cross-owner writes.
This applies the existing accepted logical ownership decision; it does not enable
Besu payment selection, reservations or execution.

## Besu commitment storage implementation note - 2026-11-25

Blockchain service additionally owns besu_treasury_scope,
besu_liquidity_commitment and besu_preparation_availability (V8). Payment service
reads immutable availability and never writes these tables. Execution binding
reads retain the existing accepted cross-owner read boundary. No decision or
ownership-policy change; approval/execution integration remains gated.
