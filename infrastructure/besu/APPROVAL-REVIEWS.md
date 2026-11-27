# Besu canonical approval reviews

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Status: internal Stage A review/approval integration; private payment activation is still gated.

## What is implemented

RecommendationService.proposeSettlement accepts only payment, preparation and draft
identifiers plus verified server identity. It locks and authorizes the payment
before loading the draft from SettlementQuoteService. It creates a real
routing_recommendation with the existing routing_decision, routing_audit and outbox
workflow. There is no caller-supplied evidence/quote endpoint or model write tool.

RecommendationDocument has an additive settlementQuote field. Existing USDC
records retain their old JSON shape when that field is absent. Private material
contains DEMOUSD and BESU_GAS identities; it cannot also contain a legacy USDC
quote, context or assessment. This is explicit private selection, not seven-signal
ranking or an AI recommendation. USD provider fees remain simulated.

A draft ID is its canonical recommendation ID. Canonical recommendation versions
increase across the payment's reviews; the original draft stays unchanged at its
own version one. settlementReview(canonicalVersion) reconstructs the exact typed
review for audit events and future execution binding. A later execution caller
must validate canonical approval authority, not treat that value as authority.

## Independent approvals and validation

The existing quote-acceptance and approval operations are reused. The existing
controller requires PAYMENT_APPROVER membership derived from the authenticated
identity. Internal AuthorizedCustomer values are server-created, not request data.
Each distinct actor must accept this quote before approving this exact version.
Duplicate votes do not count again. The private policy must require at least two
approvers; larger configured thresholds remain in effect.

Proposal and each NEW acceptance/approval check compliance, immutable intent and
account/beneficiary scope, active account/beneficiary and recipient, captured vs
current customer policy, explicitly allowed provider/network, and exact enabled
registry context including genesis, contract/code, gas identity and context policy.
The original quote is reloaded through its owner and compared in full. Quote
expiry is enforced; the expired original RPC snapshot is not called fresh merely
because quote terms remain valid. Execution still needs a new observation and hold.

Payment row locking, existing version uniqueness and actor/action uniqueness are
reused. Replacement supersedes proposed reviews; unresolved approved/executed work
blocks another review. Idempotent proposal replay returns the captured terms and
expiry, including historical expired terms where owner reads remain authorized.
It cannot turn an expired review into a current one or carry votes to new terms.

Private audit events use Settlement-prefixed event types and eventVersion 1.0.
Their payload contains the typed canonical review, customer/quote policy versions,
actor, state and expiry, under the normal payment/correlation/event envelope.
Legacy Routing event types and version 1 payload remain unchanged. These private
events are evidence, not submission commands.

## Boundaries still closed

TOKEN_WALLET and DEMOUSD can be read as typed engineering material. Payment
creation remains blocked with BESU_PAYMENT_ACCEPTANCE_PENDING even with a permissive
customer policy. No private UI option or proposal endpoint is enabled. The legacy
execution authorization/mark-executed methods explicitly reject private reviews;
the legacy routing agent rejects them rather than reading missing USDC evidence.

V13 and V15 are unchanged. Canonical approval material is held in the existing
immutable JPA recommendation document and checked against the owner draft on each
new decision. No fake payment_flow_preparation or V13 REVIEW/EXECUTION row is
inserted to satisfy legacy foreign keys. This increment does not acquire liquidity,
allocate a nonce, sign, send, post accounting or reconcile a Besu payment.

Next: introduce the real token-aware execution owner claim/schema linking this
canonical recommendation/version, approvals, quote and private preparation, then
connect fresh observation and hold/pin, nonce/submission recovery, receipt-based
release, DEMOUSD/BESU_GAS journals and reconciliation. Keep activation gated until
complete private-chain and separate public regression acceptance succeeds.

## Validation

Run the Java regression from the repository root:

~~~bash
bash services/mvnw -f services/pom.xml test
~~~

SettlementRecommendationServiceTest covers typed JSON/event round-trip, distinct
approvers and replay, exact versions, expiry, changed policy/network/context/
recipient/intent, supersession, unresolved approvals, and private execution/agent
guards. PaymentIntentServiceTest includes a direct private creation gate test.

The current increment's observed results are recorded in docs/36-POC-ACTIVITY-STATUS.md.
Unit tests do not establish actual PostgreSQL concurrency/rollback, full migration
compatibility or private-chain settlement. Existing Docker-host integration tests
and the future full private approval/execution acceptance remain separate gates.

Local SQS publication and the CDK EventBridge rule route private review events
only to the existing durable review notification queue, which has no financial
execution consumer. The infrastructure regression checks every private event
against that queue and excludes routing it to an execution owner.

## Execution-claim persistence checks (26 November 2026)

B01 now includes SettlementExecutionClaimPersistenceIT with the full payment
Flyway migration chain through V16 and 12 PostgreSQL cases for immutable storage,
scoped foreign keys, exact/conflicting concurrent replay, transaction/audit
rollback and document integrity. Approval authorization remains covered separately
by service tests; persistence fixtures are not evidence of independent approval.
The suite compiled locally but has not run against Docker/PostgreSQL.

On a Docker-enabled Codespace, from the repository root:

~~~bash
bash services/mvnw -f services/pom.xml -pl payment-service -am \
  -Dit.test=SettlementExecutionClaimPersistenceIT,SettlementApprovalClaimPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

This command runs unit tests plus the named integration suite in a disposable
Testcontainers database. No wallet, RPC configuration or token funding is needed.
Missing Docker is an error, not a successful skip. Record the actual integration
results before closing B01; B02 and later financial execution remain gated.
The additional SettlementApprovalClaimPersistenceIT covers nine service-boundary
cases with real JPA payment/recommendation/decision repositories and the JDBC
claim/audit DAO in the same PostgreSQL transaction. It checks two actual stored
approvals, outer rollback, failure while writing claim audit, simultaneous exact
and conflicting requests, role/customer rejection and canonical review version
two against an unchanged version-one quote draft. Only the quote/preparation
owner read boundary is substituted using scoped persisted fixture reads; this is
not complete observation/quote-owner or blockchain acceptance. Both IT classes
compile, but require execution in Docker before B01 can close.
The subsequent B02 increment adds payment V17's exact execution projection and
its persistence constraint test (13 claim-store cases total). Claims and their
projection are created atomically by the payment owner. Historical claims with
no projection remain readable as history but cannot authorize a Besu reservation.
See [the reservation boundary](SETTLEMENT-BINDINGS.md#approved-claim-to-execution-reservation---2026-11-26-b02)
for hold/pin semantics and the combined PostgreSQL acceptance command. B01/B02
remain open until their required database evidence actually executes.