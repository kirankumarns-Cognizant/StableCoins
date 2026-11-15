# ADR-012: Customer ACCOUNT and LEDGER_ACCOUNT are Separate Concepts

Status: ACCEPTED

## Context

Two distinct financial concepts exist in the system that could both be
described as "accounts":

1. A customer's payment account (the source of funds for a payment).
2. An entry in the double-entry chart of accounts (a ledger account code).

If these are conflated in naming or schema, the financial integrity of the
ledger is compromised and the data model becomes confusing.

## Decision

Maintain two clearly separated tables with distinct names, owned by different
services.

### ACCOUNT (payment-service)

The customer's payment account.

Represents a customer's simulated USD holding from which payments are debited.
Used by payment-service to validate that a sender has an account and a
sufficient balance before a payment is created.

### LEDGER_ACCOUNT (settlement-ledger-service)

A chart-of-accounts entry.

Represents an accounting classification (e.g., "120001 USDC Treasury",
type ASSET, asset USDC). Used as the debit or credit account in LEDGER_ENTRY
rows within a double-entry JOURNAL.

## Ownership

| Table | Owner |
|---|---|
| ACCOUNT | payment-service |
| LEDGER_ACCOUNT | settlement-ledger-service |

No service may treat these tables as interchangeable.

## Rationale

- Naming clarity prevents developers from accidentally using customer account
  IDs as ledger account codes or vice versa.
- Separating ownership ensures that the settlement ledger is not coupled to
  the customer account model and can evolve independently.

## Consequences

- payment-service must never write to LEDGER_ACCOUNT.
- settlement-ledger-service must never write to ACCOUNT.
- Code reviews must check that no service crosses this boundary.
