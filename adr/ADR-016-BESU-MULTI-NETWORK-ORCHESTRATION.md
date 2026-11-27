# ADR-016: Additive Besu Network and Governed Multi-Network Orchestration

Status: ACCEPTED by owner on 2026-11-25.

Date: 2026-11-25

## Context and conflicts

The owner requested adding Hyperledger Besu alongside Base Sepolia, followed
by orchestration according to explicit preferences. The agreed sequence was
first a real Base Sepolia payment, then explicit network selection, then
policy routing and controlled fallback. The owner has now supplied a successful
Base Sepolia payment, receipt excerpt and RECONCILED application journey.

The present implementation selects one global BlockchainAdapter/Web3j/signer.
BlockchainConfig restricts encrypted-file signing to public testnet chain IDs.
OwnerEvidenceAssembler builds provider candidates from one treasury observation.
Funding, quotes, SupportedAsset, posting mappings and reconciliation currently
assume USDC and ETH. Adding a second URL alone cannot safely implement routing.

This proposal extends ADR-001/002 with an additional private development network,
ADR-011/014 with an explicitly separate demonstration asset, and ADR-015 with
separate asset accounting. Existing public behavior remains authoritative; this accepted ADR adds a separate
private demonstration path. AGENTS.md sections 15 and 17 require an explicit
decision before introducing a custom token contract or changing architecture.
Owner acceptance is explicit in the instruction to proceed with the recommendation
and the subsequent instruction to restart Besu implementation.

## Accepted decision

### 1. Add a network; preserve the public path

Retain the six application services, Java 21, Spring Boot, React/TypeScript,
PostgreSQL, EventBridge/SQS and existing AWS target. Base Sepolia remains the
default public testnet with official Circle test USDC. Ethereum Sepolia remains
its existing separately configured public alternative. Besu is an additional,
opt-in local/Codespaces network; it does not replace either URL or wallet.

Keep MOCK explicitly separate. Selecting an unavailable or unconfigured network
fails with a structured reason; no silent MOCK substitution or default switching.
Do not reinterpret REAL_TESTNET / TESTNET_ENABLED as private-network permission.
Introduce an explicit private-demo capability/enablement gate. Existing public
testnet acceptance gates still require a real PUBLIC testnet transfer.

Besu deployment on AWS, managed consortium operations and production custody are
outside this local addition. No seventh application microservice, Kubernetes,
Kafka, bridge, exchange, real fiat funding or real bank integration is introduced.

### 2. Local Besu infrastructure and durable identity

Use a version-pinned Besu QBFT Docker Compose overlay with four validator nodes
and a separate application RPC node. These are blockchain infrastructure, not
new business services. Four validators permit demonstrating consensus with one
validator unavailable; all nodes on one host do not provide independent failure
domains or production resilience.

Generate private validator identities locally under an ignored, protected path.
Never commit node keys, treasury keys, passwords or funded example private keys.
Keep genesis, network data and deployment metadata persistent across ordinary
stop/start. Initialization refuses to overwrite an existing chain. A reset is a
separate explicitly destructive owner action; ordinary demo scripts never reset.

Proposed local chain ID: 20261125, distinct within this application's registry.
This is not a claim of global uniqueness. Pin networkId, chainId AND genesis hash;
chain ID alone cannot distinguish a reset or another private chain using it.

Restrict RPC to the Compose network; an optional operator mapping binds loopback
and remains Private in Codespaces. Disable unlocked-account signing and unnecessary
admin/debug namespaces. Configure node/account permissioning and explicitly list
deployment/treasury/beneficiary access. Isolation alone is not permissioning or
transaction confidentiality. Do not claim that Besu hides data from its validators.

Before startup, check available RAM/disk/ports and document measured requirements.
Pin supported Besu/EVM milestone and dependency versions during implementation;
do not use floating latest images or copy public tutorial keys.

### 3. Besu asset and funding: accepted narrow exception

Circle's Base Sepolia contract is not automatically present on Besu.
A contract address or identical token symbol does not make assets interchangeable.

Propose one development-only standard ERC-20 token:
- Display name: Besu Demo USD; symbol DEMOUSD; decimals 6.
- Fixed initial supply: 1,000,000 DEMOUSD allocated to a dedicated Besu treasury.
- Minimal fixed-supply implementation using a pinned standard ERC-20 library.
- No upgrade proxy, privileged later mint, redemption promise or transfer tax.
- Record deployment transaction, chain identity, contract and runtime code hash.
- It is NOT Circle USDC, backed USD, redeemable fiat or a bridged asset.

Acceptance of this ADR explicitly authorizes this narrow custom-contract exception
to AGENTS.md section 15 / ADR-011 for the Besu development path only.
The public path continues to use official Circle test USDC.

Create dedicated encrypted Besu treasury/beneficiary wallets, separate from the
verified public wallets. Allocate native development currency in genesis for
deployment and gas. Display it as BESU_GAS, not public ETH or a USD value.
Generate signing material interactively/locally with protected files; no passwords
in arguments or documentation. No public faucets or purchase are needed for Besu.

Token deployment is a one-time explicit setup action after chain identity checks;
never a side effect of application startup or a model tool. Reuse the recorded
deployment on restart. Fail if the contract/code/decimals do not match.

### 4. Server-owned network registry and adapter selection

Inside blockchain-service, introduce a typed registry of network configurations
and adapter contexts. Each entry binds:
- network ID, chain ID, expected genesis and private/public classification;
- RPC configuration, identity checks and confirmation/finality rules;
- settlement asset identity, contract/code hash, decimals and gas asset;
- treasury signer reference and expected address;
- fee model, enabled capability, permissioning requirements and timeouts.

Keep URLs and signer references server-configured; clients/model output cannot
supply arbitrary RPC URLs, chain IDs, token contracts or signing paths.
Keep public-testnet signer restrictions. Add a separately gated private signer
context instead of deleting the existing public allowlist.
Signatures bind the selected chain ID. Transport never blindly retries submission.

A registry entry is more than an RPC URL. Readiness checks actual chain identity,
contract, balances, permissions, block progress and fees. Query the selected
context for every preparation, reservation, submission and receipt operation.

### 5. Stage A: explicit selection and complete end-to-end Besu flow

First implement explicit network/asset selection on a NEW instruction.
Legacy requests retain the documented Base Sepolia behavior; fingerprints include
new material fields and conflicting idempotent replays are rejected.

Keep USDC_WALLET as a USDC-only outcome. Add TOKEN_WALLET with an explicit,
validated accepted-asset/network list for the Besu demo. A USDC-only instruction
cannot route to DEMOUSD. Recipient acceptance is scoped to chain and asset, not
just the same hexadecimal address on different chains.

The confirmed instruction, review, quote, treasury snapshot and approvals bind
the exact selected network, genesis, token, amount, recipient and policy version.
Changing them requires a new instruction or a new approved review where allowed
by that instruction. Never reuse old approvals on materially different evidence.

Extend owner contracts/events/read models so the tuple survives preparation,
execution, journals, closure and reconciliation. Evolve event versions with
explicit legacy handling; missing or unknown identity fails closed rather than
being guessed from the current default configuration.

Pin an execution to its context before signing. A configuration reload, restart,
event redelivery or receipt poll must use its persisted context. Disabling new
work on a network must not redirect monitoring of existing submissions.

### 6. Reservations, concurrency and recovery

Scope native nonce locks to chain identity plus sender; scope token liquidity to
chain, sender and contract. A signer shared accidentally across configurations
cannot cause cross-network reservation or nonce collisions.
Available balances subtract all relevant active reservations atomically.

Preserve payment/execution idempotency. Duplicate or concurrent events do not
produce another signed transfer, debit, journal or payout. Record signed/submitted
transaction identity durably using the existing owner boundary. Keep unresolved
submission reservations pinned beyond ordinary readiness expiry.
Unknown signing/submission outcome requires investigation; no alternate network
transfer, speculative reversal or blind nonce reuse.

Retain history for existing Base payments. Schema changes are additive through
service-owned migrations; no rewriting posted journals or historical asset labels.

### 7. Asset-scoped ledger and fee policy

Preserve ADR-015's current USD/USDC/ETH templates and immutable entries.
Introduce network/contract-qualified asset identity and separate DEMOUSD/BESU_GAS
accounts. Symbols alone are insufficient; two networks' same-named tokens must
not share inventory, reservations or reconciliation identity.

Besu wallet-flow templates follow the same CONTROL-book lifecycle:
- FUNDING and CONVERSION_USD retain simulated USD meanings.
- A separate token-allocation journal debits DEMOUSD treasury control and credits
  DEMOUSD allocation control by the exact approved DEMOUSD quantity.
- Submission moves DEMOUSD treasury control to DEMOUSD in-flight.
- Confirmation moves DEMOUSD in-flight to DEMOUSD delivered.
- Actual network fees debit BESU_GAS expense and credit BESU_GAS clearing.

Each journal balances within one asset; no USD/USDC/DEMOUSD/ETH/BESU_GAS netting.
New account codes and mapping versions are introduced via ledger-owned migrations
after acceptance and validated for asset/type/active status. Models cannot choose
accounts or posting amounts. Simulated allocation does not mint tokens.

For this plain QBFT chain, fee completeness is its configured actual receipt gas
fee model; do not require or fabricate Base L1 fee fields on Besu.
If the chosen private configuration permits actual zero fees, record complete
zero-fee evidence and no fabricated positive expense/zero-value financial journal.
A zero gas-price policy is not assumed merely because consensus is QBFT.
Public Base fee completeness checks remain unchanged.

Reconciliation compares exact network/genesis/token/recipient/amount/receipt and
required journals, plus policy versions and fee model. It must reject a valid
receipt from the wrong network or token. Besu wallet settlement does not imply
a public transaction or real fiat payment. Besu simulated bank/Visa payout is
deferred until its quote/accounting contracts are separately exercised.

### 8. Stage B: policy-based routing and controlled fallback

Implement only after Stage A's private transfer and public regression gates pass.
Customer policy defines allowed network/asset outcomes, preferred order,
cost/deadline constraints, live readiness, available liquidity and permissions.
Capture policy version and evidence provenance. Compare only outcomes explicitly
accepted by the customer.

The deterministic assessor computes eligibility/ranking for network + provider
candidates. Candidate IDs include network and asset to prevent collisions.
Model tools remain read-only; a model may explain eligible choices but cannot
change policy, approve, reserve, sign, deploy or submit.

Controlled fallback means:
- Before a review is approved, unavailable preferred networks may cause selection
  of another explicitly allowed candidate in a fresh versioned review.
- Changing a selected network after review/approval requires refreshed evidence,
  quote and independent approvals. It is not executed under stale approvals.
- Once financial execution/signing starts, keep that execution pinned and resolve
  it. SUBMITTING, SUBMITTED and SUBMISSION_UNKNOWN never trigger another network.
- For USDC_WALLET, Besu DEMOUSD remains ineligible even when cheaper/available.
  Cross-network selection in TOKEN_WALLET requires explicit acceptance of both
  assets; it is a demo capability, not an economic equivalence or bridge.

There is no transparent mid-execution failover. Automated execution of a
preapproved alternative plan would need a further explicit design/approval model.

### 9. UI, operations and evidence

Show selected network, private/public class, token identity and gas currency on
intent, comparison, treasury, quote/approvals, execution, evidence and ledger views.
Private evidence can be LIVE (real RPC/transaction), but must additionally say
PRIVATE_DEMO; LIVE never means public or production. Preserve existing persisted
evidence meanings with additive classification. Do not emit BaseScan links for
Besu. Supply read-only RPC receipt/block checks; a local explorer is optional and
must not become a requirement for confirmation or a new business service.

Extend the central SOP, presenter walkthrough and phased DB queries with separate
public and private branches: initialize once, start, balance/permissions check,
approve, execute, verify receipt/Transfer, inspect ledger, reconcile, stop/resume.
Capture genesis and contract deployment references alongside transaction hashes.
No script automatically spends public funds, resets data or hides pending work.

## Implementation order

1. Shared typed network/asset contracts and backwards-compatible migrations.
2. Isolated version-pinned Besu topology, protected initialization, fixed-supply
   token deployment tooling and read-only network/token preflight.
3. Blockchain registry, private signer gate, selected-context readiness,
   reservations, nonce handling, submission and receipt reconciliation.
4. Explicit payment selection, immutable intent/review/event bindings, React
   labels, DEMOUSD/BESU_GAS posting policy and owner reconciliation.
5. Regression and end-to-end evidence for Stage A; update SOP/start/stop helpers.
6. Versioned policy preferences, multi-network candidate preparation and controlled
   pre-execution fallback; exercise policy and failure scenarios.

Deliver vertical increments with tests; no operational capability is claimed from
scaffolding alone. Do not enable a half-wired Besu option in the product.

## Component isolation

Keep chain setup, token source/compiler/deployment, Compose and setup tests
under infrastructure/besu. Runtime Besu-specific code belongs in a dedicated
besu package within blockchain-service. Shared immutable identities stay in
platform-flow. Orchestration consumes fresh provider/network signals through
server-owned adapters; it does not start/deploy a chain for each instruction.
This records the owner's additional isolation instruction on 2026-11-25.

## Acceptance gates

- Existing Base Sepolia defaults, public signer guards, completed history, receipt
  fee accounting and owner authorization remain intact.
- Wrong chain/genesis/code/asset/recipient, missing permissions, stale readiness,
  insufficient token/gas and incompatible outcomes are rejected.
- One real private-chain token transfer progresses through two approvals,
  exact receipt/Transfer verification, balanced journals and MATCHED reconciliation.
- Existing public-testnet acceptance remains a separate successful public gate;
  local fixtures/Besu transactions cannot satisfy it.
- Concurrent duplicate deliveries and restarts yield at most one payment transfer
  on the pinned network, including unknown submission and nonce outcomes.
- Stage B tests preference changes, unavailable candidates, insufficient liquidity,
  stale evidence and no eligible route; no cross-chain substitution without allowed
  outcome and new approvals. No fallback after signing/submission uncertainty.
- Private stop/start retains chain identity, token deployment, balances and history.
  Validator interruption demonstrates expected quorum behavior and fail-closed
  readiness; one-host results are not claimed as consortium resilience.
- Docker/PostgreSQL tests execute in an available environment; skipped tests are
  reported. Current Windows host lacks Docker/psql, so Codespaces runtime validation
  remains required. Compile/unit tests alone do not establish real Besu settlement.

## Alternatives rejected

Replacing BLOCKCHAIN_RPC_URL globally loses simultaneous routing and history.
Calling a Besu token USDC conflates assets and violates ADR-011.
Using native currency instead of a token does not demonstrate the token journey.
Running only mocked receipts does not establish a real Besu integration.
Silently retrying another chain can double-pay and violates existing controls.

## Acceptance record

The owner accepted the recommendation and explicitly instructed restarting Besu
implementation on 2026-11-25. This accepts the additive private QBFT network, narrow
fixed-supply DEMOUSD exception, separate gas/accounting assets, and staged routing
controls above. Acceptance authorizes implementation; it is not runtime evidence.

## Sources

- [Besu private networks](https://docs.besu-eth.org/private-networks):
  private chains are separate networks; supports permissioning and PoA.
- [Besu QBFT tutorial](https://docs.besu-eth.org/private-networks/tutorials/qbft):
  four-validator educational topology and generated network identities.
- [ADR-011](ADR-011-TEST-USDC.md),
  [ADR-014](ADR-014-STABLECOIN-AGENTIC-ROUTING.md),
  [ADR-015](ADR-015-PER-ASSET-POC-ACCOUNTING.md),
  [AGENTS.md](../AGENTS.md).
