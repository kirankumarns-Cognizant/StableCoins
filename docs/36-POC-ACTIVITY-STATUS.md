# POC activity status

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Recorded 2026-11-23. **Owner-observed MOCK USDC wallet walkthrough completed through RECONCILED. Overall POC acceptance remains in progress.**

## 27 November 2026 - Besu database acceptance evidence gate

- Continued the owner-approved sequence: Besu payment acceptance and separate Base Sepolia regression before issuer-lifecycle expansion. No shared business refactor or issuer contract change.
- Found ordinary blockchain Maven verify selected only TreasuryReservationPersistenceIT, omitting BesuLiquidityPersistenceIT. Added the private PostgreSQL suite to the ordinary build; live private readiness and public testnet profiles remain separate.
- Expanded strict workbench verification to 19 required database suites, including nine Besu suites and Visa wallet persistence. Fresh reports retain integration/unit provenance; the gate rejects missing, skipped, empty, failed, duplicate or wrong-origin required evidence. Structured per-suite reasons are saved under databaseEvidence in report.json. A source-inventory regression check guards against silently omitting new IT suites; two explicit live-chain suites and the existing excluded/disabled legacy BlockchainServiceIT fixture are classified separately (legacy review remains D09).
- Executed verification-tooling regressions: **46 tests passed, zero failures, zero skips** across local-workbench, database-evidence, startup-status and failure-diagnostics suites. Real PostgreSQL, Besu chain, authenticated UI and Base Sepolia regression were not executed on this host; Docker/psql remain unavailable. B01-B10 and D01 remain open against their existing closure criteria.
- Updated the Codespaces handoff, staged application guide and personal knowledge notes. Customer private creation remains hard-gated; no credentials changed, chain started, transaction submitted or runtime worker enabled. The owner will execute Codespaces gates from the published source. Maven validate for blockchain-service and shared dependencies passed at 19:51:47 Asia/Calcutta. The first offline attempt failed because Failsafe 3.2.5 was not cached; the approved online retry succeeded. This is configuration validation, not Java integration-test execution. GitHub publication is verified separately in the ignored handover.

## 27 November 2026 - Scope comparison and GitHub publication preparation

- Reviewed both network paths and the supplied issuer intent. Added [the dated comparison and Codespaces handoff](42-NETWORK-COMPARISON-AND-ISSUER-SCOPE.md): eight path differences; sixteen functional groups (nine new core issuer, six extensions, one deferred institutional integration); three separately counted assurance/delivery groups. Counts are a stated grouping, not an industry certification or a task-completion percentage.
- Updated knowledge notes with ERC-20 versus issuer responsibilities, network/token/application policy boundaries and chain-specific assets/fees/evidence. I01-I10 remain the issuer backlog; no issuer contract or real compliance/custody integration was implemented in this documentation increment. Clarified B10's staged activation/acceptance bridge and the need to inspect every Besu database-suite result.
- Confirmed the ancestor workspace repository points to a different project. Publication is restricted to the separate ignored StableCoins checkout. Read-only GitHub checks established StableCoins push permission; Codespaces access returned HTTP 403. The owner explicitly elected to pull and execute Codespaces validation personally. No Codespace connection, pull, container operation or test execution is claimed here.
- Publication validation: isolated snapshot contains 277 changed/new files; eight ignored generated CDK JS/type outputs were excluded. Wallet/password/.env/private handover and chain-state paths are excluded; a key-marker match was verified as a dummy redaction-test fixture. Scoped staged whitespace and comparison/knowledge local-link checks passed. CDK tests initially failed local-helper resolution in the sandbox; outside the sandbox **15 tests passed across two suites**. Existing current-source Java/frontend/Besu results are recorded in the step-5 entry; no new chain/database tests were executed by this publication preparation.
- Git fetch initially failed inside the sandbox with Schannel credentials unavailable; the outside-sandbox attempt required a command-scoped safe.directory exception for the isolated checkout's ownership. Fetch then succeeded against StableCoins main. No global Git safety setting or unrelated repository was modified. Publication result is recorded separately after verification.

## 27 November 2026 - Besu step 5 staged application and lifecycle integration

- Added a default-off, customer-scoped private-settlement application facade/controller. Existing owners prepare the quote/review, claim the exact approved version and authorize only source-bound recovery. Private evidence queries whitelist fields and exclude signed transaction bytes and secret paths. Scope checks precede private-owner access; recovery requires the existing independent approver rules.
- Added dedicated persisted PRIVATE_DEMO views for routing, treasury, approvals, execution, receipts, ledger and recovery. DEMOUSD and BESU_GAS remain distinct from public USDC/ETH; private receipts have no public explorer links. Existing private claims disable repeated execution. New TOKEN_WALLET instruction creation remains hard-gated in both server and UI until B09/B10 acceptance.
- Added `infrastructure/besu/application.compose.yml`, `application.cjs` and [the staged operator guide](../infrastructure/besu/APPLICATION.md). The ignored variables/configuration files contain references and identity metadata, never password contents. Preparation verifies existing deployment metadata and refuses conflicting files. Start verifies the pre-initialized chain before application startup. Stop preserves infrastructure, volumes and signed evidence; resume suppresses private new funding and first submission. Existing public mode must be selected explicitly and is retained. Added missing default-off capability environment mappings in `services/config/connections.properties`.
- Executed validation: focused Java owner/controller tests **12 passed**. Full affected Java reactor **1268 cases, 1267 passed, one optional model skip, zero failures/errors**, finished 2026-11-27T10:59:41+05:30; all affected modules succeeded. Frontend **39 tests passed**, production build and lint passed. Besu setup/lifecycle suite **29 cases, 28 passed, one Windows-specific POSIX permission skip**, zero failures. Frontend checks include server-rendering all private tabs, not authenticated browser acceptance.
- Validation history: sandboxed focused Maven dependency resolution failed against its offline cache; the same focused build passed outside the sandbox. A new lifecycle assertion initially searched unrelated process environment text for `init`; it was corrected to inspect issued command arguments, then the complete 29-case suite passed. Ignored logs: `.local/validation/besu-application-focused.log`, `besu-application-regression.log`, `besu-application-node.log`.
- No Docker or PostgreSQL executable is available on this host. Compose runtime/configuration, real PostgreSQL migration/rollback/concurrency/atomicity, authenticated browser actions, private-chain delivery/revert/restart and public Base Sepolia regression were **not executed**. These remain B07-B10 acceptance gates in [the central backlog](40-IMPLEMENTATION-BACKLOG.md). No runtime configuration file generation, migration, wallet/password change, chain start, token deployment, transaction, publication or customer activation occurred.
- Architecture assumptions: ADR-016, existing six business services, a pre-initialized private QBFT network and existing dedicated encrypted wallets; Base Sepolia remains the public default. Source changes are grouped under payment private API/tests, shared connection flags, frontend components/models/tests and isolated Besu application tooling/docs. The backend-worker versus AI-agent distinction is captured in the knowledge notes.

## 27 November 2026 - Besu step 4: conclusive failure and authorized control compensation

Observed validation completed: 2026-11-27T10:02:24+05:30.

- Added payment-owned, repeatable-read failure continuation: only an exact, completely accounted conclusive Besu revert moves the funded instruction to FAILED. State/history, qualified audit outbox and immutable failure acknowledgement commit together.
- Added explicit internal recovery authorization using current server-verified customer PAYMENT_APPROVER membership, independent of the original funding operator. The grant binds to the failed execution and exact source hash; it never arises automatically from a scheduler.
- Added ledger-owned append-only BLOCKCHAIN_REVERT_RELEASE and immutable compensation acknowledgement. The release moves DEMOUSD in transit back to treasury CONTROL and links the original submission journal. Original posted journals, simulated USD funding/conversion/allocation and actual BESU_GAS expense remain intact. It neither refunds USD nor retries a transaction, and the payment remains FAILED.
- Added bounded failure/compensation workers, separate default-off recovery/worker flags, qualified audit routing, payment migration V20 and ledger migration V10. A deferred ledger constraint prevents committing the reserved release journal type without its authorized acknowledgement. No public workflow command consumers or existing public defaults changed.
- Initial focused run: 44 tests passed at 09:53:31 IST. Final expanded affected Java reactor: **1256 cases, 1255 passed, 1 optional model skip, zero failures/errors**. Shared identity/flow, payment, blockchain, ledger and reconciliation modules all succeeded. All added integration-test sources compiled. Logs: ignored `.local/validation/besu-revert-recovery-focused.log`, `besu-revert-recovery-regression.log`, and structured `besu-revert-recovery-summary.json`.
- Validation history: the sandboxed compiler failed with `Cannot close compiler resources` / unknown compilation error before tests; the same focused build succeeded outside the sandbox. An expanded test initially made an invalid assumption that changing an opaque nonempty persisted actor ID was inherently invalid. The fixture now tests a missing actor; current role/customer/independence checks are covered separately. The final expanded regression passed after that correction. The initial failure log is preserved locally.
- Six additional PostgreSQL cases (three payment owner, three ledger) cover failed-state/source/independent-actor guards, immutable acknowledgements, rollback and denial of a generic unacknowledged release. They **were not executed**: Docker and psql are unavailable locally. Ledger ITs use minimal foreign-owner storage fixtures, not complete payment/chain migrations or live evidence. Compile success is not runtime acceptance.
- Updated central B04-B06/B08, settlement bindings and learning notes. No runtime migration, container activation, wallet/password change, mint, chain transaction, push or deployment occurred.

Current acceptance and next integration work remain only in [B06-B10 of the implementation backlog](40-IMPLEMENTATION-BACKLOG.md): actual PostgreSQL/private/public acceptance and B07 authenticated UI/isolated Compose/start-stop-resume integration. This increment does not establish customer Besu readiness or a customer-refund policy.

## Observed milestone

The owner supplied Codespaces health output, application screenshots and text,
a two-approval quote summary, execution references, a read-only PostgreSQL query
result and a completed audited payment journey. This is an observed application
run reported by the owner, not a synthetic browser fixture. The coding agent did
not independently connect to the Codespaces database or replay the payment.

| Activity | Recorded result |
|---|---|
| Local stack readiness | Owner reported READY, 9/9 components at the startup checkpoint; not continuous monitoring |
| Payment instruction | USD 1,000 to 1,000 USDC; USDC_WALLET |
| Initial compliance | COMPLIANCE_APPROVED; screening explicitly simulated |
| Recommendation preparation | READY / Preparation completed; deterministic provider assessment ASSESSED |
| Preferred provider | SIMULATED-PROVIDER-A |
| Treasury observation | READY, MOCK; network label base-sepolia, chain ID 84532 |
| Quote | Quoted fee USD 2.21; quote version 1 |
| Review approvals | Review version 2, APPROVED, 2/2 recorded |
| Execution validation | Database output reported VALIDATED |
| Execution | Database output and execution references reported CONFIRMED, no failure code, linked internal transaction UUID |
| Payment journey | SETTLEMENT_COMPLETED followed by RECONCILIATION_PENDING and RECONCILED |
| Terminal audited event | Nov 23, 2026, 9:06:16 AM GMT+05:30 (Asia/Calcutta) |
| Optional fiat/Visa payout | Not applicable to this USDC_WALLET run |

The supplied audited journey was:

CREATED -> COMPLIANCE_PENDING -> COMPLIANCE_APPROVED -> QUOTE_ACCEPTED ->
RAIL_SELECTED -> FUNDING_PENDING -> FUNDED -> BLOCKCHAIN_SUBMITTED ->
BLOCKCHAIN_CONFIRMED -> SETTLEMENT_COMPLETED -> RECONCILIATION_PENDING -> RECONCILED.

During execution, the blockchain consumer logged COMPLIANCE_VALIDATION_PENDING.
A subsequent database read showed validation completed and execution CONFIRMED;
the journey then reported RECONCILED. The pending validation was transient for
this run. No manual database edits, approval bypass or repeat execution were
requested during diagnosis. Detailed run identifiers and the owner-supplied
timeline are retained privately in ignored .local/poc-status/ checkpoint files,
following the evidence handling rules in the
[proof package](35-HACKATHON-PROOF-PACKAGE.md#evidence-acceptance-and-remaining-completion-gates).
Raw customer evidence, credentials and full logs are not included in this record.

## Meaning and limits

The observed result supports completion of the MOCK wallet application workflow,
including its recorded approvals, asynchronous execution and terminal
reconciliation state. The application reported receipt/ledger acknowledgement and
matched reconciliation evidence. Detailed journals and the immutable
reconciliation report have not yet been exported and independently reviewed for
this checkpoint. No new accounting correctness test is claimed.

Funding, conversion and compliance screening are simulated. MOCK blockchain
evidence is synthetic even when it carries a Base Sepolia network label. An
internal transaction UUID and CONFIRMED status do not establish a public-chain
transfer. The existing real-public-testnet requirement in ADR-001 remains open.
No successful runtime AI/model invocation was supplied for this review.
Deterministic provider assessment alone is not an AI recommendation.

The runtime Git/image revision was not captured with this evidence. Do not infer
which commit ran from the current repository HEAD. This run used USD 1,000; the
primary USD 10,000 demonstration remains a separate acceptance activity.

## Application shutdown verification - 2026-11-23

The owner ran `node --env-file=.env scripts/local-workbench.cjs stop --show-failures`
in Codespaces. The supplied output reported **STOPPED, 9/9 components stopped,
Result: PASSED**. React stopped first, followed by all six backend services,
LocalStack and PostgreSQL. The command reported retaining containers, volumes
and recorded payments. This records the owner's runtime result; no independent
post-stop database inspection or restart/persistence check is claimed.

A subsequent `gh codespace list` showed the Codespace as `Available`. This is
consistent with stopping the application while leaving its development host
running. The stop command does not stop the Codespace or AWS resources.

To resume the application, use the workbench start command and check readiness.
Payment history persistence across that restart remains to be verified.

## Next activities

1. Inspect Blockchain evidence and Ledger & reconciliation, and privately export
   the completed payment's mode, receipt details, journals, report and decisions.
2. Retain this completed payment as audit history; do not execute it again.
3. Complete a separately configured real runtime-model call and capture its run
   evidence before claiming AI inference.
4. Complete an explicitly authorized public-testnet payment and independently
   verify the public hash, receipt, token Transfer log, recipient, amount and
   confirmations.
5. Capture the primary USD 10,000 scenario and the separate optional simulated
   bank/Visa payout and second-customer scenarios where required.
6. Complete the remaining database verification, recovery, AWS deployment and
   final evidence/deliverable gates in the existing runbooks.

This checkpoint supersedes earlier blocked MOCK wallet walkthrough statuses for
that path only. It does not mark the entire POC, live inference, public-testnet
settlement, optional payout, or AWS application deployment complete. No
architecture, service, database, permission or financial-rule changes are made.

## Local encrypted-keystore implementation - 2026-11-24

Local/Codespaces signing now supports an encrypted V3 keystore and a separate
password file through the existing WalletSigningService boundary. The file provider
requires local profile (rejects aws-dev), rejects mixed/incomplete sources and the
known MOCK signer, checks the decrypted public address against an explicit expected
address, and restricts signing to the two accepted public testnets. Legacy raw-key
tooling remains available; ordinary startup keeps the existing two-flag MOCK guard.
An explicit docker-compose.testnet.yml overlay selects Base Sepolia and mounts
file-backed secrets for blockchain-service only. No image contains a wallet/password.

Java 21 compilation and 16 focused signing/configuration tests passed with zero
failures/errors/skips. These tests generated disposable fixture wallets and used
no owner secrets or public RPC. The first sandboxed build failed in compiler resource
cleanup; the approved rerun outside the sandbox passed. Bash syntax validation passed
for the hidden-input password preparation helper. Docker Compose configuration was
parsed/asserted in a unit test; actual Compose merge, secret mounting/container
startup and public-testnet settlement were not executed on this Windows host.

This is a local source implementation, not evidence of GitHub publication, Codespaces
activation or AWS secret integration. See the Codespaces encrypted-keystore activation
section in the central SOP. Next: distribute/review the change in Codespaces, inspect
outstanding work before restarting consumers, create the protected password file,
validate Compose/runtime signer address and fee coverage, then execute a fresh approved
1-test-USDC payment and independently verify its receipt and reconciliation.

## First real Base Sepolia payment: owner evidence - 2026-11-25

The owner supplied database output showing execution and chain CONFIRMED,
compliance validation VALIDATED, evidence_mode LIVE, a nonempty transaction hash
and no execution failure. Their public explorer excerpt shows Success, block
63118174 and an official Base Sepolia USDC Transfer of exactly 1 token from the
new treasury to the new beneficiary. The application journey subsequently records
SETTLEMENT_COMPLETED at 00:08:38 IST and RECONCILED at 00:08:41 IST (Nov 25).
Two approvals applied to the same v1 review; source instruction was 1 USD,
transfer 1 test USDC, $3 fee ceiling, observed simulated provider cost $2.02.

This is owner-provided runtime and explorer evidence. The agent did not
independently query RPC for this receipt or rerun the owner database. Sequencer
inclusion/receipt confirmation is not a claim of independently checked Ethereum
finality. Compliance, fiat conversion, provider pricing and payout remain simulated.
The quoted provider fee is not automatically charged as an actual USD expense
under ADR-015; actual network fee accounting is separate.

A prior $0.10-ceiling instruction had no eligible provider at the observed costs.
Another creation attempt was uncertain during a host interruption: the corrected
read-only query joined payment through account.customer_id and found only the
earlier instruction before the successful new payment was created.
Codespace recovery restarted the stack successfully, 9/9, using the explicit
TESTNET overlay. Preserve the completed payment; do not resubmit it.

Private payment/correlation/review/execution/hash and wallet references remain
in ignored handover records. Evidence export/PDF preservation is requested but
not confirmed. Next: retain the proof package and rehearse the phased SOP;
Besu remains a later additive architecture change.

## Copy-and-paste presenter procedure - 2026-11-25

Added docs/38-COPY-PASTE-DEMO-WALKTHROUGH.md as the ordered companion to the
central SOP: configuration, dependency/pending-work checks, start/status/signer
verification, exact form values, phased SQL after each user action, receipt/Transfer
checks, ledger/closure evidence, export, stop/resume and failure recovery.
Added scripts/demo/db-check.sh and eight phase SQL files; no stored procedure,
database migration, financial write API or architecture change is introduced.
A read-only receipt verifier checks exact official USDC Transfer bindings and
canonical block/depth, with fixture tests. Test/runtime scope is recorded below.
Publication and execution of new helpers in Codespaces are separate from local edits.

Local validation: 19 Node tests passed, 0 failed, 0 skipped. This includes
mocked Docker/psql argument forwarding, identifier rejection, failure propagation,
Bash syntax for all walkthrough command blocks, relative document links, and
receipt/balance RPC fixtures. SQL was checked against repository migrations and
owner read models; PostgreSQL execution was NOT performed in this Windows
session (Docker and psql unavailable). No new public transaction was submitted.
New receipt verification has not yet been run against live RPC. These changes
are local; GitHub publication and Codespaces DB rehearsal remain outstanding.

## Besu architecture proposal - 2026-11-25

Owner requested starting additive Besu and orchestration. Repository inspection
found one configured adapter/context, public-testnet signer restrictions and
USDC/ETH-specific contracts/accounting. Added PROPOSED ADR-016 covering an
additional private QBFT network, explicitly separate DEMOUSD/BESU_GAS assets,
network-pinned execution, explicit selection then policy routing and controlled
pre-execution fallback. This extends ADR-001/002/011/014/015 and requires review
under AGENTS.md sections 15/17, including the custom development-token exception.
No application code, migrations, running containers, wallet material, financial
state or existing transactions were changed. No Besu runtime/compile test is
claimed for a documentation-only proposal. Next: owner accepts/amends ADR-016,
then implement vertical increments and execute the documented acceptance gates.

## Besu foundation accepted and implemented locally - 2026-11-25

The owner explicitly accepted ADR-016 and requested resuming implementation.
ADR-016 is now ACCEPTED. The owner further specified keeping Besu isolated and
invoking its adapter when orchestration selects that path from fresh signals.

Added infrastructure/besu for the standalone four-validator QBFT plus separate
RPC topology, pinned tool dependencies, protected initialization, dedicated
verified-wallet launcher, fixed-supply DEMOUSD source/compiler, explicit durable
deployment/recovery, read-only preflight and operating guide. Chain identity
includes genesis; DEMOUSD and BESU_GAS remain separate from public USDC/ETH.
Added typed shared network identities and an immutable registry primitive that
rejects context changes on a pinned execution and never silently falls back.
No runtime beans, product selection, existing public adapter/configuration,
historical payments, posted journals or database schema were changed.

Observed local validation: 13 Node tests passed, 0 failed/skipped, including
wrong chain/genesis/validator/token identity, halted blocks, gas coverage,
redacted RPC failures and no duplicate deployment after uncertain submission.
DEMOUSD compiled successfully for London with solc 0.8.30/OpenZeppelin 5.4.0.
Java 21 compiled the wallet launcher without executing it. Maven's offline
platform-flow build succeeded: 9 new identity/registry tests passed, no failures,
errors or skips. The full shared module regression subsequently passed all 54 tests.
No dedicated wallets generated, Besu nodes started, contract deployed, private
payment submitted, Docker/PG runtime test or GitHub publication occurred.
Docker and psql were not found on PATH. Compose/image/runtime compatibility,
resource sizing, permission rejection, quorum and persistence need Codespaces.

Implementation is a foundation increment, not completed Besu payment support.
Next: runtime validation of the isolated setup; add dedicated besu adapter package
inside existing blockchain-service, server-owned registry wiring, network-scoped
reservations and immutable selection/review/execution bindings, distinct ledger
and reconciliation policies, then explicit UI selection. Stage B policy routing
starts only after private end-to-end and separate public regression gates.

## Besu Java runtime adapter increment - 2026-11-25

Continued ADR-016 in a dedicated blockchain-service/besu package. Added immutable
private settings, a bounded/redacted RPC transport with one network attempt even
for HTTP Retry-After, dedicated encrypted-keystore signing, pinned-context adapter,
read-only observation/runtime holder and separately gated local Spring configuration.
Identity checks bind chain ID, genesis, four current validators, token bytecode,
symbol, decimals and fixed supply. Signing constrains sender, token, recipient,
quantity and gas. Receipt checks verify canonical block/transaction and exact
Transfer evidence; private fees are execution-only with explicit zero-fee support.

Existing public adapter/signing/fee code and financial schemas were not changed.
The Besu holder is not a second default adapter/signer bean. New private payment
creation remains disabled until full approved-context persistence, reservations,
DEMOUSD/BESU_GAS accounting and reconciliation are integrated and accepted.
No Besu UI selection or new payment HTTP endpoint is enabled.

Added the explicit besu-readiness Maven profile and BesuReadinessIT. It calls
actual private RPC with a signer that cannot sign, checks block progress and
one-token capacity, and fails for missing configuration instead of silently
skipping. Added a read-only manifest metadata exporter and infrastructure/besu/
RUNTIME.md with copy/paste acceptance commands. Exporting metadata does not enable
application execution or expose passwords, keystore content or keys.

Observed validation:
- Full affected offline Java reactor unit run: BUILD SUCCESS; platform-flow 54,
  platform-security 22, blockchain-service 140; 216 total, no failures/errors/skips.
- Final isolated transport/observation hardening: BUILD SUCCESS; all 43 Besu tests
  passed, no failures/errors/skips (these supersede the earlier 41 Besu results).
  This includes a real loopback HTTP test proving one server request for a 503
  Retry-After: 0 response and strict JSON response handling.
- Besu setup/tooling: 15 Node tests passed, no failures/skips.
- BesuReadinessIT compiled successfully; live execution NOT performed.
- Docker and psql are unavailable on this host. No PostgreSQL integration,
  running Besu, real private transfer or new public-testnet regression transaction
  was executed. Cached older integration reports are not current runtime proof.

Changes remain local and unpublished. No owner wallet/password was read, no
network/application container started/restarted, no funds moved and no financial
records changed by this increment. Unit tests used disposable fixtures only.
Next: publish reviewed sources into the intended repository/Codespace, run the
isolated setup and explicit read-only Java acceptance on a Docker host; then
complete immutable payment/review/event bindings, network-scoped durable
reservations, dedicated journals/reconciliation and explicit TOKEN_WALLET UI.
Stage B policy routing remains gated on private end-to-end and public regression.

## Besu settlement binding storage increment - 2026-11-25

Added shared SettlementBinding Instruction/Review/Execution records and strict
canonical persistence/fingerprinting. Material includes network/chain/genesis,
classification, token contract/code/decimals, treasury, gas asset/fee policy,
customer/payment/correlation, recipient, separate USD/token quantities, outcome,
fee caps and exact review/quote/preparation versions. USDC_WALLET rejects private
DEMOUSD. Native gas reserves are not presented as USD fees.

Added payment-owned V13 append-only snapshots and a lower-level owner DAO.
Parent records must match exactly; payment customer/correlation and an active
owner transaction are required. Exact replay does not overwrite terms. The DAO
is not a workflow bean or HTTP endpoint and grants no approval or signing
authority. Its future callers must retain existing authenticated owner checks,
quote acceptance and independent approvals in the same transaction. Current
USDC APIs/events and historical JSON were not changed or backfilled.

Observed validation:
- Offline payment-service affected reactor test run: BUILD SUCCESS; platform-flow
  63, platform-security 22, payment-service 323 tests, 408 total; 407 passed,
  no failures/errors, one existing RoutingAgentLiveTest skipped because
  RUN_REAL_MODEL_TESTS was absent. The initial fixture-quoting compile error was
  corrected before this successful run.
- Final explicit JSON-coercion hardening: focused BUILD SUCCESS; 10 shared binding
  and 9 owner-store tests passed, zero failures/errors/skips. These 19 focused
  tests overlap the full run; do not add the runs to claim distinct test totals.
- The latest DAO and PostgreSQL acceptance sources were included in compilation.
- SettlementBindingPersistenceIT was NOT executed. It requires Docker and tests
  V13 against disposable minimal owner tables, including concurrency, immutable
  rows, exact parents, null references and transaction rollback.
- Docker/psql were not available on PATH. No full PostgreSQL migration-chain,
  Codespaces container, live Besu/private transfer or new public-testnet
  transaction acceptance was performed.

Integration remains intentionally incomplete: server-resolved explicit selection
on new intent, canonical review/approval and versioned event wiring, chain/asset
scoped reservations and durable submission claims, DEMOUSD/BESU_GAS journal policy,
owner reconciliation and UI acceptance are pending. Besu payment selection stays
disabled. No running stack, owner wallet/password or financial data was accessed
or changed. Sources remain local and unpublished.

See [the binding handover](../infrastructure/besu/SETTLEMENT-BINDINGS.md) for the
activation contract and exact unit/PostgreSQL acceptance commands. DEMOUSD is a
project-specific demo asset using ERC-20, not an industry-standard stablecoin.

## Besu owner intent integration - 2026-11-25

Implemented the first owner-workflow connection for explicit settlement material:
- Optional customer selection contains network ID, token contract and quantity;
  the server resolves full context through its registry and customer allowlist.
- Canonical intent retains resolved context, quantity and original recipient.
  Payment creation flushes its JPA owner row and captures the append-only
  Instruction in the same transaction before history/outbox publication.
- Explicit selection participates in idempotency; legacy fingerprints and JSON
  retain their exact shape when selection is omitted.
- Registry/allowlist configuration is empty by default. Private selection is
  hard-blocked pending complete Besu acceptance, even if configured enabled.
- Bound instructions cannot enter the legacy USDC preparation/proposal/approval
  revalidation/execution workflow or debit USD through its worker. No UI option
  or token-aware execution event has been activated.

Observed validation:
- Focused run BUILD SUCCESS: 84 tests, no failures/errors/skips.
- Final full affected offline reactor BUILD SUCCESS: platform-flow 64,
  platform-security 22, payment-service 345; 431 total, 430 passed, no
  failures/errors, one existing RoutingAgentLiveTest skipped because the optional
  real-model gate was absent. The full run includes 15 explicit resolver/config/
  intent tests, 4 creation/fingerprint/capture-order tests and 3 new owner
  workflow guard tests. Focused and full totals overlap; do not add them.
- The capture-order unit test proves that failure prevents history/outbox calls;
  it does NOT establish actual PostgreSQL transaction rollback.
- Docker/psql were unavailable on PATH. PostgreSQL/full migration/owner rollback,
  container validation, running Besu/private transfer and a new public-testnet
  transaction were NOT executed.

This remains an incomplete Besu payment path. Next are full selected-context
owner evidence and token-aware quotes/reviews, exact independent approvals and
execution claim, versioned events, scoped durable reservations, dedicated
DEMOUSD/BESU_GAS journals and reconciliation. captureReview/captureExecution are
not wired yet. Sources remain local/unpublished. No wallet/password, running
stack, owner financial records or funds were accessed or changed.

See [the binding implementation handover](../infrastructure/besu/SETTLEMENT-BINDINGS.md).

## Besu instruction-bound observation increment - 2026-11-25

Continued after the owner-intent checkpoint. Added a shared SettlementObservation
and strict canonical codec support. BesuRuntime.observe(Instruction) binds fresh
RPC results to the exact instruction/context/recipient/quantity. It rejects a
changed context or expired instruction before RPC, validates accepted transfer
inputs before RPC, limits evidence to 15 seconds and clips expiry to the customer
deadline. Evidence validates gas arithmetic, token/native precision and exact
instruction reuse. Execution-only estimates cannot masquerade as Base L1 fee
evidence. No BESU_GAS/USD conversion is invented.

These observations contain raw totals without reservation deductions. Calls use
bounded-window latest state, not an atomic snapshot at one block. No owner
request/persistence, review, approval, quote, event consumer or execution workflow
consumes this new record yet. Besu new-work and owner acceptance gates remain
closed. The existing public API, USDC event contracts, signer and fee policy were
not redirected or activated differently.

Observed validation:
- Offline Java 21 command: services/mvnw.cmd -o -f services/pom.xml
  -pl payment-service,blockchain-service -am test.
- BUILD SUCCESS, finished 2026-11-25 19:46:14 Asia/Calcutta, duration 14:48.
- platform-flow 72, platform-security 22, payment-service 345, blockchain-service
  148; 587 total, 586 passed, zero failures/errors, one existing optional
  RoutingAgentLiveTest skipped. New suites: 8 shared observation tests and 6
  runtime observation tests passed. These 14 are included in the total.
- BesuReadinessIT was extended and compiled, NOT run. Docker and psql were
  unavailable on PATH. PostgreSQL/full migration/owner rollback, live private
  transfer, container validation and a new public-testnet transaction were NOT run.
- The separate restricted read-only log-summary command did not return and was
  terminated; Maven itself completed successfully. The final summary was verified
  directly from its completed session and the correctly decoded UTF-16 log.

Changed source groups: platform-flow SettlementObservation/SettlementBindingJson;
BesuRuntime and adapter input validation; shared/runtime tests and BesuReadinessIT.
Details are in infrastructure/besu/SETTLEMENT-BINDINGS.md and RUNTIME.md. Sources
remain local/unpublished. No owner wallets/passwords, running application
containers, financial records or funds were accessed or changed. Unit tests used
disposable fixtures only. No architecture change beyond accepted ADR-016.

Next: connect these observations to canonical owner preparation requests and
persistence with network/asset reservation evidence; then token-aware quote/review,
exact independent approvals/execution claims, versioned events, durable submission
ownership, DEMOUSD/BESU_GAS journals and owner reconciliation. Complete private
end-to-end and separate public regression acceptance before activation. Automatic
signal-based network selection follows that complete Stage A acceptance.

## Besu read-only preparation handoff - 2026-11-25

Implemented the payment-owner to blockchain-owner observation handoff:
- Typed SettlementPreparation Request/Evidence bind one request UUID to the exact
  captured instruction, with a 120-second processing window and the existing
  15-second RPC evidence lifetime.
- Payment service locks and authorizes the customer payment, checks the immutable
  instruction against current account/beneficiary/intent terms, and atomically
  records an append-only request plus the existing payment outbox command.
- SettlementObservationRequested 1.0 reaches only the existing blockchain treasury
  queue. The dedicated observer validates canonical owner evidence before and
  after RPC, persists the first immutable result and never signs/submits.
- An observation-only operator API is disabled by default. It does not enable
  TOKEN_WALLET, create private instructions, propose a quote or bypass the Besu
  payment acceptance gate. Exact key replay does not refresh evidence.
- Results are PENDING / OBSERVED_NOT_READY / EXPIRED, with the explicit gate
  RESERVATION_INTEGRATION_PENDING. Raw totals are not available balances.

Changed source groups: platform-flow preparation contracts/strict codec/read
models; payment-owned preparation service/controller/event route and V14;
blockchain-owned observer/dispatch and V7; CDK rule; related unit and PostgreSQL
acceptance tests. Existing public USDC commands, quote/approval/execution guards,
signing, fee journals and reconciliation were not redirected. No new service or
architecture decision was introduced; ADR-007/016 apply.

Observed validation:
- Offline Java 21 affected reactor test command: BUILD SUCCESS, finished
  2026-11-25 20:47:44 Asia/Calcutta, duration 3:21.
- platform-flow 78, platform-security 22, payment-service 355, blockchain-service
  158; 613 total, 612 passed, zero failures/errors, one existing optional
  RoutingAgentLiveTest skipped. Includes 26 additional tests over the prior
  increment: 24 in new suites and two existing consumer dispatch regressions.
- Infrastructure TypeScript build passed; both Jest suites passed, 13 tests.
  The first Jest run failed three assertions because an older emitted
  infrastructure-stack.js shadowed the edited TypeScript. Rebuilding with
  npm run build and rerunning the suites resolved the source/artifact mismatch.
  Generated JS/declarations are ignored infrastructure build outputs.
- SettlementPreparationPersistenceIT (four tests) compiled, NOT executed.
  Docker and psql were unavailable on PATH. The suite covers minimal owner
  tables plus V13/V14, real JDBC rollback after outbox-save failure, immutable
  rows, duplicate concurrency and changed recipients. It does not establish
  full Flyway/JPA-outbox or live private-chain acceptance.
- No live migrations, containers, owner wallets/passwords, financial records or
  funds were accessed/changed. No new public/private blockchain transfer ran.
  Sources remain local/unpublished; the running Codespaces application was not
  changed by these local edits.

Next: chain/treasury/token-scoped reservation commitments and fresh availability
evidence, followed by token-aware quotes/reviews, exact independent approvals,
execution claims, versioned execution events, durable submission ownership,
DEMOUSD/BESU_GAS journals and owner reconciliation. Run PostgreSQL/full migration,
private end-to-end and separate public regression acceptance before activation.

See [settlement bindings](../infrastructure/besu/SETTLEMENT-BINDINGS.md) and
[Besu runtime](../infrastructure/besu/RUNTIME.md). Java validation log is local
.local/validation/besu-preparation-regression.log; it is not a deployment artifact.

## Besu scoped commitments and availability - 2026-11-25

Implemented an additive reservation-aware preparation increment:
- Shared SettlementAvailability binds the exact original preparation/RPC evidence
  to token/native commitments, checked time and bounded freshness. Available
  balances floor at zero; capacity is never execution authorization.
- Blockchain-owned V8 adds the treasury scope lock, execution-bound commitments
  and append-only availability evidence. Scope is network/chain/genesis/treasury;
  token commitments additionally use exact contract/code/decimals. Gas commitments
  include other tokens sharing that same scope.
- Internal BesuLiquidityStore implements exact-key holds, pinning and unsigned
  release. Explicit READ_COMMITTED transactions and clock checks after locking
  prevent old snapshots/lock waits from granting fresh capacity. Pinned work
  remains committed beyond lease expiry and cannot be released as unsigned.
- The canonical Besu observer takes the scope lock before RPC and records its
  immutable availability in the same owner transaction. The customer-scoped
  observation view verifies the original evidence and availability fingerprints.
  Its status remains OBSERVED_NOT_READY/EXPIRED, with
  RESERVATION_CLAIM_INTEGRATION_PENDING where availability exists.
- Hold/pin are package-private storage methods, NOT yet called by a payment
  execution endpoint/consumer. A stored Execution document is not two approvals.
  Exact quote/approval/execution-claim integration is still required before those
  methods enter the authorized workflow. No private UI activation is provided.
- No receipt-based commitment release or nonce/signature submission claim was
  introduced. Pinned commitments stay conservatively held pending that recovery
  implementation. Old V7 observations are preserved without refresh.

Validation observed:
- Offline Java 21 affected reactor test: BUILD SUCCESS, finished
  2026-11-25 21:25:48 Asia/Calcutta, duration 3:00.
- platform-flow 83, platform-security 22, payment-service 356, blockchain-service
  173; 634 total, 633 passed, zero failures/errors, one existing optional
  RoutingAgentLiveTest skipped. This includes 21 tests added in this increment:
  five shared availability tests, fourteen store tests, one observer storage
  failure test and one authorized availability-view integrity test.
- An earlier complete run also passed (632 passed/one skipped); the final run
  adds the explicit isolation guard and its additional regression. The initial
  focused compile/test run was interrupted for the post-lock clock adjustment;
  it is not claimed as a completed test run.
- BesuLiquidityPersistenceIT (four tests) compiled, NOT executed. Docker and psql
  are unavailable on this host. PostgreSQL concurrency/rollback/migration tests,
  full Flyway chains, real private-chain transfer and a new public-testnet
  transaction were NOT run. Infrastructure/frontend suites were not rerun:
  their source was not changed in this increment.

Changed files/groups: platform-flow SettlementAvailability/strict codec and tests;
blockchain BesuLiquidityStore/observer/V8 and tests; payment preparation read view
and tests; Besu binding/runtime documentation, ADR-007 ownership note and activity
handover. No new microservice or architecture beyond accepted ADR-007/016.

No running Codespaces container, owner wallet/password, financial record or fund
was accessed or changed. Sources remain local/unpublished. Existing public USDC
submission, signer guards, fee journals and reconciliation were not redirected.

Assumptions/next gates: a single configured identity for the private Besu chain,
dedicated treasury and all future commitment writers using this same store/lock.
Execution must revalidate live balances/fees; alias identities for one physical
chain and external wallet spends are not accepted as a reservation guarantee.
Next: token-aware quotes/reviews, exact independent approvals and execution claims
connected to hold/pin, then durable nonce/submission ownership, receipt-based
release, DEMOUSD/BESU_GAS journals and owner reconciliation. Complete private
end-to-end and separate public regression acceptance before network activation;
automatic network selection follows Stage A acceptance.

Details and Docker-host acceptance command:
[infrastructure/besu/SETTLEMENT-BINDINGS.md](../infrastructure/besu/SETTLEMENT-BINDINGS.md).
Local Java log: .local/validation/besu-liquidity-regression.log.

## Besu immutable quote drafts - 2026-11-25

Implemented the next preparation increment: shared SettlementQuote binds the exact
private instruction, availability/preparation, simulated USD charge, native gas
reserve and quote-policy terms. The original RPC evidence must be fresh at
assessment. Quote validity is separately bounded (maximum 900 seconds/deadline);
execution must obtain a new live observation and fail/review again if the approved
native reserve no longer covers it. No fabricated USD gas conversion is used.

Payment-owned SettlementQuoteService authorizes/locks the existing owner before
lookup, checks allowed network and exact enabled registry context for NEW drafts,
and resolves all quote terms from server configuration. Exact retries return the
original draft/quote ID/expiry, including after configuration changes or expiry.
No endpoint/model write tool, execution event, reservation call or selection
activation was added.

V15 adds append-only payment_settlement_quote with a composite preparation/
instruction/payment/correlation foreign key and canonical document fingerprint.
The existing V13 approved review schema is unchanged: a draft is not a routing
recommendation, two approvals or execution authority. Existing private acceptance
and legacy workflow guards remain in place. No wallet/password/fund access.

See [quote-draft implementation and acceptance](../infrastructure/besu/QUOTE-DRAFTS.md).
Validation observed: full offline Java 21 reactor BUILD SUCCESS, finished
2026-11-25 22:38:14 Asia/Calcutta, duration 4:06. 772 total tests: 771 passed,
zero failures/errors, one existing optional RoutingAgentLiveTest skipped.
Current-run module totals: platform-flow 92, platform-security 22, payment 377
(one skipped), compliance 12, blockchain 173, ledger 79, reconciliation 1,
Visa 16. Includes all 30 new unit tests (9 quote, 15 service, 6 store).
The earlier focused quote/service run also passed (21 tests).

SettlementPreparationPersistenceIT now has seven cases (three new quote cases).
It compiled but did NOT execute. Docker/PostgreSQL migrations, concurrency,
private-chain payment and public-testnet acceptance were NOT run. No
frontend/infrastructure tests were repeated: their source was unchanged.
Java log: .local/validation/besu-quote-regression.log.

Next implementation: canonical token-aware recommendation/review records and
exact two-approver decisions that bind these drafts, followed by execution claims
connected to hold/pin. Existing V13 public-flow foreign keys must not be satisfied
with invented legacy records. Preserve quote history and get fresh live
capacity/fees when authorized execution begins. Signing/nonce recovery, receipt
release, DEMOUSD/BESU_GAS journals and reconciliation remain required before
activation. Automatic rail selection follows complete Stage A acceptance.


## Besu canonical reviews and independent approvals - 2026-11-26

Implemented the internal Stage A approval increment under accepted ADR-016.
RecommendationService.proposeSettlement resolves the owner-stored draft by IDs,
authorizes/locks the payment first, and creates a canonical routing_recommendation.
The existing acceptance, distinct-actor decisions, audit, expiry, supersession and
version workflow is reused. Each approver accepts the exact quote before voting;
private customer policy must require at least two independent actors.

The additive RecommendationDocument.settlementQuote holds exact DEMOUSD/native gas/
chain/genesis/contract/recipient/amount/preparation/quote terms. A private document
cannot also carry a legacy USDC quote/context/assessment. Canonical recommendation
versions advance while original draft documents remain unchanged. New decisions
revalidate current policy/provider/network permission, exact registry context,
compliance, intent, beneficiary/account and the original owner draft. Legacy JSON
omits the new field when absent and retains existing Routing v1 event payloads.

Private Settlement-prefixed v1.0 notifications carry the complete typed canonical
review and customer/quote policy versions. Local SQS publication and CDK route them
only to the existing durable review notification queue, with no financial execution
consumer. TOKEN_WALLET/DEMOUSD are supported as typed engineering material; private
payment creation remains hard-gated. Legacy execution and agent entry points
reject private review material. No private UI/proposal endpoint was activated.

No V13/V15 migration changed and no legacy preparation rows were fabricated.
The canonical JPA document is rechecked against the immutable owner quote on new
decisions. Real execution claim/schema integration is still the NEXT increment;
a typed review or APPROVED state alone must not be passed to hold/pin or signing.

Validation observed:
- Full offline Java 21 reactor BUILD SUCCESS, finished 2026-11-26 07:16:23
  Asia/Calcutta, duration 13:15. 798 tests: 797 passed, zero failures/errors,
  one existing optional RoutingAgentLiveTest skipped. Module totals: platform-flow
  92, platform-security 22, payment 403 (one skipped), compliance 12, blockchain
  173, ledger 79, reconciliation 1, Visa 16.
- Includes 26 new unit cases: 25 private recommendation/approval cases and one
  explicit private payment-creation gate case. Focused approval regression also
  passed all 98 tests before final publication-route completion.
- After the final event catalog change, targeted publication/review regression
  BUILD SUCCESS: all 28 passed, finished 2026-11-26 07:18:28, duration 1:11.
- Infrastructure TypeScript build passed. Both Jest suites passed, 14/14 tests,
  including new private audit-only delivery isolation. Existing ts-jest TS151002
  configuration warning remains nonfatal; it was not changed in this increment.
- Docker/psql unavailable on PATH. No PostgreSQL migration/concurrency/rollback,
  real Besu payment, new public-testnet transaction or Codespaces runtime gate was
  executed. Existing *IT sources compile during test compilation but do not run
  with the ordinary Maven test goal. Frontend source/tests were not changed/run.

Production changes: payment approval RecommendationDocument/RecommendationService/
RoutingDecisionEvents; new settlement/SettlementReviewGuard; typed intent enums,
PaymentIntentService and ExplicitSettlementService; agent IntentDraft and
StoredAgentEvidenceSource guards; infrastructure EventBridge review rule.
Tests: new SettlementRecommendationServiceTest, PaymentIntentServiceTest and
infrastructure audit-route test. No new microservice or architectural deviation.

No deployed runtime, wallet, password, database financial record or fund was
accessed/changed. Work remains local and unpublished. Existing Base Sepolia source
flow/defaults and historical records were preserved; regression tests are not a
new public-chain acceptance run.

Next: token-aware execution owner claims linking exact canonical review/version,
quote, private preparation and independent approvals; fresh live capacity/fee
validation and hold/pin; durable nonce/signature/submission recovery; receipt-based
commitment release, separate DEMOUSD/BESU_GAS journals and owner reconciliation.
Complete private end-to-end and separate public regression acceptance before
activation. Automatic multi-network routing follows Stage A acceptance.

Assumptions: explicit Stage A private selection, dedicated private treasury,
server-controlled simulated USD pricing, and existing server-verified approver
membership. The engineering authorization does not replace payment approvals.
Details: [Besu review integration](../infrastructure/besu/APPROVAL-REVIEWS.md).
Local logs: .local/validation/besu-review-regression.log,
besu-review-focused.log, besu-review-publication.log and
besu-review-infrastructure.log.

## Visa wallet provider simulator and Besu credential setup - 2026-11-26

Implemented the owner's requested wallet payout simulation alongside existing
fiat payout simulation, behind VisaPaymentAdapter in the existing visa-simulator.
No new application microservice or change to the accepted financial posting policy.

WalletPayouts binds exact token/chain/genesis/code identity, recipient, quantity,
execution, correlation and review/quote IDs and versions. VisaWalletSimulator,
JdbcWalletPayoutStore and V3__simulated_wallet_payout.sql provide independent
simulated token pockets, idempotent funding/creation/completion, retained capacity
for UNKNOWN and conclusive idempotent resolution. Each state is audited.
Fiat and wallet provider creates share a per-payment PostgreSQL lock and reject
a payout already recorded for the other destination. Existing fiat paths and
Base Sepolia configuration remain in place.

The wallet capability is disabled by default. Opt-in configuration must pin an
explicit nonempty token allowlist; supported fixtures are Base Sepolia USDC and
private Besu DEMOUSD. The internal operations controller requires existing
internal-service authority and the separate simulator operations flag.
Responses always carry SIMULATED, blockchainDeliveryVerified=false and no hash.
No chain transaction, conversion, ledger entry or financial completion is inferred.

This completes the provider simulator component, not customer-facing routing to
Visa wallet delivery. Its internal test API accepts provider fixture references;
it does not establish that an application's supplied review IDs are approved.
Immutable delivery-path selection, exact owner-approved execution, event recovery
and provider/chain/ledger reconciliation must be connected before customer use.
Existing direct-wallet outcomes retain ADR-015's no-off-ramp/payout accounting.
No duplicate chain transfer is invoked by the simulator.

Besu settings already existed; actual dedicated wallet/password files were
absent in this Windows checkout. Added credential-status.cjs, which checks only
existence/permissions, and interactive PrepareBesuPassword.java, which unlocks
the saved dedicated treasury, verifies its recorded address against the derived
key, and creates an exact UTF-8, no-newline, owner-only password file with
CREATE_NEW. Existing files are preserved. No actual wallet/password was generated,
read or overwritten during this work. The endpoint string is not a file;
host and container paths/endpoints still need separate runtime wiring.

Validation observed:
- Offline Java 21 Visa/shared reactor BUILD SUCCESS, finished 2026-11-26
  08:34:25 Asia/Calcutta, duration 1:01. All 155 tests passed: platform-flow 92,
  platform-security 22, Visa 41. Includes original fiat tests and 25 new unit
  cases across simulator behavior, disabled-mode compatibility, configuration,
  internal operations checks and cross-destination guard.
- Final test-only configuration wiring compiled and passed its four focused
  cases again, BUILD SUCCESS at 08:36:04, duration 30 seconds.
- Besu Node suite: 18 tests, 17 passed, no failures, one Linux permission case
  skipped on Windows. Password helper compiled against the actual wallet JAR;
  a piped/noninteractive run returned the expected INTERACTIVE_TERMINAL_REQUIRED
  with exit code 2 and wrote no secret files.
- All three dedicated credential paths were confirmed Git-ignored.
- New WalletPayoutPersistenceIT has nine PostgreSQL acceptance cases. It and the
  test ObjectMapper configuration compiled; these ITs did NOT execute. Docker
  and psql remain unavailable on PATH. No migration, real database concurrency,
  rollback, Codespaces runtime, new public-chain or private-chain payment was
  executed. No claim of complete Besu application acceptance.
- UI/CDK source unchanged; their suites and the full eight-module Java reactor
  were not repeated for this isolated increment. Existing full-reactor evidence
  remains separately recorded in the preceding activity entry.
- Scoped git diff --check produced no whitespace errors. Work local/unpublished;
  no deployment, application restart, chain start or financial operation.

Changed production files: VisaPaymentAdapter/VisaSimulatorAdapter, new
WalletPayouts/WalletPayoutStore/VisaWalletSimulator/JdbcWalletPayoutStore/
VisaWalletConfiguration, internal VisaWalletPayoutController, Visa migration V3,
and disabled-default wallet configuration in services/config/connections.properties.
Added four test classes (three unit and one PostgreSQL IT), extended existing
fiat adapter tests, and added Besu credential status tests/setup helper/runbooks.

See [Visa wallet provider simulation](39-VISA-WALLET-SIMULATOR.md) and
[Besu credential setup](../infrastructure/besu/CREDENTIAL-SETUP.md).
Next Besu work remains token-aware execution claims/reservations/signing recovery,
DEMOUSD/BESU_GAS accounting and reconciliation. Complete Codespaces chain and
application acceptance before customer activation. AWS EC2 node/container
deployment is a later stage after Codespaces testing, per the owner's sequence.
## 2026-11-26: Besu local credential setup and calculated Visa wallet balances

Dedicated local Besu wallet creation and both saved-file unlock checks completed
through the operator's interactive console. The treasury password helper then
unlocked the saved wallet and created the dedicated UTF-8 password file.
The Windows wrapper independently checked current-user-only ACLs for all three
files and recorded BESU_LOCAL_CREDENTIALS_VERIFIED at 08:57:42 +05:30.
All three credential paths were confirmed Git-ignored. No secret contents were
printed or published. Existing public and historical wallet files were preserved.
This is Windows checkout evidence; no Codespace credential creation is claimed.

Windows support was added to the existing Java helpers and an interactive
setup-credentials.ps1 wrapper. An initial run created and verified the pair but
stopped at a leftover Linux-only password-helper guard. That guard was removed,
both helpers recompiled, and setup resumed with the existing pair successfully.
Do not recreate wallets after a password-step failure.

Visa wallet simulation now stores before/after provider total, reserved and
available funds and recipient simulator credits for each state transition.
Successful payouts debit and credit the same exact token quantity once;
declines release capacity, unresolved timeouts retain it. Subsequent operations
do not change saved observations. Recipient figures represent credits within
this provider simulator, never an external wallet balance or blockchain receipt.
Every statement and observation is tagged SIMULATED.
New internal GET /visa-simulator/v1/wallet/payouts/{id}/balances is exposed through
VisaPaymentAdapter with existing internal-service and operations gates.
Migration V4 adds observations without changing earlier migrations or financial
journal policy. Existing fiat arithmetic remains in its current separate pockets.
Wallet statements are not yet connected to customer UI or customer execution.

Validation actually run:
- Visa Maven reactor: BUILD SUCCESS at 08:58:53 +05:30; 159 tests passed
  (platform-flow 92, platform-security 22, visa-simulator 45), no failures/skips.
  New PostgreSQL evidence IT also compiled but did not execute.
- Besu Node suite: 18 tests; 17 passed, zero failed, one Linux-permission test
  skipped on Windows.
- Both Java credential helpers compiled against the existing wallet utility JAR.
  PowerShell setup parsed without errors; scoped git diff --check passed.
- Docker/psql still unavailable on PATH. No PostgreSQL V4 migration, concurrency
  acceptance, chain startup, new chain payment, UI test or deployment was run.
  No Git publication or Codespaces/AWS runtime acceptance was performed.

Next: validate migrations and replay/concurrency/rollback in Codespaces Docker,
then continue the private Besu execution, accounting and reconciliation gates.
Runbooks: infrastructure/besu/CREDENTIAL-SETUP.md and docs/39-VISA-WALLET-SIMULATOR.md.

## 2026-11-26: consolidated backlog and Besu execution-owner claim

The single work queue is now docs/40-IMPLEMENTATION-BACKLOG.md. README, this
activity history, delivery plans, SOP, audit and Besu/Visa runbooks link to it.
Stable B/V/D/A/P/R IDs distinguish implementation, validation and deferred work;
C IDs retain completed milestones. Older dated TODOs are historical context.
AGENTS.md now requires every new deferral and closure to update this register.
The agreed Visa Direct AFT/OCT contract-fidelity deferral is V03; wallet API
mapping is separate V04. Continue Besu before those integrations.

B01 now has an internal transactional execution-owner claim bound to the exact
canonical private review/quote/preparation/instruction and independently accepted
approval decisions. It checks current operator/approver membership and exact
customer scope, rejects changed material and conflicting execution keys, and
returns the original claim for exact replay without fresh signing authority.
V16 adds immutable claim storage and scoped foreign keys; first capture audits
in the same transaction. An unresolved claim blocks replacement review creation.
The legacy public execution path remains separate; no Besu endpoint, automatic
selection, hold/pin, signing, financial event or private payment was enabled.

Validation: Java 21 payment reactor BUILD SUCCESS at 09:54:10 +05:30,
539 cases: platform-flow 92, platform-security 22, payment-service 425;
538 passed, one optional live-model test skipped, zero failures/errors.
An initial focused run exposed a record compact-constructor timestamp accessor
error; a subsequent run exposed use of the shared codec's fixed type allowlist.
Both were corrected: parameter validation and a private claim-only strict JSON
codec. The successful full reactor includes the 19 new claim tests and 28 private
review tests. No allowlist broadening or new shared-service dependency was added.

PostgreSQL V16 migration/concurrency/rollback acceptance has not run here. Docker
and psql are unavailable on this host; unit mocks do not prove durable persistence.
B01 stays IN PROGRESS until these checks pass; remaining implementation order is
in the central backlog. No deployment, publication, runtime switch or payment was
performed. Detailed logs and private handover remain ignored local artifacts.

## 2026-11-26: terminal setting persistence before restart

Saved local ignored .local/terminal-env.ps1 (Windows), .local/terminal-env.sh
(Codespaces Bash) and .local/TERMINAL-RESTORE.md. They reconstruct known project
settings, public identities, protected file references and historical DEMO_LAST_*
references, without exporting unrelated terminal state or secret payloads.
Existing .env and wallet/password files were not modified. Windows loader parsed
and was smoke-checked; default loading preserved COMPOSE_FILE. The first smoke
command failed because native PowerShell argument quoting removed string quotes;
the corrected direct-shell check succeeded. Bash runtime was not verified here.
Explicit Bash base-sepolia argument selects the existing testnet overlay; default
loading leaves runtime selection unchanged. No process/chain/payment was started.
These ignored files do not automatically appear in Codespaces. B01/B09/B12 remain
open as recorded in the central implementation backlog; no acceptance gate closed.

## 2026-11-26: conversation Word export

Created a local ignored conversation transcript under artifacts/chat-export:
StableCoinsDemo-Complete-Chat-2026-11-26.docx. Snapshot covers 12-26 November,
through the user's export request; it excludes export-generation progress,
internal reasoning and machine-supplied instructions. It is not the final
architecture/design Word deliverable D07 and closes no application acceptance gate.

Content checks executed: 2150 captured messages (750 user, 1400 assistant) in
sequence, all message text accounted for after formatting-marker normalization,
117 embedded screenshots and 29 embedded pasted-text attachments. Two referenced
pasted-text files are unavailable locally; original references remain. The DOCX
ZIP/XML parsed successfully and its SHA256 remained unchanged after rendering.

Word opened the transcript and computed 678 pages. Complete visual QA did not
execute: packaged render_docx.py could not find LibreOffice, two hidden Word PDF
conversions stalled (including a minimal one-page probe), and direct page-image
rendering retries returned COM Command failed while opening the document. Only
verification Word instances created by this task were stopped; no application
services, wallets, payment configuration or financial records were changed.

The export's remaining visual/attachment checks are tracked in the central
implementation backlog. Detailed source snapshots, renderer logs and content QA
reports remain local and ignored. Do not publish the conversation artifact or
private handovers as part of a normal source-code push.

## 2026-11-26: resumed Besu review, claim database suite and additional issuer intent

Reviewed ADR-016, SOP, current backlog, prior local handover and actual private
contract/adapter/claim/migration sources. Besu remains an incomplete gated private
payment path; infrastructure tooling and credentials do not establish running
chain or full payment acceptance.

Added SettlementExecutionClaimPersistenceIT: 12 strict Docker PostgreSQL cases
using all payment migrations through V16, including JSONB replay, immutable rows,
claim/audit atomic rollback, competing and duplicate concurrent captures, execution
ID reuse, owner/composite FK scope, uniqueness and integrity checks. No production
Java, existing migration, wallet or runtime configuration was changed.

Executed Java 21 focused claim/review regression: BUILD SUCCESS at 13:18:58
Asia/Calcutta; 47 tests passed, zero failures/errors/skips. New IT compiled in that
run but did not execute; Docker/psql are unavailable on PATH. Initial sandboxed
offline run could not resolve a cached Surefire dependency; outside-sandbox retry
succeeded. Log: .local/validation/besu-claim-persistence-preparation.log.
B01 remains IN PROGRESS; database execution and service/JPA/JDBC acceptance are
still required. Added the targeted Codespaces verify command to the review guide.
No chain start, submission, application activation, publication or deployment.

Owner supplied additional fiat-backed issuer lifecycle intent. Captured I01-I10
in the central backlog: issuer ADR, controlled ERC-20 supply/compliance operations,
APIs, deposit/mint, redemption/burn, compliance, monitoring/reconciliation, contract
security tests, deployment/documentation and later custody integration. ERC-721/
ERC-1155 are P02 and Quorum scope assessment P03. Existing fixed-supply DEMOUSD
must not be silently changed into an issuer-backed or redeemable token.
## 2026-11-26: Besu approval-to-claim JPA/JDBC acceptance preparation

Owner directed preserving working implementation and continuing Besu. Enterprise
foundation review/refactoring is deferred to the end as E01-E06. No such runtime
refactor or UI feature was implemented. Broader learning capture now includes
functional/value-chain, domain, tooling, technology and deployment explanations;
operational commands and execution results remain separate.

Added SettlementApprovalClaimPersistenceIT with nine PostgreSQL service-boundary
cases: actual JPA approval/decision repositories join JDBC claim/audit capture,
including shared outer transaction, rollback, audit failure, concurrent exact and
conflicting claims, role/customer checks and canonical review version two.
Quote/preparation-owner reads alone use scoped persisted fixtures; this does not
exercise that complete owner boundary or RPC. No production Java, migration,
existing application setting, wallet, chain or customer activation changed.

Java 21 focused regression BUILD SUCCESS at 15:33:12 Asia/Calcutta: 47 tests
passed (28 private reviews, 19 execution claims), no failure/error/skip. The new
nine-case IT and earlier 12-case persistence IT compiled but were not executed;
Docker/psql remain unavailable. An initial compilation exposed an ambiguous Role
import and incorrect exception getter in the new test; both corrected before the
successful run. Log: .local/validation/besu-jpa-claim-boundary-preparation.log.

B01 remains IN PROGRESS until both PostgreSQL suites execute successfully. The
review guide now provides one Codespaces command selecting both suites. Then B02
connects approved ownership to fresh private-network/token/balance/fee checks and
durable liquidity hold/pin. No publication, deployment, payment, network startup
or enterprise refactoring occurred.
## 2026-11-26: Approved Besu claim to durable execution reservation (B02)

Implemented an internal BesuExecutionReservationService and shared read-only
SettlementExecutionReads boundary. Payment V17 adds exact execution projection
columns with a CHECK against the immutable owner claim document; new claims write
them atomically with claim/audit capture. Historical claims are not backfilled
and cannot authorize reservations without this projection. No legacy execution
or V13 execution-binding rows are fabricated.

The Besu worker requires the exact approved claim, explicit READ_COMMITTED
treasury locking, current review and owner instruction, and a new bounded-window
RPC observation. It checks ownership again after lock/RPC waits. Existing scoped
capacity/fee checks then acquire HELD -> PINNED and immutable execution evidence
(blockchain V9) in one transaction. Replay returns the same pinned result without
RPC or renewed lease. Partial/released commitments require recovery. Result:
PINNED_NOT_SUBMITTED; no signature, nonce, broadcast, journal or reconciliation.
The new-work registry remains closed; no HTTP/listener/scheduler/customer wiring,
existing public configuration, wallet or runtime activation changed.

Added 16 shared owner-read cases, 12 worker unit cases and six additional store
cases. Focused Java 21 BUILD SUCCESS at 16:11:23 Asia/Calcutta: 153 tests passed,
zero failures/errors/skips. Full payment/blockchain/shared reactor BUILD SUCCESS
at 16:20:57: 746 cases, 745 passed, one optional model skip, zero failures/errors.
Logs: .local/validation/besu-execution-reservation-focused.log and
.local/validation/besu-execution-reservation-regression.log. The initial sandbox
compiler run failed closing resources; outside-sandbox execution succeeded.

PostgreSQL suites compile but DID NOT EXECUTE: owner store 13 cases (full payment
migrations through V17), approval/JPA/JDBC nine cases, blockchain liquidity/worker
10 cases (V7/V8/V9 with minimal owner projection fixtures and substituted RPC/
instruction reads). Docker and psql are unavailable on this Windows host.
Neither new migration was applied to an application database. These suites do
not yet establish full cross-owner or live Besu acceptance.

B01/B02 remain IN PROGRESS for database acceptance. Updated the central backlog,
approval guide and settlement-binding guide with assumptions, replay/recovery
semantics and the combined Codespaces verify command. Next: execute/fix the
PostgreSQL gates in Codespaces, then B03 durable nonce/payload/submission ownership.
B08 owns eventual runtime command wiring; B09/B10 private acceptance and separate
public acceptance still gate activation. No publication, deployment, chain start,
financial transaction or enterprise refactoring occurred.
## 2026-11-26: Durable Besu signing and single-attempt submission (B03)

Added internal BesuSubmissionService, BesuSubmissionStore and BesuSignedTransfer
under blockchain-service/besu. The worker requires an exact durable payment-owner
claim and committed B02 pinned reservation. Separate REQUIRES_NEW READ_COMMITTED
transactions lock the chain/genesis/treasury, revalidate current owner/review and
fresh token/shared-gas capacity, persist independently decoded signed bytes and
nonce/hash, then persist an ATTEMPTED marker before any broadcast. No database
transaction spans broadcast. Payload/hash/nonce are immutable; V10 constrains
scope/nonce/hash uniqueness and status transitions with append-only audit.

A matching acknowledgement becomes SUBMITTED; timeout, rejection or differing
hash becomes UNKNOWN. Redelivery after an attempt only queries the original hash
and verifies returned transaction fields against the saved payload. It never
re-signs, automatically rebroadcasts, replaces a nonce/fee or changes chains.
Recovery can inspect historical submitted work after quote expiry without renewing
approval. A saved but unattempted signature requires fresh checks and an unexpired
review before its first attempt. Signed data is excluded from API results and
object diagnostics; database access/backups must protect it. No private keys are
stored in PostgreSQL.

B04 receipt/finality/payment-outbox integration and conclusive reservation release
remain pending. Until then, unresolved signed work conservatively blocks further
submission from the same treasury. A crash after the attempt marker but before RPC
may remain UNKNOWN and require investigation; no automatic rebroadcast is claimed.
B08 runtime wiring remains pending. No public service/configuration, token contract,
wallet files, new-work registry, UI or active runtime was changed or enabled.

Java 21 compilation passed at 17:40:11 Asia/Calcutta. Focused Besu regression passed
118 tests, zero failures/errors/skips, at 18:37:13. Full payment/blockchain/shared
reactor BUILD SUCCESS at 18:43:53: 774 cases, 773 passed, one optional model test
skipped, zero failures/errors (108 flow, 22 security, 425 payment with one skip,
219 blockchain). The initial sandbox build could not access cached Maven artifacts;
outside-sandbox compilation succeeded. Two focused iterations exposed test-stub
setup errors; these were corrected before the successful runs. Logs:
.local/validation/besu-submission-compile.log,
.local/validation/besu-submission-focused.log and
.local/validation/besu-submission-regression.log. Scoped source whitespace and
scoped git diff checks passed.

Seven additional PostgreSQL tests cover signed payload persistence/immutability,
signature/audit rollback, attempt/audit rollback, nonce uniqueness/treasury exclusion,
concurrent single-attempt ownership, UNKNOWN/audit guards and the actual database
worker's commit-before-broadcast plus restart recovery. BesuLiquidityPersistenceIT
now has 17 cases; all compiled but DID NOT EXECUTE because Docker/psql are unavailable
on this host. No application migrations were applied, network started, real chain
transaction sent, publication or deployment performed. B03 remains IN PROGRESS.
Next: execute/fix B01-B03 PostgreSQL gates in Codespaces; continue B04 exact private
receipt confirmation and commitment resolution, then B05/B06 accounting/reconciliation.

## 2026-11-26: Exact Besu receipt and atomic capacity resolution (B04)

Implemented internal BesuReceiptService and immutable BesuFinalReceipt in the
blockchain-service besu package. The adapter's saved-payload receipt read verifies
exact signed transaction fields, approved Transfer log, canonical block before/after
reads and complete execution-only fees bounded by signed gas. Reverts require no
logs and retain actual gas cost. QBFT_COMMITTED finality relies on the trusted,
identity-pinned four-validator node; the application does not independently verify
consensus seals. ADR-016 and ADR-007 boundaries remain unchanged.

Blockchain V11 adds immutable besu_final_receipt linked to the original execution,
scope and hash. Receipt, CONFIRMED/REVERTED submission and audit commit atomically
with database guards. Historical PINNED rows remain unchanged; final receipts
resolve their active capacity commitments. Preparation/hold/signing requires balance
observations starting at or after the latest resolved block in the treasury scope.
Uncertain/invalid/unavailable evidence keeps capacity and the submission fence pinned.
Receipt RPC occurs outside database transactions, followed by owner rechecks under
the treasury lock. Concurrent polling replays the first result; terminal submission
replay never signs or broadcasts. No payment-owner writes, ledger, public runtime,
wallet, token contract, endpoint/listener/scheduler, UI or customer activation changed.

Java 21 compile BUILD SUCCESS at 19:11:26 Asia/Calcutta. Focused Besu tests passed
145 cases at 19:14:30, zero failures/errors/skips. After adding a canonical-block
reread negative test, full payment/blockchain/shared reactor BUILD SUCCESS at
19:17:29: 802 cases, 801 passed, one optional model skip, zero failures/errors
(108 flow, 22 security, 425 payment including one skip, 247 blockchain). Logs:
.local/validation/besu-receipt-compile.log,
.local/validation/besu-receipt-focused.log and
.local/validation/besu-receipt-regression.log. Scoped source whitespace checks passed.
Initial sandbox compilation could not see cached Maven dependencies; the same offline
build succeeded outside the sandbox without dependency upgrades or downloads.

Five added PostgreSQL scenarios bring BesuLiquidityPersistenceIT to 22 and apply
V7-V11 in its disposable fixture: resolution/stale balances, immutable revert evidence,
orphan receipt commit rejection, audit rollback and concurrent receipt workers.
Sources compiled but DID NOT EXECUTE: Docker/psql remain unavailable locally.
RPC/owner fixtures do not establish live Besu acceptance. No runtime migration applied.

Updated settlement bindings, backlog B04 and a conceptual knowledge note. B04 remains
IN PROGRESS for database/live receipt/recovery acceptance and payment-owner/outbox
integration under B08. Next implementation: B05 asset-specific posting contracts,
then B06 reconciliation and B08 wiring. Execute/fix PostgreSQL gates in Codespaces
and complete B09/B10 private/public acceptance before activation. No chain start,
real transfer, secret-file operation, publication or deployment performed.


### 26 November 2026 - B05 internal private control accounting

Implemented the ledger-owned private accounting increment authorized by ADR-015/016.
New besu package: BesuAccountingEvidence, BesuAccountingPolicy,
BesuAccountingStore and BesuAccountingService. Ledger migration V9 adds six
DEMOUSD/BESU_GAS accounts and immutable receipt acknowledgement with scoped
posted-journal binding. Funding/conversion/allocation is atomic; submitted movement
uses the saved owner hash, successful receipt creates delivery, and actual complete
execution-only gas creates a separate native-asset expense. Zero fee is acknowledged
without a synthetic expense. Reverts retain actual gas evidence without successful
token delivery or an automatic accounting reversal.

Private posting evidence retains canonical approved execution JSON/fingerprint,
network/genesis/contract identity and source funding reference. Private-asset
validation requires payment-scoped provenance, disallows mixed assets and limits
DEMOUSD to six decimals; gas remains eighteen. Existing public PostingEvidence
serialization is preserved by omitting only the new null field and retaining the
six-argument constructor. No private roles were added to the existing public account
policy. LedgerServiceIT's expected additive chart size is now 21.

The internal commands are conditional on app.besu.accounting.enabled=true, absent
by default. They expose no HTTP/model tool/listener, call no RPC/signing code and
write no payment/blockchain owner table. Claim verification is required, but B08
must supply the separately authorized simulated funding/debit command; claim alone
does not automatically fund. The commands do not mint/fund a wallet or debit a
customer's application account. B06/B08 own reverted in-flight recovery, owner
closure/outbox and exact receipt/journal reconciliation. Public defaults and
customer Besu selection remain unchanged.

Executed Java 21 focused tests: 43 passed at 19:53:22 Asia/Calcutta, no failures,
errors or skips. After adding private-asset provenance/precision and context tests,
full payment/blockchain/ledger/reconciliation/shared reactor BUILD SUCCESS at
19:57:16: 929 cases, 928 passed and one optional model skip, zero failures/errors.
Module totals: flow 108, security 22, payment 425 including the skip, blockchain
247, ledger 126, reconciliation 1. New private accounting tests: 29 service and
18 owner-read tests. Existing public ledger and payment/blockchain unit regression
ran; this does not establish a real public testnet regression. Logs:
.local/validation/besu-accounting-focused.log and
.local/validation/besu-accounting-regression.log. Scoped source whitespace passed.
Initial sandbox test execution could not see the cached Surefire dependency; the
same offline build succeeded outside the sandbox without downloading/upgrading it.

Added eight PostgreSQL BesuAccountingPersistenceIT cases for full ledger migrations,
JSONB evidence round-trip/replay, allocation rollback, acknowledgement/confirmation/
fee atomic rollback, concurrent receipt replay, reverted/zero-fee handling, SQL
immutability and rejection of missing receipt journals. These compile but DID NOT
EXECUTE: Docker and psql are unavailable locally. The suite mocks payment/chain
owner reads and tests real ledger persistence only; cross-owner/live-chain authority
remains separate acceptance. No application database migration, chain start,
transaction, secret-file operation, publication or deployment occurred.

Updated settlement bindings, central B05/B06 backlog and conceptual knowledge K09.
B05 stays IN PROGRESS. Next implementation: B06 exact private reconciliation,
followed by B08 authorized workflow/outbox integration. Execute PostgreSQL gates
and B09/B10 Codespaces private/public acceptance before activation.


## 26 November 2026 - Besu private chain/ledger comparison (B06)

Implemented shared read-only BesuClosureEvidence and internal BesuReconciliationService,
conditional on app.besu.reconciliation.enabled=true (absent by default). Capture
requires the canonical payment-owner claim and reads selected payment, submission,
final receipt, ledger acknowledgement, journal/account and payout-existence evidence.
It never reads signing payload bytes or credentials. Comparison binds the full
approved execution, genesis/contract/wallet context, source customer/amount/currency,
receipt hash/finality/gas math and exact per-asset journal entries/provenance/IDs.
Missing observations remain pending; inconsistent, duplicate, unexpected, reversed
or reverted delivery evidence cannot match. Complete zero fees require the ledger
acknowledgement and prohibit an invented network-fee journal.

The report is explicitly BESU_CHAIN_LEDGER_V1 with closure gate
PAYMENT_OWNER_INTEGRATION_PENDING. MATCHED means this bounded comparison matched;
it cannot transition a payment to RECONCILED. Reports use repeatable-read capture,
canonical JSONB/numeric/row-order-stable fingerprints and immutable same-key replay.
Only reconciliation and reconciliation_detail are written. V2 adds private report
and detail binding/immutability guards; existing public reports remain unaffected.
A delivery retry uses the original report ID; a later source observation uses a
new ID. B08 must recapture/check fresh source identity before owner closure and
connect authorized simulated funding/debit/outbox/settlement handling.

Executed Java 21 regression at 20:25:29 Asia/Calcutta: BUILD SUCCESS, 990 cases,
989 passed, one optional model skip, zero failures/errors. Module totals: flow159,
security22, payment425 (one skip), blockchain247, ledger126, reconciliation11.
After extracting test data and adding five owner-projection row-mapper tests,
final focused BUILD SUCCESS at 20:26:06: 66 passed, no failures/errors/skips
(51 comparison, five owner-read, ten report-writer/default-gate tests). Logs:
.local/validation/besu-reconciliation-regression.log and
.local/validation/besu-reconciliation-focused.log. Initial compilation caught a
method-reference type-inference error; after correction, sandbox compiler-resource
failure required the same offline Maven tests outside the sandbox. No dependency
upgrade/download was needed.

Seven new PostgreSQL BesuReconciliationPersistenceIT cases compile but DID NOT
EXECUTE. They cover JSONB replay, exact findings, atomic summary/detail rollback,
private report/detail immutability/binding and concurrent same-key recovery; owner
capture/claim reads are stubbed in this persistence suite. Docker and psql remain
unavailable locally. Real source-reader/schema and live-chain acceptance remain
open under B06/B09/B10. No runtime database migration, chain start, transaction,
wallet/password change, publication or deployment occurred.

Updated settlement bindings, B06 central backlog and conceptual K09 notes. B06
remains IN PROGRESS; B08 owner workflow integration is the next implementation
boundary. Follow the acceptance gates in docs/40-IMPLEMENTATION-BACKLOG.md before
customer selection or automatic routing activation.


Follow-up compatibility validation for this B06 increment: four new parameterized
cases feed actual BesuAccountingService/LedgerPostingService output, JSON-round-
tripped PostingEvidence and the real acknowledgement DTO into the shared comparator.
Successful/reverted transfers with positive/zero fees agree on their exact evidence
contract; reverts retain TRANSFER_REVERTED_REQUIRES_REVIEW. The complete 33-case
BesuAccountingServiceTest class passed at 20:31:38 Asia/Calcutta, zero failures,
errors or skips. Log: .local/validation/besu-reconciliation-ledger-contract.log.
There are 70 new executed reconciliation/compatibility cases across the focused
runs (66 plus four); these are not PostgreSQL or live-chain tests. Nine scoped
Java/SQL source/test files passed trailing-whitespace checks. B06 acceptance stays
open as recorded above and in the central backlog.


## 26 November 2026 - B08 payment-owned simulated Besu funding

Implemented the internal SettlementFundingService with default-absent
app.besu.funding.enabled flag; no endpoint, listener or Compose activation.
The command locks payment and simulated customer account, verifies canonical
claim and current independent accepted votes/operator membership, revalidates
recommendation/quote/policy/beneficiary/compliance, then checks an exact current
unsigned PINNED execution observation before debiting source USD. Existing
public USDC execution remains separate. Claimed-work policy validation now
requires the exact canonical claim; new approval/review validation still rejects
claimed work.

Payment V18 adds immutable payment_besu_funding with scoped claim/outbox foreign
keys, per-payment/execution/event/reference uniqueness and balanced-debit checks.
Customer debit, recommendation EXECUTED, four explicit payment history transitions,
qualified simulated BesuFundingAuthorized 1.0 outbox event and acknowledgement
share the owner transaction. JPA flush precedes JDBC acknowledgement insertion.
Historical replay returns the original acknowledgement without a new debit/event
or renewed signing authority. Shared SettlementReservationReads performs only
read-only blockchain-owner evidence checks; no ledger/chain write is made here.

Focused run passed 90 tests at 20:53:59 Asia/Calcutta. Full Java
payment/blockchain/ledger/reconciliation/shared reactor BUILD SUCCESS at 20:59:55:
1041 passed, 1 optional model skip, zero failures/errors (1042 cases).
The increment adds 43 executed unit cases: 16 reservation-reader, 16 funding-service,
six claim authority and five recommendation cases. Six PostgreSQL funding storage
cases compile but did NOT execute; Docker and psql were checked and unavailable.
Those tests cover migration, exact/concurrent replay, rollback, immutability and
balanced-debit enforcement using a JDBC storage harness, not full JPA owner wiring.
Actual JPA/JDBC atomicity and real cross-owner schema reads remain acceptance work.
The first sandboxed compile failed with Cannot close compiler resources; the same
focused run and subsequent full regression succeeded outside the sandbox.
Logs: .local/validation/besu-funding-focused.log and besu-funding-regression.log.

Updated B08 in the central backlog, settlement bindings and learner knowledge notes.
B08 remains IN PROGRESS. Next connect trusted qualified funding consumption and
ledger acknowledgement before signing, adapt owner-state validation after funding,
then private workers, settlement and fresh-fingerprint owner closure under B08/B06.
Real PostgreSQL, QBFT/Codespaces and a new public Base Sepolia acceptance remain
B01/B09/B10 gates. No running database migration, application/network activation,
chain transaction, wallet/password change, GitHub publication or deployment occurred.


### 26 November 2026 ? committed Besu funding, ledger allocation and signing guard

Implemented the next B08/B05/B03 boundary without runtime activation. New shared
SettlementFundingReads verifies the payment-owner immutable funding debit, account
scope and exact qualified outbox command. Internal gated BesuFundingHandler accepts
only that persisted command; BesuAccountingService checks committed funding under
its ledger lock before posting. Three matching immutable POSTED journals serve as
allocation acknowledgement. BesuSubmissionService checks FUNDED owner state and
complete allocation before new signing and first attempt, including checks after
slow RPC work. Existing attempt recovery remains observation-only. Canonical
SIMULATED-BESU references now agree across payment, ledger, signer and reconciliation;
historical SIM evidence remains readable without authorizing new funding.

Validation executed: full affected payment/blockchain/ledger/reconciliation/shared
Java reactor BUILD SUCCESS at 22:23:56 Asia/Calcutta: 1081 passed, one optional model
skip, zero failures/errors (1082 cases). After adding producer/reader and four
canonical-reference reconciliation contract cases, final focused suite BUILD SUCCESS
at 22:29:17: 117 passed, zero failures/errors/skips. Final production code is the
same as the successful full regression; later changes add contract tests only.
Logs: .local/validation/besu-funded-signing-focused.log,
besu-funded-signing-regression.log and besu-funded-signing-contracts.log.
An initial sandbox compile failed to close compiler resources; the first elevated
compile caught an accidental extra test-harness constructor argument, which was
corrected before successful runs. PostgreSQL persistence ITs compiled but did NOT
execute. Docker and psql are still unavailable; mocked owner reads do not establish
real cross-owner SQL/JPA-JDBC atomicity, rollback or concurrency acceptance.

Updated the sole backlog B08/B05/B03, settlement bindings and knowledge notes.
Remaining implementation/acceptance is recorded in B08/B06/B07/B09/B10. This
increment adds an internal handler, not a running event subscription. No database
migration, application/network activation, blockchain transfer, wallet/password
change, GitHub publication or deployment occurred. Base Sepolia defaults remain.


### 27 November 2026 - gated private funding transport

Completed the next B08 delivery boundary. Payment EventingConfig routes the private
BesuFundingAuthorized 1.0 envelope separately from existing public USDC funding.
Transport is false by default, including AWS publication, which fails closed while
disabled. LocalStackInitializer conditionally creates only the extra private pair,
configures 120-second visibility/three-receive redrive, and rejects active queue
collisions before mutation. The conditional CDK route has its own encrypted queue
and 14-day DLQ, exact source/type/version filtering and $.detail delivery.
BesuFundingConsumer requires both accounting and funding-events flags. It delegates
to the existing transactional committed-record validator/accounting handler;
errors propagate and replay retains the original accounting references. No signing
or financial compensation is performed by this listener.

Executed validation:
- Full affected payment/blockchain/ledger/reconciliation/shared Java reactor:
  BUILD SUCCESS, 1096 passed plus one optional model skip (1097 cases), zero
  failures/errors, 26 November 23:51:16 Asia/Calcutta.
- Final focused producer/SDK envelope, publication retry, initializer, listener,
  funding and allocation contract run: 103 passed, zero failures/errors/skips,
  27 November 01:18:02. One AWS envelope test was added after the full regression;
  production code remained the same.
- Infrastructure TypeScript build succeeded; both Jest suites passed, 15 tests.
  Includes default resource counts, private route isolation/version/envelope,
  queue/DLQ properties, configured destination collision rejection and existing
  public/Compose connection contracts. Build refreshed generated infrastructure JS.

Logs: .local/validation/besu-funding-transport-focused.log and
besu-funding-transport-regression.log. Initial sandbox runs could not read Maven's
cached Surefire artifact / Jest's shared configuration module; reruns outside the
sandbox passed. No dependency download or application change was needed for that
access issue. Docker and psql are unavailable; no live LocalStack delivery/redrive,
PostgreSQL transaction/concurrency, QBFT transfer, or public-testnet acceptance ran.
Those remain explicit B01/B09/B10 gates. Next integration remains B08 workers and
payment-owner settlement/closure, with B06 exact reconciliation authority. No
application/network activation, wallet/password change, database migration,
GitHub publication, blockchain transaction or AWS deployment occurred.


### 27 November 2026 - gated Besu execution and receipt worker

Implemented the next B08/B03/B04 owner integration increment. BesuExecutionWorker
and its read-only discovery reader independently page committed funded executions
and saved nonterminal private submissions, ten per lane per poll. Cursor movement
continues after deferred work and wraps at the end. Current owner/claim/funding and
exact three-journal allocation checks still precede new signatures/first attempts
through BesuSubmissionService. The worker adds no broad transaction, second signing
path, cross-owner financial write or per-payment deployment.

Added explicit observeSubmission: restart monitoring cannot create a signature,
first-attempt marker or broadcast, even when given an unattempted SIGNED row.
Already attempted records are discovered independently of current payment/funding
status and expiry. They reuse observation-only saved-hash recovery and the exact
receipt owner. SIGNED work requires the new-submission switch and allocation again.
First submission and monitoring have separate default-off gates. Monitoring remains
available when only new submission is disabled, with the Besu runtime retained.
The existing locked owners enforce concurrent-instance safety; the in-memory poll
lock/cursors are not claimed as financial uniqueness or persistence guarantees.

Executed validation:
- Focused worker/discovery/submission/receipt suite: BUILD SUCCESS, 61 passed,
  zero failures/errors/skips, 27 November 06:31:55 Asia/Calcutta. Includes the actual
  submission owner across a worker restart with one signature and broadcast,
  disabled/missing gates, allocation wait, unknown/attempted recovery with new
  work off, no first attempt through monitoring, isolated row/scan failures,
  invalid bindings, independent bounded pages and cursor wrap/progression.
- Full affected payment/blockchain/ledger/reconciliation/shared Java reactor:
  BUILD SUCCESS, 1115 passed plus one optional model skip (1116 cases), zero
  failures/errors, 27 November 06:39:46 Asia/Calcutta.
- BesuLiquidityPersistenceIT now has 24 compiled cases, including two actual-SQL
  discovery cases: recovery without current payment/funding, terminal exclusion,
  funded owner state and skipping already signed work. Its payment read tables are
  explicitly storage fixtures, not execution of payment migrations or authority
  acceptance. These ITs did NOT execute; Docker and psql remain unavailable.

Logs: .local/validation/besu-execution-worker-focused.log and
besu-execution-worker-regression.log. The first sandboxed run could not access
cached Maven Surefire dependencies; the same focused run succeeded outside the
sandbox. No library download, database migration, running application change,
network activation, transaction, wallet/password change, publication or deployment
occurred. Base Sepolia behavior/defaults remain. Updated central backlog B08/B03/B04,
settlement bindings and the existing recovery knowledge topic. Remaining integration
and actual PostgreSQL, QBFT/Codespaces/private/public acceptance stay in
B08/B06/B07/B01/B09/B10; no end-to-end Besu completion is claimed.


## 27 November 2026 - committed Besu claim to reservation/funding workers

Implemented an internal continuation from the saved independently approved owner
claim to fresh treasury reservation and simulated customer funding. The blockchain
reservation worker reads only claim projections and delegates to the existing
reservation owner. The payment worker loads the full canonical claim and original
operator before invoking the existing funding transaction, preserving current role,
approval, compliance, quote and fresh unsigned PINNED-capacity checks. Replay uses
the saved execution identity and existing funding receipt rather than another debit.
Both workers use bounded keyset pages with per-row failure isolation. No scheduler
transaction spans service owners or RPC; no cross-owner financial writes were added.

Production additions: shared SettlementClaimWorkReads, BesuReservationWorker,
SettlementClaimedFundingService and SettlementFundingWorker. Central properties
add BESU_FUNDING_WORKERS_ENABLED=false and BESU_FUNDING_WORKER_POLL_MS=5000.
No endpoint, customer-selection enablement, ordinary Compose change, migration,
chain initialization, token deployment/mint, wallet change or transfer was performed.
The design remains ADR-007/016: existing owner authority, shared-schema reads,
fixed initial DEMOUSD supply, separate simulated fiat funding and public default.

Validation executed against this revision:

- Focused shared discovery, reservation owner/worker and funding owner/worker tests:
  54 passed, zero failures/errors/skips; BUILD SUCCESS at 07:33:39 Asia/Calcutta.
  Log: .local/validation/besu-claim-funding-workers-focused.log.
- Full affected payment/blockchain/ledger/reconciliation/shared Java reactor:
  1141 cases, 1140 passed, one optional model skip, zero failures/errors;
  BUILD SUCCESS at 07:38:37 Asia/Calcutta.
  Log: .local/validation/besu-claim-funding-workers-regression.log.
- Tests include original-operator rehydration, changed/missing claim rejection,
  current authority and stale-reservation rejection, one debit/event across
  restart replay, independent-row failures, default-off gates and pagination.
- Two additional actual-PostgreSQL storage-fixture cases cover discovery advancing
  from unreserved to pinned to funded, and reservation rollback/payment-state
  exclusion. The 26-case BesuLiquidityPersistenceIT compiled, but did NOT execute:
  neither Docker nor psql is available locally. Fixtures are not full payment
  migration or JPA/JDBC cross-owner acceptance.
- The initial sandboxed compiler failed closing compiler resources. Focused and
  full successful runs used the existing local Maven cache outside the sandbox.
  Scoped diff whitespace check passed. No live private/public chain, SQS delivery,
  Codespaces or database acceptance is claimed by these unit regressions.

Current work and closure criteria remain B01/B02/B07/B08/B09/B10 in the
[central backlog](40-IMPLEMENTATION-BACKLOG.md), not a separate queue here.
The next integration boundary in B08 is submission/receipt ledger processing;
authenticated customer entry and final owner settlement/reconciliation remain gated.
The mint-versus-funding explanation was added to the existing knowledge topic.


## 27 November 2026 - committed Besu chain evidence to ledger continuation

Implemented the next B05/B08 increment in settlement-ledger-service:
BesuAccountingWorkReads and default-disabled BesuAccountingWorker discover saved
attempted/unknown/submitted/confirmed/reverted executions without ledger receipt
acknowledgement. They validate canonical binding/fingerprint and delegate to the
existing transactional accounting owner: submission first, then terminal receipt.
There is no broad transaction, cross-owner write, RPC, signer, mint, customer
activation or payment-owner closure authority. Terminal discovery survives a
restart spanning mining; unknown outcomes remain in flight. Revert gas, zero-fee
acknowledgement and no fabricated delivery/compensation retain the existing policy.
Scheduling is conditional on both accounting and accounting-worker gates.
BESU_ACCOUNTING_WORKERS_ENABLED defaults false; polling/initial delay is 5000 ms.
The design remains within ADR-007/015/016 and the six existing services.

Validation executed against this revision:

- Focused worker/accounting owner/store suite: 73 passed, zero failures/errors/skips;
  BUILD SUCCESS 07:57:15 Asia/Calcutta. Log:
  .local/validation/besu-accounting-worker-focused.log.
- Full payment/blockchain/ledger/reconciliation/shared Java reactor: 1157 cases,
  1156 passed, one optional model skip, zero failures/errors; BUILD SUCCESS
  08:02:30 Asia/Calcutta. Log:
  .local/validation/besu-accounting-worker-regression.log.
- Coverage includes conditional activation, submission-before-receipt ordering,
  uncertain outcomes, row/scan failure isolation, canonical binding rejection,
  page fairness, receipt retry, and actual accounting-owner replay across worker
  recreation for success/revert and positive/zero fees. The owner fixture uses
  mocked storage; it is not PostgreSQL transaction/concurrency acceptance.
- Two new BesuAccountingWorkReadsIT cases compile (test class timestamp 08:01:37)
  but did NOT execute: Docker and psql are unavailable. They exercise discovery
  SQL over minimal owner-storage fixtures, not complete migrations or authority
  projections. Existing PostgreSQL migration/rollback/concurrency suites remain
  unexecuted locally. No live SQS/private/public/Codespaces acceptance is claimed.
- Initial sandboxed Maven could not access a cached Surefire dependency. Successful
  focused and regression runs used the existing local cache outside the sandbox.
  Scoped whitespace check passed; no production Java changes followed validation.

Updated settlement bindings, central B04/B05/B08 records and the existing accounting
knowledge topic. B08's next boundary is payment-owner settlement and scoped fresh-
fingerprint reconciliation closure; authenticated entry/UI/isolated Compose,
reverted compensation and private/public Codespaces acceptance remain governed
by B06/B07/B08/B09/B10 in the central backlog. No chain, wallet, runtime or ordinary
public Compose change was performed. Besu selection remains hard-gated.


## 27 November 2026 - Besu payment settlement and reconciliation closure (B04/B06/B08)

Implemented the third continuation increment, independently gated and disabled by
default. Payment-owned BesuPaymentClosureService revalidates the immutable claim
and instruction, exact committed simulated funding/debit/outbox and allocation,
then the current private-chain receipt, receipt acknowledgement and per-asset
journals. Complete evidence advances audited settlement states to
RECONCILIATION_PENDING. A reconciliation-owned bounded worker writes/reuses a
report identified by canonical current evidence; the payment owner independently
requires that exact scoped MATCHED report before RECONCILED. A stale report cannot
authorize closure after source changes. Replays use the original execution and an
immutable payment-owned terminal acknowledgement without duplicate history/events.

Payment V19 adds payment_besu_closure, exact immutable funding scope and an insert
guard requiring RECONCILED plus the matching canonical funded execution. The owner
flushes JPA status/history/outbox before the JDBC acknowledgement in the same
transaction. The three added PostgreSQL guard/immutability/rollback cases in
SettlementFundingPersistenceIT compile; actual execution is NOT established.
Its storage harness uses real Flyway/SQL when run with Docker, not a complete
cross-owner JPA runtime. Existing latest-migration assertions now expect V19.

New defaults: BESU_CLOSURE_ENABLED=false, BESU_CLOSURE_WORKERS_ENABLED=false,
BESU_CLOSURE_WORKER_POLL_MS=5000. Payment also requires app.flow.enabled; report
worker also requires app.besu.reconciliation.enabled. Both use bounded UUID-keyset
pages and delegate each execution through the transactional owner proxy. Deferred
rows do not starve subsequent work. Qualified Besu lifecycle notifications go to
the existing local audit/routing queue only when enabled; they do not invoke public
execution/reconciliation command consumers. No runtime, Compose, customer selection,
public default, wallet, chain transaction or token supply was changed.

Validation on Windows / Java 21:

- Initial sandboxed focused compilation failed with "An unknown compilation
  problem occurred"; the diagnostic did not identify the cause. The rerun outside
  the sandbox passed. This is not evidence of a proven permissions root cause.
- Focused shared/payment/ledger/reconciliation suite: 148 tests passed, no failures,
  errors or skips, completed 08:53:24 Asia/Calcutta. Log:
  `.local/validation/besu-payment-closure-focused.log`.
- After the final atomicity guard/flush, cross-mapper source-identity and audit-route
  additions, full affected shared/security/payment/blockchain/ledger/reconciliation
  reactor: 1198 cases, 1197 passed, one optional model skip, zero failures/errors;
  BUILD SUCCESS at 09:08:33 Asia/Calcutta. Log:
  `.local/validation/besu-payment-closure-regression.log`.
- Tests cover owner settlement vs final reconciliation, exact report/document/hash,
  changed intent/claim/evidence rejection, restart replay without duplicate history,
  per-row failure isolation, bounded scan progress and default-off gates. The full
  run compiles integration-test sources but does not execute Failsafe PostgreSQL ITs.

Remaining acceptance is centralized in B01/B06-B10/D01: actual PostgreSQL migration,
rollback/concurrency and JPA-JDBC atomicity, cross-owner source/discovery reads,
live private payment and restart/redrive, plus separate public Base Sepolia
regression. Next implementation increment is reverted transaction handling and
explicitly authorized compensation (step 4), then UI/isolated Compose and operating
commands (step 5). Step 3 code completion does not close those acceptance gates or
authorize enabling customer Besu selection. Details: settlement bindings and the
central backlog. Knowledge notes include owner reconciliation concepts and the
build-sandbox explanation requested during this increment.
