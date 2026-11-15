# ADR-015: Per-Asset POC Conversion, Gas and Payout Accounting

Status: ACCEPTED by owner on 2026-11-14.

Date: 2026-11-14

## Context and blocking conflict

[docs/10-LEDGER.md](../docs/10-LEDGER.md) requires each asset to balance, but its
acquisition example debits USDC and credits USD. Neither asset balances in that
example. The approved chart also lacks counterpart accounts for gas payments,
conversion and INR payouts. The existing LedgerPostingService correctly rejects
cross-asset netting and must remain authoritative.

[ADR-014 section 9](ADR-014-STABLECOIN-AGENTIC-ROUTING.md) requires a reviewed
accounting decision before new clearing accounts or conversion postings. Under
[AGENTS.md section 17](../AGENTS.md), owner acceptance above authorizes the accounting implementation below.
Existing posted entries remain immutable.

## Accepted decision and book boundary

Keep the existing settlement-ledger-service and LedgerPostingService; add no
microservice. Use a versioned, configured, server-owned posting policy. Treat this
POC as a payment control subledger, with explicit REAL_TESTNET or SIMULATED leg
labels and per-payment movement records. It is not a statutory general ledger,
a complete custody inventory or evidence of real fiat assets/liabilities.

Treasury already holds test USDC and ETH. Simulated conversion records allocation
of that prefunded capacity to this payment, not minting, buying or moving USDC
on-chain. Do not also seed the full wallet balance as per-payment acquisitions.
Actual wallet readiness/reservations continue to use observed on-chain balances,
not the control accounts below. A future full custody book requires its own
opening balances and approved inventory policy.

Keep current account codes and meanings. Approved additions are:

| Code | Name | Type | Asset | Purpose |
|---|---|---|---|---|
| 110003 | Simulated USD Conversion Control | ASSET | USD | USD side of simulated allocation |
| 220001 | Simulated USDC Allocation Control | LIABILITY | USDC | USDC counterpart of simulated allocation |
| 130001 | ETH Network Fee Clearing | ASSET | ETH | Counterpart of evidenced testnet gas expense |
| 110004 | Simulated USD Payout Provider Receivable | ASSET | USD | Simulated provider payout obligation |
| 140001 | Simulated INR Payout Provider Receivable | ASSET | INR | Simulated provider payout obligation |
| 240001 | Simulated INR Beneficiary Payable | LIABILITY | INR | INR counterpart; existing 210001 remains USD |

Control-account classifications describe this POC book. In particular, the ETH
clearing account may carry a credit balance; it must not be displayed as the
wallet's actual ETH holding. Model output cannot choose account codes, amounts,
exchange rates, journal types or posting policy versions.

## Accepted posting templates

Let S be confirmed simulated USD funding, U the quote's USDC transfer amount,
P the optional quote's net beneficiary USD/INR payout and G the actual network
fee in ETH. Use exact decimals and the accepted quote, not a hardcoded peg.
For the primary demonstration, S = 10,000 USD and the simulated quote can
explicitly select U = 10,000 USDC. Never offset unlike assets to balance.

| Leg / journal type | Debit | Credit | Amount / evidence |
|---|---|---|---|
| FUNDING | 110002 USD | 110001 USD | S; simulated funding confirmation |
| CONVERSION_USD | 110003 USD | 110002 USD | S; accepted simulated quote |
| CONVERSION_USDC | 120001 USDC | 220001 USDC | U; same conversion reference |
| BLOCKCHAIN_SUBMISSION | 120002 USDC | 120001 USDC | U; persisted submitted transaction |
| BLOCKCHAIN_CONFIRMATION | 120003 USDC | 120002 USDC | U; successful receipt and required confirmations |
| NETWORK_FEE | 510001 ETH | 130001 ETH | G; confirmed receipt/network fee evidence |
| OFFRAMP_USDC | 220001 USDC | 120003 USDC | U; explicitly simulated off-ramp |
| PAYOUT_INITIATION_USD | 110004 USD | 210001 USD | P; simulated USD payout acceptance |
| PAYOUT_COMPLETION_USD | 210001 USD | 110004 USD | P; simulated USD payout success |
| PAYOUT_INITIATION_INR | 140001 INR | 240001 INR | P; simulated INR payout acceptance |
| PAYOUT_COMPLETION_INR | 240001 INR | 140001 INR | P; simulated INR payout success |

The two conversion journals are committed atomically by one ledger-owned
transaction, with shared conversion ID, quote ID/version and policy version.
Reuse the current posting validation within that transaction. Do not commit
one half and publish successful conversion. No same-asset debit/credit pair may
be satisfied using an account whose configured asset differs from the entry.

Wallet-delivery payments stop after testnet settlement and reconciliation;
they have no off-ramp or payout journals. Bank-delivery payments use one payout
currency only. Simulated off-ramp does not imply that the beneficiary's actual
testnet USDC has been sold or moved. These entries must remain separately labeled
so chain reconciliation evaluates the real transfer leg before simulated payout
movements, rather than comparing unrelated ending balances.

USD conversion control positions can remain open in this operational book;
closure of those positions is not implied by payment reconciliation. Conversion
and off-ramp references link the amounts and modes across assets; reconciling a
payment does not assert that all control-account balances are zero.

Quoted route costs remain labeled observations/estimates. This proposal does not
charge extra USD, recognize platform revenue or post estimated provider fees as
actual expenses. USD fee charging, fee revenue and production FX gains/losses
require a separate approved policy if added. Source funding, transferred amount
and net payout must be visible independently; never silently deduct a cost or
increase the authorized debit. The actual testnet network fee is the only new
expense posting specified here.

For Base, G includes all evidenced network fee components, including L1 fees
where applicable, in addition to execution gas. Gas estimates are not posted as
actual fees. If required components cannot be obtained, retain an explicit
incomplete fee-evidence finding and prevent a fully reconciled claim. A reverted
transaction may still incur G; record the evidenced expense, do not post a
successful USDC confirmation, and require explicit exception recovery.

## Integrity, recovery and ownership

- Add accounts through a new ledger-owned migration after acceptance. Preserve
  existing posted entries. Extend SupportedAsset and database constraints for INR
  without altering existing USD/USDC/ETH meanings.
- Configure approved account mappings/policy version centrally; validate types,
  assets and active status at startup and before posting. Fail closed for missing
  or incompatible mappings. Configuration is not authorization for new policy.
- Derive financial evidence only from trusted service records/events. Reuse the
  VisaPaymentAdapter and existing transaction, settlement and reconciliation
  owners; agents have no posting or execution tools.
- Keep paymentId, correlationId, quote/conversion/payout/transaction references,
  amounts, mode, posting policy version and journal idempotency scope auditable.
- Redelivery must reuse the same journal with matching immutable business content.
  A matching idempotency key with different amounts, asset, evidence or correlation
  must conflict rather than returning apparent success. Use database uniqueness
  and locking to protect concurrent consumers and compound journal pairs.
- Unknown blockchain submission or payout outcome never causes another financial
  leg, automatic rerouting or speculative reversal. Resolve original evidence
  first. Failed payouts do not prove a refund; any evidenced compensation uses
  an explicitly linked reversal/compensating journal, never entry edits.
- Reconciliation verifies exact per-leg amounts, currencies, success/confirmations,
  journal pairs, modes and references, including missing/duplicate/partial legs.
  It never equates simulated bank credit with actual banking evidence.

## Acceptance and verification required after review

Owner acceptance authorizes the control-book boundary, added accounts and
templates above. Update docs/10, implement migrations and
versioned mappings, reuse the ledger posting service, and wire existing events.

Required executed tests: per-asset and account-asset validation; atomic conversion
rollback; mismatched replay; concurrent duplicate delivery; immutable entries and
reversals; USD/INR payout success/failure/unknown outcomes; actual versus estimated
gas; receipt reversion; missing fee components; no payout for wallet outcome; and
reconciliation failures for altered amounts, references, modes or missing legs.
PostgreSQL migration/locking tests require an available database. Public-testnet
evidence remains separately required; fixtures are not proof of settlement.

## Alternatives and consequences

Cross-asset netting is rejected because it violates existing ledger rules.
Posting no conversion at all leaves the intended funding-to-settlement control
trail incomplete. A full production general ledger/custody implementation is
outside this POC and must not be implied by these control accounts.

This decision introduces accounting policy, accounts, INR support and additional
posting/reconciliation work. Acceptance authorizes implementation, not a claim that migrations, account seeds
or financial postings have executed. Runtime provider configuration and
its verification are independent and already implemented.


## Implementation note (2026-11-14)

Approved templates, provenance/replay guards and funding/submission/confirmation consumers are implemented. Manual journal writes remain disabled by default and require internal authority when enabled; customer reads require payment ownership. Actual PostgreSQL migration/atomicity tests and the complete producer-to-settlement lifecycle remain pending.

## Step 7 implementation note (2026-11-14)

payment-service emits bound StablecoinFundingCompleted 2.0 atomically with its
one-time simulated USD debit and audited transitions. The existing ledger consumer
posts the accepted FUNDING, CONVERSION_USD and CONVERSION_USDC templates; submission
waits for all three POSTED journals with matching conversion, quote, amount, outcome
and SIMULATED provenance. This does not purchase/mint USDC: treasury must already
hold test tokens. Submitted/confirmed chain events retain their own immutable
legs. Gas reserves and gas-price budgeting remain estimates, not actual fee expense.
At completion of step 7, actual network-fee completeness, final settlement, optional
payout and reconciliation still awaited step 8 (implemented below). PostgreSQL concurrency/
rollback tests were added but skipped without Docker; they remain acceptance work.

## Implementation note - step 8 (2026-11-15)

Step 8 connects actual complete receipt-fee posting, final settlement and optional simulated USD/INR off-ramp/payout to the existing approved templates. Six wallet journals or nine bank journals are validated independently by asset, account, amount and provenance. Unknown/failed payout never infers customer refund or bank credit; explicit reviewed compensating entries remain required for financial exceptions. PostgreSQL and full live acceptance remain pending.

See [lifecycle implementation and limitations](../docs/31-SETTLEMENT-PAYOUT-RECONCILIATION.md). Accepted decision and status are unchanged.

## Step 9 implementation note - 2026-11-15

The connected React workspace reuses existing owner command APIs and adds a
customer-authorized, read-only payment-service evidence projection. Shared-schema
reads follow the accepted ownership boundary; the private Visa endpoint stays
private. UI decimal-string/BigInt display checks preserve per-asset precision,
while the existing ledger, agent authority and approval/execution services remain
authoritative. No architectural decision or accepted status changes. See
[connected UI design](../docs/32-CONNECTED-REACT-WORKSPACE.md).
