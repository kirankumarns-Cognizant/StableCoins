# ADR-008: Reconciliation Service Direct Database Read

Status: ACCEPTED

## Context

The Reconciliation Service must compare records across four legs:
Payment, Ledger, Blockchain, and Visa Simulator.

Two approaches were considered:

Option A: Reconciliation Service queries each service's HTTP API.
Option B: Reconciliation Service queries the shared Aurora database directly.

## Decision

Use Option B.

The Reconciliation Service reads directly from Aurora for all four data legs.

Write access is restricted to RECONCILIATION and RECONCILIATION_DETAIL tables.

## Rationale

- Consistent with ADR-007 (shared schema): direct reads are the intended
  access pattern for cross-service queries within the POC.
- Avoids cascading HTTP call failures during a reconciliation run; all data
  is available in a single database transaction.
- Simpler to implement and test for the POC.
- A four-way join in SQL is more efficient than four sequential HTTP calls
  with intermediate assembly.

## Consequences

- Read coupling: schema changes in PAYMENT, LEDGER_ENTRY, BLOCKCHAIN_TRANSACTION
  or VISA_TRANSACTION require coordination with the Reconciliation Service.
- The Reconciliation Service must not write to any table it does not own.

## Production Evolution

Replace direct reads with event-projected read models:
- Each service projects relevant state into a reconciliation-specific read
  model (e.g., a materialized view or a dedicated reconciliation topic).
- Reconciliation Service subscribes to these projections.
- Removes schema coupling and enables independent deployment.

## Implementation note - step 8 (2026-11-15)

Step 8 implements the accepted direct-read approach. Repeatable-read reports compare exact canonical execution, quote, receipt, immutable journals and optional simulated payout/consumed fiat reservation. The service writes only reconciliation and reconciliation_detail. The payment owner checks a fresh matching fingerprint before its own RECONCILED transition. Source discrepancies remain recorded, never silently repaired.

See [lifecycle implementation and limitations](../docs/31-SETTLEMENT-PAYOUT-RECONCILIATION.md). Accepted decision and status are unchanged.
