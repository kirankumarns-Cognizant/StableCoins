# Visa wallet payout simulator

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Date: 2026-11-26. Implements the owner's request to simulate wallet delivery
alongside fiat payouts under ADR-003. This is a provider component acceptance
runbook, not proof of an approved customer payment or a real Visa connection.

## What is implemented

The existing VisaPaymentAdapter now has separate wallet balance, prefunding,
creation, read, completion and explicit unknown-outcome resolution operations.
VisaSimulatorAdapter delegates them to VisaWalletSimulator. Fiat USD/INR methods,
events, journals and payout flow retain their existing meanings.

Wallet balances and payout capacity are SIMULATED and stored independently from
fiat pockets and actual chain balances. Six-decimal Base Sepolia USDC and private
Besu DEMOUSD can be configured. The operator must pin the complete token identity:
network ID, chain ID, genesis hash, public/private classification, contract, symbol,
decimals and code hash. A matching symbol is insufficient. Mainnet is rejected.
This models a Visa-like capability, not Visa support for private Besu or DEMOUSD.

Each payout captures execution/payment/correlation, recommendation ID/version,
quote ID/version, token identity, recipient and exact amount. Replay returns the
same operation; changed business terms conflict. Server configuration selects
SUCCEEDED, DECLINED or TIMEOUT; request callers cannot choose the scenario.
Configuration changes do not rewrite a previously captured scenario.

PROCESSING/UNKNOWN retain capacity. Success consumes the simulated token amount
once. Decline releases the hold without debiting total. Timeout remains UNKNOWN
until an explicitly idempotent resolution. A late resolution cannot reverse a
terminal outcome. There is no implicit expiry, reroute, refund or second payout.
Fiat and wallet creates share a per-payment database lock and reject a payout in
the other destination table. This guards the provider boundary, not direct
blockchain execution; the orchestrator still needs an immutable delivery-path
claim before customer selection can be enabled.

Every result says evidenceMode=SIMULATED, blockchainDeliveryVerified=false and
transactionHash=null. A provider SUCCEEDED record is not a token Transfer event.
No RPC, signer, chain submission, fiat conversion or journal writer exists in
this simulator component. Never fabricate a transaction hash or reuse the direct
beneficiary transfer as evidence of an additional provider transfer.

## Application integration boundary

The current customer UI and direct-wallet payment flow are unchanged. They do
not automatically invoke the wallet simulator. Its internal operations API is a
test harness, not a payment authorization API; it does not verify application
approvals merely because supplied review IDs are syntactically valid.

Before exposing Visa wallet delivery to customer routing, add an immutable
provider-delivery choice to instruction/quote/review/execution, perform exact
owner-side two-approver validation, select one transfer owner, wire provider
events/status recovery and reconcile provider evidence separately from chain
delivery. Any additional financial posting policy needs a reviewed accounting
decision. ADR-015's existing wallet-only no-off-ramp rule remains in force.

Besu payment execution/accounting/reconciliation gates also remain outstanding.
Passing these simulator tests does not establish completed Besu integration or
readiness to deploy to AWS. EC2 deployment follows Codespaces acceptance.

## Configuration and private API

Default: VISA_SIMULATOR_WALLET_ENABLED=false. No wallet API is registered and
adapter wallet calls fail WALLET_SIMULATOR_DISABLED.

To run a controlled provider-only acceptance fixture, enable the following in
the visa-simulator process/container, retaining all existing configuration:

- VISA_SIMULATOR_WALLET_ENABLED=true
- VISA_SIMULATOR_OPERATIONS_ENABLED=true
- VISA_SIMULATOR_WALLET_SCENARIO=SUCCEEDED (or DECLINED / TIMEOUT)
- VISA_SIMULATOR_WALLET_TOKENS_JSON: JSON array of exact server-owned
  SettlementNetwork.Token objects, from validated deployment metadata.

Do not use example/fabricated genesis or bytecode hashes for an application
payment. Enabled mode with missing/empty/invalid tokens fails startup. These
settings contain token metadata, not wallet passwords. Changing host exports
does not update an already running container: a tested Compose overlay and
recreation are needed when accepting in Codespaces.

Endpoints are internal to visa-simulator on port 8086, beneath
/visa-simulator/v1/wallet. Existing internal-service client ID AND scope are
required; browser access tokens cannot grant internal authority. Operations
remain denied unless the operations flag is enabled. Do not add a public
gateway route or expose the Codespaces port publicly.

| Method/path | Body | Result |
|---|---|---|
| POST /balance | providerId, token | Simulated token total/reserved/available |
| POST /prefund | providerId, token, amount | Simulated capacity; Idempotency-Key UUID required |
| POST /payouts | WalletPayouts.Request | Same key as executionId; stable provider reference |
| GET /payouts/{id} | None | Durable provider status |
| POST /payouts/{id}/complete | None | Captured success/decline; UNKNOWN stays held |
| POST /payouts/{id}/resolution | succeeded boolean | Explicit conclusive timeout outcome; Idempotency-Key UUID |

The request token shape is:
token.chain={networkId,chainId,genesisHash,classification}, plus token.contract,
token.symbol, token.decimals and token.codeHash. The payout request also contains
executionId, paymentId, correlationId, providerId, recommendationId,
recommendationVersion, quoteId, quoteVersion, recipient and amount.
Use decimal strings for exact amounts. Idempotency-Key on create must equal
executionId. Scenario is deliberately absent from this request contract.

## Automated and database acceptance

From repository root in Codespaces/Bash:

~~~bash
bash services/mvnw -f services/pom.xml -pl visa-simulator -am test
# Requires Docker; IT names must be explicit and fail if nothing is selected.
bash services/mvnw -f services/pom.xml -pl visa-simulator -am \
  -Dit.test=ProviderLiquidityPersistenceIT,WalletPayoutPersistenceIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
~~~

Review Failsafe reports: disabledWithoutDocker can skip ITs. Skips are not
PostgreSQL acceptance. Wallet IT cases cover contention, replay, audit rollback,
timeout resolution, exact consumption and fiat/wallet exclusion.

Read-only database inspection after a deliberately authorized simulator fixture:

~~~sql
BEGIN READ ONLY;
SELECT payout_id, execution_id, payment_id, correlation_id, reference, status,
       scenario, evidence_mode, resolution_id, created_at, updated_at
FROM visa_wallet_payout ORDER BY created_at DESC;
SELECT provider_id, token_document, total, evidence_mode
FROM visa_wallet_pocket;
SELECT payout_id, status, evidence_mode, timestamp
FROM visa_wallet_payout_audit ORDER BY audit_id;
COMMIT;
~~~

These are provider simulation tables. They do not prove a completed application
payment, beneficiary chain balance, settlement journal or reconciliation.

Reference: Visa describes stablecoin wallet payouts and stablecoin prefunding
as distinct capabilities:
https://corporate.visa.com/en/solutions/commercial-solutions/knowledge-hub/b2b-stablecoin-payments.html
## Calculated balance evidence

GET /visa-simulator/v1/wallet/payouts/{id}/balances returns an internal-only
SIMULATED statement through VisaPaymentAdapter. It includes immutable transition
observations, the current provider pocket and recipientCreditsInThisSimulator.
The latter starts at zero and sums successful payouts for this provider, exact
token identity and recipient; it is not an observation of the external wallet.

Each stored BalanceChange contains providerBefore/providerAfter totals,
reservations and available funds, recipientBefore/recipientAfter simulator
credits, providerDebit and recipientCredit. The provider debit equals the
recipient credit. Capture, status, pocket movement and audit share the adapter
transaction. Create and completion replays add no duplicate movement. Decline
releases the reservation; UNKNOWN preserves it until explicit resolution.

Example only, not a claimed runtime payout: prefund 20 DEMOUSD, request 5 DEMOUSD.

| Stage (all SIMULATED) | Provider total | Reserved | Available | Recipient simulation credits |
|---|---:|---:|---:|---:|
| Prefunded | 20 | 0 | 20 | 0 |
| Processing / unresolved timeout | 20 | 5 | 15 | 0 |
| Success | 15 | 0 | 15 | 5 |
| Decline instead of success | 20 | 0 | 20 | 0 |

Subsequent successful 5-token payouts yield 10/10, 5/15, then 0/20
(provider total / recipient simulator credits). A fifth request fails for
insufficient available funds. Additional simulated prefunding is explicit and
idempotent; it is not Circle minting, a bank deposit or a chain transfer.
USDC and DEMOUSD pockets never share capacity. Existing fiat pockets continue
their actual decimal total/reserved/available arithmetic under a SIMULATED tag.

There is no invented blockchain gas debit: this simulator submits no chain
transaction. A provider fee is not modeled by this fixture; do not interpret it
as a zero-fee commercial Visa quote. Actual approved payment fees and chain fees
remain separate evidence.

Migration V4 adds transition balance observations; it does not rewrite V3.
Older payout records have no historical balance snapshots and return an empty
changes list rather than reconstructed history.

~~~sql
BEGIN READ ONLY;
SELECT payout_id, status, evidence_mode,
       document->'providerBefore' AS provider_before,
       document->'providerAfter' AS provider_after,
       document->>'recipientBefore' AS recipient_before,
       document->>'recipientAfter' AS recipient_after,
       document->>'providerDebit' AS provider_debit,
       document->>'recipientCredit' AS recipient_credit
FROM visa_wallet_balance_change ORDER BY sequence_id;
COMMIT;
~~~

This endpoint is an internal provider harness. Customer UI, orchestration and
application settlement/reconciliation activation remain acceptance work.
