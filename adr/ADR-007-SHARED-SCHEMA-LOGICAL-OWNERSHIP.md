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
