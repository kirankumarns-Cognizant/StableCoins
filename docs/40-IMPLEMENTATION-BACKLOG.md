# Consolidated implementation and deferred-work backlog

Updated: 27 November 2026. This is the single authoritative register of unfinished
implementation, deferred product scope, validation gates and final deliverables.
README, activity status, delivery plans and runbooks link here. Their older dated
TODO/next-step statements are historical evidence, not competing work queues.

## How to maintain this file

- Record every new deferred decision here before ending an implementation turn.
  Reuse the existing ID when scope is the same; reference IDs from other files.
- Statuses: OPEN (implementation remains), IN PROGRESS, VALIDATION REQUIRED
  (code exists but acceptance is incomplete), DEFERRED (intentionally later),
  VERIFY (older finding needs current evidence), CLOSED (closure evidence linked).
- Preserve IDs and closed rows. Close only against executed evidence, with date,
  revision, tests/artifacts and limitations. Compilation is not runtime acceptance.
- An item may have several acceptance checks, but its status lives here. Detailed
  designs, commands and dated observations remain in their source documents.
- Do not copy ignored private handovers, passwords, encrypted wallet payloads,
  identity tokens or private environment values into this backlog.
- Accepted ADRs govern architecture. Future proposals below do not authorize a
  new rail, service, deployment or financial transaction by themselves.

## Current implementation order

Finish B01-B06, then B07-B10 in Codespaces with public-path regression. Complete
Visa wallet application integration V02 after its required asset/ledger contracts
are accepted and exercised. Stage B automatic multi-network routing B11 follows
Stage A private payment acceptance. Visa external API fidelity V03-V05 and broader
rails P01 remain deferred. AWS A01-A03 follows Codespaces acceptance.

A dated eight-dimension network comparison and sixteen-group issuer scope mapping are in
[the scope comparison](42-NETWORK-COMPARISON-AND-ISSUER-SCOPE.md). Those counts group
requirements; statuses and closure criteria remain exclusively in this file.

## Besu use-case completion checklist

Owner clarification, 26 November 2026: the intended Besu delivery covers all
seven capabilities below. Completing transfer-only Stage A (B10) is a milestone,
not completion of the expanded stablecoin lifecycle. Complete the existing private
payment path first, then the issuer lifecycle on Besu under I01-I09. Automatic
network selection remains B11 after B10; it does not substitute for issuer work.
The statuses and closure decisions remain in the referenced rows below; this
checklist groups their use cases without creating a second work queue.

1. **Minting â€” I01-I04, I08-I09.** Authorize an issuer, record a simulated fiat
   deposit, validate screening and mint the exact approved quantity to an eligible
   customer wallet. Prove unauthorized, duplicate, restricted-wallet and invalid
   amount requests cannot create supply; reconcile issued supply to simulated
   reserve records and immutable journals. A separately identified issuer token
   requires the I01 ADR; preserve existing fixed-supply DEMOUSD and public USDC.
2. **Burning and redemption â€” I02-I03, I05, I07-I09.** Accept an authorized
   redemption, establish token custody/lock or burn ordering, confirm the exact
   burn and perform simulated fiat settlement. Prove insufficient balance,
   restricted wallets, duplicate requests, rejected settlement and uncertain chain
   outcomes cannot cause duplicate burns/payouts or premature completion. Reconcile
   supply reduction, redemption liability and simulated reserve movement.
3. **Transfers â€” B01-B04, B07-B10; I02, I06, I08-I09.** Complete the independently
   approved private payment from the exact treasury to the approved beneficiary,
   with durable signing/submission, receipt and Transfer-event verification.
   Prove idempotent replay, restart recovery, insufficient funds, quorum/RPC loss,
   revert and unknown outcome handling. For the issuer token, also enforce agreed
   pause, wallet and transaction-limit policies. Never change chain after signing.
4. **Balance management â€” B02, B05-B06; I03, I07.** Query network/contract-scoped
   token and native-gas balances, held/pinned reservations and available capacity;
   expose issuer total supply and restricted/frozen status where applicable.
   Prove competing payments cannot overspend, conclusive outcomes resolve holds,
   uncertain outcomes retain commitments, and chain observations reconcile with
   asset-specific accounting without treating blockchain as the application ledger.
5. **Treasury operations â€” B01-B06, B08-B12; I03-I05, I07.** Complete dedicated
   credential provisioning, readiness/fee checks, approved liquidity reservation,
   nonce/signature ownership, submission/recovery, gas accounting and reconciliation.
   Add authorized issuer allocation, issuance and redemption operations after I01.
   Demonstrate repeatable start/stop/resume and per-step database/balance evidence
   through D06. Private development gas and simulated reserves imply no real USD
   backing; institutional custody remains the explicit later scope I10/A03.
6. **Compliance controls â€” B01, B09; I01-I03, I06, I08-I09.** Enforce operator,
   approver and issuer authorization; integrate labelled simulated KYC/AML/sanctions
   decisions, screening expiry and per-customer/operation limits. Implement agreed
   contract-level allowlisting/blocklisting, pause/unpause and emergency freeze,
   including role changes and denied-operation audit. Test restrictions across
   transfer, delegated transfer, mint and burn. Besu node/account permissioning
   does not replace token-level or business compliance controls.
7. **Blockchain transaction monitoring â€” B03-B06, B09-B10; I07-I09.** Track durable
   transaction identity, pending/submitted/confirmed/reverted/unknown outcomes,
   receipt/log identity, confirmations/finality and restart/redelivery recovery.
   Extend monitoring to mint, burn, pause/freeze, role and wallet-policy events;
   handle reorganization according to the accepted chain policy. Retain correlated
   audit evidence and reconcile exact token, gas, supply and reserve records.

Acceptance for this expanded scope requires executed Besu deployment and lifecycle
scenarios (I09), contract security/negative tests (I08), PostgreSQL concurrency and
rollback checks (B01/B02/D01), authenticated UI/API evidence (B07/D02), operational
documentation (D06/D07) and a separate public Base Sepolia regression (B10).
Compilation, unit tests, simulated screening or historical public success alone
cannot close the expanded Besu scope. Existing public functionality stays intact.

## Besu private rail

Source: [accepted ADR-016](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[setup](../infrastructure/besu/README.md),
[runtime](../infrastructure/besu/RUNTIME.md),
[bindings](../infrastructure/besu/SETTLEMENT-BINDINGS.md),
[private quotes](../infrastructure/besu/QUOTE-DRAFTS.md),
[reviews](../infrastructure/besu/APPROVAL-REVIEWS.md).

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| B01 | IN PROGRESS | Durable execution-owner claim for exact canonical review/version, quote, preparation, instruction, independent acceptances/votes and current authorized identities. V16 claim code and unit regressions pass. The PostgreSQL persistence suite now has 13 cases, including the full payment migration chain through V17, JSONB replay, rollback/audit atomicity, concurrency, scoped foreign keys, immutability/integrity and the new execution projection. A further nine-case approval/claim IT exercises real JPA approval/decision repositories and the JDBC claim/audit store; only quote-owner reads use persisted fixtures. Both suites compile but have not executed because Docker/psql are unavailable locally. B02 adds the atomically written execution projection; claim alone grants no signing/submission authority. PostgreSQL approval/JPA/JDBC execution remains required for closure. |
| B02 | IN PROGRESS | Implemented the internal approved-claim to fresh Besu validation and atomic token/native-gas hold/pin/evidence boundary. Payment V17 projects exact execution material into the immutable owner claim; blockchain V9 stores immutable execution-time observation. READ_COMMITTED treasury locking, exact replay without RPC/lease renewal, current review/owner checks, fresh full execution-only fee coverage and rejection of partial prior reservations are covered by unit tests. Focused Java regression passed 153 tests on 26 November; full payment/blockchain/shared reactor then passed 745 tests with one optional model skip, zero failures/errors (16:20:57 Asia/Calcutta). The blockchain PostgreSQL suite now has 10 cases, including worker concurrency, competing capacity and hold/pin/evidence rollback; it compiled but is unexecuted. Complete B01 and real PostgreSQL cross-owner acceptance before closure. No HTTP/listener/customer activation or signing/submission; runtime wiring remains B08 and durable submission B03. Historical claims without the projection fail closed and are not backfilled. |
| B03 | IN PROGRESS | 27 November B08 adds a separately gated first-submission worker and explicit observation-only recovery API; saved SIGNED rows cannot first-broadcast through monitoring. Default-off behavior and restart use of one signature/hash are covered in the B08 focused/regression evidence.  26 November funding handoff increment: new signatures and first attempts now require FUNDED owner state, exact committed simulated funding/outbox proof and all three matching POSTED allocation journals, rechecked around slow RPC/signing work. Historical attempted/submitted recovery remains observation-only. See B08 validation and remaining integration gates.  Internal BesuSubmissionService now persists exact decoded signature/nonce/hash before a separately committed single-attempt marker and broadcast. V10 adds immutable signed material, unique treasury-scope nonce/hash, allowed submission transitions and append-only audit. Fresh owner/review/token/shared-gas checks precede signing and first attempt; retries recover the same hash read-only without re-signing, fee replacement, automatic rebroadcast or chain switching. Unknown/read failures retain PINNED funds; another signed treasury operation is blocked until the B04 receipt owner conclusively resolves it. Focused Besu tests passed 118; full payment/blockchain/shared reactor passed 773 with one optional model skip, zero failures/errors on 26 November at 18:43:53 Asia/Calcutta. Seven added PostgreSQL cases bring BesuLiquidityPersistenceIT to 17; all compile but none executed locally because Docker/psql are unavailable. Complete B01/B02 and PostgreSQL/live restart/timeout/unknown acceptance, then B04 reverted/final receipt integration and B08 runtime wiring before closure. No endpoint/listener/UI or customer activation is enabled. See settlement bindings for crash windows and conservative recovery limits. |
| B04 | IN PROGRESS | 27 November step 4 adds default-off conclusive-revert payment failure and explicitly authorized control recovery; runtime acceptance remains B06/B08-B10. 27 November B08 connects a default-disabled bounded recovery/receipt scheduler to the existing receipt owner; ledger continuation and payment-owner settlement/reconciliation closure are connected under B08; runtime acceptance remains pending. See B08 for current test evidence.  Internal BesuReceiptService now verifies the saved signed transaction against a canonical QBFT receipt, exact Transfer log and complete execution-only fees. V11 atomically persists immutable receipt, CONFIRMED/REVERTED submission and audit; the receipt resolves the historical pinned capacity commitment. Missing, invalid or unavailable evidence keeps capacity pinned. New balance observations must include every resolved receipt block; duplicate/restarted polling and terminal submission replay do not sign or broadcast. Five new PostgreSQL cases bring BesuLiquidityPersistenceIT to 22; they compile but have not executed because Docker/psql are unavailable locally. Focused Besu suite passed 145 tests; after the additional canonical-block-reread case, the full payment/blockchain/shared reactor passed 801 tests with one optional model skip, zero failures/errors, on 26 November at 19:17:29 Asia/Calcutta. Complete real PostgreSQL/live receipt/revert/restart acceptance and owner payment/outbox integration under B08, then B05/B06 journals/reconciliation, before closure or activation. No scheduler, endpoint or customer selection was enabled. Depends B03. |
| B05 | IN PROGRESS | 27 November step 4 adds an append-only, explicitly authorized DEMOUSD in-flight release with immutable source/actor/original/release linkage and deferred acknowledgement guard. Original USD/allocation/submission and gas journals remain POSTED. See B06/B08 for current regression and unexecuted PostgreSQL acceptance. 27 November B08 connects default-disabled ledger-owned discovery and submission/receipt continuation to the existing accounting service. Exact owner revalidation, ordering, replay and actual fee/revert semantics are retained; no owner payment closure is implied. Focused suite passed 73; full affected Java regression passed 1156 plus one optional model skip at 08:02:30 IST, zero failures/errors. Two new PostgreSQL discovery storage-fixture cases compile but have not executed. See B08 and docs/36 for acceptance limits. 26 November handoff increment: fundAndAllocate now rejects absent/mismatched payment-owner committed funding before posting. A default-disabled internal BesuFundingHandler validates the exact persisted qualified outbox command; the three immutable journals form the allocation acknowledgement for signing. The exact SIMULATED-BESU execution reference is supported alongside historical private evidence. See B08 for test evidence and runtime acceptance.  Implemented gated internal BesuAccountingService, separate adr016-control-v1 account mapping and ledger V9 migration. Simulated USD funding/conversion plus DEMOUSD allocation, saved-hash in-flight movement, exact successful receipt delivery and actual BESU_GAS fee journals use existing immutable balanced posting/locking. Every private journal retains canonical execution/chain/genesis/contract provenance; private token precision is six decimals and gas eighteen. Immutable receipt acknowledgement and confirmation/fee journals commit together; complete zero fees create no fabricated expense, and reverts post actual gas without successful token delivery or silent reversal. Existing public evidence serialization/fingerprint input is preserved. Full payment/blockchain/ledger/reconciliation/shared Java reactor passed 928 tests with one optional model skip, zero failures/errors on 26 November at 19:57:16 Asia/Calcutta; this includes 47 new private accounting tests and 126 total ledger tests. Eight new real-PostgreSQL ledger cases compile but have NOT executed because Docker/psql are unavailable locally; payment/chain reads in those ledger ITs are mocked. Execute migration/rollback/immutability/concurrency tests, then integrate the trusted payment-owner funding/debit and outbox commands under B08 and exact private reconciliation under B06; complete live/private/public acceptance under B09/B10 before closure or activation. No runtime migration, endpoint/listener/Compose activation, chain transaction or wallet change. Depends B04 and ADR-015/016. |
| B06 | IN PROGRESS | 27 November step 4 implements conclusive-revert failure and explicitly authorized CONTROL-book release under ADR-015/016. Shared BesuRevertEvidence verifies canonical execution, exact historical funding/allocation, REVERTED saved submission, QBFT receipt and complete actual fee accounting; UNKNOWN, successful or inconsistent evidence cannot authorize compensation. A current customer PAYMENT_APPROVER independent of the funding operator grants RELEASE_REVERTED_TOKEN_IN_FLIGHT for the exact failed execution/source hash. The ledger appends BLOCKCHAIN_REVERT_RELEASE linked to the original submission, preserving all original POSTED journals, simulated USD funding/conversion and actual gas; no USD refund, mint, transfer retry or successful delivery is implied. Payment stays FAILED. Exact current-source successful-settlement comparison and independent owner closure remain in place; reverted transfers never become MATCHED delivery. Full affected Java regression: 1256 cases, 1255 passed, 1 optional model skip, zero failures/errors at 2026-11-27T10:02:24+05:30. Six new PostgreSQL recovery guard/rollback cases compile but are unexecuted. Remaining closure criteria: execute real PostgreSQL migration/atomicity/immutability/concurrent replay plus actual cross-owner source validation and private revert/restart acceptance; expose the trusted authenticated recovery action/evidence through B07/B08; complete B09/B10 private/public acceptance. The three new ledger ITs use minimal foreign-owner storage fixtures and do not establish full cross-service acceptance. Customer funding disposition/refund is a separate accounting policy, not inferred by this in-flight control release. Details and dated evidence: docs/36 and settlement bindings. |
| B07 | IN PROGRESS | 27 November: staged customer-scoped private settlement API, DEMOUSD/BESU_GAS screens, exact review/quote acceptance and execution references, private receipt/ledger/recovery evidence, isolated application Compose overlay and protected variable-file/start/stop/resume helper implemented. The helper retains the explicitly selected public mode, verifies existing private chain identity, and suppresses private new funding/first submission on resume. Every private capability defaults off; new private instruction creation remains hard-gated pending acceptance. Frontend: 39 tests, production build and lint passed; lifecycle suite: 28 passed, one Windows permission-test skip. Authenticated API owner/controller checks: 12 passed. Remaining closure criteria: actual authenticated browser, Spring configuration/Compose binding, PostgreSQL owner transactions and complete private end-to-end acceptance B09 plus public regression B10. Operator guide: infrastructure/besu/APPLICATION.md. |
| B08 | IN PROGRESS | 27 November: steps 1-4 are code-integrated behind separate default-off gates. Canonical approved claims lead to fresh reservations and committed simulated funding/allocation; saved signing/submission and receipt evidence lead to ledger journals; successful settlement requires fresh exact evidence and independent current MATCHED reconciliation report before owner closure. Conclusive accounted reverts lead to atomic FAILED/history/audit/failure acknowledgement, followed only by explicitly granted source-bound control compensation. Payment V20 adds immutable failure/authorization records; ledger V10 links authorization, original/release journals and an immutable compensation acknowledgement, with a deferred guard blocking a generic unacknowledged release. Failure and compensation schedulers discover bounded canonical pages and delegate one owner transaction per item; no scheduler auto-authorizes recovery. BESU_RECOVERY_ENABLED and BESU_RECOVERY_WORKERS_ENABLED default false, as do existing private continuation gates. Focused first increment passed 44 tests; final full affected Java reactor: 1256 cases, 1255 passed, 1 optional model skip, zero failures/errors at 2026-11-27T10:02:24+05:30. All six added PostgreSQL recovery cases compile but did not execute. Step 5 staged application/UI and isolated lifecycle integration is now implemented under B07; final affected Java reactor: 1268 cases, 1267 passed, one optional model skip, zero failures/errors at 2026-11-27T10:59:41+05:30. Remaining: actual authenticated application/Compose acceptance and PostgreSQL cross-owner SQL/JPA-JDBC atomicity, migration, rollback and concurrency; live delivery/redrive/revert/restart acceptance under B09 and public Base Sepolia regression B10 before activation or closure. No runtime migration, wallet change, mint, chain transaction, deployment or customer activation. Base Sepolia remains the default. Logs and detailed boundaries: docs/36. |
| B09 | VALIDATION REQUIRED | Execute four-validator QBFT setup, genesis/token/code pinning, deployment/preflight and persistence checks on Docker in Codespaces. Verify permissioning, unauthorized-account rejection, validator loss/quorum failure and fail-closed readiness. Local source/unit checks and verified credentials are not chain acceptance. |
| B10 | OPEN | Provide and execute a reviewed staged acceptance/activation path without prematurely removing the private instruction hard gate; current UI code alone does not permit new private customer payments. 27 November acceptance tooling now requires 19 fresh Failsafe database suites, including all nine Besu persistence suites and Visa wallet persistence; suite-origin, duplicate, skip and source-inventory checks are implemented. Ordinary blockchain verify now selects BesuLiquidityPersistenceIT as well as the public treasury suite. Execute these checks in Codespaces; local tooling tests and Maven validation do not close database acceptance. Run complete authenticated private payment through two approvals, transfer, receipt, asset journals and reconciliation; collect per-step DB evidence and restart/duplicate/unknown recovery results. Run separate public Base Sepolia regression before activation. Requires B01-B09. |
| B11 | DEFERRED | Stage B measured/versioned multi-network signals and automatic policy choice: fees, availability, liquidity, expected latency, evidence quality/expiry and required outcome. Prove stale/no-eligible/unavailable cases and allowed preference changes. No cross-asset substitution or rerouting after signing/submission uncertainty. Begins after B10. |
| B12 | VALIDATION REQUIRED | Create or securely provision dedicated matching Besu keystore/password files in Codespaces; verify owner-only protection and successful unlock there. Windows files were verified locally; they do not automatically exist in another checkout. Never regenerate or replace historical/public wallets as a shortcut. |

## Additional issuer-lifecycle intent captured 26 November 2026

The owner supplied a broader fiat-backed token lifecycle brief while Besu work
was continuing. These are additional requirements to assess and implement after
the applicable design decision; they do not describe existing functionality.
ADR-016 currently permits only a fixed-supply private DEMOUSD contract with no
later mint or redemption promise. I01 must precede conflicting contract changes.
The existing payment/Besu work continues under its accepted boundaries. External
fiat, reserve backing, KYC/AML and custody integrations must retain their explicit
simulation/integration-point labels until actually integrated and evidenced.

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| I01 | OPEN | Propose an issuer-lifecycle ADR and obtain acceptance before changing the fixed-supply contract decision. Define a separately identified issuer token, mint/burn authority, reserve/backing assumptions, redemption, custody, chain targets and security model; preserve existing DEMOUSD and public USDC history. No real fiat backing or regulated status is implied by this brief. |
| I02 | OPEN | Implement the agreed Solidity ERC-20 issuer contract: controlled later mint, burn/redemption semantics, pause/unpause, wallet allowlist/blocklist and emergency freeze. Specify role separation, supply invariants, zero-address handling, privileged-operation events and exact behavior of transfers/approvals/mint/burn under each restriction. Depends I01. Existing constructor supply allocation is not an issuer mint workflow. |
| I03 | OPEN | Implement issuer/treasury Java Spring APIs and appropriate ethers.js tooling/SDK components for authorized token operations and balance/supply queries. Specify authentication, role checks, idempotency, exact decimals, errors, approvals, limits and durable operation ownership/recovery. Reuse existing services where justified; do not create a new microservice by default. Depends I01/I02. |
| I04 | OPEN | Fiat deposit to screening to approved mint to customer wallet workflow, with deposit reference, duplicate prevention, simulated reserve/deposit records, exact token issuance and balanced immutable accounting/reconciliation. External bank funding and reserves stay simulated unless explicitly integrated. Depends I01-I03. |
| I05 | OPEN | Customer redemption to controlled token custody/lock or burn to fiat settlement workflow. Define ordering, authorization, rejection/cancellation, uncertain chain/bank outcomes, burn evidence and replay-safe settlement/accounting. Fiat payout stays simulated unless explicitly integrated. Depends I01-I03. |
| I06 | OPEN | Issuer compliance policy: KYC status and AML/sanctions provider integration points, wallet eligibility lifecycle, per-operation/customer transaction limits, screening expiry, authorized emergency pause/freeze and auditable decisions. Prove simulated results remain labelled; application/network permissioning alone does not establish token-enforced restrictions. Depends I01/I02. |
| I07 | OPEN | Extend durable chain monitoring, audit and reconciliation to issuer mint/burn/pause/role/wallet-control events and supply/reserve/redemption records, including restart, duplicate delivery, confirmation/finality and reorganization handling for agreed chain targets. Existing transfer/receipt monitoring is a foundation, not issuer-lifecycle acceptance. |
| I08 | OPEN | Establish Foundry or Hardhat contract unit/integration tests and basic security checks for agreed issuer contracts: access/authorization, privileged role changes, supply/amount invariants, business logic, pause/list bypass, relevant reentrancy/callback paths and integer/boundary conditions. Exercise negative cases and report tool versions/results/limitations; library reuse and compilation are not a security audit. Depends I01/I02. |
| I09 | OPEN | Deploy the agreed issuer contract to selected local Besu and/or Ethereum-compatible test environment, verify deployment identity, execute complete deposit/mint/transfer/redemption/burn scenarios and document architecture, APIs, contracts, deployment, tests and PoC limitations. Deployment target and any public testnet spend must be explicit. Extend D06/D07 rather than create competing documentation queues. |
| I10 | DEFERRED | Institutional custody/wallet and enterprise integrations beyond encrypted local keystores: define provider/API boundary, signing policies, approval controls, key lifecycle and enterprise deposit/redemption event contracts; verify integrations when access exists. Coordinate Secrets Manager/KMS and later MPC/HSM scope with A03, without claiming encrypted files are institutional custody. |

ERC-721/ERC-1155 remain the separate scope decision P02. Quorum framework
requirements are P03 below. Their appearance in the skills/framework list does
not establish that all frameworks and token standards are required deliverables.
## Visa simulation and external API compatibility

Source: [Visa architecture](07-VISA-SIMULATOR.md),
[wallet simulator](39-VISA-WALLET-SIMULATOR.md),
[ADR-003](../adr/ADR-003-VISA-SIMULATOR.md).
The current simulator uses the application's adapter contracts and internal HTTP
APIs. It is not a drop-in Visa Direct endpoint or a Visa-certified integration.

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| V01 | VALIDATION REQUIRED | Run wallet simulator PostgreSQL migrations, concurrent prefunding/reservation, arithmetic conservation, rollback, replay, decline and UNKNOWN resolution acceptance. Fiat pockets and calculated token provider/recipient simulation balances exist; persist exact before/after evidence and keep SIMULATED labels. Local Visa reactor previously passed 159 tests; database IT execution remains separate. |
| V02 | DEFERRED | Connect simulated wallet payouts to customer orchestration, approved quote/outcome, UI, application ledger and reconciliation. Exercise token/network-scoped capacity and beneficiary simulated credit end to end; provider success never substitutes for a real chain receipt. Resume after Besu quote/accounting contracts are exercised, per ADR-016. |
| V03 | DEFERRED | Visa Direct card AFT/OCT contract fidelity: select authorized/versioned reference contracts, map request/response/error bodies and status semantics, authentication/protocols, correlation/idempotency, status inquiry/notifications, timeouts and negative contract tests behind VisaPaymentAdapter. Owner agreed to defer this and continue Besu. Do not claim existing custom payloads match Visa APIs. |
| V04 | DEFERRED | Separately evaluate and map the applicable Visa wallet/stablecoin payout API. Card AFT/OCT compatibility alone does not establish wallet API compatibility. Confirm supported product, authorized specifications and simulator cases before implementation. |
| V05 | DEFERRED | Implement authorized VisaDirectAdapter and obtain sandbox access, credentials, provider verification/certification and reconciliation evidence if real integration is pursued. Current fiat and wallet payouts remain SIMULATED. Depends V03/V04 and external access. |
| V06 | VERIFY | Reconcile older proposed simulator endpoints/states with implemented contracts, especially cancellation, failure injection and status inquiry. Retain only evidenced gaps; do not reimplement capabilities already present in the later Step 8 or wallet simulator. |

## Demo, acceptance and final deliverables

Source: [delivery plan](21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md),
[architecture](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md),
[proof package](35-HACKATHON-PROOF-PACKAGE.md),
[SOP](37-DEMO-OPERATIONS-SOP.md),
[copy/paste walkthrough](38-COPY-PASTE-DEMO-WALKTHROUGH.md),
[activity history](36-POC-ACTIVITY-STATUS.md).

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| D01 | VALIDATION REQUIRED | Execute strict Docker/PostgreSQL migrations, constraints, transactional rollback, concurrency and idempotency suites for the current revision in Codespaces. Record actual passes/skips; old unit totals or compiled IT sources do not close this gate. On 27 November the owner passed Node, frontend build/lint and CDK build/tests. After the payment fixture correction, a focused payment reactor succeeded and full verification advanced to blockchain-service. BesuLiquidityPersistenceIT executed 26 cases with one failure and two errors: an assertion checked the outer commit exception instead of its expected PostgreSQL atomicity-guard cause, and two synthetic address seeds produced invalid lengths. Corrected only those test expectations/fixtures; production validation and constraints remain intact. Require the corrected suite and full strict Codespaces verification to pass with fresh evidence. Docker is unavailable locally; later Java modules and the final database-evidence gate remain unaccepted. Dated evidence is in activity status. |
| D02 | VALIDATION REQUIRED | Current-version authenticated UI/API walkthrough, independent operator/approvers, same-key replay, expired/superseded approval rejection and denied foreign-customer access. Prior public payment demonstrates a milestone, not every later regression. |
| D03 | VALIDATION REQUIRED | Actual configured OpenAI or Bedrock inference with permitted read-only tool trace, provider/model/usage and limits; deterministic ranking and fixture output are not an AI recommendation. Verify no financial/model approval authority. |
| D04 | OPEN | Complete second fictional customer and controlled simulated provider disruption demonstrations with comparable deterministic/model evidence, timing and cost measurements. Separate measured results, simulated inputs and commercial hypotheses. |
| D05 | VALIDATION REQUIRED | Preserve/export independently reviewable public receipt, exact Transfer log, fee components, per-asset journals and final reconciliation report for the successful public payment; verify bundle availability/redaction rather than declaring the transfer itself unimplemented. |
| D06 | OPEN | Extend the existing copy/paste SOP/walkthrough with the completed Besu branch and wallet-payout branch when activated: prerequisites, build/start/stop/restart, every state/DB query, expected balance movements, failures/recovery and receipt validation. Rehearse from clean documented prerequisites. |
| D07 | OPEN | Produce final StableCoins-Solution-Architecture-and-Design.docx from current Markdown/ADRs and actual evidence, including diagrams, contracts, security, data ownership, ledger, deployment, operations and limitations. Render and inspect every page. Markdown exists; final rendered Word artifact is not established. |
| D08 | DEFERRED | Complete published OpenAPI specification and contract checks for supported APIs; reconcile with implemented authentication, errors and idempotency. Original plan D7 explicitly deferred this deliverable. |
| D09 | VERIFY | Reconcile old delivery-plan checklist entries (wallet provisioning, identity setup, service tokens, account/Region selection, developer prerequisites and old test failures) with later activity evidence. Close evidenced items; retain only unresolved scope here. Do not use the historical 9/11 count as current acceptance. |
| D10 | VALIDATION REQUIRED | Conversation Word export validation: original chat messages/screenshots and available text attachments were exported on 26 November. Recover the two unavailable pasted-text attachments (message references 1565 and 1937 in the export) if available, and complete visual pagination review. Structural/text fidelity checks passed; packaged renderer lacked LibreOffice and native Word PDF/page-render attempts failed or stalled. This transcript is separate from the architecture deliverable D07. |

| D11 | IN PROGRESS | Whole-solution backup/recovery: owner selected S3 in us-west-2 on 27 November. Implement the [recovery design](BACKUP-AND-RECOVERY.md): verify AWS identity/authentication; provision private/versioned storage and least-privilege access; independently recoverable client-side encryption; inventory/preserve every wallet generation, secrets/configuration, correct Git history and uncommitted work; coordinated PostgreSQL, Besu identity/state and messaging checkpoint; Windows/Codespaces backup, failure reporting, scheduling/age monitoring and reviewed retention. Close only after independently restoring into an isolated environment and verifying chain/token identity, ledger/reconciliation and pending-transaction duplicate protection. The owner verified an authenticated assumed-role session. A separate retained/private/versioned CDK storage stack, Windows setup procedure and four storage assertions are implemented; TypeScript compilation, synthesis and all 19 infrastructure tests passed locally (full Jest rerun outside the sandbox). AWS deployment/permissions, scoped clients, encrypted captures, unattended authentication, monitoring and actual restore acceptance remain pending; no bucket, backup upload or restore was created. Preserve R07 retention boundaries; no automatic wallet cleanup. |

## AWS and later product scope

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| A01 | DEFERRED | After Codespaces acceptance, validate application AWS deployment/operations using the accepted ECS Fargate, API Gateway, Aurora PostgreSQL, EventBridge/SQS and CDK architecture. Confirm SSO directory Region/permission set and deployment prerequisites; exercise health, identity, alarms, retries/DLQ and recovery. Existing IaC is not deployment evidence. Sources: delivery plan Step 10 and architecture. |
| A02 | DEFERRED | Plan/deploy Besu validators/RPC in containers on EC2 as requested, separately from application services. Validate private networking, RPC authentication/authorization, permissioning, TLS, storage/backups, node identities, monitoring and quorum/recovery. Single-host demo is not institutional consortium resilience. Depends B10 and deployment design review. |
| A03 | DEFERRED | Validate deployed Secrets Manager/KMS/IAM credential delivery and rotation; avoid secrets in Git, config, logs or database. Institutional MPC/HSM/custody is a later production evolution, not a completed POC claim. |
| A04 | DEFERRED | Optional Kubernetes deployment evaluation, including Amazon EKS for AWS. ECS Fargate remains the accepted application deployment target under ADR-006; Kubernetes is excluded from the initial POC. Closure requires a documented decision to retain ECS Fargate or, if Kubernetes is selected, an accepted superseding ADR and updated repository contract before implementation, followed by deployment, security, persistence, recovery and operational acceptance evidence for the agreed scope. Recording this option does not authorize a platform migration. |
| P01 | DEFERRED | Future RTP/FedNow and ACH/Fedwire adapters behind scheme-independent payment interfaces. Define eligibility, settlement finality, evidence, failure handling and accounting before adding adapters. Do not assume these rails all run through Visa. Owner agreed expansion follows Besu. |
| P02 | DEFERRED | ERC-1155 / ERC-721: not implemented. Reiterated in the additional issuer brief on 26 November; confirm the business use case and whether these standards are mandatory deliverables or contextual skills. Closure requires an explicit scope decision: document that these standards are not required, or accept the applicable ADR and implement the agreed contracts, integration, tests and deployment evidence. This entry records the conditional gap; it does not authorize implementation or change the current ERC-20 scope. |
| P03 | DEFERRED | Quorum was named alongside Ethereum/Besu in the additional issuer brief. Determine whether it is an actual target or contextual framework experience. Any additional client/deployment scope requires a documented support decision, applicable ADR and executed compatibility/operations evidence; do not assume Besu testing establishes another client's acceptance. |

## Enterprise foundations: review after current delivery

Owner direction, 26 November: preserve existing working implementation and
continue Besu. Consider the following at the end for reuse/refactoring where
needed. Audit existing implementations before proposing replacements. These
entries authorize recording the intent, not immediate framework/service changes.

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| E01 | DEFERRED | Inventory cross-cutting concerns and existing shared libraries/configuration/UI components; identify actual duplication and ownership boundaries. Propose incremental reuse only where it improves consistency without disrupting public/Besu/payment behavior. Preserve the six-service architecture; use an ADR if a boundary/platform change is required. |
| E02 | DEFERRED | Review reusable structured logging, correlation, redaction, audit and exception/error contracts. Keep diagnostic logs separate from durable business audit and immutable financial journals; verify authorization, retention, failure semantics and consistent safe API errors. Reuse existing implementations before refactoring. |
| E03 | DEFERRED | Review reusable analytics/metrics and an app-corner model token-utilization display; reuse existing AgentReport inputTokens/outputTokens and provider usage records, using actual authorized provider usage and defined scope (run/session/customer), refresh behavior and unavailable/estimated labels. Distinguish model tokens from settlement tokens. Enforce customer isolation and avoid prompt/credential disclosure; surface monetary cost only with an explicit pricing basis. |
| E04 | DEFERRED | Evaluate reusable caching only for suitable reads, with explicit owner, key/customer scope, TTL, invalidation, observability and unavailable-cache behavior. Do not treat stale balance, approval, compliance, policy or receipt caches as fresh financial execution authority. Add caching only where justified by measured need. |
| E05 | DEFERRED | Review reusable notification contracts/adapters and existing event/outbox delivery: recipient authorization, preferences, templates, retry/DLQ, deduplication and status evidence. Notification success must not substitute for payment/chain/accounting evidence; external sending requires separately authorized setup. |
| E06 | DEFERRED | Review enterprise integration interfaces/adapters, authentication, versioning, idempotency, errors, retry/recovery and monitoring. Keep domain financial decisions in their owner services and simulated/external boundaries explicit. Define concrete integrations before implementing a generic integration framework. |
## Repository audit and retention work

Source: [25 November project audit](PROJECT-AUDIT-2026-11-25.md).
Findings below are not proof they remain unfixed today; recheck before changing.

| ID | Status | Remaining work and closure criteria |
|---|---|---|
| R01 | VERIFY | Audit A01: verify project Git root/remote and isolated publication checkout before publication. Ancestor repository risk was confirmed; never broadly stage this workspace. Close after a repeatable scoped publication procedure is verified. |
| R02 | VERIFY | Audit A02: services build-context .dockerignore and explicit root .local exclusion; inspect current files, preserve Maven inputs and validate all six image builds. |
| R03 | VERIFY | Audit A03: avoid stale emitted infrastructure JavaScript resolving instead of edited TypeScript; choose separate output/source resolution or enforced build-before-test, and verify CDK entrypoint/tests. |
| R04 | VERIFY | Audit A04/A05: check references to empty services/.vscode, retain significant dated diagnostic logs, review generated artifacts/prototype ZIP/archive usefulness. Make explicit retention decisions before deletion. |
| R05 | VERIFY | Audit A06: capture actual VS Code Problems entries and diagnostic source, correct project/import/classpath scope where needed. Folder colors alone do not establish build failure. Never edit dependency templates to hide diagnostics. |
| R06 | VERIFY | Audit A09: investigate Windows Git Bash timeout in scripts/demo/db-check.test.cjs and rerun target Linux/Codespaces suite; preserve diagnostics and determine cause before changing timeout. Audit recorded 131 passes and one failure, not an all-green suite. |
| R07 | DEFERRED | Review wallet provenance/backup inventory only if needed, with owner approval for any retention change. Preserve all intentional original/replacement/Besu generations and password history; no automatic deduplication, rename, overwrite or deletion. |
| R08 | VERIFY | Audit A08: current dependency/security review and runtime/container gates have not been established by the audit's successful local builds. Record scoped security checks and current acceptance evidence without calling the audit a comprehensive security review. |

Audit A07 is tracked by B01-B11; container/DB portions of A08 are D01/B09/B10.
No duplicate work items are needed in the audit itself.

## Completed milestones retained to prevent duplicate work

| ID | Status | Evidence and boundary |
|---|---|---|
| C01 | CLOSED | Session-reported public Base Sepolia payment PAY-7EC315B302DC reached CONFIRMED and RECONCILED; transaction 0xd3fb25b6dbd5eac9d2a4c1682c2dc7454450b2d027b0e02639fb56d3688c8e0e transferred 1 test-USDC. Historical successful execution; current-version regression and exported evidence remain D02/D05. See activity status and SOP. |
| C02 | CLOSED | Dedicated Besu pair and treasury password file verified locally on Windows on 26 November; both saved keystores unlocked and owner-only ACLs/Git ignore checked. No chain startup or Codespaces credential verification implied. See credential runbook and activity status. |
| C03 | CLOSED | Internal simulated Visa wallet payout engine and calculated provider/recipient before/after balance evidence implemented alongside existing fiat simulation. Prior Visa reactor run: 159 passed, zero failed/skipped. UI/customer integration, PostgreSQL acceptance and external Visa contract fidelity remain V01-V05. |

## Reconciliation of previous TODO sources

| Former source | Treatment now |
|---|---|
| Root README and docs/36 activity status | Entry points and dated observations; link here for remaining work. |
| docs/17 implementation plan and docs/21 delivery plan | Historical sequence/checklists; unfinished deliverables mapped to D/A items above. |
| docs/28 architecture and docs/35 proof package | Detailed design and acceptance definitions; closure tracked by D01-D09/A01-A03. |
| infrastructure/besu README/runtime/bindings/quotes/reviews | Technical implementation details; all outstanding gates tracked by B01-B12. |
| docs/07 and docs/39 Visa notes; session deferral | Simulator details and limitations; V01-V06 are the current queue. |
| Project audit findings A01-A09 | R01-R08 plus B/D cross-references; retain audit as a dated report. |
| Ignored .local/poc-status handovers | Private supporting evidence; never publish contents. Public backlog contains no secret payloads. |

No other authored file named backlog or TODO was found by the initial filename
scan outside dependencies, generated outputs and .local. Future discoveries must
be merged into this file, preserving their source and recording uncertain status
as VERIFY rather than inventing completion or reopening known completed work.
