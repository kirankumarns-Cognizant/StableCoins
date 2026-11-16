# Local-first validation before AWS deployment

Updated 2026-11-15. The delivery order is now local validation, complete local
payment acceptance, then AWS deployment planning and implementation. This is
within accepted ADR-010; AWS remains the target architecture. Step 10 is in
progress, not complete. No AWS resource is deployed by these commands.

## Overall picture and evidence boundaries

A corporate operator instructs USD 10,000, reviews a stablecoin recommendation,
obtains independent approvals, and authorizes the existing payment workflow.
Treasury sends USDC; ETH pays gas. The ledger balances each asset, an optional
USD/INR bank payout is simulated, and reconciliation checks the canonical records.
Funding, compliance, conversion, FX, Visa and bank credit remain simulated.

Validate in this order:

| Gate | What executes | What success establishes |
|---|---|---|
| 1. Code verification | Java 21 reactor, Node tests, React build/lint, CDK build/tests | Compiles and automated contracts pass; skipped database tests remain unverified |
| 2. Local infrastructure and application | Docker Compose PostgreSQL/LocalStack and six actual services | Services start, migrations run, health responds, anonymous/forged tokens fail |
| 3. Authenticated MOCK payment | Configured Cognito users, fictional dedicated data, actual APIs/UI and PostgreSQL | Local payment, approvals, journals, simulated payout and reconciliation work; no public chain proof |
| 4. Explicit live acceptance | Configured model and separately guarded prefunded testnet transfer | Actual inference and independently verified public-testnet evidence |
| 5. AWS deployment | Reviewed infrastructure/configuration and deployment checks | Later work after local acceptance; not run by the local helper |

LocalStack publishing currently uses the existing local SQS fanout adapters.
A local success does not verify real EventBridge routing, AWS IAM or Aurora.
CDK tests inspect templates and configuration; they do not provision AWS.

## Current Windows machine

Java 21 is installed at `C:/Users/884412/tools/jdk-21`, alongside a default Java 17.
Node and frontend/CDK dependencies are available. The preflight reports that
Docker and Compose are unavailable. Gate 1 can run here. Gates 2-3 need a reachable
Docker engine. A Docker CLI ZIP alone cannot supply that engine. No container
installation, database startup, identity provisioning or AWS deployment has been
performed by this increment.

A Docker-capable development machine or the existing Codespace can execute the
same commands. Codespaces is the development fallback while this laptop lacks an
engine; it does not mean deploying the application to AWS. Follow the existing
[Codespaces guide](18-CONTAINERIZATION-CODESPACES.md) when that environment is used.

## Gate 1: inspect prerequisites and verify code

Open a terminal at the repository root. On this Windows machine:

```powershell
$env:JAVA_HOME='C:\Users\884412\tools\jdk-21'
node scripts/local-workbench.cjs doctor
node scripts/local-workbench.cjs verify --offline
```

On a prepared Linux/Codespaces host with Java 21:

```bash
node scripts/local-workbench.cjs doctor
node scripts/local-workbench.cjs verify
```

The doctor reports booleans, not connection values or credentials. `BLOCKED`
means some full-stack prerequisites are absent; exit code 2 is intentional.
`verify` can still run without Docker. `--offline` uses existing Maven dependencies;
remove it when downloading dependencies is intended. Install missing frontend/CDK
packages with `npm --prefix frontend ci` and `npm --prefix infrastructure ci`.
The helper never installs tools or packages automatically.

Each invocation writes a separate timestamped `.local/validation/.../report.json`
and local logs. A report begins as RUNNING, receives each stage result, and ends
PASSED, PASSED_WITH_SKIPS, FAILED or BLOCKED. Only fresh Java XML reports from that
invocation are counted. An interrupted RUNNING report is not success. These local
artifacts are ignored by Git; inspect logs before sharing them.

For mandatory PostgreSQL execution on a Docker-capable host:

```bash
node scripts/local-workbench.cjs verify --require-database
```

This fails if a module has no fresh report or any case skips other than the named,
explicitly optional `RoutingAgentLiveTest`. Testcontainers starts its own isolated
PostgreSQL databases; Compose need not already run. The helper removes inherited blockchain activation flags and active profiles,
and disables the live-model test in verification subprocesses, even if the parent
terminal opted into them. Normal defaults remain MOCK/testnet-off while profile
unit tests can supply their own inputs. It never adds a testnet Maven profile.

## Gate 2: start and probe the local application

Use a dedicated POC database. Copy `.env.example` to `.env` only if absent, set the
local database password, and configure identity, memberships and policy as described
below. Preserve the existing file on subsequent runs. The helper does not read,
print, replace or create credentials.

```bash
# --env-file makes the same .env overrides available to Node and Compose.
node --env-file=.env scripts/local-workbench.cjs start
# Repeat only the read-only health/access probes for an already running stack.
node --env-file=.env scripts/local-workbench.cjs smoke
```

`start` first requires a reachable Docker server and Compose, then invokes the
existing six-service Compose profile with build, detached mode, health waiting
and a bounded timeout. It retains all existing data volumes on failure. It does
not delete or reset the database, seed balances, post journals or submit payments.
Use `docker compose --profile services ps` to inspect a failed startup; the local
report links its Compose log. Image pulls/builds require network and disk space.

After Compose and all 18 health/access probes pass, `start` prints
`Server startup completed. All six backend services are healthy and access checks passed.`
It then prints the application URL (port 5173) and backend `/health` URL (port
8080). In Codespaces these use the current `CODESPACE_NAME` and
`GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN`, rather than a hardcoded Codespace.
Outside Codespaces they use localhost. React/Vite must be running separately on
port 5173; the helper does not start the UI. Keep forwarded ports Private.
A failed or blocked startup never prints these success messages or URLs.
This reports backend readiness, not completed sign-in or payment acceptance.

The 18 read-only probes require all six health routes to return 200 and each
service to return 401 for both missing identity and a forged bearer token. They
accept only HTTP loopback origins from the existing shared connections file or
environment overrides, refuse redirects, and use the centrally configured
`LOCAL_VALIDATION_HEALTH_TIMEOUT_MS` (default 10000). Custom host ports must be set
through the corresponding existing `*_SERVICE_URL` settings for the host probe.
No arbitrary external destination is contacted by this smoke command.

All published Compose database, emulator and service ports bind to 127.0.0.1.
Visa remains private in the AWS architecture. The local shared-schema bootstrap
sets `LOCAL_SCHEMA_BOOTSTRAP=true` and baseline version 0, allowing each owner's
V1 migration to run when another owner has already created tables. It does not
skip V1, repair checksums, mark existing owner tables migrated or erase data.
A collision/migration failure needs investigation. The central flag defaults
false outside Compose; never enable it on an existing production database as a
migration shortcut. Live PostgreSQL execution is still required to prove startup.

## Gate 3: configure sign-in, open screens and run a payment

Configure the existing Cognito issuer, HTTPS JWKS, public client, authorization/
token endpoints, registered callback and server-owned customer memberships.
Use the [identity instructions and example](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md).
Use distinct verified approver subjects, each with the reading and approval roles.
Do not replace authentication with a local bypass or a browser role selector.
Using a previously configured identity service for local testing does not deploy
the application to AWS. Offline fixture tests remain separately labeled.

Enable the existing workflow/execution switches deliberately for the dedicated
MOCK demonstration, as described in [the integration guide](30-PAYMENT-FLOW-INTEGRATION.md).
The default stack keeps flow/execution gates off, MOCK mode selected and testnet
off. Seed only the fictional dedicated POC data using the existing wallet/bank SQL
scripts after migrations; they do not replenish existing account balances. Follow
[the settlement guide](31-SETTLEMENT-PAYOUT-RECONCILIATION.md) for simulated USD/INR
provider prefunding and payout recovery.

Open another root terminal and run:

```bash
npm --prefix frontend run dev -- --host 127.0.0.1
```

Open `http://localhost:5173`. Vite proxies API requests to the configured payment
service; it does not manufacture successful backend results. Restart Vite after
changing its build-time public configuration. If using Codespaces, use the exact
private forwarded host and registered HTTPS callback from the connected UI guide.

Use the existing [complete screen-by-screen demonstration](29-PROTOTYPE-EXECUTION-RUNBOOK.md)
for the opening story, role, context before each action, navigation, clicks and
expected outcomes. Run its connected screens in order: sign-in/Overview, New
payment, Intent & routing, Treasury readiness, Quote & approvals, Execution
journey, Blockchain evidence, Ledger & reconciliation and Optional fiat payout.
Use separate signed-in sessions for the distinct approvals. A presenter persona
never grants an extra server role.

Use the existing `run-payment-lifecycle.cjs` or `run-wallet-flow.cjs` for repeatable
API acceptance and exact replay checks. Preserve the original run ID, deadline,
intent and idempotency keys after an uncertain response. Do not rerun an uncertain
payment with a new run ID. Health and authentication-denial probes cannot replace
this authenticated payment gate.

## Gate 4 and later AWS work

Only the explicit testnet setup and runner may establish genuine on-chain proof.
A MOCK hash or synthetic browser screenshot cannot. See the
[testnet setup](20-BLOCKCHAIN-TESTNET-SETUP.md), [agent runtime](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md)
and the cumulative runbook. Record the actual revision, database checks, payment/
correlation IDs, model trace, receipt/log evidence, ledger and reconciliation.
Never include tokens, private keys or RPC credentials in the exported evidence.

After local acceptance, finish step 10's ECS Fargate/Aurora/API Gateway/private
ALB/Cognito/Secrets Manager/KMS, monitoring, alarms and recovery implementation,
then review the concrete deployment for the intended AWS account/region. Current
CDK provisions messaging foundations only. No account, region, endpoint or GitHub
repository is invented. Step 11 still includes final live acceptance, the commercial
and hackathon evidence package, and the cumulative Word architecture/design document.


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

## Codespaces verification findings (2026-11-15)

The first strict run compiled and passed its build stages but skipped PostgreSQL
integration tests: Testcontainers 1.20.4 selected Docker API 1.32, below the
Codespaces daemon's reported minimum of 1.40. A one-run API 1.44 override allowed
database tests to execute and exposed actual failures: six response-decoding
errors in PaymentApiIT and one outbox-count assertion in PaymentFlowPersistenceIT.
That run stopped in payment-service; later modules were not verified by that run.

The shared Maven parent now supplies api.version to Surefire and Failsafe test
JVMs through test.docker.api-version, default 1.44. Direct Maven invocations can
override it with -Dtest.docker.api-version=VALUE for a compatible daemon. This is
test-client configuration, not an application integration or a Docker daemon
change. The Node verification command remains:

```bash
node scripts/local-workbench.cjs verify --require-database
```

Immutable creation, status and journey response constructors now declare their
JSON properties explicitly, including nested status steps. Existing field names,
getters and HTTP contracts are preserved. A client-side regression test uses
Jackson without constructor-name inference to exercise decoding and timestamps.

PaymentFlowPersistenceIT explicitly clears only its isolated Testcontainers
outbox during fixture setup and counts events for the current payment. An outbox
has no customer foreign key, so truncating customer fixtures previously retained
events from earlier tests. The concurrency case still requires a single funding
event and a single USD debit. Runtime outbox, ledger and locking code are unchanged.

The existing TreasuryReservationPersistenceIT is now included in ordinary
blockchain-service verify. Its PostgreSQL checks require no chain RPC. The explicit
testnet profile retains its original live-suite selection and activation guards.
Strict database acceptance now requires fresh, nonempty, fully passed results
from all nine named database suites, including treasury reservations; unit test
reports alone cannot satisfy this gate. The optional live-model skip remains
permitted. A successful local unit run does not establish Codespaces acceptance.

After pulling a fix, run one command at a time and inspect the newest report path.
Retain failed reports; a later report must not be confused with an earlier run.
Full PostgreSQL acceptance remains pending until the updated Codespace run passes.

Local verification of these fixes: 32 targeted Java regression cases passed;
40 workbench/configuration tests passed. The full Java reactor built successfully,
with 558 passed and 52 skipped cases, zero failures/errors. Database
skips on the Windows host remain unverified; the corrected Codespace run is pending.
