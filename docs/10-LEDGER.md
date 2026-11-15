# Double-Entry Ledger

## Principle and book boundary

Blockchain is settlement infrastructure, not the internal accounting ledger.
Every journal balances independently per asset. Never offset USD against USDC,
ETH or INR because their numerical amounts happen to match.

This is the payment control subledger approved in
[ADR-015](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md), accepted 2026-11-14.
Fiat funding, conversion, FX, Visa and bank credit are simulated. Journals are real
application records with explicit mode/provenance. The subledger is not a full
custody inventory or statutory general ledger. Treasury readiness uses actual
wallet observations and reservations, not balances inferred from control accounts.

## Approved chart of accounts

| Code | Name | Type | Asset |
|---|---|---|---|
| 110001 | Corporate USD Control | ASSET | USD |
| 110002 | Stablecoin Funding Clearing | ASSET | USD |
| 110003 | Simulated USD Conversion Control | ASSET | USD |
| 110004 | Simulated USD Payout Provider Receivable | ASSET | USD |
| 120001 | USDC Treasury | ASSET | USDC |
| 120002 | USDC In Transit | ASSET | USDC |
| 120003 | Beneficiary USDC Settlement | ASSET | USDC |
| 130001 | ETH Network Fee Clearing | ASSET | ETH |
| 140001 | Simulated INR Payout Provider Receivable | ASSET | INR |
| 210001 | Beneficiary Settlement Payable | LIABILITY | USD |
| 220001 | Simulated USDC Allocation Control | LIABILITY | USDC |
| 240001 | Simulated INR Beneficiary Payable | LIABILITY | INR |
| 410001 | Payment Fee Revenue | REVENUE | USD |
| 510001 | Blockchain Gas Expense | EXPENSE | ETH |
| 510002 | Payment Fee Expense | EXPENSE | USD |

Existing accounts retain their meanings. V7 adds the six ADR-015 accounts, INR
constraints, NUMERIC(38,18) journal-line amounts, provenance/fingerprints and
ledger-owned payment locks. It does not rewrite previous monetary postings.
The ETH clearing account can have a credit balance and is not a wallet holding.

## USD 10,000 illustration

Funding is a simulated movement between two USD control accounts:

```text
FUNDING
DR 110002 Stablecoin Funding Clearing    10,000 USD
CR 110001 Corporate USD Control          10,000 USD
```

The name Stablecoin Funding Clearing describes earmarked USD; it is not USDC.
For a quote explicitly specifying 10,000 USD -> 10,000 USDC, conversion requires
two separately balanced journals, committed atomically with funding:

```text
CONVERSION_USD
DR 110003 Simulated USD Conversion Control   10,000 USD
CR 110002 Stablecoin Funding Clearing        10,000 USD

CONVERSION_USDC
DR 120001 USDC Treasury                      10,000 USDC
CR 220001 Simulated USDC Allocation Control   10,000 USDC
```

Actual amounts come from the accepted quote; a 1:1 peg is not hardcoded.
Conversion allocates existing prefunded test-USDC capacity. It neither buys nor
mints tokens on-chain. Do not also count full wallet prefunding as per-payment
acquisition. USD conversion controls may remain open; payment reconciliation is
not a claim that every control-account balance is zero.

```text
BLOCKCHAIN_SUBMISSION
DR 120002 USDC In Transit                10,000 USDC
CR 120001 USDC Treasury                  10,000 USDC

BLOCKCHAIN_CONFIRMATION
DR 120003 Beneficiary USDC Settlement    10,000 USDC
CR 120002 USDC In Transit                10,000 USDC
```

A confirmation requires the matching submitted hash, quote terms, amount, mode
and correlation. Wallet delivery has no fiat payout. A legacy BLOCKCHAIN_SETTLEMENT
posting requires review before new confirmation accounting; the old direction
must not be silently reposted or corrected by modifying entries.

## Actual network fees

```text
NETWORK_FEE
DR 510001 Blockchain Gas Expense      G ETH
CR 130001 ETH Network Fee Clearing    G ETH
```

G is evidenced by a mined transaction receipt, not a gas estimate. Include
execution gas and all required additional network components (Base L1 fee where
applicable). Persist the breakdown/source with the journal. Incomplete components
block a fully reconciled claim. A reverted mined transaction can still incur gas;
record its actual fee without inventing successful USDC settlement. ETH amounts
retain up to 18 decimal places; silent six-decimal rounding is unacceptable.

## Optional simulated payout

For a bank outcome, post OFFRAMP_USDC: DR 220001 / CR 120003 in the quoted USDC
amount. This is simulated; the beneficiary's actual testnet tokens remain where
the real transfer delivered them. USD payout initiation posts DR 110004 / CR
210001, with completion DR 210001 / CR 110004. INR uses 140001 / 240001 in the
same directions. Use the quote's net payout amount and one currency only.

Off-ramp and payout initiation are atomic. Completion requires matching initiation
and explicit simulated success. Failed or unknown payout is not success or proof
of a refund. No automatic reversal or second payout is permitted. Reconciliation
compares individual real and simulated legs, not unrelated ending balances.

Route fee estimates are observations. This implementation does not debit extra
USD, recognize revenue or turn estimated provider costs into actual expenses.
Additional fee charging/production FX policies require reviewed accounting.

## Integrity, provenance and recovery

LedgerPostingService remains the single posting validator. PaymentAccountingService
builds approved internal templates and atomic compound operations; it is not a
model tool or public financial-command API. Its immutable Terms bind conversion,
quote/version, source USD, transferred USDC, outcome, net payout and funding
reference. Mode, policy version, transaction/payout references and receipt fee
evidence accompany applicable journals.

Shared app.ledger.accounting configuration selects policy version/account codes.
Startup and posting validate account asset, type and active status. Missing or
incompatible mappings fail closed. All payment journals serialize on a ledger-owned
payment/correlation row; the ledger never writes payment-service ACCOUNT records.

Idempotency uses UNIQUE(payment_id,journal_type) plus a canonical content hash.
Matching replays return the original journal. Changed correlation, quote, amount,
asset, references or provenance conflict. Legacy journals are compared against
original entries without modifying their metadata. Reversed/pending journals do
not become fresh postings on redelivery. Partial compound journal histories need
review rather than speculative repair.

PENDING headers are flushed before lines; lines are flushed before POSTED.
Database triggers reject line updates/deletes, new lines on posted journals,
unbalanced posting, entry/account asset mismatch and changes to financial header
content. Corrections use linked compensating entries. Only the original header's
reversal status/link may change; original financial lines remain immutable.
Used account classifications cannot change. These guards do not grant database
administrators immutability against changing the schema itself.

Manual POST /api/v1/journals and reversal HTTP operations default disabled through
LEDGER_MANUAL_API_ENABLED=false. Enabling requires an independently protected
internal boundary; this flag is not authentication. Customer-facing access must
be mediated by the owned payment API and deployment authorization design.

## Verification and remaining integration

ADR-015 ledger verification: Java 21 compilation/package/verify succeeded; 67
unit/API tests passed and ten PostgreSQL migration/locking/rollback cases were
skipped without Docker. Ten shared Node configuration tests passed. Skips do not
prove migration correctness, transaction rollback or concurrency behavior.

The confirmation consumer now validates event headers, network, mode, token and
amount and invokes the approved confirmation template. It requires previously
recorded allocation/submission evidence. Trusted funding/submission event
producers/consumers, receipt fee extraction, payout wiring, settlement publication
and end-to-end reconciliation remain integration work. No live chain, fiat or
AWS deployment is established by these unit tests. See the
[architecture/design](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md) and
[execution runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md) for delivery tracking.


Latest full-reactor verification (2026-11-14): ledger has 79 executed checks passing and 10 PostgreSQL checks skipped without Docker. Shared access tests separately verify customer/internal authority. Database triggers/migrations and full payment orchestration still require actual PostgreSQL execution; successful unit tests do not establish those results.

## Step 8 producer and acknowledgement integration

Accepted ADR-015 templates now have connected lifecycle consumers. Complete actual
receipt evidence produces NETWORK_FEE in ETH, not an estimated-fee posting.
Settlement completes only after the six expected wallet journals are independently
verified. Optional simulated bank payout atomically posts OFFRAMP_USDC plus
PAYOUT_INITIATION_USD/INR and later posts PAYOUT_COMPLETION_USD/INR. Each journal
balances its own asset using existing LedgerPostingService; posted entries remain
immutable. Report API exactDebit/exactCredit strings preserve wei-level ETH
precision for JavaScript clients. Existing numeric response fields remain compatible.
See [the full sequence and exception limits](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
Database migration/atomicity and six-service execution require Docker/Codespaces;
no local database acceptance is asserted by successful unit tests.
