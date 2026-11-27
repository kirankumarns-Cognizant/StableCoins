# Network-bound settlement material

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


ADR-016 implementation increments, 2026-11-25. Sources remain local/unpublished.

DEMOUSD is this project's private demonstration token name. ERC-20 is the token
interface standard. DEMOUSD is not Circle USDC, backed USD, a redeemable deposit
or an industry-standard stablecoin. The private adapter accepts only its pinned
DEMOUSD deployment.

## Shared contracts and append-only storage

Shared SettlementBinding Instruction/Review/Execution records carry immutable
customer/payment/correlation and intent versions; network, chain ID, genesis and
classification; token contract/code/symbol/decimals; gas asset, treasury, fee model
and policy version; recipient; separate USD and token quantities; outcome, USD
fee cap and delivery deadline. A review additionally binds provider, preparation,
recommendation/version, quote/version, quoted USD fee, native gas reserve and
evidence lifetime. Execution retains the exact complete review.

USDC_WALLET rejects private DEMOUSD. TOKEN_WALLET in the shared contract accepts
the explicitly named asset, without implying economic equivalence. The current
application intent enum has not yet enabled that outcome. Native gas quantities
remain separate from USD fees; no BESU_GAS dollar valuation is invented.

The strict codec rejects unknown fields, duplicate keys, trailing JSON, numeric
enums, scalar coercions and null material. Canonical serialization tolerates
JSONB key order and insignificant decimal zeros. Its fingerprint detects changed
material; it is not a signature or approval.

Payment-owned V13 creates append-only snapshots. Parent records bind the same
payment/correlation; unique keys and exact replay checks reject replacement terms.
The DAO requires an active owner transaction, locks the payment, checks its
customer/correlation, exact parent material and stored fingerprints. Review and
execution callers must still verify the complete canonical owner records and
authenticated approval authority in that transaction. Foreign keys alone do not
establish those checks.

## Owner intent integration

PaymentIntentRequest now has an optional settlement value containing only:
networkId, tokenContract and tokenAmount. It accepts no RPC endpoint, chain
parameters, signing paths, credentials or account mappings. New selection values
participate in payment-creation idempotency; changing network, token or quantity
conflicts with the same key. Address case and insignificant decimal zeros are
canonicalized. Omitting selection preserves the exact historical fingerprint and
omits the new null fields from persisted/returned intent JSON.

ExplicitSettlementService resolves the selected network through the server-owned
registry and a customer-specific network allowlist. Both configuration collections
are empty by default. Missing, disabled, unauthorized or substituted identities
fail closed, with no default-network substitution. The context includes pinned
genesis, code hash, treasury and fee policy. These are configured identities;
capturing an intent is not fresh RPC readiness or chain verification.

For the currently supported public USDC intent, the accepted quantity must retain
the existing notional one-to-one USD convention, six decimals, USDC_WALLET outcome
and no alternative outcome. That convention is not applied to DEMOUSD. Private
selection is hard-blocked by BESU_PAYMENT_ACCEPTANCE_PENDING even if a registry
entry is configured enabled. No configuration flag bypasses that acceptance gate.

The canonical intent stores the resolved context, token quantity and recipient
from creation time. PaymentService flushes the payment INSERT, then captures the
immutable Instruction through the DAO before writing history/outbox, in the same
existing payment creation transaction. A failure propagates for transaction
rollback. No old payment is backfilled from today's configuration.

## Execution boundary

All explicitly selected instructions are currently rejected by the legacy
preparation, proposal, approval revalidation and new execution paths with
SETTLEMENT_WORKFLOW_ACCEPTANCE_PENDING. The legacy worker also rejects them before
simulated USD debit or submission publication. Existing non-selected USDC payments
continue through their existing workflow.

This is an owner intent integration increment, not completed network-bound
review/approval/execution. In particular, captureReview and captureExecution are
not yet called by the authorized owner workflow. Creating an explicit engineering
instruction does not produce an executable payment. No UI selection or operational
configuration recipe is exposed before the full path passes.

Monitoring of already pinned submissions is not redirected to another network.
The existing public signer, transaction submission, fee accounting and event
versions were not changed.

## Instruction-bound RPC observations

BesuRuntime now accepts the exact SettlementBinding.Instruction for a read-only
observation. Before RPC, it checks the complete configured context, customer
deadline and accepted recipient/quantity. The existing adapter verifies network,
genesis, validators, token code/metadata and recent head during observation.

The shared SettlementObservation retains that complete instruction, observed
block, raw token/native totals, estimated and buffered gas, native gas price,
execution-only fee estimate, observed time and expiry. Expiry is at most 15
seconds and never beyond the customer deadline. requireFor rejects a different
instruction, future evidence or evidence at/after expiry. The strict codec
supports this fourth document type with the same canonical/strict JSON rules.

These are bounded-window RPC calls against latest state, not an atomic snapshot
at one block. The observed block identifies the head checked at the start.
Raw totals do not subtract or acquire reservations and cannot be treated as
spendable balances. The contract rejects Base's additional-L1-fee model rather
than presenting its execution fee alone as complete public fee evidence.

The runtime remains disabled for new work. The read-only owner preparation
request/consumer and persistence described below now consume this observation;
quote, review and approval workflows do not yet consume it.
The opt-in BesuReadinessIT exercises the bound observation when explicitly run
against a real private chain; compilation alone does not establish that result.

## Remaining integration work

1. Integrate the scoped commitment store with exact authorized execution claims;
   reservation-aware observation storage is implemented in the latest increment below.
2. Build token-aware provider evidence and versioned quotes, then capture an exact
   Review with the canonical instruction in the same owner transaction.
3. Bind existing quote acceptance and independent approvals to that material.
   Capture Execution only with the exact authorized review and execution claim.
4. Carry complete identity through versioned events and strict owner read models.
   DEMOUSD must never be placed in legacy transferUsdc/ETH fields.
5. Integrate chain/asset reservations, durable nonce/hash claims, exact receipt
   validation, DEMOUSD/BESU_GAS accounts and journals, closure and reconciliation.
6. Run complete private transfer acceptance and separate public regression before
   enabling explicit TOKEN_WALLET selection. Policy routing follows later.

## Validation

Unit compilation/regression, without Docker or funds:

~~~bash
cd /workspaces/StableCoins
bash services/mvnw -f services/pom.xml -pl payment-service -am test
~~~

Explicit PostgreSQL binding acceptance on a Docker host:

~~~bash
cd /workspaces/StableCoins
bash services/mvnw -f services/pom.xml -pl payment-service -am \
  -Dit.test=SettlementBindingPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

These commands require a checkout containing the unpublished increments. The
PostgreSQL test creates disposable minimal prior owner tables and applies V13;
it is not full application migration-chain or Spring payment-creation acceptance.
A real owner-transaction rollback test remains required in that environment.
The unit capture-order test is not claimed as database rollback proof.

For chain-only read-only Java acceptance, use [RUNTIME.md](RUNTIME.md). Docker,
PostgreSQL, live Besu, full private payment and public-testnet transaction
acceptance remain separate gates.

## Read-only owner preparation handoff - 2026-11-25

SettlementPreparation.Request now identifies an immutable owner instruction and
one request UUID with a bounded 120-second processing window. The payment-owned
service authorizes the customer, locks the payment, verifies the captured binding
against the current account, beneficiary and confirmed intent, and writes V14's
append-only request plus the existing transactional outbox event. It accepts no
caller-supplied context, wallet, amount or RPC URL. Exact key replay returns the
original request and does not refresh evidence or issue another command.

SettlementObservationRequested version 1.0 is routed only to the existing
blockchain treasury queue, locally and in the CDK definition. The dedicated Besu
observer verifies envelope identity, strict payload, canonical owner request and
current instruction before RPC, then checks the owner material again before
persisting blockchain-owned V7 evidence. The first immutable result wins;
redelivery does not refresh it or call signing/submission. Existing USDC request,
quote, execution and receipt commands retain their original contracts/routes.

The observation-only HTTP controller requires a verified PAYMENT_OPERATOR and
both app.flow.enabled and app.besu.observation-api-enabled. The latter has no
enabled default or published demo configuration. It cannot create a private
instruction or bypass BESU_PAYMENT_ACCEPTANCE_PENDING. TOKEN_WALLET remains
unavailable in the application intent flow. The new consumer also requires the
separately configured Besu runtime; an unavailable runtime fails closed instead
of invoking public USDC readiness.

The view reports PENDING, OBSERVED_NOT_READY or EXPIRED. Its readiness gate is
always RESERVATION_INTEGRATION_PENDING. At that initial handoff increment no available-balance calculation was exposed.
The scoped-commitment increment below adds capacity evidence, while reservation
acquisition in the payment workflow, recommendations and approvals remain gated. These bounded-window raw balances are not yet suitable for quotes.
Historical evidence is not refreshed by GET or by replay of its request UUID.

Next: implement chain/treasury/token-scoped reservation commitments and their
freshness/availability semantics, then feed validated preparation into token-aware
quotes and exact owner reviews/approvals. All execution/accounting/reconciliation
and private/public acceptance gates remain closed.

Additional PostgreSQL acceptance (Docker host; engineering fixtures only):

~~~bash
bash services/mvnw -f services/pom.xml -pl payment-service -am \
  -Dit.test=SettlementPreparationPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

This suite uses minimal owner tables plus V13/V14 to check immutable requests,
duplicate concurrency, recipient changes and real JDBC rollback when outbox save
fails. It is not full Flyway, JPA-outbox persistence or private-chain acceptance.

## Scoped commitments and availability - 2026-11-25

The Besu observer now takes a blockchain-owned treasury scope lock before RPC and
persists a separate immutable SettlementAvailability alongside the original V7
observation. V8 adds besu_treasury_scope, besu_liquidity_commitment and
besu_preparation_availability. Existing V7 documents and checksums are unchanged.
Old observations without availability are not retrospectively refreshed.

The scope includes network ID, chain ID, pinned genesis and treasury. Token
commitments additionally select the exact contract/code/decimals. Native gas
commitments include every token under that same treasury scope. Available amounts
are raw totals minus relevant commitments, floored at zero. Capacity sufficiency
is an observation, not a quote, acquired reservation or permission to execute.

The package-private BesuLiquidityStore implements transactional hold, pin and
unsigned release. It is not an endpoint or command handler. New holds require
an exact persisted owner Execution binding, a fresh matching RPC observation,
fresh review, sufficient shared capacity and a native reserve covering the
observed fee estimate. The owner binding by itself is NOT approval authority;
these methods are not yet invoked by payment execution. The future authorized
caller must verify canonical quote, two independent approvals and its execution
claim in the existing workflow before calling them.

All store operations require an explicit READ_COMMITTED transaction and serialize
on a chain/treasury row. Clock checks happen after lock acquisition. Duplicate
execution IDs preserve the original lease rather than extending it; one payment
cannot acquire a different commitment. Unsigned HELD commitments stop reducing
capacity at their exact lease expiry and cannot subsequently be pinned. PINNED
commitments count even after expiry and cannot be released by the unsigned path.
There is deliberately no receipt-based release yet: unresolved/signed work stays
conservatively committed until durable nonce/receipt recovery is implemented.
This may understate capacity after an actual spend; it cannot claim recovered
funds from elapsed quote time.

The operator observation view verifies the availability fingerprint and its exact
original request/evidence. It remains OBSERVED_NOT_READY (or EXPIRED), with
RESERVATION_CLAIM_INTEGRATION_PENDING when availability exists. An older raw-only
observation retains RESERVATION_INTEGRATION_PENDING. No UI/network activation is
added. Ordinary expiry, GET and duplicate commands never refresh saved evidence.

Implementation assumptions: one configured Besu network identity for this private
chain; dedicated service-managed treasury; all future private commitment writers
use this same lock/store. Alias identities for one physical chain, external wallet
spends and a future submission worker require explicit validation; a capacity
snapshot alone never guarantees a later successful transfer. Submission must
revalidate balances/fees and persist its exact nonce/hash before broadcast.

Next: token-aware quotes and canonical reviews, exact independent approval and
execution-claim integration of hold/pin, durable nonce/submission ownership,
DEMOUSD/BESU_GAS journals, receipt-based release and owner reconciliation.
Full private acceptance and separate public regression remain activation gates.

PostgreSQL acceptance on a Docker host (engineering fixtures, not a payment):

~~~bash
bash services/mvnw -f services/pom.xml -pl blockchain-service -am \
  -Dit.test=BesuLiquidityPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

The four-test suite applies V7/V8 to disposable tables and checks competing gas
holds across contracts, rollback, immutable/pinned records and JSONB availability.
It does not establish full migration-chain, owner approval or live Besu acceptance.

## Private quote-draft preparation - 2026-11-25

The additive quote draft and V15 owner persistence are described in
[quote drafts](QUOTE-DRAFTS.md). These drafts preserve the original RPC evidence
and separate simulated USD terms from native gas reserves. They are not approved
V13 review records and do not call hold/pin or enable Besu execution.

## Approved claim to execution reservation - 2026-11-26 (B02)

Payment migration V17 adds an exact execution document/fingerprint projection to
the immutable V16 claim. The payment owner writes it in the same transaction as
claim and audit capture. A CHECK binds the projection to the execution inside
the owner claim. Existing claims retain null projections and fail closed; no
backfill, extended approval or fabricated legacy payment_execution/binding row
is created. Apply payment-owner migrations before exercising this boundary.

SettlementExecutionReads consumes this projection read-only under ADR-007,
checking exact execution, instruction/customer/payment/correlation, canonical
review/version, quote/version and preparation identity. Caller-provided terms,
a V13 legacy execution binding or a preparation snapshot alone cannot authorize
a private reservation.

BesuExecutionReservationService is an internal Spring transaction boundary
available only with app.besu.enabled. It has no HTTP endpoint, scheduler or queue
listener. It does not change the closed new-work registry or invoke signing,
nonce allocation, submission, financial accounting or reconciliation. B08 owns
event/runtime wiring after the remaining acceptance gates.

Within explicit READ_COMMITTED isolation, the service:

1. Verifies the durable owner claim, locks the chain/genesis/treasury scope and
   verifies ownership again after any lock wait.
2. Returns an exact already-pinned reservation and its original evidence on
   replay, including after expiry; it performs no RPC and renews no lease.
3. For new work, checks current review and owner instruction, then obtains a new
   instruction-bound Besu observation. Runtime validates configured chain/genesis,
   token bytecode/metadata, participant permissions and recent chain readiness.
4. Rejects a cached preparation observation, stale result or owner change while
   RPC was in flight. It verifies capacity after subtracting token-specific holds
   and every token's gas commitments in the shared treasury.
5. Requires the approved native-gas reserve to cover the fresh execution-only fee
   estimate, including the gas-limit buffer. Fee growth requires a new approved
   review; it never silently increases the reserve.
6. Writes HELD, changes it to PINNED and records immutable execution-time
   observation evidence (blockchain V9) in one transaction. Failure rolls back all
   three operations. The returned status is PINNED_NOT_SUBMITTED and its gate is
   DURABLE_SUBMISSION_INTEGRATION_PENDING.

An incomplete HELD/released/pinned-without-evidence state requires recovery;
ordinary retries cannot adopt it as a fresh reservation. PINNED capacity remains
conservatively reserved after expiry until B03/B04 recovery can resolve signing
and receipt evidence. An old result is historical evidence, never a renewed
capability to sign. B03 must revalidate immediately before signing and persist
exact nonce/payload ownership before any broadcast.

Acceptance commands on a Docker host (disposable PostgreSQL, no chain or wallets):

~~~bash
bash services/mvnw -f services/pom.xml -pl payment-service,blockchain-service -am \
  -Dit.test=SettlementExecutionClaimPersistenceIT,SettlementApprovalClaimPersistenceIT,BesuLiquidityPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

The owner suites use the full payment migration chain through V17. The blockchain
suite uses V7/V8/V9 with minimal persisted owner-projection fixtures; owner
instruction reads and RPC are substituted at that boundary. It tests real
PostgreSQL scope serialization, atomic hold/pin/evidence rollback, exact replay,
conflicting capacity and immutable evidence. It does not prove full cross-owner
authorization, live Besu execution or public-path acceptance. See B01/B02/B09/B10
in the central backlog for current acceptance status.
## Durable private signing and single-attempt submission - 2026-11-26 (B03)

BesuSubmissionService is an internal owner boundary under the existing private
capability flag. It has no HTTP endpoint, event listener, scheduler or UI action.
The registry remains closed to new customer work. It uses the dedicated Besu
runtime, never the default public adapter. This increment adds no chain startup,
contract deployment, automatic routing, issuer mint/burn or application activation.

The caller must already have the exact payment-owner claim and a committed B02
PINNED reservation. Each database phase uses a new READ_COMMITTED transaction and
the chain/genesis/treasury lock. Ambient transactions are suspended by Spring;
direct invocation inside a transaction is rejected so broadcast cannot occur
before its durable claim commits.

1. Read the exact immutable owner claim and pinned observation. Existing signed
   execution data is replayed without refreshing its original approval/lease.
2. For a new signature, reject another unresolved signed operation in this treasury
   scope. Recheck current owner material, a fresh instruction-bound observation,
   approved gas reserve and all active token/shared-gas commitments, including this
   operation's own hold exactly once. Obtain the pending nonce from the selected
   adapter and sign with its current buffered gas limit and price.
3. Independently decode the signature and verify chain, sender, contract, zero
   native value, nonce, gas, calldata recipient and exact quantity. Recheck freshness
   after signing. Commit the signed bytes, expected hash, immutable execution and
   SIGNED audit together in blockchain migration V10 storage. Never store keys.
4. In a second transaction, revalidate readiness/capacity. An existing signature
   cannot be silently repriced or replaced; insufficient signed gas parameters
   require review. Atomically change SIGNED to ATTEMPTED with its audit, and commit.
5. Only the invocation owning that transition makes one broadcast outside any
   transaction. A matching RPC acknowledgement records SUBMITTED. A timeout,
   rejection or differing hash records UNKNOWN without exposing provider messages.
6. Redelivery after ATTEMPTED/UNKNOWN/SUBMITTED performs read-only recovery using
   the original signed hash. The adapter checks returned chain, sender, contract,
   nonce, value, gas and calldata against the saved payload. A matching pending or
   mined transaction proves submission, not success/finality. Absence or read
   failure retains uncertainty; an acknowledged state is never downgraded.

A crash after signing but before ATTEMPTED may resume the SAME saved signature
only while review and fresh readiness checks permit. A crash after ATTEMPTED,
including before the actual RPC send, never authorizes automatic rebroadcast.
Such a transaction may remain UNKNOWN and require investigation. This conservative
policy prefers retained funds to duplicate or alternate-chain transfers. No nonce
replacement, new signature, fee bump or alternate-network fallback is implemented.

V10 enforces unique scope/nonce and transaction hash, immutable signed material,
allowed monotonic submission transitions and append-only status audit. Signed
transaction bytes are broadcast-capable data: restrict database/backups and never
include them in logs, API results or evidence exports. They are not private keys.
Public blockchain_transaction tables and the public submission workflow are unchanged.

B04 still owns exact receipt/Transfer evidence against the approved execution,
final outcome transitions, outbox/payment handoff and conclusive resolution of
commitments. Until then, signed commitments remain PINNED and conservatively block
another submission from that treasury even if an RPC acknowledgement exists.
B05/B06 accounting/reconciliation and B08 application wiring remain separate gates.

The existing combined PostgreSQL command above now includes V10 and seven added
persistence cases: payload round-trip/immutability, signature+audit rollback,
attempt+audit rollback, unique nonce/treasury exclusion, concurrent attempt claims,
UNKNOWN transition/audit guards, and commit-before-broadcast with restart recovery.
These use disposable signing fixtures and substituted RPC/owner instruction reads;
they are not live chain or full cross-owner acceptance. Track executed evidence in
B01-B04/B09/B10 in the central backlog.

## Exact private receipt and capacity resolution - 2026-11-26 (B04)

The internal BesuReceiptService accepts an exact persisted execution claim and
B02 pinned evidence. It reads the original signed submission under the treasury
lock, performs read-only receipt RPC outside a database transaction, then locks
and rechecks the same owner submission before recording a conclusive result.
It does not renew approval, sign, broadcast, replace a nonce or switch chains.
No receipt is sought for an unattempted SIGNED transaction. ATTEMPTED and UNKNOWN
can resolve directly from conclusive evidence, including a lost acknowledgement.

The adapter verifies the full saved signed transaction fields in the same receipt
read, selected chain/genesis/code/validators, canonical receipt block and a second
canonical block check after transaction/log/fee reads. Successful execution requires
exactly one matching token Transfer log for the approved sender, recipient and
amount. A reverted execution must have an empty log list. Legacy effective gas
price must equal the saved signed price, gas used cannot exceed its limit, and
fees must be complete execution-only evidence. Zero gas price, if explicitly
configured, remains zero; no synthetic network expense is introduced.

The finality policy is QBFT_COMMITTED. This dedicated four-validator QBFT path
uses its trusted, identity-pinned node's canonical committed block; it does not
reuse Base confirmation rules or independently verify QBFT signatures. A halted
head does not prevent inspection of a previously committed receipt. See
[Besu consensus documentation](https://github.com/besu-eth/besu-docs/blob/main/docs/private-networks/concepts/poa.md)
for QBFT finality and its validator/trust assumptions. This policy is not portable
to a different consensus configuration.

Blockchain migration V11 adds besu_final_receipt. Its composite foreign key binds
the immutable receipt to the exact stored execution/scope/hash. Gas arithmetic,
fee/limit agreement, immutable rows, allowed terminal transitions and deferred
receipt/status/audit consistency are database constrained. Receipt insertion,
CONFIRMED or REVERTED submission, terminal audit and capacity resolution share
one transaction. Audit failure rolls the entire resolution back.

Historical commitment rows remain PINNED and unchanged for recovery/provenance;
an immutable final receipt is their conclusive resolution marker. Active capacity
queries exclude resolved executions. Before excluding them, preparation/hold/signing
requires an observation whose starting block is at least the latest resolved block
in the same treasury scope. This prevents a lagging or pre-debit balance from making
spent tokens/gas available again. Both success and revert consume the original
nonce; revert evidence retains actual gas cost while proving no token transfer.
Accounting of that outcome is separate B05/B06 work. Uncertain or invalid receipts
never release the treasury submission fence or its pinned capacity.

Terminal polling replays the immutable receipt without RPC; terminal submission
replay never broadcasts again. The worker's result remains explicitly gated by
PAYMENT_ACCOUNTING_INTEGRATION_PENDING. No payment-owner table, ledger, public
runtime, scheduler, API, UI or customer selection is changed. B08 must wire the
owner command/outbox handoff after asset contracts are ready; B04 remains open for
that integration and executed database/live-chain acceptance. See B04/B05/B06/B08
in the central backlog rather than treating this internal receipt milestone as
complete payment settlement or reconciliation.

BesuLiquidityPersistenceIT now includes V11 and 22 PostgreSQL scenarios, including
receipt/status/audit atomic rollback, immutable revert/fee evidence, receipt-only
commit rejection, capacity resolution with stale-balance rejection and concurrent
receipt workers. These sources compiled but did not execute on this Windows host;
Docker and psql are unavailable. The existing combined Codespaces verification
command above selects the expanded suite. No runtime migration was applied here.


## Internal private accounting commands - 2026-11-26 (B05)

BesuAccountingService is a ledger-owned internal boundary, disabled unless
app.besu.accounting.enabled=true. This is not an activation instruction: no
endpoint, queue handler, scheduler, Compose override or customer option invokes
it. B08 must connect the authorized simulated-funding owner and durable private
workflow; an approved claim alone must not automatically initiate funding.
The service verifies the immutable payment-owner claim before every command.
It never signs, broadcasts, performs RPC or writes another owner's tables.

The accepted mapping version is adr016-control-v1, qualified in evidence by the
existing USD mapping version. Every private posting retains the complete canonical
execution and fingerprint, including chain ID, genesis, contract/code hash, treasury,
recipient, exact quote/review and quantities. Asset symbols are display/accounting
codes, not sufficient identifiers for inventory or reconciliation. B06 must group
private quantities by that qualified context, never combine different genesis or
contract identities because their symbols match.

| Journal | Debit | Credit | Quantity/evidence |
|---|---|---|---|
| FUNDING | Existing simulated USD funding control | Existing corporate USD control | Approved source USD; SIMULATED |
| CONVERSION_USD | Existing simulated USD conversion control | Existing USD funding control | Approved source USD; SIMULATED |
| CONVERSION_DEMOUSD | 150001 DEMOUSD treasury control | 250001 DEMOUSD allocation control | Approved token quantity; SIMULATED allocation |
| BLOCKCHAIN_SUBMISSION | 150002 DEMOUSD in transit | 150001 DEMOUSD treasury control | Same approved token quantity; saved attempted/submitted/uncertain hash |
| BLOCKCHAIN_CONFIRMATION | 150003 DEMOUSD delivered control | 150002 DEMOUSD in transit | Exact verified successful receipt only |
| NETWORK_FEE | 550001 BESU_GAS expense | 150004 BESU_GAS clearing | Actual complete receipt gasUsed x gasPrice / 10^18 |

Allocation is a CONTROL-book record against prefunded inventory. It neither mints
DEMOUSD nor asserts real USD backing, converts fiat, debits the customer account or
sends tokens. The quoted provider fee is retained in the review; it is not inferred
as actual USD expense. The future payment-owner funding handoff must preserve
those distinctions and its own debit/idempotency boundary (B08).

Ledger migration V9 adds six accounts, private asset constraints and immutable
besu_ledger_receipt. Existing USD/USDC/ETH/INR account mappings and posted financial
rows are preserved. Account existence, type, asset and active status are checked.
New private-asset journals require payment-scoped, network-qualified provenance;
DEMOUSD supports six decimal places and BESU_GAS eighteen. No mixed-asset netting.
Legacy public PostingEvidence JSON omits the new null privateSettlement field,
preserving existing replay fingerprint input; its six-argument constructor remains.

Funding/allocation's three journals commit atomically. Submitted postings require
all three predecessors and the exact saved blockchain-owner execution/hash. Final
accounting reads only the owner's immutable final receipt, matching scope/hash,
terminal success/revert, QBFT_COMMITTED finality, gas limit and signed gas price.
It does not read signed payload bytes or trust caller-supplied receipt amounts.
Payment-scoped ledger locks serialize posting, reversal and replay.

Confirmation, any actual gas journal and the receipt acknowledgement commit in one
ledger transaction. A complete zero fee writes an acknowledgement without creating
a zero-value or fabricated positive expense. A revert may expense actual gas but
creates no successful token-delivery journal; it never silently reverses prior
accounting. In-flight exception resolution/compensation and terminal payment
reconciliation still require B06/B08. Database constraints bind acknowledgements
to matching posted receipt journals and prevent acknowledgement update/deletion.
A retry replays the same evidence and journal IDs; conflicting evidence fails closed.

BesuAccountingPersistenceIT exercises real ledger migrations, JPA/JDBC, immutable
receipt acknowledgement, zero-fee/revert evidence, allocation/receipt rollback and
concurrent replay. Its payment/chain owner reads are mocked; it does not establish
cross-owner authority or live Besu acceptance. Docker/psql are unavailable on this
Windows host, so the suite is compiled but unexecuted. Run on a disposable Docker
host in Codespaces, inspect the report and require zero skipped database cases:

```bash
bash services/mvnw -f services/pom.xml -pl settlement-ledger-service -am \
  -Dit.test=LedgerServiceIT,BesuAccountingPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

B05 remains IN PROGRESS until database and integrated acceptance. B06/B08-B10
remain the authoritative reconciliation, runtime and private/public acceptance gates.
No application migration or private/public transaction was executed by this change.


## Private chain/ledger comparison boundary (B06)

BesuClosureEvidence is a shared ADR-007 direct-database reader/comparator. It reads
only selected columns from the immutable payment claim, private submission/receipt,
ledger receipt acknowledgement and journal/account projections, plus payment terms
and payout existence. Signed payloads and secrets are excluded. It verifies full
execution identity and exact amounts, account roles/assets/types, posting mode,
reference, provenance, receipt fee arithmetic/finality and acknowledgement journal
IDs. Historical account active/inactive status is not treated as posting evidence;
asset/type integrity is checked. Reverted transfers always require review even if
actual gas accounting is correct. Private TOKEN_WALLET settlement rejects payout
records and unexpected/reversal journals. Authorized compensation remains B06/B08.

BesuReconciliationService.run(reportId, execution) is internal and conditional on
app.besu.reconciliation.enabled=true, absent by default. There is no HTTP endpoint,
SQS listener, Compose switch or caller/model execution tool. Invoke through the
Spring transactional proxy in a future trusted B08 handler. REPEATABLE_READ captures
one database snapshot, verifies claim authority and writes only reconciliation and
reconciliation_detail. A delivery retry uses the same immutable report ID; a new
observation uses a new ID. Serialization conflicts retry the same key, never a chain
submission. Canonical source hashing normalizes JSONB keys/numeric scales and
relational row ordering; altered source evidence produces a different fingerprint.

Reports carry kind BESU_CHAIN_LEDGER_V1 and closure gate
PAYMENT_OWNER_INTEGRATION_PENDING. MATCHED is a chain/ledger comparison result,
not permission to complete the payment. Reconciliation V2 guards private report and
finding bindings/immutability without changing legacy public reports. B08 must
integrate authorized customer-account debit/simulated funding, owner settlement,
qualified report selection and fresh source-fingerprint revalidation before owner
RECONCILED transitions. Do not enable customer Besu selection from this milestone.

Seven PostgreSQL report tests use real persistence with stubbed owner capture/claim
reads. They compile but have not executed on this Docker/psql-free Windows host.
Run on disposable Codespaces Docker infrastructure and require no skipped cases:

```bash
bash services/mvnw -f services/pom.xml -pl reconciliation-service -am \
  -Dit.test=ReconciliationPersistenceIT,BesuReconciliationPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

This checks report migrations/rollback/immutability/concurrent replay; it does not
prove real cross-owner source reads or private-chain execution. Those acceptance
conditions remain B06/B09/B10 in the central backlog. No runtime migration or
private/public chain operation was performed during this implementation increment.

Unit-level cross-component compatibility also feeds actual private ledger posting
output and JSON-round-tripped evidence into the shared comparator for successful/
reverted and positive/zero-fee combinations. This validates the ledger/comparison
payload agreement; mocked repositories do not establish PostgreSQL or chain acceptance.


## Payment-owned simulated funding boundary (B08)

SettlementFundingService.fund(execution, verifiedCustomer) is an internal Spring
transactional command, conditional on app.besu.funding.enabled=true (absent by
default). There is no endpoint, listener, scheduler or Compose activation. This
increment implements owner authorization/debit and durable handoff production;
it does not yet connect the outbox to private ledger/submission workers.

The owner locks payment then customer account, verifies the exact immutable claim,
current operator and independent accepted approval votes, current recommendation,
policy, beneficiary and compliance state. The original private review cannot be
replaced by another execution. The existing new-review/approval guard continues to
reject claimed payments; the new claimed-work validation verifies the canonical
execution instead. Immediately before funding, read-only blockchain evidence must
show the exact PINNED reservation and a still-current execution-time observation,
with no signed submission or final receipt. Review validity never refreshes evidence.

Only an ACTIVE DEMO_SIMULATED_USD account with matching customer and USD currency
can fund this path. The source principal is debited, matching existing simulated
funding semantics; the provider quote fee is not an actual native gas debit.
QUOTE_ACCEPTED / RAIL_SELECTED / FUNDING_PENDING / FUNDED history, recommendation
EXECUTED, customer-account debit, immutable payment_besu_funding acknowledgement
and BesuFundingAuthorized 1.0 outbox envelope commit in the same owner transaction.
The envelope carries the complete qualified execution/fingerprint and explicit
simulated funding reference SIMULATED-BESU-<executionId>. It is a separate event
from legacy USDC funding. A retry reads the original acknowledgement without a
second debit/event or renewed execution authority. It performs no ledger, token,
nonce, signature, RPC or cross-owner write.

Payment V18 adds acknowledgement constraints, canonical claim-scope foreign key,
unique payment/execution/event/reference and an immutable-row trigger. Outbox JPA
writes flush before JDBC receipt insertion so FK visibility and rollback share one
transaction. Six PostgreSQL storage tests cover migration, replay, rollback,
concurrent duplicate funding, immutability and balanced-debit enforcement. They
require Docker; unit tests do not establish those PostgreSQL properties.

Acceptance and remaining integration are tracked only in backlog B08 (and B01,
B06, B09/B10). The internal BesuFundingHandler now validates the exact committed qualified outbox
command before invoking ledger allocation. Its listener is separately gated and remains disabled by default.
New funding requires SIMULATED-BESU-<executionId>; legacy private evidence remains
readable for historical reconciliation. The signer requires current FUNDED owner
state, the committed debit/outbox and all three exact POSTED allocation journals.
These immutable journals are the durable allocation acknowledgement; no additional
acknowledgement table is introduced. Rechecks surround RPC/signature preparation
and precede the first ATTEMPTED marker. Recovery of an already attempted transaction
observes its saved hash without requiring fresh funding authority or rebroadcast.
See B08 for remaining runtime integration and real PostgreSQL acceptance, and
B09/B10 for private/public validation. Customer Besu selection remains disabled.

```bash
bash services/mvnw -f services/pom.xml -pl payment-service -am \
  -Dit.test=SettlementExecutionClaimPersistenceIT,SettlementFundingPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Run against disposable Docker PostgreSQL before acceptance; no migration was
applied to a running demonstration database during this implementation.


## Private funding transport boundary (26 November 2026)

`BesuFundingAuthorized` version `1.0` uses its own ledger funding queue and DLQ,
never the legacy `StablecoinFundingCompleted` USDC route. The payment outbox keeps
the original envelope identity across publish retries. Local publication sends the
domain envelope directly; the gated CDK EventBridge rule routes only the payment
source, exact event type/version, and extracts `$.detail` for the same consumer.
LocalStackInitializer creates the additional pair only when the transport flag is
true, attaches three-receive redrive and a 120-second visibility timeout, and
rejects collisions with active owner queues before creating anything. CDK rejects
collisions with all configured owner queues, encrypts both queues and retains the
DLQ for 14 days. No queue is purged or reset.

Configuration is defined in shared connections.properties:

- `BESU_FUNDING_EVENTS_ENABLED=false` by default; controls the payment transport,
  LocalStack bootstrap, conditional CDK resources and the ledger subscription.
- `SQS_BESU_LEDGER_FUNDING_QUEUE_NAME` and
  `SQS_BESU_LEDGER_FUNDING_DLQ_NAME` name only this private delivery pair.
- The ledger consumer additionally requires `app.besu.accounting.enabled=true`.

These are internal acceptance gates, not instructions to enable customer Besu
selection. They do not activate funding production, signing, receipt polling or
payment closure. The handler's database transaction must complete before its
listener returns successfully. Exceptions propagate to SQS retry/DLQ handling;
replays reuse the existing execution and immutable journal references. A publish
failure retains the outbox record for retry; the existing five-failure terminal
policy still requires investigation rather than automatic financial compensation.
An EventBridge publish acknowledgement alone does not establish consumer delivery.

Acceptance and remaining workers/closure wiring stay in B08; real PostgreSQL and
LocalStack delivery/redrive acceptance remain B01/B09/B10. CDK synthesis and mocked
SDK checks do not establish running SQS delivery or database transaction behavior.


## Gated execution and receipt worker (27 November 2026)

The blockchain owner now has a bounded background worker over committed private
funding and its own saved submission records. Discovery is read-only: it does not
make another payment claim, debit an account, modify ledger records or deploy a
chain. Before new signing or the first attempt of a saved SIGNED transaction, the
worker requires the exact POSTED funding/allocation journals, then delegates to
BesuSubmissionService's existing canonical claim, current owner, expiry, liquidity,
nonce, signature and separately committed attempt fences. A discovery row cannot
replace those checks.

Recovery scans SIGNED / ATTEMPTED / UNKNOWN / SUBMITTED independently of current
payment state or quote expiry. Already attempted work calls the explicit
observeSubmission method, which cannot create a signature or first attempt.
BesuReceiptService then verifies the saved hash, exact private receipt/Transfer
log, fees and finality and commits its existing atomic receipt/status/audit record.
Missing or unverified receipts leave commitments unresolved. SIGNED work remains
unattempted while first submission is disabled. No automatic rebroadcast, fee
replacement, chain switch, speculative refund or terminal payment closure occurs.

The scheduler has no enclosing database transaction. Existing owner services keep
their lock/commit boundaries, including committing the attempt marker before RPC.
Multiple application instances still rely on those database fences, not in-memory
scheduler locking. Each discovery lane reads at most ten rows per poll using UUID
keyset pagination. The cursor advances after each row, including deferred rows,
and wraps after reaching the end. Cursors may reset on restart because execution
and submission identity remain durable. One deferred row or unavailable scan lane
does not prevent the other discovered work from being considered.

Shared configuration defaults preserve existing behavior:

- `BESU_WORKERS_ENABLED=false`: scheduler gate, together with app.besu.enabled.
- `BESU_WORKER_SUBMISSION_ENABLED=false`: separate permission for new signatures
  and first attempts; keep off while monitoring already attempted transactions.
- `BESU_WORKER_POLL_MS=5000`: delay after each completed poll.

Keep the Besu runtime and worker enabled when monitoring existing transactions;
turn off only first submission to stop new financial work. These flags remain
internal acceptance controls; they are not added to the ordinary public Compose
startup and do not authorize customer Besu activation. Deferred binding/expiry,
allocation or provider failures require investigation of durable evidence. Logs
contain only the phase and execution ID, never saved payloads or provider errors.
Terminal receipt evidence still needs ledger posting and payment-owner settlement/
reconciliation closure integration under B08/B06 before end-to-end acceptance.
Real storage/concurrency/restart and QBFT acceptance remain B01/B09/B10.


## Committed claim to reservation and simulated funding continuation (27 November 2026)

The private path can resume an existing canonical payment-owner execution claim
without manufacturing a new approval or accepting an actor from a message.
BesuReservationWorker discovers COMPLIANCE_APPROVED claims with no reservation,
funding or saved signature and delegates the exact stored execution to
BesuExecutionReservationService. That owner rechecks the immutable claim and
current instruction, obtains a fresh RPC observation and atomically holds/pins
capacity using its existing treasury lock. Discovery is not reservation authority.

After the reservation commits, payment-owned SettlementFundingWorker discovers
unsigned PINNED claims with no funding receipt. SettlementClaimedFundingService
loads and validates the canonical owner claim, rehydrates its original operator
and calls SettlementFundingService in an owner transaction. The funding service
locks payment/account, checks current membership, independent approval evidence,
review/quote/compliance and fresh PINNED capacity before the existing exact
simulated USD debit, history, funding receipt and outbox commit. A revoked actor
or stale reservation therefore cannot be funded merely because a claim exists.

Each worker scans at most ten UUID-keyset rows per poll; deferred rows advance
the cursor and do not starve later claims. Restart may reset the cursor because
owners retain identity and deduplicate under database locks. Payment and blockchain
workers never share a broad transaction or write another service's tables.
Pending/error logging contains phase/execution ID only. The existing separate
funding-event transport carries committed funding to ledger allocation; signing
still requires all exact allocation journals through the existing execution worker.

New defaults are BESU_FUNDING_WORKERS_ENABLED=false and
BESU_FUNDING_WORKER_POLL_MS=5000. Reservation also requires app.besu.enabled;
funding also requires app.flow.enabled and app.besu.funding.enabled. None of
these flags removes the hard customer-selection acceptance gate. The ordinary
public Compose path is unchanged. No worker creates claims, mints, deploys a token,
submits a transfer, compensates a failed payment or changes recorded chain identity.
The accepted one-time constructor mint directly to Besu treasury remains separate
from simulated customer funding. B07/B08/B09/B10 remain the acceptance references.


## Committed chain evidence to ledger continuation (27 November 2026)

BesuAccountingWorker is ledger-owned and requires both app.besu.accounting.enabled
and app.besu.accounting-workers.enabled. The new worker switch maps to
BESU_ACCOUNTING_WORKERS_ENABLED=false; BESU_ACCOUNTING_WORKER_POLL_MS=5000 controls
initial delay and polling. Scheduling is registered only when this worker is enabled.
Ordinary Compose and customer-selection acceptance gates are unchanged (B07/B08/B10).

Read-only discovery selects at most ten attempted/unknown/submitted/confirmed/reverted
signed-submission projections without a ledger receipt acknowledgement. It excludes
unattempted SIGNED material, reads no signed bytes, and does not require a still-live
quote, current payment state or new-submission switch. A saved canonical execution
and fingerprint remain mandatory. Terminal rows are included so an outage spanning
submission and mining cannot lose accounting work. UUID keyset progress advances
past deferred rows; after the end it wraps, and process restart resets discovery.

Each row invokes the existing proxied accounting.submitted owner transaction before
accounting.resolve for a terminal projection. There is no surrounding worker-wide
transaction. Both calls revalidate exact durable owner evidence and lock the payment;
a stale discovery cannot authorize mismatched posting. A crash between calls leaves
an immutable in-flight journal to replay, then the receipt acknowledgement and
confirmation/actual-fee journals commit atomically in the existing resolve command.
Unknown outcomes do not post delivery/fees. Zero actual fees need no artificial
expense; reverts do not post delivery or silently compensate the in-flight position.
The immutable acknowledgement removes completed work from discovery, while duplicate
or concurrent stale discoveries still pass through owner idempotency and locks.

The worker has no signing/RPC/submission/payment-state/reconciliation authority.
Per-row failure logging contains phase and execution ID only. Payment-owner closure,
reverted compensation and actual PostgreSQL/live/private/public acceptance remain
tracked in B05/B06/B08/B09/B10 of the central backlog.


## Payment settlement and reconciliation closure (27 November 2026)

BesuPaymentClosureWorker discovers only CONFIRMED saved submissions with committed
funding and ledger receipt acknowledgements. Discovery is bounded to ten rows,
advances past deferred executions and wraps on exhaustion; a restart reuses the
original immutable execution. No signed bytes are read and no transfer is sent.
The payment-owned command locks the payment, requires its exact canonical claim
and unchanged instruction, and verifies the committed debit/outbox and allocation
through SettlementFundingReads before taking a repeatable-read chain/ledger
snapshot. Missing or mismatched evidence never advances the payment.

Complete evidence produces audited BLOCKCHAIN_SUBMITTED, BLOCKCHAIN_CONFIRMED,
SETTLEMENT_COMPLETED and RECONCILIATION_PENDING transitions. These are recognition
of persisted evidence, not new blockchain operations. BesuReconciliationWorker
selects pending payments and invokes the reconciliation owner's runCurrent.
Canonical current evidence and execution scope determine a stable report ID;
identical source retries reuse the report, changed sources require another ID.
Historical explicitly keyed report reads retain their original replay behavior.

The payment owner independently recomputes the current comparison and requires
the exact scoped MATCHED report/document/source hash before RECONCILED. It flushes
JPA status/history/outbox in the same transaction as a new immutable V19
payment_besu_closure acknowledgement. The insert guard requires the same funded
canonical execution and RECONCILED state. Replay checks that acknowledgement
without creating more history, events, debits, journals or signatures. The report's
historical PAYMENT_OWNER_INTEGRATION_PENDING marker remains a chain/ledger-only
scope boundary; the separate payment-owned acknowledgement proves full workflow
closure. A report by itself still has no payment-state authority.

BESU_CLOSURE_ENABLED=false and BESU_CLOSURE_WORKERS_ENABLED=false are defaults;
poll delay is BESU_CLOSURE_WORKER_POLL_MS=5000. Payment continuation also requires
app.flow.enabled; the report worker requires app.besu.reconciliation.enabled.
Besu-prefixed lifecycle notifications use the existing local routing/audit queue
only when closure is enabled. They do not enter public execution/reconciliation
command consumers. Workers are driven by committed owner evidence, not by these
notifications. AWS event-routing/deployment acceptance remains A01/D01/B10.
No runtime flag, migration or customer-selection gate is enabled by this increment.
Reverted/unknown outcomes cannot close as delivery or automatically compensate;
B06/B08 track that next increment and all real database/private/public acceptance.


## Reverted execution and authorized control release (27 November 2026)

This is the ADR-015/016 CONTROL-book recovery action, not a customer refund.
The payment owner consumes only a canonical REVERTED submission, a conclusive
QBFT receipt, exact historical funding/allocation and complete actual gas
accounting. FAILED state, status history, qualified audit outbox event and the
immutable payment_besu_failure acknowledgement commit together. Missing receipt,
UNKNOWN outcome, successful delivery or mismatched accounting cannot enter this
path. Historical quote expiry does not prevent observing an already attempted
transaction; it never authorizes a new attempt.

An explicit internal owner command authorizeRelease requires a server-verified
customer PAYMENT_APPROVER whose current membership matches the instruction and
who differs from its funding operator. The grant binds to the failed execution,
its canonical document/fingerprint and exact source hash. The grant and its audit
outbox event commit together. No scheduler creates grants. An existing unacknowledged
release cannot acquire retroactive authorization.

The ledger consumes committed grants under its payment lock and a repeatable-read
snapshot, rechecking the conclusive receipt, original journals and exact authority.
BLOCKCHAIN_REVERT_RELEASE debits 150001 DEMOUSD treasury control and credits
150002 DEMOUSD in transit for the original token amount. Its reference links to the
original BLOCKCHAIN_SUBMISSION journal. The original journals remain POSTED;
simulated USD funding/conversion, token allocation and actual BESU_GAS expense are
unchanged. No BLOCKCHAIN_CONFIRMATION, USD refund, token mint, transfer retry or
chain switch is inferred. Payment status stays FAILED.

The release journal and immutable besu_ledger_compensation acknowledgement commit
atomically. The acknowledgement records the grant source hash, actor, original
journal and release journal. A deferred constraint prevents the generic journal
API from committing the reserved release type without this acknowledgement.
Replay validates the same binding and returns the original acknowledgement;
serialization/lock failures can be retried as accounting work without chain action.
The ordinary successful-settlement comparator never labels a reverted payment as
matched delivery, including after a control release.

BESU_RECOVERY_ENABLED and BESU_RECOVERY_WORKERS_ENABLED default false. The payment
failure worker additionally requires app.flow.enabled; ledger release additionally
requires app.besu.accounting.enabled. Recovery audit notifications use the existing
routing audit queue, not public workflow command consumers. No HTTP recovery
endpoint or customer Besu selection is enabled by this increment. Current acceptance
and authenticated UI/Compose work are tracked only in B06-B10 of
[the implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).

## Staged application integration update - 27 November 2026

The separate default-off private application API, persisted private evidence UI,
and isolated Compose/lifecycle helper are now code-integrated. Earlier dated
statements about missing HTTP/UI wiring describe their respective increments.
See [the application operator guide](APPLICATION.md) for build, start, stop,
resume and protected variable-file procedures. Resume suppresses private first
submission and new funding; it does not change the selected public runtime mode.
New private customer instructions remain hard-gated. Executed local checks and
unexecuted runtime boundaries are recorded in
[activity status](../../docs/36-POC-ACTIVITY-STATUS.md); current acceptance and
remaining work live only in B07-B10 of
[the consolidated backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
