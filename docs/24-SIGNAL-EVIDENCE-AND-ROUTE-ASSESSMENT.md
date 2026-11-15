# Seven-signal evidence and deterministic route assessment

ADR-014 delivery step 3 is implemented inside payment-service. The new internal
`DeterministicRoutingAssessmentService` evaluates typed evidence using server-owned,
versioned rules. It performs no HTTP calls, inference, payment state transitions,
reservations, signing, submission, payout or accounting writes. There is no new
microservice, database migration, public assessment endpoint or authentication bypass.

## Boundary and inputs

A trusted caller supplies `AuthorizedCustomer`, a previously validated
`PaymentIntentSnapshot`, `AssessmentContext`, and an immutable evidence `Snapshot`.
The context carries payment/correlation IDs, customer and corridor, USD instruction
amount, USDC transfer amount, gas ETH requirement, requested rail, and explicit
simulated fiat requirements by outcome. These must be obtained server-side from
the persisted instruction, validated quote and gas estimator. The current increment
does not implement those live adapters or establish quote authenticity. Never
construct these inputs directly from client/model assertions. Authentication and
customer membership still must be connected before exposing intent or assessment APIs.

Missing identity or cross-customer context/evidence fails before results are returned.
Malformed snapshot envelopes, unknown candidate references and duplicate global
signal IDs are rejected. Candidate and observation counts have configured bounds.
No current payment creation, consumer or legacy endpoint invokes this assessor;
its future integration belongs to recommendation/approval work.

## Evidence contract

`routing/RoutingEvidence.java` contains the seven sealed, typed signal values:

| Signal | Required value and deterministic checks |
| --- | --- |
| Success rate | Completed/attempted eligible deliveries, sample size and historical window; end-to-end scope and minimum sample count. The threshold compares counts using decimal arithmetic. |
| Latency | Positive end-to-end delivery milliseconds, percentile, sample size and historical window; chain-only timing cannot prove fiat delivery. |
| Transaction cost | Seven explicit fee components, native assets/amounts, USD comparison amounts and conversion references; missing and duplicate components or mismatched totals invalidate evidence. |
| Fraud risk | Score on the fixed 0?100 scale, model version and screening reference; screening remains SIMULATED. |
| Rail availability | Separate network, RPC, provider and beneficiary-delivery statuses; UNKNOWN is insufficient evidence, UNAVAILABLE or DEGRADED is ineligible. |
| Liquidity | Treasury USDC, gas ETH and applicable provider fiat pockets; each includes total, reserved and available. Available must equal total minus reserved. |
| External data | Four allowlisted kinds: network congestion (milliseconds), absolute stablecoin price deviation (basis points), provider incident and calendar/cutoff (0/1 flags). Unknown, blocking or invalid values are explicit. |

Every top-level observation retains ID, schema version, type, candidate, customer /
corridor / instruction-amount scope, source ID/reference, observed/received/expiry
UTC timestamps, unit, SIMULATED/MOCK/LIVE mode, quality and network/chain/contract/
testnet context. The native context is mandatory, including for LIVE evidence;
LIVE testnet observations do not establish real fiat delivery. Bank-outcome end-to-end
observations must remain SIMULATED in this baseline. The supported chain
tuples remain Base Sepolia and Ethereum Sepolia; a candidate must match the
configured primary tuple. Assessment never initiates network fallback.

A candidate requires exactly one usable observation of each signal type. Missing,
unknown, malformed, contradictory, future-dated, expired, wrong-scope, wrong-unit,
wrong-network, incompatible-window or insufficient-sample evidence does not become
a zero value. Duplicate type observations are ambiguous; the assessor does not
silently choose the newest. External observations and conversion quotes also have
independently validated timestamps and expiry. All four external kinds are required
in this baseline; adapters must report UNKNOWN rather than omit unavailable data.

## Cost normalization and labels

The complete comparison budget includes FUNDING_CONVERSION, FX, GAS, OFF_RAMP,
PAYOUT, PLATFORM and INFERENCE, exactly once each, including explicit zero lines.
The budget must cover the complete required delivery outcome. It is a simulated
planning estimate, not an actual charge or production price observation.

Native USD amounts must match their comparison amounts directly. Every other asset
requires a quote ID, source asset, positive USD-per-unit budget rate, timestamp,
expiry and explicit simulated-budget flag. Multiply using BigDecimal and round
each converted line to two USD decimals using HALF_EVEN; then sum the rounded
lines and compare the declared total. Preserve native amounts alongside the USD
estimate. Test ETH/USDC are never assigned a real market valuation by this module.
GAS includes a positive native ETH estimate no larger than the caller's explicit
ETH reserve requirement. Gas and provider liquidity still require re-estimation
and atomic reservations before execution in later steps.

## Eligibility and ranking

Customer policy provider/outcome restrictions, explicitly permitted alternatives,
requested rail, configured testnet, deadline, complete fee cap, minimum success,
maximum fraud, availability, available USDC/ETH/fiat, external blockers and price
deviation are hard gates. Invalid or insufficient evidence returns reason codes;
a future model cannot override them.

Ranking is lexicographic within each beneficiary outcome:

- LOWEST_COST: total delivery USD cost, then delivery duration.
- MEET_DEADLINE: delivery duration, then total delivery USD cost.
- Remaining ties: higher observed success ratio, lower fraud score, then stable
  candidate ID. No weighted or invented confidence score is produced.

All eligible candidates already satisfy the deadline. Required-outcome candidates
are preferred before explicitly allowed alternative outcomes. If only one alternative
outcome group remains, the baseline can identify its preferred candidate; if multiple
alternative outcome groups remain without the required outcome, return
OUTCOME_CHOICE_REQUIRED without comparing unlike deliverables.

Result statuses are ASSESSED, NO_ELIGIBLE_ROUTE, INSUFFICIENT_EVIDENCE or
OUTCOME_CHOICE_REQUIRED. Each candidate is ELIGIBLE, INELIGIBLE or
INSUFFICIENT_EVIDENCE, with typed findings. A known hard failure makes a candidate
ineligible even if other evidence is missing. Results include payment/correlation,
intent/policy/rule/snapshot versions, assessment and expiry times (capped by evidence/quote freshness and latest safe
delivery start), per-outcome
rankings, itemized cost, metrics, evidence references and modes. An empty candidate
set yields insufficient evidence. `preferredCandidateId` is an internal baseline
assessment, not a durable recommendation, selected payment rail or approval.

## Configuration

All rule defaults are in the existing shared
[`services/config/connections.properties`](../services/config/connections.properties):

| Setting | Baseline default |
| --- | --- |
| ROUTING_ASSESSMENT_VERSION | baseline-v1 |
| ROUTING_MAXIMUM_EVIDENCE_AGE | PT5M |
| ROUTING_HISTORICAL_WINDOW | PT24H |
| ROUTING_MINIMUM_SAMPLE_SIZE | 100 |
| ROUTING_LATENCY_PERCENTILE | 95 |
| ROUTING_MINIMUM_SUCCESS_RATE | 0.95 |
| ROUTING_MAXIMUM_FRAUD_SCORE | 30 |
| ROUTING_MAXIMUM_PRICE_DEVIATION_BPS | 100 |
| ROUTING_MAXIMUM_CANDIDATES / ROUTING_MAXIMUM_OBSERVATIONS | 20 / 200 |

These are illustrative server-owned baseline settings, not empirically calibrated
production risk limits or customer SLAs. They must be reviewed for a deployment.
Change the rule version when changing thresholds or comparison semantics. Rules
are validated at bean creation; invalid settings fail startup. Customer policy
snapshots still control allowed providers/outcomes, fee ceiling and approvals;
the confirmed intent controls deadline, tighter fee limit and priority. Shared
configuration/external overrides and the explicit Compose environment allowlist
apply as described in [the configuration guide](23-SHARED-CONNECTION-CONFIGURATION.md).

## Controlled scenario fixtures

`SimulatedRoutingScenarios` is an explicit factory, not a production feed bean or
an automatic fallback. It creates two fictional provider variants for the same
outcome, with all seven signals and every source labeled SIMULATED. The normal
scenario has a cheaper/slower provider A and a more expensive/faster provider B.
Bank-INR budget totals are illustrative USD 5.30 and 8.30; wallet budgets are
USD 2.80 and 5.80. The ETH/USD budget rate and every other numeric fixture are
assumptions, never measured fees, market prices, production success or savings.
The factory requires an explicit positive payout amount for bank scenarios.

Scenarios: NORMAL, CONGESTION, PROVIDER_OUTAGE, LOW_USDC, LOW_ETH, LOW_FIAT,
FRAUD_BREACH, STALE_EVIDENCE, CONTRADICTORY_LIQUIDITY and NO_ELIGIBLE_ROUTE.
Disruptions affect provider A; the final scenario makes both unavailable. The
congestion scenario changes end-to-end latency, so it is not added a second time
as an independent delay during ranking.

## Verification and remaining work

Unit tests exercise the scenario matrix, priority changes, freshness/quality/scope,
full cost and conversion validation, reserved balances, outcome compatibility,
customer isolation, no forced route, immutable/JSON contracts and external rules.
These tests use fixed clocks and require no network, database or wallet.

Next: treasury readiness and concurrent reservations, payment/event identifier
compatibility, durable recommendation/approval snapshots, authenticated APIs,
real signal adapters and bounded model integration. Database concurrency, live
provider/blockchain evidence, actual model calls, testnet execution and deployment
are not established by this increment.

Validation on 2026-11-14: all six Java services compiled and packaged, with 205
Java tests passing (including 64 new routing tests), zero failures/errors/skips.
The shared configuration was verified in all six jars. Five Node shared-config
tests passed. No live inference, RPC, fiat, deployment or database integration
was executed by this increment.
