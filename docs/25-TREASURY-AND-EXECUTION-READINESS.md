# Treasury and execution readiness (step 4)

Date: 2026-11-14

Status: implementation in the existing services; database concurrency/migration
execution remains a verification gate when Docker/PostgreSQL is available.
This increment does not connect the full payment journey or introduce runtime AI.

## Reuse and ownership

- **blockchain-service:** extends the existing `BlockchainTransactionService`,
  `BlockchainAdapter`, `BlockchainProperties`, wallet/transaction repositories,
  signer, response/event contracts and outbox. The existing transaction record
  is also the reservation operation: no parallel transfer implementation or
  separate treasury microservice. New audit records belong to blockchain-service.
- **visa-simulator:** previously only a health endpoint. Adds the previously
  specified `VisaPaymentAdapter` and `VisaSimulatorAdapter` for persistent,
  explicitly SIMULATED provider liquidity. No additional deployable service.
- **settlement-ledger-service:** the existing confirmation consumer now preserves
  opaque payment/correlation identifiers. Identifier columns are widened; account
  codes, posting rules and journal amounts are unchanged. This does not resolve
  the separate acquisition/FX accounting decision in docs/10-LEDGER.md.
- **payment-service:** its existing intent/policy/evidence/assessment implementation
  remains the decision baseline. The future routing agent can gather readiness
  as evidence through a scoped read tool. Reservation, release, signing, submission,
  payout and journal methods are never model tools. Model-driven interpretation,
  evidence gathering and explanations remain step 6.

These changes implement accepted ADR-014 within ADR-003, ADR-007, ADR-008,
ADR-010 and ADR-011. No new network, token, smart contract or architecture decision.

## Identifiers and compatibility

Payment and correlation identifiers are opaque strings of 1?64 characters using
letters, numbers, `.`, `_`, `:`, `-`, beginning with a letter or number. Existing
UUID JSON strings remain valid. `PAY-...` and `CORR-...` pass through the blockchain
API, transaction, event envelope, outbox, confirmation consumer and ledger without
UUID conversion or replacing a missing correlation with a different identifier.
Internal transaction/wallet/event UUID identifiers remain UUIDs.

The blockchain operation has a unique **paymentId across networks**, preserving
the existing one-transfer-per-payment scope. A replay must match correlation,
recipient, amount, network and token contract. A changed request conflicts.
Prepared, expired, released, failed and uncertain records are retained and never
replaced by a new transfer for that payment. Another network cannot bypass an
existing operation. A separate approved recovery workflow is required to resolve
legacy or abandoned operations; there is no automatic retry/reroute command.

Migration `blockchain V4` converts existing UUID columns to their string form,
normalizes sender addresses, adds wallet/network ownership and lifecycle constraints,
and adds execution audit records. Existing rows receive `LEGACY_REVIEW` and
`LEGACY_UNKNOWN`; missing chain/gas/source evidence is not invented. Legacy
unresolved rows block that wallet, including historical FAILED rows that could
have represented a confirmation timeout. Confirmed/reverted legacy rows do not
block other operations. Existing unsigned legacy operations cannot be submitted
through the new path without reviewed remediation.

The migration intentionally fails for conflicting payments across networks,
case-colliding wallet records or orphan sender references. Investigate those data
conditions rather than deleting historical financial evidence. It has not yet
executed in this environment. Ledger V6 widens identifier columns to 64 characters.

## Readiness and gas budget

`BlockchainTransactionService.getReadiness(recipient, amount)` returns typed,
read-only evidence, including actual adapter mode (MOCK or LIVE), configured
network/chain/contract, treasury public address, total/reserved/available USDC and
ETH, gas estimate, buffered limit/price, required gas ETH, observation time,
expiry and reason codes. LIVE means public **testnet**, never production money.
No RPC error silently becomes mock evidence.

The existing real adapter now reads RPC gas price and pending nonce; its existing
balance, estimate, signing, submission and receipt capabilities are reused.

The ETH reservation is:

```
ceil(estimatedGas ? gasLimitMultiplier)
  ? ceil(rpcGasPriceWei ? gasPriceMultiplier) / 10^18
  + additionalGasReserveEth
```

Defaults are 1.2 for each multiplier and 0.00001 additional test ETH. Gas units
and wei stay integers; token arithmetic uses BigDecimal. The extra ETH is an
explicit configurable safety allowance for uncertainty, including network data
fees. **It is not a measured Base L1/data-fee estimate or a guarantee of final
cost.** Actual receipt gas is recorded separately. Network-specific complete fee
estimation and public-testnet execution remain verification work before a demo.
There is no market USD valuation of test ETH and no USDC-to-ETH swap.

Balances are fresh RPC observations, not a single atomic chain snapshot.
Application-owned wallet locks prevent local over-allocation; they cannot control
external transfers or reorgs. Assume exclusive application signing authority for
the treasury wallet. Revalidate readiness immediately before claiming submission.
Provider availability, compliance, policy, quote/deadline and approval gates must
also be revalidated by the upcoming orchestrator; readiness is not authorization.

## Reservation and execution lifecycle

1. Prepare validates the recipient, positive six-decimal USDC amount and configured
   supported public testnet tuple. Under one PostgreSQL transaction, insert the
   owned wallet if missing, lock that wallet, check the payment operation, expire
   eligible unsigned holds, fetch readiness and reserve USDC plus buffered gas ETH.
2. HELD reservations subtract both assets from availability. No signing occurs in
   preparation. Wallet locking serializes concurrent requests, including bootstrap.
3. HELD / PREPARED may expire or be explicitly released. Terminal records retain
   their payment key. Expiry is evaluated on reads and persisted during commands;
   no background scheduler is needed to prevent execution of an expired unsigned hold.
4. Submission locks wallet then operation, refreshes readiness, checks expiry,
   retrieves RPC pending nonce and signs through `WalletSigningService`. It commits
   SUBMITTING / PINNED, expected hash, gas budget and claimed nonce **before** any
   broadcast. Signed bytes remain in memory and are never persisted or logged.
5. Broadcast happens once outside the database transaction. The acknowledgement
   must match the locally computed hash. Timeout, rejection or mismatched hash
   becomes SUBMISSION_UNKNOWN and keeps the expected hash and PINNED hold. A DB
   failure after broadcast leaves the earlier committed claim recoverable.
6. Replayed submission returns the existing operation and never broadcasts again.
   The service conservatively permits one unresolved submitted operation per
   treasury wallet, blocking new execution until the prior leg is resolved.
7. Confirmation can inspect SUBMITTING after a restart, SUBMITTED, or
   SUBMISSION_UNKNOWN. Receipt hash, inclusion block and configured confirmations
   must match. Inclusion counts as confirmation one. A successful receipt transitions
   to CONFIRMED; a reverted receipt to REVERTED. Both release the budget as SETTLED;
   fresh chain balances then reflect the outcome. Only success emits the existing
   BlockchainTransactionConfirmed event.
8. An absent/late receipt stays unresolved. Neither polling timeout nor TTL makes
   a submitted operation FAILED or releases funds. A process stopping before
   broadcast may leave a claim with no receipt: manual deterministic investigation
   is required; no nonce rollback, automatic resend or fallback is implemented.

Status changes and reservation changes have append-only application audit records
in the same transaction. Outbox row and envelope use the same eventId. New chain
event payload fields carry chainId, tokenContract and evidenceMode. Existing
structured JSON logs are retained without raw RPC errors or signing material.

## Simulated provider fiat liquidity

`VisaPaymentAdapter` currently exposes typed internal liquidity operations:
`getBalance`, `prefund`, `reserve`, `pin`, `cancel`, `resolve`.

- Provider IDs are explicitly allowlisted in shared configuration. USD and INR are
  separate pockets, use two-decimal arithmetic, and start with zero prefunding.
- `prefund` credits only the simulator and is idempotent by operationId.
- `reserve` is idempotent by paymentId and binds correlation, provider, currency
  and amount. It holds only available simulated fiat under a database pocket lock.
- An unstarted HELD payout reservation may expire/cancel. PINNED reservations do
  not expire/cancel on timeout. Only a conclusive simulator result can consume or
  release them. Consumption debits the simulated total exactly once.
- Audits, funding receipts and terminal reservations persist. Concurrent completion
  acquires the pocket lock before loading mutable reservation state.
- There is no external Visa connection, fiat conversion, payout reference, bank
  credit or completed payout API in this increment. The existing docs/07 payout
  contract and future adapter operations remain step 8. No HTTP write endpoint was
  added. Upcoming payout orchestration must use this interface, not direct tables.

Provider and treasury reservations are local transactions in separate services.
A future orchestrator must coordinate them with durable operation keys and safe
compensation before signing. There is no distributed atomic commit claim.

## Shared configuration and API boundary

Canonical settings remain in `services/config/connections.properties`, packaged
in all six jars and forwarded through the existing Compose allowlist:

| Environment variable | Default |
|---|---|
| BLOCKCHAIN_RESERVATION_TTL | PT5M |
| BLOCKCHAIN_READINESS_TTL | PT30S |
| BLOCKCHAIN_GAS_LIMIT_MULTIPLIER | 1.2 |
| BLOCKCHAIN_GAS_PRICE_MULTIPLIER | 1.2 |
| BLOCKCHAIN_ADDITIONAL_GAS_RESERVE_ETH | 0.00001 |
| BLOCKCHAIN_EXECUTION_API_ENABLED | false |
| VISA_SIMULATOR_ALLOWED_PROVIDER_IDS | SIMULATED-PROVIDER-A,SIMULATED-PROVIDER-B |
| VISA_SIMULATOR_RESERVATION_TTL | PT5M |

TTL values must be positive and at most 24 hours. Multipliers are 1?10 with up to
four decimal places; extra ETH is 0?1 with at most 18 decimals. The browser
allowlist does not expose these settings. Replace configuration only after
resolving affected in-flight operations; network/provider changes fail closed.

The existing execution HTTP controller is now default-off. Enabling it is only
appropriate behind a protected internal boundary and is not authentication.
Structured API errors avoid exposing raw provider URLs. Intent/assessment HTTP
remains closed pending verified customer identity (step 2 gap). No customer-facing
readiness or financial command API is added here.

Local testnet signing still loads its key from the existing environment mechanism.
The adapter now uses the protected signer interface without obtaining credentials.
Secrets Manager/KMS signing integration and institutional custody evolution remain
required before AWS execution; this increment is not a claim that managed signing
or production execution is ready.

## Validation and remaining gates

Java 21 compiled and packaged all six services. Final execution discovered 257
Java cases: 248 passed, zero failures/errors, and nine PostgreSQL cases skipped
because Docker is unavailable (five treasury and four provider). This includes
43 new unit/HTTP tests. Six Node configuration tests passed; CDK TypeScript
compiled and two Jest suites / seven tests passed. The existing ts-jest TS151002
warning remains nonfatal. Detailed completion tracking is in the delivery plan.
The unit/HTTP tests cover asset availability, gas buffers/revalidation, idempotency,
expiry/release, durable claim ordering, pending nonce, hash/receipt mismatch,
uncertain submission retention, recovery, event identity, API defaults/errors,
signer abstraction, simulated provider prefunding/reservation/completion and
ledger identifier propagation. PostgreSQL tests cover concurrent wallet/pocket
allocation, concurrent replay/completion, commit visibility and outbox failure.

Database tests require Docker and real PostgreSQL under ADR-010. Skipping them is
not proof that SQL migrations or database locks work. No H2 substitution. Existing
real-testnet tests are read-only preflight and were not run; they do not prove a
transfer. No model, public testnet, AWS deployment or beneficiary credit was invoked.

Next: step 5 durable recommendations and approvals, including authenticated customer
mapping before exposing APIs. Step 6 supplies the actual bounded runtime agent;
step 7 connects the nine-step journey and real testnet recovery; step 8 completes
payout and approved accounting/reconciliation. Full network fee evidence, managed
signing and actual database/concurrency execution remain explicit release gates.

Subsequent step 5 now secures payment HTTP and implements durable human review; see [approval/access boundary](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md). Execution-time fresh evidence and protected command integration remain pending.
