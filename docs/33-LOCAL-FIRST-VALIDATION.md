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
# Inspect containers, all backend boundaries, and the frontend/API proxy without starting them.
node --env-file=.env scripts/local-workbench.cjs status --show-failures
```

`start` first requires a reachable Docker server and Compose, then invokes the
existing six-service Compose profile with build, detached mode, health waiting
and a bounded timeout. It retains all existing data volumes on failure. It does
not delete or reset the database, seed balances, post journals or submit payments.
Use `docker compose --profile services ps` to inspect a failed startup; the local
report links its Compose log. Image pulls/builds require network and disk space.

After Compose and all 18 backend health/access probes pass, `start` launches the
existing React/Vite frontend as a detached development process with a log linked
from its report. It derives the exact Codespaces host allowlist and sets the
frontend-only proxy target to the configured host payment-service origin. It
reuses an already healthy StableFlow Vite server and refuses an unrelated or
unready listener on port 5173; stop an older manual Vite terminal with Ctrl+C
before retrying in that case. It never kills another port owner or opens a browser.

The helper checks the development-server marker, application HTML and the
proxied anonymous API denial (401) before announcing:
`Server startup completed. You can access the application now. React and all six backend services are ready; access checks passed.`
It prints the application URL (5173) and labeled `/health` URLs for all six backend
services (default ports 8080, 8081, 8082, 8083, 8084 and 8086), using current
Codespaces environment variables and each configured local service port. Outside
Codespaces it prints the configured loopback service origin. The application URL
is the demo entry point; backend health URLs are optional diagnostics, not screens.
A service browser URL requires that port to be forwarded privately in Codespaces;
printing it does not create a port forward or launch a browser. Keep forwarded
ports Private. The frontend lasts until its process or the Codespace is stopped;
re-run `start` after resuming a stopped Codespace. `smoke` remains a backend-only
read-only check. Neither command establishes completed sign-in or payment acceptance.
A failed or blocked startup never prints success messages or application URLs.

### Console startup progress and component failures

The unified command prints `Startup status: STARTING` immediately, stage progress
and an elapsed-time heartbeat every 15 seconds during long operations. Container
health events are read from Compose output while it runs; only known component
names and readiness states are printed, not arbitrary application/build log text.
Each new result has the form `[3/9] payment-service | STARTED | Container health check passed`.
The nine counted components are PostgreSQL, LocalStack, the six Java services and
React. The number counts components with an observed started/failed result;
remaining components are pending, not assumed healthy. Readiness can regress:
a subsequent failed health/access check changes that component to FAILED.

After Compose finishes, a structured status snapshot identifies exited, unhealthy,
restarting and missing containers; exit codes are retained when available. A
failed build or unavailable Docker status cannot always identify the application
root cause: the console links `compose.log` rather than inventing a reason.
The 18 backend API checks print their HTTP results, followed by frontend/proxy
readiness. A final summary states how many of nine components started, failed or
remain pending. `Startup status: STARTED` and the application URL appear only on
complete success. Failure prints `Startup status: FAILED`, failed component/check
names, diagnostic reasons, pending components, log paths and the JSON report path.
Raw logs remain local for diagnosis; do not share credentials from them.

If PostgreSQL or LocalStack is stopped, the backend cannot establish full
readiness. Exit code 255 alone does not prove an out-of-memory event or its cause.
Recover those dependencies without deleting volumes, then recheck the backend.
Use a second terminal while a startup command is still active.

Concurrent Java cold starts can take several minutes. Application container health
grace defaults to `LOCAL_SERVICE_HEALTH_START_PERIOD=240s`; Compose readiness has
`LOCAL_START_WAIT_SECONDS=600`, and frontend readiness has
`LOCAL_FRONTEND_START_TIMEOUT_MS=30000`. These are local development controls in
`.env.example` and shared configuration, not production capacity measurements.
Health failures still fail startup; increasing grace does not bypass health checks.

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

The unified `start` command above already starts React. Use its printed application
URL; a separate frontend command is only needed for manual frontend development:

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

Verification of startup reporting (2026-11-16): all 36 targeted Node checks passed,
including split Compose events, duplicate suppression, safe component names,
exit-code 255 diagnosis, heartbeats and no success announcement after failure.
Node syntax and the runbook link/fence checks passed. A real local invocation on
this Windows host failed explicitly at its missing Java 21 selection and missing
Docker/Compose prerequisites, named those prerequisites and all pending components,
and printed no success URLs. Full nine-component startup still requires the
Docker-capable Codespace; these checks do not claim a connected payment or deployment.

Subsequent owner execution in Codespaces: report `1794830715419-85490-start`
completed PASSED with nine of nine components ready, 18 passing backend probes
and successful frontend/proxy checks. This establishes local startup readiness;
authenticated payment, model execution and public-testnet acceptance remain
separate gates. The URL summary now includes all six labeled backend health URLs,
including configured port overrides; targeted startup tests cover that rendering.

## Completion percentages and verification configuration isolation (2026-11-16)

Both commands retain the same invocation:

```bash
node --env-file=.env scripts/local-workbench.cjs start
node --env-file=.env scripts/local-workbench.cjs verify --require-database
```

Run one command at a time. Percentages count finished checks, not elapsed time,
individual Java tests, payment progress or live acceptance. Startup counts nine
components. Eight healthy backend/dependency containers show 88%; frontend HTML
and API proxy readiness complete the ninth check. Verification counts eight
stages: prerequisites, Node tests, frontend build, frontend lint, CDK build, CDK
tests, Java reactor and final Java evidence/database-gate validation. The Node
stage includes all frontend `*.test.mjs` unit tests, including session renewal.
Long-running stages print their current percentage and elapsed time every 15
seconds, and verification prints each stage's diagnostic log path before running it.

Example console messages:

```text
[3/9 | 33%] payment-service | STARTED | Container health check passed
[STARTING] java: still working (30s elapsed) | 75% complete
[7/8 | 87%] java | PASSED
[8/8 | 100%] java-evidence | PASSED
Verification progress: 100% | 8/8 stages finished, 0 failed
```

A failed check counts as finished and is shown separately. Thus 100% can accompany
FAILED when the final check fails; only the explicit final status establishes the
result. Early failures leave later checks pending. Each JSON report now includes
`progress` with its basis, completed count, total, percent and failure count.
Startup still prints application URLs only after every readiness/access check passes.

Owner report `1794847618091-8861-verify` passed Node/frontend/CDK stages but failed
Java: 359 passed, four failed and one optional live-model case skipped. All four
failures were in `ConnectionConfigurationTest`. The assertions read the deployed
Cognito issuer and Base Sepolia chain ID instead of test settings because the
verification subprocess inherited application environment overrides. This is
separate from the earlier recommendation persistence fix.

Verification now removes connection environment variables declared by the shared
configuration, external connection-file selectors and Spring overrides from its
child processes. Java/Docker tooling variables remain available. Real inference
stays disabled. This does not edit `.env`, private properties, running containers,
application connection precedence or test-injected configuration. Continue to use
the normal `.env` when starting the application. The next Codespaces strict run
must still establish full database acceptance; these changes do not claim it.

Local verification of this update: all 81 Node unit checks passed with no skips;
all six `ConnectionConfigurationTest` cases passed using Java 21 with the verifier's
actual isolated environment and deliberately supplied dummy deployment overrides.
The targeted Maven reactor compiled successfully. CLI checks with an intentionally
missing JDK confirmed both commands report prerequisite failure, retain pending
checks and print no application success URL. These are local regression results;
the full Codespaces Java/database reactor and live payment demonstration remain
pending. No application configuration values were changed by this fix.

## CLI failure details and command responsibilities (2026-11-17)

`start` initializes/reuses all three application layers: React frontend, six Java
backend services, and PostgreSQL with LocalStack messaging. It then checks container
health, API access boundaries and the frontend proxy before announcing readiness.
`verify` builds and tests these layers, including CDK checks. Its Java integration
tests use isolated test database containers; it does not start the interactive
application or substitute for `start`. Neither command deploys to AWS.

Failure summaries now appear directly in the CLI for both commands, including
failed prerequisites, checks, available reasons/exit codes, failed Java suite
counts, diagnostic paths and an unsatisfied database gate when applicable.
To add short, redacted diagnostic excerpts, use the optional flag:

```bash
node --env-file=.env scripts/local-workbench.cjs start --show-failures
```

For verification, run this separately:

```bash
node --env-file=.env scripts/local-workbench.cjs verify --require-database --show-failures
```

Read the explicit final result together with the progress percentage. The flag
adds diagnostics after failure; it does not change checks, exit codes, approval
requirements, configuration isolation, data volumes or financial execution.
Successful commands do not dump logs. Without the flag, a failure prints the
summary and a reminder that `--show-failures` is available.

On failure, excerpts come from failed-stage logs and fresh failed Surefire/Failsafe
text reports. Startup also makes one bounded, read-only Compose log request for
allowlisted failed containers, including failed PostgreSQL/LocalStack dependencies.
It never dumps container environments or restarts a component to gather diagnostics.
An unavailable log request leaves the original failure result intact.

Output is limited to six diagnostic sources and 20 selected lines per source;
only the last 64 KiB of each file is read and displayed lines are shortened.
Configured secret values, credential fields, authorization material, key-like
values, email addresses, URL paths/query strings and terminal control sequences
are redacted. Full local diagnostic files remain available for detailed inspection;
do not share unreviewed raw logs. Fresh report timestamps and bounded path checks
prevent reading unrelated or stale files as current test failures.

The UI demonstration continues to follow the
[presenter navigation](29-PROTOTYPE-EXECUTION-RUNBOOK.md#numbered-presenter-navigation-url-login-roles-actions-and-outcomes)
after successful startup and the separate required acceptance gates.

Validation of the CLI diagnostic option: 91 Node unit checks passed, with no
failures or skips. They cover option parsing, failure summaries, assertion excerpts,
credential/control-character redaction, stale/outside file rejection, output bounds
and read-only failed-service selection. Actual CLI invocations of both commands
with an intentionally missing JDK displayed the failure summary/details, retained
exit code 2 and emitted no success URL. Container log collection was exercised
with a controlled command stub; real Codespaces diagnostics and the full database
acceptance run remain pending. No Java/application runtime logic changed.

## Sequential component startup (2026-11-17)

The unified `start --show-failures` command now starts one Compose target at a
 time: PostgreSQL, LocalStack, payment-service, compliance-quote-service,
blockchain-service, settlement-ledger-service, reconciliation-service,
visa-simulator, then React after the backend API/access checks. Each target must
pass its Compose health check before the next starts. Backend images are built
one at a time with existing Dockerfiles; `--no-deps` prevents Compose from
implicitly starting additional targets. Infrastructure is explicitly started first.
This reduces concurrent compiler pressure; total startup time is not guaranteed
to decrease, and existing running services continue to consume resources.

The entry command is unchanged:

```bash
node --env-file=.env scripts/local-workbench.cjs start --show-failures
```

The active component has a STARTING message and 15-second heartbeat. Completion
updates the nine-component percentage; a long image build does not imply a
percentage increase. Failed build/start commands stop the sequence, identify the
failed target and leave later targets pending. Existing containers and volumes
are retained. A still-healthy old image cannot turn a failed rebuild into success.
Final API, access-boundary, React and proxy checks remain required before URLs
are announced. The shared `compose.log` and report contain per-target outcomes;
`--show-failures` retains bounded redacted diagnostics. No manual component
commands, AWS deployment, payment submission or configuration changes are added.

Validation: 34 relevant Node tests passed, including sequential execution,
infrastructure ordering, health-wait flags, failure/timeout/exception handling,
progress and failure diagnostics. Docker execution used controlled command stubs;
the sequential Docker startup must still be exercised in Codespaces.

Separately, the owner's Codespaces verification run
`1794894736276-45358-verify` completed all eight stages: 614 Java tests,
613 passed, zero failures/errors, and only RoutingAgentLiveTest skipped. The
required database evidence gate passed. This is code/database verification;
live application AI inference and public-testnet payment acceptance remain open.


## LocalStack Secrets Manager availability (2026-11-17)

The Codespaces DescribeSecret request returned InternalFailure because the prior
Compose SERVICES list enabled only sqs,events. Compose now reads the non-secret
LOCALSTACK_SERVICES setting, defaulting to sqs,events,secretsmanager; the environment
template includes that value. Existing .env files without the setting use the new
default. An explicit older override must include secretsmanager to enable it.

Follow local implementation/test -> GitHub publication -> Codespaces pull ->
unified startup. The startup command applies the updated LocalStack container
configuration and the previously saved private 15-minute MOCK evidence settings.
Do not run two startup commands concurrently. Following startup, verify service
availability with the metadata-only DescribeSecret command in the Codespaces
guide. A missing secret is a separate result from a disabled service.

The Node configuration check on the Codespaces host can show an empty AWS endpoint;
Compose injects LOCALSTACK_URL into backend containers. Real AWS secrets are not
automatically copied to LocalStack. Local secret storage is emulated; an OpenAI
model call is real only when a successful provider response is recorded. No API
key is added to Git or printed by these metadata checks. Agent enablement still
requires a model, secret, customer allowlist and positive pricing estimates.

Validation for this configuration change: Compose YAML was parsed locally and
34 relevant Node tests passed. Local Docker execution is unavailable; actual
service enablement and key retrieval still require Codespaces verification.


## Oregon configuration cutover (2026-11-17)

Target AWS region is now us-west-2. Existing private .env/connection copies must
be explicitly migrated after pulling; their defaults do not update automatically.
Run `node scripts/configure-oregon-demo.cjs --preview`, then `--apply` separately.
The helper backs up both private files, validates/migrates the six known subjects,
retains customer policies/passwords/callback/TTLs and keeps the routing agent off.
Restart and perform fresh Oregon sign-in before connected payment testing.
See [the cutover evidence and outstanding gates](34-CODESPACES-DEMO-LAUNCH.md#oregon-migration-and-cutover-2026-11-17).
Startup health success does not prove real AWS secret access or Cognito login.

## Read-only running-stack status (2026-11-21)

`node --env-file=.env scripts/local-workbench.cjs status` inspects the eight existing
Compose containers, runs the 18 backend health/access probes, and checks the React
development marker, application HTML and anonymous API proxy denial. It does not
require Java/build dependencies to inspect an already running application, and
never builds, starts, restarts, seeds or changes application resources. Only local
HTTP service origins are accepted. A timestamped `.local/validation/.../report.json`
records the snapshot; it is not continuous monitoring.

The summary reports nine components ready or failed/unavailable. A healthy Docker
container cannot hide a failed API probe or a frontend proxy returning 502.
Unavailable Docker inventory is reported as unavailable, not as evidence that
containers are stopped. All checks must pass for exit code 0; failures return 2.
Add `--show-failures` for bounded redacted logs from allowlisted failed containers.
Logs cannot always identify a root cause; frontend/proxy failures still retain
their failure classification. No environment dump or raw Docker output is saved.

This command does not validate Cognito sign-in, payment completion, model
inference, public port forwarding or real blockchain transactions. The existing
`smoke` command remains backend-only. Neither command starts or repairs the stack;
use `start --show-failures` separately when startup is needed.


Docker inventory parsing keeps stdout separate from stderr, including Compose's
obsolete-version warning. A warning does not turn valid container JSON into a
failed inventory check. With --show-failures, command failure reports include
a bounded redacted error, exit code and timeout indicator; invalid JSON has a
separate diagnostic cause without printing container metadata. Unknown container
state alone does not trigger log collection from every service. Successful HTTP
probes remain visible even if Docker inventory cannot be inspected.

## Rebuilding a running application (2026-11-23)

Run one lifecycle command at a time:

```bash
node --env-file=.env scripts/local-workbench.cjs restart --show-failures
```

The existing start command is safe to rerun: it builds the six backend images,
starts missing containers and lets Compose reuse unchanged containers. Restart
uses the same sequential startup and health/access checks, adding force-recreate
for each of the six backends. It rebuilds before replacing each service so pulled
Java changes and Compose environment settings apply. Docker build cache remains
enabled; restart is not a faster no-build shortcut or docker compose restart.

Both commands ensure PostgreSQL and LocalStack are running, retain their existing
containers when unchanged, and preserve volumes. Neither removes volumes, resets
payments, seeds records, submits payment commands or deploys to AWS. Normal service
workers resume pending work when started. React is started if absent; a healthy
Vite server is reused and serves watched source updates. Frontend environment or
Vite configuration changes may require stopping its existing dev terminal before
retrying; this command does not kill an unrecognized process on port 5173.

Restart records its own timestamped report and supports --show-failures with the
same bounded, redacted diagnostics as start. It stops at the first failed Compose
target; a healthy old image cannot hide a failed rebuild. All backend health/auth
and frontend/proxy checks must pass before restart success and URLs are printed.
Payment deadlines and quote expiry still apply across a restart. Verify the live
Codespaces run separately; controlled command tests do not establish Docker,
authenticated payment, model or public-testnet acceptance. No architecture change.
