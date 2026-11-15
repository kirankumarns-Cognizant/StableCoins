# ADR-014: Stablecoin-Aware Agentic Payment and Treasury Routing

Status: ACCEPTED

Date: 2026-11-14

Accepted by project owner: 2026-11-14 (explicit acceptance in project conversation).

## Context

The product direction is a reusable agentic stablecoin payment and treasury
platform for cross-border B2B supplier payments. Stablecoins remain central;
routing intelligence enhances the existing USD -> test USDC -> beneficiary
wallet -> optional simulated fiat payout flow.

The hackathon demonstration must connect an actual model-powered recommendation,
real public-testnet USDC settlement evidence, an internal double-entry ledger,
and reconciliation. The existing offline HTML artifact cannot prove any runtime
AI or blockchain capability. Commercial value is a hypothesis to validate with
customers and measured baselines, not an established revenue or savings claim.

The owner requested sequential implementation of the proposed product changes.
AGENTS.md section 17 requires proposing an ADR before implementing an architecture
change. The owner accepted this concrete proposal on 2026-11-14. Other accepted
ADRs remain authoritative. Acceptance authorizes incremental implementation; it
is not evidence that runtime agent or settlement capabilities already work.

ADR-013 is PROPOSED and covers read-only payment investigation only. Routing
recommendations are explicitly outside that proposal. This ADR introduces a
separate intent and routing capability; it does not grant the investigation
agent payment execution authority or imply acceptance of ADR-013.

## Decision

### 1. Preserve the core architecture and settlement journey

Keep React + TypeScript, Java 21 / Spring Boot 3.x, ECS Fargate, API Gateway,
Aurora PostgreSQL, EventBridge + SQS, and CDK + TypeScript. Keep six services.
No seventh service, Kubernetes, Kafka, new token or custom smart contract.

Retain the nine business steps:

1. Customer initiates a USD payment; fiat funding is simulated.
2. Compliance and FX / fee quote are simulated; quote acceptance is explicit.
3. Simulated USD-to-USDC conversion allocates prefunded treasury test USDC.
4. Check token balance, available liquidity, gas estimate and test ETH reserve.
5. Authorize and sign in the protected blockchain execution component.
6. Submit once and durably associate the real hash with payment/correlation IDs.
7. Validate receipt, success and confirmations; post internal settlement entries.
8. Optionally simulate conversion, Visa payout and supplier fiat credit.
9. Reconcile payment, ledger, blockchain and applicable simulated payout legs.

USDC is the payment asset; ETH pays gas. No USDC-to-ETH swap is introduced.
Blockchain confirmation is not evidence of beneficiary fiat credit.
Base Sepolia is preferred; Ethereum Sepolia remains the configured fallback
under ADR-002. Fallback is not permission to send a duplicate payment on another
network when a prior submission has an uncertain outcome.

### 2. Introduce validated payment intent

payment-service owns a versioned intent tied to paymentId, correlationId,
customer scope and authenticated actor. Capture:

- Source amount and source currency (USD in this version).
- Beneficiary, supported destination type and destination currency/asset.
- Required beneficiary outcome: test USDC wallet settlement or simulated fiat
  bank credit; optional allowed alternatives must be explicitly authorized.
- Delivery deadline as an unambiguous UTC instant.
- Maximum total delivery fee with an explicit currency.
- Priority preference, such as lowest eligible cost or meeting a deadline.
- Customer policy version, allowed paths/providers, and approval requirements.

Natural-language extraction is a draft. Validate it against explicit fields;
missing, contradictory or ambiguous requirements require clarification. An
invoice or free-text instruction cannot override server-side customer policy.

Do not repurpose SupportedCurrency to mix fiat currencies with crypto assets.
Keep USD source compatibility; destination USD or INR belongs in a separate
validated payout contract. USDC is a settlement asset, not a fiat currency.
Payment-creation idempotency fingerprints must cover any new material fields.
Existing clients need a documented compatibility strategy and regression tests.

### 3. Define supported paths honestly

| Path | Existing rail mapping | Required beneficiary outcome |
|---|---|---|
| Test USDC wallet settlement | STABLECOIN | Beneficiary accepts test USDC |
| Test USDC settlement plus simulated fiat payout | STABLECOIN_PLUS_VISA | Simulated USD or INR bank credit |

AUTO remains a request to evaluate configured eligible paths, not an executable
rail or evidence of an agent decision. Preserve the existing VISA enum for API
compatibility; its presence does not establish an integrated Visa-only route.
Do not silently reinterpret it as a stablecoin route.

USD and INR payout, including any USD-to-INR FX, remain simulated and require
an implemented simulator and configured quote. No real bank or FX integration
is claimed. Circle official test USDC remains configurable under ADR-011.

Compare paths only when each can deliver the required beneficiary outcome.
A wallet-only path is ineligible when the customer requires fiat bank credit,
unless the customer explicitly allows wallet delivery as an alternative.
If only one candidate is eligible, say so. To demonstrate meaningful routing
within a bank-credit outcome, use explicitly simulated provider variants of
that supported path with different signals and liquidity. These are adapters
or configurations inside existing services, not new microservices.

A conventional bank route, new stablecoin, bridge, exchange, network or real
payout provider is outside this decision and requires a separate scope/ADR.

### 4. Establish a seven-signal evidence contract

Every observation carries signalId, schemaVersion, signalType, candidateId,
customer/corridor scope as applicable, sourceId, sourceRecordReference,
observedAt, receivedAt, expiresAt, value, unit, evidenceMode and quality status.
Timestamps are UTC. Evidence modes are SIMULATED, MOCK, or LIVE; LIVE blockchain
evidence also records network, chainId and testnet status. LIVE testnet does
not mean production money. Invalid, missing, stale, future-dated or contradictory
observations are explicit, not zero-valued defaults.

| Signal | Meaning and units | Candidate scope / important qualification |
|---|---|---|
| Success rate | Completed eligible attempts / total attempts, window, sample size | End-to-end completion; insufficient history is UNKNOWN |
| Latency | Estimated beneficiary-delivery duration, percentile, window and sample size | Include off-ramp and payout; distinguish chain-only latency |
| Transaction cost | Itemized fees and total in a declared comparison currency | Include conversion, FX, gas, off-ramp and payout without double counting |
| Fraud risk | Versioned score, defined scale and screening evidence | Deterministic policy thresholds; simulated screening stays labeled |
| Rail availability | AVAILABLE / DEGRADED / UNAVAILABLE / UNKNOWN | Per network, RPC, provider and beneficiary-delivery capability |
| Liquidity conditions | Total, reserved and available amounts by asset/currency | Treasury USDC, ETH gas and applicable provider fiat liquidity |
| External data | Typed, allowlisted observation with source and validity period | Congestion, price deviation, provider incident, calendar/cutoff |

Only compatible scopes/windows and currency-normalized values are comparable.
Keep FX quote identifiers, rate timestamps and rounding rules. Monetary values
use decimal arithmetic. Preserve native gas/asset units alongside comparisons.
Do not assign a real market value to test tokens or present synthetic production
fee estimates as measured testnet economics.

For the first demonstration, controlled fixtures may drive non-chain signals,
provider availability, fraud, FX and liquidity scenarios. Every such source is
marked SIMULATED. Missing live credentials never silently switch to a fixture
while retaining a LIVE label. Product screens show freshness and provenance.

### 5. Separate agent reasoning from financial authority

Host intent/routing application components inside payment-service. Use a typed
provider interface with an OpenAI-backed implementation as the planned runtime
integration. Select and externally configure the exact model before integration;
verify official API documentation and account access then. No agent framework
or separate deployment is required by this proposal.

The routing agent can choose from bounded, typed read tools to gather intent,
customer policy, candidate eligibility, signal observations, quote, treasury
readiness and deterministic candidate assessments. It may request additional
permitted evidence and revise its recommendation within resource limits. A
single fixed summary or a scripted UI is not represented as this agent loop.

Deterministic components validate units, compute costs and scores, apply hard
gates, and establish eligibility. The model does not invent fraud scores,
latency probabilities, exchange rates, liquidity, hashes or numeric rankings.
Customer-configured priorities and versioned scoring rules determine comparison;
the model explains tradeoffs and proposes among eligible alternatives. A
recommendation inconsistent with required policy priority is rejected.

A structured recommendation contains recommendationId, version, paymentId,
correlationId, intentVersion, policyVersion, evidenceSnapshotId, candidate
assessments, selected candidate (or none), eligibility/rejection reason codes,
itemized total cost, estimated delivery duration, concise rationale, source
references, missing evidence, model/prompt versions, createdAt and expiresAt.
The rationale is an evidence-backed explanation, not hidden chain-of-thought.

A result of NO_ELIGIBLE_ROUTE or INSUFFICIENT_EVIDENCE is valid. The UI must not
force a recommendation. Any uncertainty label is separate from statistically
calibrated confidence; do not display invented confidence percentages.

### 6. Require explicit approval and revalidation

An authenticated operator accepts a particular intent/quote/recommendation
version. Acceptance is not execution by the model. Before financial commitment,
the orchestrator rechecks policy, evidence freshness, fee/deadline constraints,
compliance, provider availability and liquidity, then reserves the needed funds
through deterministic commands. Reservation changes never run as model tools.

Use an auditable recommendation lifecycle separate from payment states:
DRAFT -> PROPOSED -> APPROVED -> EXECUTED, with REJECTED, EXPIRED or SUPERSEDED
outcomes when applicable. These labels are not additions to PaymentStatus.
Bind approval to evidence and versions; prevent replay and concurrent approval.
Expired or materially changed recommendations require a new review. Validate
transitions and execution idempotency transactionally; publish via the outbox.

Quote acceptance remains a distinct existing requirement; a UI may collect
both approvals together only when their versions and actor are recorded.
Only deterministic orchestration may advance QUOTE_ACCEPTED / RAIL_SELECTED
and existing downstream states. The model has no state-mutation capability.

After signing or submission has begun, do not create another financial leg
until the existing operation and any reservation are conclusively resolved.
Unknown submission outcomes trigger evidence inspection, not automatic retry,
network fallback, provider change or a second payout. The agent may investigate.

### 7. Preserve service ownership and reserve liquidity safely

| Owner | Added responsibility inside its existing boundary |
|---|---|
| payment-service | Intent, customer policy selection, evidence snapshot, recommendation, approval, deterministic orchestration and model-provider adapter |
| compliance-quote-service | Simulated screening, FX/fee quotes and configured quote/provider observations |
| blockchain-service | Live testnet readiness, gas, wallet transaction lifecycle and atomic USDC/ETH reservations associated with owned wallet records |
| settlement-ledger-service | Balanced immutable journals and approved accounting mappings |
| reconciliation-service | Deterministic reconciliation using direct database reads under ADR-008 |
| visa-simulator | Simulated payout/provider availability and liquidity behind VisaPaymentAdapter |

Proposed new records are owned by the service above and introduced through that
service's migrations. ADR-007's one shared schema and write ownership remain.
No agent writes another service's tables. Model tools cannot invoke reservations.

Available liquidity excludes active reservations. Reservation commands use unique
operation/payment keys and concurrency control so two requests cannot spend the
same USDC or gas reserve. Provider fiat reservations use the same principle in
the simulator. Define release, expiry and reconciliation behavior; an unresolved
submitted transfer must not lose its reservation merely because a TTL expires.

### 8. Security, failure and audit boundaries

All model calls and tools are bound to authenticated customer/payment scope.
Ignore model-provided authorization claims. No shell, arbitrary SQL/HTTP,
private keys, secrets, signing bytes, payment creation, rail execution, Visa
initiation or ledger write tools. Treat invoice text, external observations
and stored notes as untrusted data, not instructions. Redact sensitive fields.
Use Secrets Manager/KMS for provider credentials and protected signing material.

Bound iterations, tool calls, tokens, time, concurrency and inference cost.
Validate model output and source references. Missing/invalid evidence or model
unavailability returns an explicit incomplete/unavailable result. Deterministic
execution of already approved valid payments must not require model availability;
new agent recommendations may wait or use a separately labeled manual process.

Versioned audit records include actor, decision/evidence references, status,
timestamps and correlation. Events retain eventId, eventType, eventVersion,
paymentId, correlationId and timestamp. New recommendation/approval events must
be specified before publication; existing event meanings are not repurposed.
Do not log secrets, full PII, raw prompts/responses by default, or hidden reasoning.

### 9. Evidence, accounting and reusable commercial offering

The production frontend remains React + TypeScript. Add intent/recommendation,
treasury, signal provenance, route comparison, approval and agent evidence views
to the real application; retain ledger, blockchain and reconciliation screens.
The offline prototype stays clearly marked as an illustrative design artifact.

Resolve the mixed-asset acquisition example versus per-asset balancing conflict
in docs/10-LEDGER.md before posting conversion journals. New clearing accounts or
accounting policy changes require their own reviewed decision. No unbalanced
journal is justified by simulated FX, and an agent cannot change ledger rules.

Demonstrate two fictional customer policy profiles using the same code and
configured adapters. This proves configurable reuse, not production multitenancy
or authorization isolation; those require separate tests and deployment work.

Separate measured application/testnet metrics from simulated provider data and
projected production benefits. Compare equivalent beneficiary outcomes using a
fixed baseline and include inference/provider costs. Candidate commercial models
are implementation/integration, platform licensing and managed operations;
pricing and revenue remain unvalidated hypotheses.

## Alternatives Considered

- Keep deterministic routing only: appropriate baseline, but does not demonstrate
  adaptive model-driven evidence gathering and explanation.
- Let the model directly execute payments: rejected due to financial authority,
  nondeterminism, recovery risk and conflicts with current controls.
- Deploy independent agents/microservices: deferred; not needed for this POC.
- Make stablecoins one incidental option in a generic router: does not satisfy
  the owner's stablecoin payment and treasury product direction.

## Acceptance Criteria

- Structured and natural-language intent paths produce validated, reviewable
  intents; ambiguous requirements and unauthorized alternatives are rejected.
- All seven signal types preserve scope, units, provenance, mode and freshness.
- Fixtures cover congestion, provider outage, low USDC/ETH/fiat liquidity, fraud
  threshold breach, stale/contradictory data and no eligible route.
- Eligible candidates deliver equivalent permitted outcomes; unavailable,
  unsupported or policy-ineligible paths cannot be recommended or approved.
- A real model uses permitted tools, changes its recommendation on relevant
  scenario evidence, and returns verifiable references without financial writes.
- Concurrency, approval replay, stale approval and reservation tests prevent
  over-allocation and duplicate execution. Unknown submission never reroutes.
- Disabling or failing inference is explicit and does not affect monitoring of
  existing transactions; prompt injection cannot expand tools or customer scope.
- A real public-testnet USDC transfer has stored hash, receipt, confirmations,
  network/contract/wallet evidence and matching payment/correlation IDs.
- Journals balance per asset; reconciliation detects receipt/ledger/payout
  discrepancies and never conflates chain settlement with simulated fiat credit.
- React views preserve real/testnet/simulated labels and source freshness.
- Reproducible scenario results compare against a defined baseline without
  invented savings, success-rate uplift or production performance claims.

## Consequences and Next Step

This expands the proposed model scope beyond ADR-013 and introduces new intent,
evidence, approval and reservation contracts without changing deployment count.
It adds inference cost/latency, data freshness, concurrency and evaluation work.
An actual model selection, authorized roles, operational limits, retention and
customer policy defaults must be recorded before enabling runtime inference.

Owner acceptance is recorded above. Implementation proceeds incrementally under
this decision and AGENTS.md; ADR-013 remains separately PROPOSED. Implementation order and evidence gates are tracked in
[docs/21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md](../docs/21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md).


## Implementation note (2026-11-14)

The implementation reuses existing payment identity checks through the non-deployable platform-security Java library. Other services apply the same verified-subject membership contract, scoped internal command authority and read-only payment ownership. This preserves six deployables and the deterministic financial authority boundary; it does not grant agents financial command tools.

## Step 7 implementation note (2026-11-14)

Existing services now capture immutable per-provider simulated quote/signal records
and LIVE/MOCK treasury observations. The existing deterministic assessor selects
an eligible candidate under customer policy; the bounded OpenAI/Bedrock runtime
can explain those captured sources using its existing read tools. Actual model
execution is still opt-in and unverified in this environment. Agent output cannot
approve, debit, sign or submit. Independent approvals and current quote/policy/
execution bindings gate the durable wallet claim. Existing account/outbox,
blockchain reservation/nonce/recovery and ledger posting code remains authoritative.
The connected wallet path ends at BLOCKCHAIN_CONFIRMED; fee-complete settlement,
optional simulated payout and reconciliation remain step 8. This is implementation
of the accepted decision, not an additional service or an expanded agent authority.

## Implementation note - step 8 (2026-11-15)

Step 8 extends approved stablecoin paths to equivalent simulated USD/INR bank outcomes with captured FX and provider fiat availability. The existing seven-signal assessor and bounded OpenAI/Bedrock runtime are reused. The model receives no settlement, signing, ledger, prefund or payout-resolution tool. All financial actions and acknowledgements remain deterministic owner services.

See [lifecycle implementation and limitations](../docs/31-SETTLEMENT-PAYOUT-RECONCILIATION.md). Accepted decision and status are unchanged.

## Step 9 implementation note - 2026-11-15

The connected React workspace reuses existing owner command APIs and adds a
customer-authorized, read-only payment-service evidence projection. Shared-schema
reads follow the accepted ownership boundary; the private Visa endpoint stays
private. UI decimal-string/BigInt display checks preserve per-asset precision,
while the existing ledger, agent authority and approval/execution services remain
authoritative. No architectural decision or accepted status changes. See
[connected UI design](../docs/32-CONNECTED-REACT-WORKSPACE.md).
