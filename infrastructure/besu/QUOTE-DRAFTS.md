# Besu private quote drafts

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Status: implemented internal quote-draft preparation; not an enabled payment rail.

## Purpose and boundary

`SettlementQuoteService` turns a fresh, owner-authorized private preparation and
reservation-aware capacity snapshot into an immutable DEMOUSD quote draft. Its
inputs are payment/preparation/review identifiers and verified server identity.
There is no HTTP proposal endpoint, model write tool or execution event.

A draft is not a `routing_recommendation`, either approval, an execution claim,
a held balance, a signed transaction or a ledger entry. The existing
`BESU_PAYMENT_ACCEPTANCE_PENDING` and legacy-workflow gates remain in force.
The existing public USDC recommendation/approval/submission path is unchanged.

## Material and validity

`SettlementQuote` carries a typed `SettlementBinding.Review` draft, the exact
original `SettlementAvailability`, and the captured quote-policy terms. It binds
network ID, chain ID, genesis, token contract/code/decimals, native gas identity,
treasury, recipient, instruction, source USD, token quantity and preparation ID.

The USD provider charge is explicitly **simulated** (`simulatedFeeUsd`). It is
bounded by the confirmed instruction's USD fee cap. It is not an ETH/USD rate
or an assertion that gas was paid. Native reserve is the observed execution-fee
estimate multiplied by the configured reserve multiplier and rounded UP to a
native smallest unit. Zero observed fees remain zero; no fee is invented.
Quoted capacity must cover both token quantity and this buffered native reserve.

The original RPC snapshot must be current when the draft is created. Quote
validity is separately bounded to at most 900 seconds and the customer's deadline.
A quote may still have valid terms after its original 15-second RPC snapshot
expires. It does not call that snapshot fresh. Before execution, a future
authorized owner must acquire a NEW live observation, recheck capacity and fee
coverage, and acquire the exact execution's hold/pin under the treasury lock.
If the new estimate exceeds the approved reserve, do not silently increase it:
prepare and approve new terms.

This is Stage A explicit private selection material, not a seven-signal ranking,
AI recommendation or automatic cross-network selection.

## Server configuration

There is no default provider, fee or quote policy. Engineering configuration
must explicitly supply `app.besu.quote.terms`, for example:

~~~yaml
app:
  besu:
    quote:
      terms:
        version: besu-quote-v1
        provider-id: SIMULATED-BESU-DEMO
        simulated-fee-usd: 0.00
        validity-seconds: 900
        native-reserve-multiplier: 1.25
~~~

This example is a simulated private-demo pricing policy, not a market quote.
The customer must also be permitted for the selected network in the existing
server-owned selection configuration, and the registry must enable new work on
the exact same persisted context. These settings cannot activate Besu payments.

## Persistence and replay

Payment-owned V15 creates append-only `payment_settlement_quote` records.
A composite foreign key binds each quote to the exact V14 request, instruction,
payment and correlation. Row metadata and canonical SHA-256 document fingerprint
are verified on load. Payment locking serializes creation with owner changes.

The service authorizes and locks the payment before consulting a quote ID.
The same review ID and exact preparation return the original draft, including
after expiry or quote configuration changes. Replay does not generate another
quote ID, extend expiry, poll/refresh RPC or acquire capacity. Key reuse for a
different preparation or instruction fails. History reads cannot reprice drafts.

V13 is intentionally unchanged: its REVIEW records reference actual
`routing_recommendation` and `payment_flow_preparation` rows. Creating a quote draft
must not invent legacy USDC recommendations/preparations to satisfy those foreign
keys. The next approval integration must establish the real token-aware owner
records, exact two-approver decisions, policy checks and versioned event bindings
before promoting draft material to an approved review/execution claim.

## Validation

Focused and full Java unit regressions are recorded in the current activity
handover. PostgreSQL acceptance is separate; compilation is not execution:

~~~bash
bash services/mvnw -f services/pom.xml -pl payment-service -am \
  -Dit.test=SettlementPreparationPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

The persistence fixture now includes V13/V14/V15 and tests draft JSONB
round-trip/replay, update/delete rejection, missing preparation foreign keys and
transaction rollback. It uses disposable minimal owner tables, not a full
application migration chain, live Besu payment or complete approval acceptance.
Docker is required.

The next review increment is now implemented internally: see
[canonical independent approval reviews](APPROVAL-REVIEWS.md). Draft preparation
itself still grants no approval authority. Next execution work must bind canonical
approvals to real execution claims, hold/pin, nonce ownership, signed submission
recovery, receipt accounting and reconciliation.
