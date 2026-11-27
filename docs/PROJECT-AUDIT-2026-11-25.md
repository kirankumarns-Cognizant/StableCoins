# Project audit and retention backlog — 25 November 2026

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


## Outcome and scope

The current Java reactor, frontend and infrastructure builds passed. The coloured
VS Code folder decorations are not, by themselves, evidence of a broken build.
There are workspace, packaging and cleanup issues worth addressing below.

This was an audit of the local StableCoinsDemo checkout: a directory inventory,
build/configuration review, source conflict-marker checks, existing automated
tests and review of the operational/Besu handover. Dependency trees, generated
outputs and private local subdirectories were classified separately. This is not
a line-by-line security review of every source file or third-party dependency.

No application source fix, deletion, wallet replacement, runtime activation,
deployment, publication or payment was performed. Tests regenerated normal build
outputs and reports. Password files and encrypted wallet payloads were not read.

## Findings and proposed work

### A01 — High: project is inside an unrelated parent Git repository

**Confirmed:** running `git rev-parse --show-toplevel` in StableCoinsDemo resolves
to `C:/Users/884412/OneDrive - Cognizant/Public`, outside this project.

**Impact:** a broad stage/commit/push from here can include unrelated work or use
the wrong repository. Files visible locally are not proof that Codespaces or the
intended GitHub repository contains the same versions.

**Action:** use the existing isolated publication checkout for controlled
publication, or establish a separately verified project checkout. Verify its
root, remote and intended diff before publication. Do not run `git add .` here.
**Retention:** keep the project and isolated checkout; do not delete or reinitialize
the ancestor repository as a cleanup step.

### A02 — Medium: Docker ignore file does not match current build contexts

**Confirmed:** all six service build definitions in `docker-compose.yml`
(lines 257–347) use `context: ./services`. The inventory found only the root
`.dockerignore`, with no `services/.dockerignore` or Dockerfile-specific ignore
file. The services directory contains generated `target` directories and 31
historical log files totalling 11,640,594 bytes.

**Impact:** the root ignore file does not filter these service contexts. Unneeded
artifacts can be included in context transfer/cache input. Current Dockerfiles
use specific `COPY` paths; this audit did not find evidence that these logs or
root wallet directories are copied into final images. Root `.local` is outside
the current service contexts.

**Action:** add an appropriate ignore file at the effective build-context root,
then verify all six image builds. The existing root `.dockerignore` also lacks
an explicit `.local/` exclusion; add that protection before any root-context
build is introduced. Keep required Maven wrapper/config/source inputs.
**Retention:** keep Dockerfiles and source; generated outputs are cleanup
candidates only after useful diagnostic evidence has been retained.

### A03 — Medium: infrastructure tests can resolve stale emitted JavaScript

**Confirmed:** `infrastructure/tsconfig.json` emits JavaScript/declarations beside
TypeScript sources (no separate `outDir`). Matching `.ts`, `.js` and `.d.ts` files
exist under `bin`, `lib` and `test`. `infrastructure/jest.config.js` transforms
TypeScript but does not explicitly prefer TypeScript when resolving imports.
The emitted files are ignored by `infrastructure/.gitignore`.

**Impact:** running tests after editing TypeScript, without rebuilding first,
can resolve a neighbouring stale JavaScript module. The audit ran the build
before Jest and passed; it does not demonstrate that tests alone avoid this risk.

**Action:** use a separate emission directory and explicit test/source resolution,
or make build-before-test mandatory. Validate the CDK entrypoint as part of that
change. **Retention:** retain TypeScript source, Jest/CDK configuration and lock
files. Treat emitted files as generated artifacts, not alternative source copies.

### A04 — Low: empty file named services/.vscode

**Confirmed:** `services/.vscode` is a zero-byte regular file. It is not a settings
directory. The actual root `.vscode/` directory contains the editor settings.

**Action:** check that no local tooling deliberately references this file, then
remove it in a later cleanup. **Retention:** review for removal; no deletion was
performed. Keep the root editor configuration.

### A05 — Low: historical build logs are mixed with application sources

**Confirmed:** the 31 service-root logs include earlier security, approval,
treasury and reactor verification runs. They account for most of the service
inventory's non-source bytes. `*.log` is ignored, but ignored files still occupy
disk and may enter an unfiltered Docker context.

**Action:** retain significant acceptance evidence with its date/commit, then
archive redundant logs under ignored validation storage. Establish a retention
policy before deleting them. **Retention:** archive/review; do not blanket-delete
all `.local`, logs or transaction evidence.

### A06 — Verification needed: exact VS Code Problems remain unidentified

**Observed:** `.vscode/settings.json` selects the local JDK 21 and already excludes
`**/node_modules/**` from Java imports. The Java reactor passed. The saved launcher
in `.local/CreateWallets/CreateVerifiedWalletPair.java` currently has no literal
`diff --git` header or `+import` patch markers. A separate patch-text file exists
beside it and is historical material, not Java source.

**Limit:** the actual editor Problems panel was not captured. Folder colours may
represent diagnostics or source-control decorations; their precise cause is not
established by this audit. Standalone local launchers may need an explicit
classpath even when the reactor builds successfully.

**Action:** capture Problems entries with file, message and diagnostic source;
reimport only the intended Maven reactor and exclude local publication/generated
trees if indexing them causes diagnostics. Do not delete valid services or edit
AWS CDK templates inside `node_modules` to suppress editor errors.

### A07 — Planned acceptance gap: Besu is not yet an executable payment rail

**Confirmed:** `infrastructure/besu/SETTLEMENT-BINDINGS.md` documents observation,
availability and reservation integration boundaries, including
`RESERVATION_CLAIM_INTEGRATION_PENDING` and activation gates (around lines 215–253).
This is intentional staged implementation under ADR-016, not removable dead code.

**Action:** complete token-aware quote/review and exact independent approvals,
execution claims connected to commitments, durable nonce/submission ownership,
receipt-driven release, DEMOUSD/BESU_GAS journals and owner reconciliation.
Execute private-chain acceptance and separate public-testnet regression before
activation or automatic selection. **Retention:** keep the additive Besu module,
contracts, migrations and tests. Preserve the working public-testnet path.

### A08 — Verification gap: live/container acceptance was not run here

`docker` and `psql` are not on this host's PATH. This audit did not validate a
running Codespaces stack, container builds, PostgreSQL migrations/concurrency,
browser login/payment flow, AWS deployment, new public transaction or live Besu
transfer. The optional live-model test was skipped. Dependency vulnerability
databases were not queried. Passing local tests must not be presented as these
separate acceptance gates having passed.

### A09 - Medium: operational walkthrough test times out on this Windows host

**Confirmed:** the operational-script suite completed with 132 tests: 131 passed
and one failed. In `scripts/demo/db-check.test.cjs:11`, the test named
`all phases pass quoted IDs to psql and stream only the intended read-only SQL files`
hit `spawnSync .../Git/bin/bash.exe ETIMEDOUT` at its 30-second limit.
An isolated rerun of this same test also failed with the same 30-second timeout
(0 passed, 1 failed; `scripts-db-check-focused.log`).
The test injects a temporary mock Docker command; it is not evidence of a real
PostgreSQL or payment failure. Other walkthrough syntax/link tests passed.

**Action:** investigate Git Bash startup/process latency and rerun on the target
Codespaces/Linux environment. Preserve failure diagnostics; do not simply raise
or remove the timeout without understanding the delay. **Retention:** keep the
script and test. The operational suite is not fully green.

## File and folder retention decisions

| Files/group | Role | Recommendation |
| --- | --- | --- |
| Six service directories under `services/` | Deployable business services | KEEP; all are included in the reactor |
| `services/platform-flow`, `services/platform-security` | Shared contracts and security libraries | KEEP; build dependencies, not extra microservices |
| `services/config`, Maven wrapper, POMs | Build/runtime configuration | KEEP; inspect changes before publication |
| `frontend/src`, tests and build configuration | Application UI | KEEP |
| `infrastructure` TypeScript, CDK and Besu source | Deployment and private-chain implementation | KEEP |
| `scripts`, including wallet-generation source | Operations, validation and local tools | KEEP; not all are long-running application code |
| `docs`, `adr`, `prompts`, AGENTS/CLAUDE instructions | Design and operational knowledge | KEEP; review for stale guidance rather than deleting wholesale |
| `TransactionEvidence` | Historical successful payment evidence | KEEP; review contents before sharing |
| `prototype/`, `prototype.zip`, root `image.png` | Demonstration/reference artifacts | REVIEW FOR ARCHIVE; prototype is referenced by README/runbook; establish whether ZIP is a distinct snapshot first |
| `node_modules`, `target`, `dist`, `cdk.out`, emitted infrastructure JS/declarations | Regenerable dependencies/build outputs | KEEP locally as needed; exclude from source publication/build context; optional later cleanup |
| `.local/poc-status`, `.local/validation` | Private handover and test evidence | KEEP relevant records; archive older runs selectively |
| `.local/step9-ui` | Local browser automation profiles/artifacts | REVIEW retention and sensitivity; not required application source; do not publish |
| `.local/publish` | Isolated publication checkout | KEEP until repository-root/publication issue is resolved |
| `.local/tools`, launcher/verification files | Local operational tools/history | KEEP pending dependency/use review |
| `services/.vscode` | Empty regular file | REVIEW FOR REMOVAL (A04) |

### Wallet preservation rule

Keep **all existing wallet copies and generations** until the owner explicitly
approves a provenance-based retention decision. Matching filenames or 491-byte
sizes do not establish identical encrypted keys, the same account, or redundancy.

Metadata inspection found treasury and beneficiary JSON files in both
`.local/wallets/` and the intentionally created `.local/wallet-pair-.../` directory.
The replacement pair was created because the earlier recorded password did not
unlock the original wallet. Preserve original-wallet history, replacement files,
backups and the link between each address, generation and verified password record.

Do not merge, rename, overwrite or deduplicate keystores automatically. Keep
passwords/private keys out of reports, Git, logs and databases. A separate reviewed
inventory can record role, public address, origin and backup status without secrets.
This audit did not unlock wallets or compare their encrypted contents.

## Executed validation

All commands were run locally on 25 November 2026. Logs are ignored local artifacts
under `.local/validation/project-audit/` and should be reviewed before sharing.

| Check | Observed result | Log |
| --- | --- | --- |
| Java 21: `services/mvnw.cmd -o -f services/pom.xml test` | BUILD SUCCESS; 742 tests, 741 passed, 1 optional RoutingAgentLiveTest skipped; no failures/errors | `java.log` |
| Frontend: `npm.cmd run build` | TypeScript and Vite passed | `frontend-build.log` |
| Frontend: `npm.cmd run lint` | Passed | `frontend-lint.log` |
| Frontend: `npm.cmd test` | 33 passed | `frontend-tests.log` |
| Infrastructure: `npm.cmd run build`, then `npm.cmd test -- --runInBand` | Build passed; 2 suites, 13 tests passed | `infrastructure-build.log`, `infrastructure-tests.log` |
| Operational scripts: `node --test scripts/*.test.cjs scripts/demo/*.test.cjs` | 131 passed, 1 failed (Git Bash timeout; A09) | `scripts-tests.log` |
| Besu setup: `npm.cmd test` in `infrastructure/besu` | 15 passed | `besu-tests.log` |

Java current-run totals: platform-flow 83, platform-security 22, payment-service
356 (one skipped), compliance-quote-service 12, blockchain-service 173,
settlement-ledger-service 79, reconciliation-service 1 and visa-simulator 16.
These totals come from this run's log, not accumulated XML reports that may
include earlier integration-test results. Integration-test compilation is not
evidence that integration tests executed.

Inventory evidence: `.local/validation/project-audit/inventory.json`.
No enumeration errors or followed symbolic links were reported. The checked
non-private authored source/configuration files contained no unresolved merge
markers or literal patch headers. Private payloads and dependencies were excluded
from that content scan. This is not a comprehensive secret scan.

## Recommended order for subsequent fixes

1. Resolve the Git checkout boundary and capture the editor Problems list.
2. Correct Docker context exclusions and infrastructure source/test resolution.
3. Investigate the operational-test timeout; review the empty editor file, old logs and prototype archives for cleanup.
4. Finish and execute the separate Besu/container/public-regression acceptance gates.

Wallet removal is not part of this backlog. Each proposed source or cleanup change
needs its own reviewed diff and proportionate validation.
