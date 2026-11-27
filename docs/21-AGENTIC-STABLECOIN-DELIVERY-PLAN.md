# Agentic Stablecoin Product: Sequential Delivery Plan

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Current delivery status (2026-11-15): **9 of 11 steps code-complete**.
Connected UI setup and exact screen actions are in [the step-9 guide](32-CONNECTED-REACT-WORKSPACE.md).
The owner has moved step 11 preparation and Codespaces acceptance ahead of AWS deployment. Live identity/model/testnet evidence and the rendered Word document remain completion gates.

Date: 2026-11-15

Status: ADR-014 and ADR-015 accepted. Steps 1-9 have code-level implementations. Step 8 connects actual receipt-fee evidence, final ledger settlement, optional simulated USD/INR payout and immutable reconciliation. Live PostgreSQL, identity, model, AWS and testnet acceptance remain unverified. Step 9 is code-complete. Step 10 now prioritizes local validation before AWS deployment; step 11 preparation is now in progress before AWS deployment.

## Product outcome

A reusable agentic stablecoin payment and treasury platform demonstrates a
USD 10,000 corporate payment, evidence-based path recommendation, protected
public-testnet USDC settlement, an internal ledger and reconciliation. Optional
fiat conversion, INR FX, Visa payout and beneficiary credit remain simulated.

Architecture scope: [ADR-014](../adr/ADR-014-STABLECOIN-AGENTIC-ROUTING.md).
Investigation scope: [ADR-013](../adr/ADR-013-PAYMENT-INVESTIGATION-AGENT.md).
Accepted ADRs and AGENTS.md remain authoritative. A proposed document is not a
claim of acceptance, implementation, a model call or testnet execution.

## Delivery order and completion evidence

| Step | Deliverable | Completion evidence / dependencies | Current status |
|---|---|---|---|
| 1 | Concrete routing architecture and delivery plan | Define supported paths, seven signals, agent authority, approval, ownership and acceptance criteria; owner reviews ADR-014 | Accepted 2026-11-14 |
| 2 | Payment intent and customer policy contracts | Typed USD source / USDC or simulated USD/INR outcome, deadline, fee limit and preference; backwards compatibility, decimal validation, authorization and idempotency tests | Implemented; 61 unit/HTTP tests pass; PostgreSQL test skipped without Docker; HTTP JWT/customer membership implemented in step 5; deployed identity still unverified |
| 3 | Signal evidence and deterministic assessments | Seven typed signals, scope/freshness checks, itemized comparable costs, customer priorities, reason codes and labeled fixtures; tests for unknown/stale/incompatible data | Implemented; 64 new routing tests and 205 Java tests across six services pass; internal read-only assessment, live adapters/API integration pending |
| 4 | Treasury and execution prerequisites | Resolve identifier/event compatibility and configuration issues; safe concurrent USDC/ETH reservations, gas readiness, provider fiat simulation; unit and database concurrency tests | Implemented in existing services; 43 new unit/HTTP tests pass (248 Java total); 9 PostgreSQL tests skipped without Docker, so migrations/concurrency remain unverified |
| 5 | Durable recommendation and approval APIs | Versioned evidence snapshots, eligibility, expiry, approval/revalidation, audit/outbox and replay prevention; no financial model tools | Control boundary implemented; verified identity/customer roles, immutable review material and distinct approvals; trusted producers and execution revalidation now connected by step 7; database tests await Docker |
| 6 | Actual intent/routing agent | Official OpenAI API review, configured model/credentials, bounded typed tool loop, validated output and factual references; stub tests plus opt-in real-model evaluation | Runtime implemented; captured evidence only; live model/database rollout checks pending |
| 7 | Connect the approved stablecoin wallet path across services | Trusted quote/treasury capture, approved execution, simulated debit, ledger-gated one-time submission and receipt recovery; full business closure continues in step 8 | Code implemented; provider comparison, shared commands and owner-table evidence connected. Codespaces/public-testnet execution remains an acceptance gate; see docs/30 |
| 8 | Complete ledger, simulated payout and reconciliation | Resolve approved accounting mappings; balanced immutable journals, USD/INR simulated off-ramp, VisaPaymentAdapter and discrepancy detection; integration tests | Code implemented: complete receipt fees, six/nine exact journals, provider reservation before funding, VisaPaymentAdapter payout and immutable reconciliation. PostgreSQL and live end-to-end acceptance pending; see docs/31 |
| 9 | Production React / TypeScript experience | Intent/recommendation, evidence, treasury, approval, ledger and reconciliation connected to APIs; responsive/accessibility and API failure checks | Code complete; local checks passed; live acceptance pending |
| 10 | AWS deployment and operational readiness | Infrastructure, authentication, authorization, Secrets Manager/KMS, JSON logs, observability, timeout/retry/DLQ and deployment checks | In progress: local-first verification/startup tooling added; Docker/payment acceptance pending; AWS implementation/deployment deferred until local validation |
| 11 | Hackathon proof and reusable offering | Two customer profiles, baseline comparison, controlled disruption scenario, genuine AI + chain + ledger screenshots and evidence bundle; explicit simulated/projected labels | In progress before AWS deployment: Codespaces launch guide, commercial hypothesis, customer policy examples and reusable seed prepared; connected proof and rendered Word artifact pending |

Steps are reviewable increments, not claims that existing implementations are
absent. Extend current code and verify its behavior before replacing anything.
Do not postpone identifier compatibility, secure signing or recovery defects
until the final demonstration. No real-model or testnet test runs by default.

## Step 2: implemented intent and policy foundation

Implemented optional intent and server-owned policy contracts in payment-service.
See [22-PAYMENT-INTENT-CONTRACTS.md](22-PAYMENT-INTENT-CONTRACTS.md) for API boundaries, configuration, persistence and compatibility details:

- Separate source currency, settlement asset and beneficiary payout currency.
- Capture explicit deadline, fee cap, destination type and permitted outcomes.
- Bind customer policy version and approval requirements server-side.
- Include material new fields in the idempotency fingerprint.
- Resolve the current create behavior that copies source into destination currency.
- Keep automatic intent extraction a proposed draft requiring confirmation.

This increment does not claim AI capability. Model integration follows typed
contracts and a deterministic, tested eligibility baseline. At step 2 the HTTP controller could not establish trusted identity and failed
closed for intent operations. Step 5 now verifies Cognito access tokens and
server-owned membership for both intent and legacy payment APIs; deployed
Cognito connectivity remains unverified.

## Known issues and design decisions to resolve

- Step 4 preserves PAY-/CORR- strings through blockchain/event/ledger contracts, with UUID string compatibility. PostgreSQL identifier migrations still require execution; legacy unresolved operations are quarantined for review.
- Application, Docker and Compose ports for blockchain (8082) and ledger (8083) are aligned by the shared configuration increment; six Compose wiring tests check the defaults.
- Verify existing integration tests actually exercise enabled behavior; a receipt
  lookup or health check is not an end-to-end test USDC transfer.
- The former cross-asset acquisition example is resolved by accepted ADR-015.
  Approved conversion, fee and payout control accounts are implemented; each asset balances independently.
- The current PaymentRail enum includes VISA, but enum existence is not evidence
  of a working Visa-only route. This delivery scope keeps stablecoin settlement
  central and adds no conventional bank adapter.
- Only USD is currently accepted as source. INR is a separately configured,
  simulated destination FX/payout requirement, not a change to USD funding.
- Match beneficiary outcomes when comparing routes. For a bank-credit intent,
  demonstrate two labeled simulated payout-provider variants; wallet delivery
  cannot win by omitting a required fiat delivery leg.

## Hackathon evidence checklist

1. Confirm the USD 10,000 intent and show supporting signal provenance.
2. Invoke the real model; display permitted tool activity, candidate assessments,
   concise rationale and evidence references. Codex's development contribution
   is documented separately from the OpenAI-powered runtime capability.
3. Change a controlled provider/latency/liquidity signal before commitment; show
   evidence gathering, a revised eligible recommendation and operator acceptance.
4. Execute one actual test USDC transfer on the configured public testnet and
   show a verifiable hash, receipt, success and required confirmations.
5. Show balanced internal journals, optional labeled simulated fiat payout and
   reconciliation. Inject a mismatch and retain a non-reconciled result.
6. Show an unknown-submission investigation without blind retry or rerouting.
7. Apply another fictional customer's policy using the same implementation.
8. Compare equivalent outcomes with a fixed baseline; distinguish real measured
   timings/costs, simulated provider observations and projected commercial value.

## Commercial validation

Start with banks and enterprise treasury teams as candidate users. Interview
potential buyers about corridor pain, prefunding, off-ramp availability,
operational exceptions and integration constraints. Validate willingness to pay
for reusable integration, platform licensing or managed operations. Do not
claim product-market fit, production multitenancy or revenue from a POC demo.

## Validation of step 1

Documentation-only change. No Java, React, deployment, inference or blockchain
runtime is modified. Check local Markdown links, required scope/contract sections
and existing ADR statuses. Runtime compilation/tests are not applicable to this
increment and must not be reported as executed.


## Step 2 verification (2026-11-14)

Java 21 payment-service compilation and packaging succeeded. Maven verify ran
61 unit/HTTP tests with zero failures/errors. The targeted PostgreSQL
PaymentIntentPersistenceIT was discovered but skipped because Docker is unavailable;
its migration/database round trip has not executed. No real model or blockchain
transaction was invoked. Existing PaymentApiIT was not included in the targeted
integration run and is not represented as passing.

At completion of step 2, the next increment was step 3. It is now implemented
as recorded below. No further architecture acceptance is required for work
already covered by accepted ADR-014.


## Step 3 verification (2026-11-14)

Implemented the seven-signal evidence contract and read-only deterministic
assessor inside payment-service. See
[24-SIGNAL-EVIDENCE-AND-ROUTE-ASSESSMENT.md](24-SIGNAL-EVIDENCE-AND-ROUTE-ASSESSMENT.md)
for typed values, provenance, freshness, hard gates, full delivery costs, priority
ranking, fixture labels and integration boundaries. Ten explicit simulated
scenarios cover normal behavior and controlled disruptions. Rule thresholds and
limits use the existing shared configuration and Compose allowlist.

Java 21 compiled and packaged all six services. All 205 Java unit/HTTP/configuration
tests passed with zero failures/errors/skips, including 64 new routing tests.
Every jar contains the current shared connection file. Five Node configuration
tests passed. Database integration tests and live signal/model/blockchain execution
were not part of this increment. The module has no public endpoint, financial
write or production fallback to fixtures. Durable recommendation/approval and
live evidence adapters remain later work.

At completion of step 3, the next increment was step 4. Its implementation and
remaining verification gates are recorded below. The earlier step 2 authentication
and PostgreSQL verification gaps remain open.


## Step 4 verification (2026-11-14)

Implemented treasury/execution readiness by extending the existing blockchain
adapter, signer, transaction/wallet records and outbox; added the planned
VisaPaymentAdapter/VisaSimulatorAdapter inside the existing visa-simulator.
Existing ledger confirmation handling now preserves opaque payment/correlation
identifiers. See [25-TREASURY-AND-EXECUTION-READINESS.md](25-TREASURY-AND-EXECUTION-READINESS.md)
for ownership, migration compatibility, gas budget assumptions, reservation
lifecycle, durable submission claims, safe recovery and configuration settings.

Java 21 compiled and packaged all six services. The final suite discovered 257
cases: **248 passed, zero failures/errors, nine skipped**. The skipped cases are
five TreasuryReservationPersistenceIT and four ProviderLiquidityPersistenceIT
cases, explicitly skipped by Testcontainers because Docker is unavailable.
They compile but did not execute migration, locking or commit-boundary behavior.
The 43 new unit/HTTP tests passed, alongside the earlier 205 tests. Six Node
shared-configuration tests passed. CDK TypeScript compiled and both Jest suites
passed (seven tests), retaining the existing nonfatal ts-jest TS151002 warning.

The execution controller is default-off; readiness is internal read-only evidence.
No model call, blockchain RPC/transfer, AWS deployment, actual payout or bank
credit was run. The existing local testnet signing provider is retained behind
a protected signer interface; managed Secrets Manager/KMS signing remains open.
The configured extra test ETH allowance is not complete measured Base data-fee
evidence. These limitations prevent claiming production or full testnet readiness.

Next: step 5 durable recommendations and approval APIs, including authenticated
customer membership before exposing them. Step 6 implements the bounded runtime
intent/routing agent, using validated evidence/read tools. Neither deterministic
assessment nor treasury controls are represented as an AI agent. Real database
concurrency/migration execution, managed signing, full fee evidence and the full
nine-step testnet journey remain explicit verification/release gates.


## Step 5 verification (2026-11-14)

Implemented the durable review/approval control boundary in existing
payment-service, reusing intent, policy, deterministic evidence/assessment and
the transactional outbox. All payment HTTP now requires verified Cognito access
tokens and server-owned customer membership/roles, including legacy payment
operations. See [step 5 implementation and configuration](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md).

A full Java 21 reactor build compiled/packaged all six services successfully:
290 tests passed, zero failures/errors, and 24 PostgreSQL cases skipped because
Docker is unavailable. A subsequent additional legacy ownership regression
passed, bringing the aggregate executed unit/HTTP/configuration total to **291**
(43 new tests in step 5). Final packaging of all six services also succeeded.
The aggregate reports contain 315 discovered cases: 291 passed and 24 skipped.
The skips are five new recommendation persistence/concurrency/rollback cases,
nine existing HTTP/PostgreSQL cases, one existing intent persistence case, five
treasury cases and four simulated-provider cases. Skips are not database proof.
Seven Node configuration tests passed. CDK TypeScript compiled and two Jest
suites/seven tests passed; existing nonfatal ts-jest TS151002 warning remains.

Step 5 stores human review; APPROVED is not permission to commit funds. Approval
reassesses the captured, still-valid evidence and current intent/policy/compliance
state. Trusted quote/evidence producer integration and fresh execution-time
checks/reservations remain step 7. No public quote/evidence insertion endpoint or
fixture fallback was introduced. The current recommendation producer is explicitly
deterministic, not an AI agent. Real identity connectivity/deployment, PostgreSQL
migrations/concurrency, managed signing, public-testnet transfer, React experience
and approved accounting/payout/reconciliation integration remain unverified/pending.

Next: step 6 actual bounded runtime intent/routing agent, using typed read tools,
validated output and deterministic policy checks; financial mutations remain in
protected application commands. No model, live Cognito, blockchain RPC/transfer,
AWS deployment, fiat movement or beneficiary bank credit was invoked in step 5.


## Step 6 implementation (2026-11-14)

Added the actual bounded Responses API tool loop inside payment-service, including
unconfirmed source-grounded intent drafts, strict recommendation validation,
read-only customer-scoped tools, durable idempotent asynchronous runs and explicit
provider/cost/deadline limits. Existing policy, assessment, evidence, human review
and approval commands are reused. See [runtime implementation/configuration](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md).

The same runtime supports direct OpenAI or supported OpenAI models on Amazon
Bedrock through configuration; Bedrock IAM role/SigV4 and Secrets Manager bearer
authentication are separate from financial signing. Neither live provider access
nor a particular model's suitability has been verified. Model choice remains
external and inference is disabled by default. Source evidence remains captured,
not a live quote/readiness poll. No financial command is exposed as a model tool.

All six Java services compiled/packaged successfully, including the final provider
extension. Unique Java reports contain 401 discovered cases: **371 passed**, zero
failures/errors and **30 skipped** (29 PostgreSQL cases without Docker and one
opt-in real-model case). This total deduplicates old Surefire/Failsafe reports;
it does not count skipped cases as passes. The new agent package contributes
80 executed unit/HTTP/provider checks. Nine Node configuration tests and seven
CDK Jest tests passed; infrastructure TypeScript compiled. Final packaging used
skipTests after successful verification and is not an additional test run. ZIP
inspection confirms all six executable JARs contain byte-identical current
shared connections.properties. No live provider, identity, AWS deployment or
public-testnet transaction was executed.
Next: step 7 service integration, subject to the accounting decision below and
explicit deployment/access gates.


## Accounting decision resolved (ADR-015)

[ADR-015](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md) was accepted by owner on
2026-11-14. The ledger now implements the approved control-book mappings,
atomic funding/conversion commands, gas and simulated payout templates, exact
replay validation and immutable database guards. See [ledger design](10-LEDGER.md).
Java 21 ledger verify passed: 67 unit/API checks passed, ten PostgreSQL cases
skipped without Docker. Ten shared configuration checks passed. Database execution
and complete service integration remain unverified/pending; steps 7-11 are not
complete. The old proposal gate no longer blocks authorized implementation.

## Final documentation deliverables

Maintain the [solution architecture/design](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md)
and [prototype execution runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md) at every step.
The final delivery must cover all implemented features, exact configuration and
execution steps, failure demonstrations, genuine evidence and remaining gates.
ADR-015 is now ACCEPTED by owner on 2026-11-14; its accounting implementation is
in progress. Earlier proposed-status notes above describe the prior review gate,
not the current decision status.


### Documentation coverage tracking

The final architecture/design and prototype demonstration documents are required
deliverables, maintained throughout implementation. Stable feature IDs F01-F10
link design coverage to demonstration/evidence coverage. Every increment updates
configuration/security, contracts, actual verification, expected evidence and
remaining prerequisites. Final completion requires actual diagrams and executable
procedures for every implemented feature, with unverified live steps clearly
identified. See the [coverage table](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md#documentation-coverage-and-final-delivery-gate)
and [per-increment runbook rules](29-PROTOTYPE-EXECUTION-RUNBOOK.md#feature-traceability-and-per-increment-updates).


## Final Word document requirement

At final implementation delivery, produce a separate Word document named
`StableCoins-Solution-Architecture-and-Design.docx`, consolidated from all relevant
repository documentation, accepted ADRs and actual implementation evidence. It
must cover earlier, current and subsequent increments, not only the latest step.
Include revision/date, source-document/ADR index, clear current-versus-target
status, diagrams, configuration/security, APIs/events/data, agent boundaries,
accounting, deployment/operations, verification and limitations. Resolve obsolete
examples against accepted decisions; do not silently present proposed ADR-013 as
accepted. Preserve the Markdown documents as living sources and refresh the Word
artifact for final delivery. Render and visually inspect every page before release.
The full demonstration runbook remains separately maintained and is cross-referenced.
This Word artifact is a tracked final deliverable; it has not yet been generated.

GitHub Codespaces is the intended container execution environment because the
current local machine lacks container capability. Use the existing dev-container
and [container/Codespaces guide](18-CONTAINERIZATION-CODESPACES.md); actual remote
container, database and integrated feature runs must be recorded when executed.


## Earlier verification record - access/container increment (2026-11-14)

All six Java applications and the shared security library compiled/packaged in a
full Java 21 Maven verify run. 453 Java cases passed, zero failures/errors, 39 skipped (PostgreSQL/Docker and one opt-in real-model check).
Totals deduplicate Surefire/Failsafe reports by module/test class, selecting the
latest report. Eleven shared configuration checks and nine CDK checks passed.
The six packaged JARs include byte-identical current shared configuration and the
security library. Dev-container JSON, six Docker build contexts and updated local
documentation links were checked without starting containers.

This increment reuses identity/membership validation, protects internal commands
and customer ledger/chain evidence, and updates applicable API/security/service/ADR
notes. Codespaces retains the existing Docker-in-Docker setup; global placeholder
AWS credentials and the incorrect container localhost default were removed.
The sample LocalStack endpoint is blank so Compose uses its service DNS default.

Assumptions: six deployables; accepted ADR-007 permits read-only ownership joins;
internal callers use a distinct Cognito client plus scope; ADR-015 is an operational
control ledger, not a full custody GL. Remaining work includes service-token
acquisition, trusted quote/funding producer and ledger acknowledgement, payout and
reconciliation wiring, connected React UI, complete AWS infrastructure, and actual
Cognito/model/PostgreSQL/public-testnet execution. The final Word deliverable is
tracked and not yet generated. That record preceded step 7; see the current step-7 section below for updated status.


## Step 7 - connected approved wallet execution

See [integration design and executable demonstration](30-PAYMENT-FLOW-INTEGRATION.md).
The existing services now capture per-provider simulated signals and LIVE/MOCK
treasury observations, reuse deterministic assessment and bounded read-only agents,
require independent approvals, debit simulated USD once and wait for three bound
POSTED allocation journals before signing/submission. Receipt recovery preserves
signed identity and supplies missing predecessor evidence. Wallet payment status
stops at BLOCKCHAIN_CONFIRMED; fee-complete settlement, optional bank delivery and
reconciliation are step 8. UI, AWS runtime provisioning and final hackathon/Word
delivery remain steps 9-11. Code-level count is 7/11, not seven live demonstrations.

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
See [wallet integration](30-PAYMENT-FLOW-INTEGRATION.md) for opt-in Codespaces setup.

Assumptions remain the accepted six-service/shared-schema/operational-subledger
architecture. No new money-authorizing agent or deployable was added. Next: step 8
fee-complete settlement, optional simulated payout and reconciliation; steps 9-11
cover the connected UI, AWS runtime and final hackathon/documentation delivery.

## Demonstration delivery format

The living [runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md#opening-narrative-the-complete-payment-story)
now begins with the overall story and then ten screen-oriented scenes. Each
identifies roles, purpose, reason before the click, outcome and evidence. Screen
labels are proposed until connected UI step 9; final execution/capture is step 11.
Keep this format in subsequent increments and the final Word deliverable.

## Step 8 coding and verification - 2026-11-15

Steps **1-8 of 11** now have code-level implementations. Three delivery steps
remain: connected React experience (9), completed AWS deployment/operations (10),
and recorded end-to-end proof, commercial/demo package and final Word architecture
and design document (11). Step 10 already has partial shared security/messaging
foundations. Code completion does not establish live acceptance.

The final Java 21 full reactor `verify` succeeded. Combined with the targeted
blockchain verification, latest XML reports counted once per module/test class
contain **600 cases: 546 passed, zero failures/errors, 54 skipped**. Those skips
are **53 container/database cases and one opt-in real-model evaluation**. Visa
and reconciliation database suites are now bound to `verify`; blockchain DB
suites were explicitly discovered in the targeted run. None of these skipped
cases establishes SQL migration, locking, rollback or six-service acceptance.

The synthetic loopback HTTP test actually exercised Web3j/Spring Jackson
compatibility and raw receipt fee parsing. Recovery tests verify filling a missing
price only for the same mined transaction, rejection of conflicting known evidence,
and no additional signing/submission. All data in that HTTP test is synthetic.

Node verifier/configuration tests passed **30/30**. CDK TypeScript build passed;
its infrastructure/configuration tests passed **11/11**. All six packaged JARs
were checked for the current common configuration and shared workflow library.
Markdown relative-file links and fenced-code balance were checked.

Docker images, PostgreSQL execution, live Cognito, real model inference, AWS
deployment and a public-testnet end-to-end transfer did not execute locally.
[Machine-readable results](step8-build-results.json) retain the exact test scopes.
[Implementation, configuration, APIs and demo/recovery procedure](31-SETTLEMENT-PAYOUT-RECONCILIATION.md)
cover the completed increment. The existing agent runtime and deterministic
financial services are reused; the model receives no money-authorizing tools.

## Step 9 coding and verification - 2026-11-15

Steps **1-9 of 11** are code-complete. Two delivery steps remain: completed AWS
deployment/operations (10), then live acceptance/proof, the commercial/hackathon
demo package and final cumulative Word architecture/design document (11).

The connected React workspace implements authenticated customer views, confirmed
USD intents, seven-signal provider evidence, actual bounded-agent run records,
quote/version acceptance and independent approvals, treasury gas readiness,
authorized execution, chain receipts/fees, immutable journals, reconciliation and
optional simulated payout. Existing owner services and financial controls are reused.
The new payment-service read projection checks ownership before cross-owner reads;
the Visa simulator stays private. Connection settings remain centralized, including
public identity endpoints and the configured chain/explorer metadata.

The Java 21 full-reactor `verify` succeeded: **602 cases, 555 passed, zero failures/
errors, 47 skipped**. Those skips are 46 Docker/database cases and one opt-in model
evaluation. The latest combined historical reports contain 609 cases with the
same 555 passes and 54 skips; the extra seven are previously discovered/skipped
blockchain container cases, not new execution evidence. All six service packages
were refreshed after the final public configuration update and checked for the
current shared configuration and workflow library.

The frontend production build and lint pass without warnings. The combined Node
suite passes **45/45**. The headless browser rehearsal passes **19/19**
checks against an explicitly synthetic HTTP fixture. It exercises an uncertain
creation response, persistent warning and same-key replay; server-role UI gates;
separate approvers; agent display; execution; honest MOCK evidence; exact ledger
amounts; safe configured explorer links; mobile layout and session clearing.
Desktop/mobile synthetic screenshots were inspected; they are not live proof.

No Docker stack, PostgreSQL integration, live Cognito/model, AWS deployment or
public-testnet end-to-end transfer executed. See the [machine-readable scopes](step9-build-results.json),
[UI implementation/setup](32-CONNECTED-REACT-WORKSPACE.md) and
[complete role/screen/action runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## Step 10: local-first execution order

By owner instruction, validate the application locally before planning AWS deployment.
See [local-first validation](33-LOCAL-FIRST-VALIDATION.md) for staged gates, commands,
limitations and current environment. Step 10 remains in progress; do not count
local fixture success as live acceptance or a completed AWS deployment.


## Local-first increment: executed verification

The final local helper run completed PASSED_WITH_SKIPS: **555 Java tests passed,
47 skipped, zero failures/errors** (602 cases in this invocation). The skips are
46 Docker/PostgreSQL cases and one explicitly optional live-model case. The strict
database gate is NOT_SATISFIED. All **54 Node tests and 12 CDK tests passed**;
React production build/lint and infrastructure TypeScript build passed. All six
packaged applications contain the current shared connection file and security
library. The 64 Markdown files have no broken local file links or unclosed fences.
See [the machine-readable local record](step10-local-build-results.json).

A real startup attempt stopped at the missing Docker/Compose prerequisite before
invoking Compose. Six-service startup, migrations/concurrency, authenticated
payment closure, actual model and public-testnet acceptance remain unexecuted.
No AWS deployment occurred. Step 10 is in progress; nine of eleven delivery steps
remain code-complete. The earlier browser checks remain synthetic step-9 evidence
and were not rerun for this local tooling increment.

## Step 11 preparation before AWS deployment

Owner instruction on 2026-11-15 moves the demo package and Codespaces acceptance
ahead of AWS application deployment. GitHub destination is
https://github.com/kirankumarns-Cognizant/StableCoins. AWS account is 640237069320
and application Region us-east-1, using IAM Identity Center. SSO directory Region
and assigned permission set still require confirmation; no credentials are stored
in documentation. Identity users and prefunded testnet wallets still need setup.

[Codespaces launch instructions](34-CODESPACES-DEMO-LAUNCH.md) join the existing
verification, configuration, identity, seed, UI and testnet guides.
[The hackathon package](35-HACKATHON-PROOF-PACKAGE.md) defines two fictional
customer profiles, comparable deterministic/model evidence, a controlled simulated
provider outage and explicit commercial hypotheses. Both profiles reuse the same
six services, independent approval controls and authoritative financial ledger.
The wallet seed accepts optional customer/account/beneficiary identifiers, preserves
balances and rejects conflicting bindings. No new adapter, microservice or agent
financial authority is introduced; accepted ADRs remain unchanged.

Nine of eleven steps remain code-complete. Step 11 is not complete until actual
Codespaces/database, identity, model, testnet, two-customer and disruption evidence
is captured and the cumulative Word document is rendered and visually checked.
The local Windows host has no Docker or document renderer. The previously executed
Java/Node/CDK results above are historical verification, not new live execution.

## Codespaces verification progress (2026-11-15)

Owner-reported prerequisites now pass after restoring the published npm manifests
and locks. Docker API compatibility is corrected in shared Maven test settings.
Actual database execution exposed response JSON decoding and integration-fixture
isolation defects; fixes are locally checked and awaiting a fresh Codespaces run.
Treasury reservation persistence is now explicitly required by normal verification.
Delivery remains 9 of 11 steps code-complete; steps 10 and 11 are open. The live
identity/model/testnet demonstration, final Word document and AWS application
deployment remain pending. See [verification details](33-LOCAL-FIRST-VALIDATION.md#codespaces-verification-findings-2026-11-15).
