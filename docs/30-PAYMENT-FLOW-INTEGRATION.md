# Step 7 - approved USDC wallet flow integration

Status: wallet-path code implemented; final local verification recorded below.
No connected Codespaces, Cognito, model, AWS deployment or public-testnet run has
been executed by these changes. Optional bank payout, fee completeness and final
reconciliation remain step 8. The nine business steps span both increments.

## Existing services and authority

Keep six deployables. Reuse RecommendationService, its deterministic assessor and
independent human approvals; BlockchainTransactionService for treasury holds,
nonces, signing, one-time broadcast and receipt recovery; PaymentAccountingService
and LedgerPostingService for authoritative per-asset postings. The existing bounded
agent reads the captured recommendations and source references. It has no funding,
approval, signing or submission tool. OpenAI/Bedrock selection remains configurable.

platform-flow is a normal shared Java library, not a service. Its typed contracts,
strict envelope parser and parameterized read models have no financial write
method. ADR-007 permits owner-table reads; each service writes only its tables.

| Owner | New records | Purpose |
|---|---|---|
| payment-service | payment_flow_preparation, payment_execution | Canonical scoped request, operator, immutable instruction and one execution claim per payment |
| compliance-quote-service | compliance_flow_quote, compliance_execution_validation | Immutable per-provider simulated quotes; validation of the selected quote/current configured provider |
| blockchain-service | blockchain_flow_observation | Immutable treasury observation from the existing configured adapter |
| settlement-ledger-service | Existing ledger_journal / ledger_entry | Actual posted allocation/submission/confirmation acknowledgement |

## Connected sequence

1. Existing payment creation and compliance events create the payment and apply
   simulated checks. Duplicate compliance events use INSERT ON CONFLICT rather
   than catching a transaction-aborting unique-constraint exception. Payment locks
   and correlation validation protect the compliance transition.
2. An authorized operator requests preparation with a UUID Idempotency-Key.
   RoutingEvidenceRequested 1.0 fans out to separate quote and treasury queues.
   Owners verify the canonical payment/customer/instruction before capturing data.
3. Compliance captures each configured, customer-permitted simulated provider.
   The default A quote has a USD 2 platform estimate and 120-second delivery
   estimate; B has USD 5 and 60 seconds. These and history, fraud, availability and
   external assumptions are simulation configuration, not measured performance.
   Gas adds a separately itemized simulated USD budget conversion. Treasury
   liquidity retains LIVE or MOCK provenance, real source references and expiry.
4. Payment assembles all seven existing signal types per candidate and invokes the
   existing deterministic assessor. Lowest-cost versus deadline preference can
   choose different providers. The selected quote, full evidence and policy are
   captured in the recommendation. The existing actual-model agent can inspect and
   explain those records when deliberately configured/enabled; no model call is
   claimed by a stub test or by a deterministic recommendation.
5. Existing quote-acceptance and distinct-approver APIs remain mandatory. An
   operator then requests execution of the exact recommendation/quote versions.
   One durable claim per payment prevents a second financial operation.
6. StablecoinExecutionRequested 1.0 fans out to compliance validation and treasury
   preparation. Compliance checks current provider configuration; blockchain waits
   for validation and reuses prepareTransfer for a fresh USDC/ETH hold. The fresh
   hold must retain reviewed network, chain, contract, sender, recipient and mode,
   and cannot increase the reviewed ETH reserve. Changed material requires review.
7. Payment revalidates approval, intent, policy and account/beneficiary, locks the
   simulated USD account, debits it once and marks the recommendation EXECUTED.
   Audited transitions QUOTE_ACCEPTED, RAIL_SELECTED, FUNDING_PENDING and FUNDED,
   plus StablecoinFundingCompleted 2.0, commit in the same payment transaction.
   Amounts remain explicitly simulated fiat funding/allocation; no actual USDC
   acquisition or minting is asserted. The connected simulation quotes 1:1 S/U.
8. Ledger posts FUNDING, CONVERSION_USD and CONVERSION_USDC atomically using the
   existing accounting service. Payment waits for all three POSTED journals, bound
   to payment/correlation, execution/conversion ID, quote/version, funding reference,
   amounts, outcome and SIMULATED mode. Only then does it publish
   BlockchainSubmissionRequested 1.0. Blockchain rechecks the canonical claim,
   current approval, bound hold and journals before using existing submitTransaction.
9. The existing signer/nonce/hash mechanism persists signed identity before one
   broadcast. Receipt polling never rebroadcasts SUBMITTING/SUBMISSION_UNKNOWN.
   A recovered matching mined receipt emits missing submission evidence before
   confirmation, atomically, so ledger recovery has its required predecessor.
   Payment advances BLOCKCHAIN_SUBMITTED then BLOCKCHAIN_CONFIRMED only from
   actual owner evidence and the posted matching confirmation journal. This does
   not mark SETTLEMENT_COMPLETED or RECONCILED; step 8 owns those remaining gates.

```mermaid
sequenceDiagram
    participant P as Payment
    participant C as Compliance/quote
    participant B as Blockchain
    participant L as Ledger
    P->>C: RoutingEvidenceRequested
    P->>B: RoutingEvidenceRequested
    C-->>P: Read immutable simulated provider quotes
    B-->>P: Read immutable LIVE/MOCK treasury snapshot
    Note over P: Capture recommendation, optional bounded agent, independent approvals
    P->>C: StablecoinExecutionRequested
    P->>B: StablecoinExecutionRequested
    Note over B: Wait for compliance validation; reserve through existing service
    Note over P: Revalidate and debit simulated USD once
    P->>L: StablecoinFundingCompleted 2.0
    L-->>P: Read three bound POSTED allocation journals
    P->>B: BlockchainSubmissionRequested
    B->>L: BlockchainTransactionSubmitted
    B->>L: BlockchainTransactionConfirmed
    L-->>P: Read bound confirmation journal
    Note over P: BLOCKCHAIN_CONFIRMED; final settlement/reconciliation remains step 8
```

## Protected HTTP contract

| Method / path | Role and input | Result |
|---|---|---|
| POST /api/v1/payments/{paymentId}/recommendation-preparations | PAYMENT_OPERATOR, UUID Idempotency-Key, no body | 202; requestId and PENDING/READY/review status |
| GET /api/v1/payments/{paymentId}/recommendation-preparations/{requestId} | Same customer's PAYMENT_OPERATOR | Preparation, recommendationId or failureCode |
| POST /api/v1/payments/{paymentId}/recommendations/{id}/execution | PAYMENT_OPERATOR, UUID Idempotency-Key; expectedVersion, quoteId, quoteVersion | 202; durable execution claim or exact replay |
| GET /api/v1/payments/{paymentId}/execution | Same customer's PAYMENT_OPERATOR | Execution status, bound transactionId and review code |

There is no public write API for trusted evidence. Client requests cannot inject
balances, quotes, screening results, provider observations or a transaction hash.
Existing recommendation/approval/agent routes are documented in docs 26 and 27.
A replay of an existing execution returns its identity; a different key, payment,
recommendation or quote version cannot create another operation for the payment.

## Configuration and recovery

All settings are in services/config/connections.properties, with explicit Compose
aliases and per-owner queue/DLQ settings. PAYMENT_FLOW_ENABLED defaults false;
PAYMENT_EXECUTION_ENABLED independently defaults false. Keep the flow enabled and
turn only execution off to continue receipt monitoring of existing signed work.
SCHEDULING_POOL_SIZE defaults 4 so receipt polling does not occupy the sole outbox
thread. PAYMENT_FLOW_PREPARATION_TIMEOUT_SECONDS defaults 120. Polling is bounded
per batch. Readiness defaults 30 seconds; set BLOCKCHAIN_READINESS_TTL=PT5M for the
explicit interactive demonstration, retaining the assessment maximum-age check.

Provider A inherits app.flow.simulation defaults. B overrides fee, latency and
availability through FLOW_PROVIDER_B_FEE_USD, FLOW_PROVIDER_B_LATENCY_MS and
FLOW_PROVIDER_B_AVAILABLE. Other explicit provider IDs can have per-provider keys
in the server-only external configuration. Customer policies still restrict IDs.
Change FLOW_SIMULATION_VERSION for a changed simulation contract. Full outage
requires all configured providers unavailable. No missing live data becomes an
unlabeled simulation fallback.

Three EventBridge rules fan out to five new owner queues. Each has its own DLQ,
120-second visibility timeout and maxReceiveCount 3. All targets receive $.detail.
Partial local fanout may redeliver to an already successful consumer; immutable
request records and existing financial idempotency make that safe. The complete
stack currently defines nine rules and 22 queues including DLQs, not ECS/Aurora.

Expired preparation/approval, changed instruction/treasury material and known
ledger mismatches require review. An already funded failed/expired operation is
not automatically refunded, reversed, replaced or rerouted. Unknown signing or
submission remains pinned. Missing source records/temporary database errors are
retried; inspect exhausted queues. Step 8 must complete evidenced fee posting,
reversion/compensation operations, settlement, optional Visa payout and discrepancy
reconciliation. Production monitoring, pagination and alerting remain step 10.

## Codespaces demonstration

Follow [container setup](18-CONTAINERIZATION-CODESPACES.md),
[identity/customer setup](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md), and
[agent runtime setup](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md) for actual model execution.
Use the configured server-only connection override for verified subjects and the
fictional customer's policy. A configured policy alone never authenticates a user.

The optional seed scripts/seed-wallet-demo.sql creates fictional customer/account/
beneficiary UUIDs 11111111..., 22222222... and 33333333..., with USD 100,000 explicitly
simulated capacity. It requires an explicit beneficiary testnet wallet, inserts
only missing rows, never replenishes existing balances and refuses a recipient
mismatch. Run only in the dedicated POC database after service migrations:

```bash
# Replace the public wallet with the intended beneficiary testnet address.
docker compose exec -T postgres psql -U stablecoin -d stablecoin_poc -v supplier_wallet=0xYOUR_BENEFICIARY_TESTNET_WALLET < scripts/seed-wallet-demo.sql
```

Use your configured DB_USER/DB_NAME if changed. Then set non-secret run inputs and
supply access tokens through secret environment input, never a committed file:

```bash
export DEMO_RUN_ID='RETAIN-A-FIXED-UUID-FOR-THIS-PAYMENT'
export DEMO_DELIVER_BY='SET-A-FIXED-FUTURE-ISO-UTC-TIMESTAMP'
export DEMO_SENDER_ACCOUNT_ID=22222222-2222-2222-2222-222222222222
export DEMO_BENEFICIARY_ID=33333333-3333-3333-3333-333333333333
export DEMO_EVIDENCE_MODE=MOCK
read -rsp 'Operator access token: ' DEMO_OPERATOR_TOKEN
export DEMO_OPERATOR_TOKEN
read -rsp 'Independent approver token JSON array: ' DEMO_APPROVER_TOKENS
export DEMO_APPROVER_TOKENS
node scripts/run-wallet-flow.cjs
```

Before startup, set PAYMENT_FLOW_ENABLED=true, PAYMENT_EXECUTION_ENABLED=true,
BLOCKCHAIN_EXECUTION_API_ENABLED=true and BLOCKCHAIN_READINESS_TTL=PT5M in the
local Compose environment. The existing shared security still protects internal
mutation endpoints. Memberships, valid identity issuer/client and customer policy
must be configured for all relevant service containers. Shared service URLs select
the payment/blockchain/ledger origins; defaults target host ports 8080/8082/8083.
Use existing *_SERVICE_URL variables for forwarded/private Codespaces URLs.

The runner creates/replays the USD 10,000 wallet intent, waits for compliance and
captured recommendation, optionally runs the real agent (DEMO_RUN_AGENT=true),
accepts/approves with the supplied independent identities, executes and waits for
CONFIRMED. It verifies the required journals balance per asset, payment/correlation
and hash bindings, evidence mode and exact execution replay. It never installs a
local auth bypass or creates arbitrary approval votes.

For TESTNET use the existing deliberate testnet Compose/profile override and
funded treasury instructions, set DEMO_EVIDENCE_MODE=TESTNET and provide
DEMO_RPC_URL privately. The runner independently calls eth_chainId,
eth_getTransactionReceipt and eth_blockNumber, requiring a successful matching
receipt, block, configured confirmations and exact token-contract/sender/recipient/
amount USDC Transfer log. A MOCK transaction cannot satisfy this gate. It does
not label testnet tokens or simulated conversion as real fiat.

Optional DEMO_EVIDENCE_FILE writes a new JSON artifact with exclusive creation;
it never overwrites an earlier evidence file. Tokens and RPC URLs are excluded.
Retain DEMO_RUN_ID, deadline and original intent on replay. A new run ID means a
new payment instruction; after timeout/unknown outcome inspect the same payment,
claim and transaction before deciding next action. Do not reset data for recovery.

## Verification and delivery inventory

Local verification results are recorded in the delivery plan. Java tests exercise
contracts, current approval and distinct-vote requirements, provider selection,
source provenance and stale/low-balance/fraud exclusions, one-time simulated debit,
ledger acknowledgement, immutable quote redelivery, fanout replay and signed-operation
recovery. PostgreSQL tests use actual owner migration directories and cover concurrent
funding, transactional rollback and unposted-ledger gating; without Docker these
are skipped, not passed. Node receipt/log fixtures test the verifier, not a chain.

Modified areas: three new workflow packages in payment/compliance/blockchain;
shared platform-flow library; payment approval/account locking and compliance event
consumers; existing blockchain receipt recovery/mock confirmation depth; owner
Flyway migrations; shared configuration, Compose/.env example, reactor/service
POMs/Dockerfiles; CDK rules/tests; workflow/producer/control tests; opt-in runner,
seed and cumulative Markdown/ADR implementation notes. No seventh deployable,
custom token contract or new financial model tool is introduced.

The final consolidated Word architecture/design document remains required at the
end of all implementation increments, with render/visual verification; it has not
been generated or delivered by this step.

## Step 7 verification (2026-11-14)

Java 21 full-reactor `verify` completed successfully and packaged all six services
plus the two shared libraries. Latest Surefire/Failsafe reports, deduplicated by
module/test class, contain 544 cases: **502 passed, 0 failures/errors, 42 skipped**.
The skips are 41 database/container cases and one opt-in real-model evaluation.
These skips do not validate migrations, SQL concurrency or live inference.

The Node wallet-verifier/shared-configuration checks passed **21/21**. CDK TypeScript
build passed and its messaging/container tests passed **10/10**. Receipt tests use
fixtures; they do not prove a public transfer. No Docker images, PostgreSQL stack,
Cognito token flow, model call, AWS deployment or public-testnet wallet flow ran.
See the Codespaces procedure above for opt-in Codespaces setup.

Assumptions remain the accepted six-service/shared-schema/operational-subledger
architecture. No new money-authorizing agent or deployable was added. Next: step 8
fee-complete settlement, optional simulated payout and reconciliation; steps 9-11
cover the connected UI, AWS runtime and final hackathon/documentation delivery.

## Extension in step 8

The wallet-confirmation procedure above remains a focused step-7 demonstration.
The backend now continues asynchronously through fee posting, final settlement,
optional simulated USD/INR payout and reconciliation. For full-business acceptance
use [docs/31](31-SETTLEMENT-PAYOUT-RECONCILIATION.md) and
scripts/run-payment-lifecycle.cjs. A successful BLOCKCHAIN_CONFIRMED response alone
is insufficient; the full runner waits for RECONCILED and verifies owner evidence.
Live Codespaces/testnet acceptance is still required.

## Connected UI continuation - step 9

The connected React workspace now implements the proposed payment screens.
Use [its setup/API/security guide](32-CONNECTED-REACT-WORKSPACE.md) and the
[complete role/context/action/outcome runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md).
Earlier verification records above remain historical; current scope is recorded
in [step-9 verification](step9-build-results.json).

## Intent set comparison correction (2026-11-23)

Consumer approval validation compares allowedOutcomes and allowedProviderIds in
policy, and allowedAlternatives in instruction, as unordered string sets. These
three fields are declared as Java Set values; JSON serialization order can differ
between the reviewed and current snapshots without a business change. Only those
paths are normalized on copies. Other fields and array order remain significant;
missing, non-array, non-string, blank or duplicate set members fail closed.
Current approval, expiry, recipient, preparation, canonical request and current
instruction checks remain mandatory. No stored snapshot or financial record is
rewritten. Regression tests exercise the JDBC approval row mapper and rejection
gates; they do not establish a connected payment or public-testnet result.

After pulling this fix, rebuild/restart backend services. Preserve expired
execution records for audit; create a new payment when its confirmed delivery
deadline has passed, prepare fresh evidence, obtain both independent approvals
on that version, then execute before quote expiry. Do not replay old queue
messages or edit database approvals to resume an expired execution.
