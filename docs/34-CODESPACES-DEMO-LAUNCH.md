# Codespaces launch and demonstration

Updated 2026-11-16. GitHub destination: [StableCoins](https://github.com/kirankumarns-Cognizant/StableCoins).
AWS destination: account `640237069320`, application Region `us-east-1`, IAM Identity Center authentication.
The owner requested step 11 preparation before AWS deployment. Codespaces is the
container host for acceptance; deploying the application to AWS is a later gate.

## What is ready and what still needs execution

The connected React application and six Java services are implemented. The
commercial rehearsal, two customer configurations and evidence checklist are
in [the proof package](35-HACKATHON-PROOF-PACKAGE.md). The complete presenter
narrative, roles, exact clicks and expected outcomes are in
[the screen walkthrough](29-PROTOTYPE-EXECUTION-RUNBOOK.md#numbered-presenter-navigation-url-login-roles-actions-and-outcomes),
which includes the current UI URL, named demo users, Cognito/role explanation and
numbered navigation from first login through final evidence capture.

An HTML prototype or synthetic browser screenshot is not a connected demo.
Actual Codespaces startup, PostgreSQL tests, Cognito sign-in, inference and a
public-testnet payment must be recorded separately. Required identity and wallet
resources must be configured and tested. No completed live run is asserted here.

### Owner-reported progress (2026-11-16)

The startup console now reports STARTING, numbered component readiness out of nine,
15-second elapsed-time heartbeats, per-check HTTP outcomes and a final STARTED or
FAILED summary. Failure includes known component reasons, pending components and
diagnostic paths. See [startup progress and failures](33-LOCAL-FIRST-VALIDATION.md#console-startup-progress-and-component-failures).
These messages do not replace authenticated payment/model/testnet acceptance.

Owner startup report `1794830715419-85490-start` completed PASSED: nine of nine
components started, all 18 backend health/access probes passed, and React plus its
API proxy passed readiness. The successful summary prints the Application URL for
the demo and six labeled backend `/health` URLs for optional diagnostics, derived
from configured local service ports. Forward those diagnostic ports privately
before browser access; the startup script does not open browsers or make ports
public. The application UI remains the presenter entry point on port 5173.

Codespaces verification ran 610 Java tests: 609 passed, with only the opt-in
`RoutingAgentLiveTest` skipped. The required PostgreSQL gate passed. After
Cognito configuration, startup report `1794821303277-17464-start` and smoke
report `1794821530920-24765-smoke` both passed; all six services were healthy.
The owner provisioned a dedicated Cognito pool, public browser client, separate
internal client/scope, six users and server-owned customer memberships. A small
OpenAI inference succeeded outside the application, and its key is stored in
Secrets Manager. No secret values belong in this document.

The first customer workbench request failed with `Failed to fetch`. The owner's
subsequent screenshot confirms Customer A's verified operator, customer membership
and `hackathon-a-v1` policy loaded successfully. That earlier screenshot had an empty funding account
list; the later successful Customer A seed is recorded in section 4. Post-seed
New payment account/supplier selectors are confirmed by an owner screenshot;
a subsequent routing screenshot confirms PAY-52272AAC4E14 was created for USD 1,000
with a USD 5 fee limit, COMPLIANCE_APPROVED state and Recommendation pending.
The owner then requested recommendation preparation: the UI displayed PENDING,
followed by REVIEW_REQUIRED with PREPARATION_TIMEOUT. Automatic refresh displayed
the transition. Retain this payment and diagnose provider quotes, treasury evidence
and event processing before retrying; the failing dependency is not yet established.
The owner's database check shows both quotes READY at +2.84 seconds and treasury
observation READY at +3.21 seconds; inspect payment-worker logs before retrying.
Runtime agent execution and payment settlement remain to be demonstrated through
the UI. See the runbook's complete navigation map for the remaining screen path.

Open-tab session renewal is enabled by default through the shared
`IDENTITY_AUTO_REFRESH_ENABLED=true` setting. It removes no server checks and
adds no application idle limit; Cognito refresh validity/revocation still apply.
After updating code and reloading the frontend, sign out and sign in securely once
to obtain a new memory-only refresh token. Subsequent renewal should preserve the
current payment tab. A full browser reload still requires sign-in; the PKCE flow
restores the same payment path. Do not put tokens into configuration or storage.
Live Cognito renewal still requires observation in Codespaces.
The API fetch previously used `credentials: omit`, which drops the browser
cookie needed by private Codespaces forwarding. The owner patched the Codespaces checkout to use `same-origin`:
keep port 5173 private, use `/api/v1` on the UI origin, and continue to require
the Cognito bearer token. Cross-origin cookie forwarding stays disabled and
redirects remain rejected. The owner also enabled `PAYMENT_FLOW_ENABLED=true`;
without it the workbench route was disabled. This identity/catalog observation
does not establish a successful connected payment. Later screenshots showed an
API `HTTP_502`, followed by no listener on frontend port 5173; diagnose service
availability separately from Cognito or missing fictional customer records.

The non-secret mounted `.local/demo-connections.properties` must be readable
by the container user (0644 in this demo); keep `.env` and credential-bearing
files private (0600). When setting demo passwords in CloudShell, the Boto3
`admin_set_user_password` call with hidden `getpass` input worked. The CLI
`--cli-input-json file:///dev/stdin` path failed JSON parsing in that session.
Do not print passwords while diagnosing it.

## 1 Open the project in Codespaces

1. Publish the reviewed project snapshot to the repository above. Use the branch
   containing `.devcontainer/devcontainer.json`; an empty branch cannot start a
   Codespace. Preserve existing remote history if a branch already exists.
2. On the repository page select that branch, **Code**, **Codespaces**, then
   **New with options**. Select the repository dev-container configuration and
   a machine with enough memory for six JVMs, builds and PostgreSQL. An 8-core,
   16-GB-or-larger machine is a starting capacity assumption, not measured sizing.
3. Choose **Create codespace**. Wait for the post-create script, then open a
   terminal at the repository root. It installs dependencies; it does not deploy
   AWS, provision identity, create wallets or send payments.
4. Run the following checks. A skipped database test does not satisfy acceptance.

```bash
node scripts/local-workbench.cjs doctor
node scripts/local-workbench.cjs verify --require-database
```

The dev-container provides Java 21, Node 22, Docker-in-Docker, AWS CLI and GitHub
CLI. Keep forwarded ports **Private**. Docker Compose binds published ports to
loopback; do not make PostgreSQL, LocalStack or Visa public. GitHub documents the
[repository Codespaces creation procedure](https://docs.github.com/en/codespaces/developing-in-a-codespace/creating-a-codespace-for-a-repository).

### Recover a missing-lockfile bootstrap failure

If `npm ci` reports `EUSAGE` and a missing `package-lock.json`, update the
checkout before retrying. The initial published snapshot omitted both npm package manifests and
lockfiles because of inherited workspace ignore rules. Both manifests and locks
are now committed; the locks match the manifests. The existing wallet generator
Java source is also included. Generated CDK JavaScript remains excluded.
From the repository root run each command only after the previous succeeds:

```bash
git pull --ff-only origin main
npm --prefix frontend ci
npm --prefix infrastructure ci
node scripts/local-workbench.cjs doctor
```

Then run `node scripts/local-workbench.cjs verify --require-database`.
Do not substitute `npm install` or delete a lockfile to conceal this failure.
If Git reports local changes or an install fails, preserve the changes and
inspect that error before continuing. These commands do not deploy to AWS.

## 2 Authenticate to the selected AWS account

Do this in the Codespaces terminal when identity/model resources need AWS access:

```bash
aws configure sso --profile stablecoins-demo --use-device-code --no-browser
aws sts get-caller-identity --profile stablecoins-demo --query Account --output text
```

Use account `640237069320` and default application Region `us-east-1`. Enter the
device code on AWS's sign-in page, never in chat or a committed file. The **SSO
region** is where the identity directory resides and can differ from the
application Region. Select the permission set actually assigned to the account.

The owner supplied two candidate start URLs:
`https://d-9267f3e582.awsapps.com/start/` and
`https://d-906629bc40.awsapps.com/start/`. Use the one that lists the target account
after sign-in. A reachable login page alone does not establish account permission.
The SSO region and permission set are still to be confirmed. In the access portal,
**Access keys** then **IAM Identity Center credentials** shows the required
non-secret configuration. Do not copy generated credentials into this document.

SSO in the terminal does not automatically authenticate containers. Compose uses
dummy LocalStack credentials by default. Never replace those global defaults with
permanent AWS keys. Passing short-lived model/signing credentials or an SSO profile
to only the appropriate container remains an explicit setup gate. The current
Compose allowlist does not forward `AWS_SESSION_TOKEN`; do not assume temporary
credentials work without that wiring. Follow the
[AWS SSO configuration guide](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html).
The existing CDK stack implements messaging, not a complete application deployment.
Do not run `cdk deploy` expecting it to provision Cognito, ECS, Aurora and the UI.

## 3 Configure identity and customer access

Provision a dedicated demo Cognito user pool, HTTPS login domain and public app
client using authorization code with S256 PKCE. Register the exact Codespaces
frontend callback, with no browser client secret. Create three distinct verified
users per customer: an operator and two independent approvers. Complete initial
password/MFA setup privately. Use a separate confidential service client and
`stablecoins/internal` scope for internal HTTP operations; browser tokens cannot
acquire service authority. See [the access design](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md).

Prepare a server-only configuration file, preserving the complete central defaults:

```bash
mkdir -p .local
cp -n services/config/connections.properties .local/demo-connections.properties
```

Append the membership/policy keys from
[the two-customer example](../services/config/hackathon-customer-access.example.properties)
once, replacing its six placeholder subjects with the actual Cognito `sub` values.
Use the same file in all six services. Do not overwrite an existing configured
file or append duplicate policy keys during later rehearsals. Set in ignored `.env`:

```dotenv
CONNECTIONS_CONFIG_FILE=./.local/demo-connections.properties
IDENTITY_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/ACTUAL_POOL_ID
IDENTITY_JWK_SET_URI=https://cognito-idp.us-east-1.amazonaws.com/ACTUAL_POOL_ID/.well-known/jwks.json
IDENTITY_CLIENT_ID=ACTUAL_PUBLIC_CLIENT_ID
IDENTITY_SERVICE_CLIENT_ID=ACTUAL_DISTINCT_SERVICE_CLIENT_ID
IDENTITY_SERVICE_SCOPE=stablecoins/internal
IDENTITY_AUTHORIZATION_URI=https://ACTUAL_LOGIN_DOMAIN/oauth2/authorize
IDENTITY_TOKEN_URI=https://ACTUAL_LOGIN_DOMAIN/oauth2/token
IDENTITY_SCOPES=openid profile
PUBLIC_API_BASE_URL=/api/v1
PAYMENT_FLOW_ENABLED=true
PAYMENT_EXECUTION_ENABLED=true
BLOCKCHAIN_EXECUTION_API_ENABLED=true
BLOCKCHAIN_READINESS_TTL=PT5M
```

These are configuration examples containing placeholders, not provisioned resources.
Set a dedicated local `DB_PASSWORD`. Configure the signer/readiness prerequisites
from [the treasury design](25-TREASURY-AND-EXECUTION-READINESS.md), including MOCK
readiness if rehearsing without a public-chain connection. The gates above enable
payment actions only; they do not bypass identity or approval checks.

## 4 Start the six services and seed fictional data

```bash
node --env-file=.env scripts/local-workbench.cjs start
node --env-file=.env scripts/local-workbench.cjs smoke
docker compose --profile services ps
```

Expected: all six services healthy, migrations applied, 18 health/access-denial
probes pass, and `start` verifies the React app and its same-origin API proxy.
Only then does it print the startup-completed message and application URL.
Inspect a failing service privately; do not paste complete environment
or logs containing secrets. Preserve volumes on failure. Apply the existing
[wallet and bank seeds](30-PAYMENT-FLOW-INTEGRATION.md#codespaces-demonstration)
with the approved nonzero beneficiary testnet wallet. For the second customer,
use the optional identifiers documented in the proof package. Seeds never refill
an existing balance or replace an approved recipient. Run them after migrations.

For the current dedicated Customer A rehearsal, the owner supplied these public
addresses: treasury `0xFBB95f462fC41D6B44C427e6E44E3c1f4b21C551` and beneficiary
`0xB83a9FEE3557C7Fd1d206BeAfd989A8ba8533A07`. The encrypted local wallet files
were confirmed to match those addresses; that does not establish treasury token
funding or Codespaces signer configuration. Reuse the intended beneficiary for
this seed; a different wallet requires an explicit separate demo setup.

```bash
docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v supplier_wallet=0xB83a9FEE3557C7Fd1d206BeAfd989A8ba8533A07' < scripts/seed-wallet-demo.sql
```

Owner output on 2026-11-16 confirms BEGIN, three `INSERT 0 1` results and COMMIT,
with USD 100,000 in `DEMO_SIMULATED_USD` account
`22222222-2222-2222-2222-222222222222`. The supplier uses beneficiary ID
`33333333-3333-3333-3333-333333333333`. This is administrator provisioning of
fictional starting records, not a UI funding action or settlement proof. There
is no onboarding/top-up UI currently. Payment-specific funding, ledger posting
and execution remain subsequent application actions. Re-running the seed does
not replenish spent capacity. Return to the UI and follow the runbook's
[guided rehearsal checkpoint](29-PROTOTYPE-EXECUTION-RUNBOOK.md#guided-ui-rehearsal-and-evidence-record):
Overview > Refresh > inspect Corporate funding accounts > Create USD payment.

Fiat-bank scenarios also require explicit simulated provider prefunding using the
protected procedure in [the payout guide](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
There is no UI prefunding button. Keep privileged simulation operations disabled
outside that controlled preparation.

## 5 Open the connected UI

1. In Codespaces **Ports**, find port **5173**, keep it **Private**, and copy its
   forwarded HTTPS origin, such as `https://ACTUAL-CODESPACE-5173.app.github.dev`.
2. Set `FRONTEND_DEV_ALLOWED_HOSTS` to that exact hostname, and
   `IDENTITY_REDIRECT_URI` to that origin plus `/auth/callback` in `.env`. Register
   that exact callback in Cognito. Keep `PUBLIC_API_BASE_URL=/api/v1`.
3. The updated `start` helper launches/reuses Vite, derives the exact current
   Codespaces allowlist and supplies its host payment-service proxy target.
   Container-to-container URLs retain their service DNS names. Do not globally
   replace `PAYMENT_SERVICE_URL` in Compose with host localhost. For manual
   troubleshooting only, keep a separate terminal running this frontend command:

```bash
FRONTEND_DEV_ALLOWED_HOSTS="${CODESPACE_NAME}-5173.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}" PAYMENT_SERVICE_URL=http://127.0.0.1:8080 npm --prefix frontend run dev -- --host 0.0.0.0
```

4. Use **Open in Browser** for port 5173. Click **Sign in securely**. Expected:
   Cognito authenticates the operator and **Overview** shows only their customer.
   Restart Vite after changes to public settings. A private forwarded port may
   first require GitHub sign-in; that is distinct from the application's Cognito login.

If the browser is unreachable, first check `curl --max-time 10 -I http://127.0.0.1:5173/`
from a second Codespaces terminal. Connection refused means the frontend listener
is absent; a forwarded URL alone cannot start it. If the page loads with `HTTP_502`,
check `curl --max-time 10 -i http://127.0.0.1:8080/health` and the backend container
status before investigating API proxy configuration. Keep the manual Vite terminal
running when using that fallback. Do not submit a payment with empty account or
beneficiary selectors. Complete section 4's explicit fictional seed first.

**Refresh evidence** reloads records when the app and API are reachable. It shows
progress and a success/failure result; a failed refresh preserves the previous
successful timestamp and warns that retained records may be stale. It cannot
start a stopped process or populate an unseeded account/beneficiary list. See
[the UI recovery behavior](32-CONNECTED-REACT-WORKSPACE.md#recovery-behavior).

## 6 Rehearse MOCK before enabling inference or public settlement

Follow scenes 1-9 in the complete walkthrough. First request `USDC_WALLET`; then
create a separate `SIMULATED_BANK_INR` payment if provider prefunding is ready.
Use separate signed-in identities for the two approvals. MOCK is an integration
rehearsal, never genuine blockchain evidence. Record payment and correlation IDs.

For actual inference configure the existing provider/model, secret reference or
SigV4 authentication, positive model price assumptions and customer allowlist
from [the runtime agent guide](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md). Enable
`ROUTING_AGENT_ENABLED` explicitly in the launching environment; the dev-container
terminal default is false and takes precedence over `.env` for Node/Compose.
Do not put a raw model key in frontend settings. A disabled/failed model run must
remain visible as such; a deterministic assessment does not prove inference.

For actual settlement first complete [the guarded testnet setup](20-BLOCKCHAIN-TESTNET-SETUP.md).
Treasury needs sufficient test USDC for the quoted amount and test ETH for gas.
The existing Compose defaults deliberately force MOCK and testnet off; editing
only `.env` is insufficient. Use a reviewed explicit override selecting the testnet
Spring profile, `BLOCKCHAIN_MODE=REAL_TESTNET`, `TESTNET_ENABLED=true`, matching RPC/
chain/token settings and the existing local environment-key signer. Store its disposable testnet key
in a repository-scoped Codespaces Secret and forward it only to blockchain-service
through the reviewed override; do not put the value in the override file. AWS
Secrets Manager/KMS signing is still step-10 work. Check the effective mode before
authorizing any payment. Setup and funding are not yet executed for this environment.

## 7 Capture proof and stop safely

Capture the three actual screens from the same reconciled payment: **Intent &
routing** with a successful model run, **Ledger & reconciliation**, and **Blockchain
evidence** with independently verified public receipt/USDC Transfer log. Use
**Export evidence** for the authorized UI evidence. For an independent full run,
use `scripts/run-payment-lifecycle.cjs` with the existing fixed run ID/deadline,
tokens entered privately, `DEMO_RUN_AGENT=true`, `DEMO_EVIDENCE_MODE=TESTNET`,
private `DEMO_RPC_URL`, and a new ignored `DEMO_EVIDENCE_FILE`. The runner submits
a payment; do not run it as a read-only verifier of a different UI payment.

The runner verifies per-asset journals and public chain evidence; the UI export
alone is not equivalent. Preserve original IDs and keys after an uncertain outcome.
Do not clear session storage, start a replacement payment or delete volumes to
resolve uncertainty. Wait for signed/submitted work to reach a known outcome and
retain its evidence before stopping services. Then stop the Codespace from GitHub
to stop compute usage; do not delete it until needed local evidence is backed up
securely. Finish AWS deployment only after acceptance and the outstanding
infrastructure/security work, as tracked in the delivery plan.

## Wallet generation in the Codespaces terminal

The existing wallet tool is a standalone developer utility, not another service.
Its source package is explicitly retained in Git despite the general wallets
ignore rule. From an interactive Codespaces terminal at the project root:

```bash
./services/mvnw -f scripts/generate-test-wallets/pom.xml verify --no-transfer-progress
java -jar scripts/generate-test-wallets/target/generate-test-wallets.jar
```

Enter separate wallet passwords directly at the hidden terminal prompts. The
utility writes encrypted V3 keystores under ignored `.local/wallets`, prints
public addresses only, refuses to replace existing wallets and never connects
to a chain. It does not automatically load the encrypted keystore into the current
environment-key signer. Key loading for that signer requires a separate secure
operator step; never print a private key or paste it into chat. Obtain sufficient
test USDC and test ETH before attempting the primary USD 10,000 scenario.

`REAL_TESTNET` is the backend mode enum. `DEMO_EVIDENCE_MODE=TESTNET` is the
independent runner's evidence label; they are different configuration fields.

### Preparation diagnosis and recovery - 2026-11-16

The supplied records for PAY-52272AAC4E14 / CORR-17EF6A110D4B were replayed locally
at the original attempt time. Both provider documents and the MOCK treasury
observation deserialize, assemble all fourteen observations (seven per provider),
and survive a JSON round trip. The deterministic assessment selects Provider A:
USD 2.00 simulated provider fee + USD 0.21 simulated ETH budgeting cost = USD 2.21,
within the pinned USD 5 limit. Provider B's total is USD 5.21. This replay is a
regression test, not a live quote, real wallet balance or model execution.

A transaction-ordering defect was found in the recommendation handoff: JPA may
queue the new recommendation INSERT while JDBC immediately links its ID from
payment_flow_preparation. An explicit JPA flush now precedes the JDBC handoff,
inside the same transaction; failure still rolls back recommendation/audit/outbox
writes. This addresses a plausible cause of the observed retries. The supplied
logs omit the exception, so the original Codespaces failure is not conclusively
attributed until the database test and a fresh UI attempt execute.

Worker deferrals now report exception class, root cause class and a validated
SQLSTATE (for example, 23503 identifies a foreign-key violation). Exception text,
SQL parameters and stack traces are withheld because they can contain secrets or
PII. The previous 120-second timeout remains; extending it is not the fix.

Verification: 44 targeted Java tests passed, zero failures/errors/skips. These
include the captured-data replay, routing assembler, payment flow, recommendation
controls and sanitized failure logging. Two new PostgreSQL regression cases
compile but have not executed locally; Docker is unavailable on this laptop.
Actual Cognito renewal, model inference and public-testnet acceptance remain open.

The existing intent deadline, 2026-11-16T13:31:45.861Z, has now elapsed. Preserve
that payment and its timed-out preparation. Do not edit its immutable intent or
manually mark it READY. For the next UI rehearsal after updating/restarting:

1. Open the application URL and sign in securely as demo-a-operator. Cognito
   authenticates; the API resolves Customer A / PAYMENT_OPERATOR. A fresh sign-in
   obtains memory-only refresh credentials for the new session behavior.
2. Overview > New payment. Select the seeded USD account and Fictional Demo
   Supplier. Use USD 1,000, USDC_WALLET, maximum fee USD 5, LOWEST_COST and AUTO.
   Set Deliver by to a future UTC timestamp with enough time for the demonstration.
3. Confirm the instruction and click Create payment intent. Record the NEW payment
   and correlation IDs; retain the old IDs separately as diagnostic history.
4. Intent & routing > Prepare / refresh recommendation. Wait for a recorded READY
   result and inspect deterministic provider comparison, eligibility and freshness.
   Refresh only reloads evidence; it does not start a replacement preparation.
5. If preparation fails again, retain the new request ID and inspect payment-service
   logs for errorType/causeType/sqlState. Stop before approvals or execution. Do not
   rerun payment creation to work around an uncertain financial outcome.
6. Once ready, continue the existing complete navigation map: Intent & routing >
   Run reasoning agent (explicit live-provider setup required) > Treasury readiness >
   Quote & approvals as approver one and then approver two > Execution journey as
   operator > Blockchain evidence > Ledger & reconciliation > optional fiat payout.
   Each step must use the same valid recommendation and payment IDs. Quote/approval
   and treasury evidence expiry remain enforced independently of login continuity.

The captured observation is MOCK and uses the default mock treasury address. It
does not establish balances or funding of the separately generated real wallets.

### Startup/verification completion and configuration regression

`start` reports percentage completion for nine local components. `verify` reports
percentage completion across eight stages, including final Java evidence validation;
long stages emit a heartbeat every 15 seconds. Percentages measure finished checks,
including failures, and do not predict remaining time. Read the final PASSED/FAILED
status and separate failure count. Diagnostic log paths are printed before each
verification stage. Details and examples are in
[the local validation guide](33-LOCAL-FIRST-VALIDATION.md#completion-percentages-and-verification-configuration-isolation-2026-11-16).

The failed owner run `1794847618091-8861-verify` inherited deployed Cognito/network
settings in four configuration assertions. Verification now isolates connection
variables and external configuration selectors in its child processes; `.env`
and application startup are unchanged. After pulling the fix, rerun strict
verification and share its newest result before starting a fresh payment intent.
An optional skipped live-model case does not explain those four failures or
establish a live model run.

### Display failures directly in the terminal

Use `start --show-failures` to start React, the six backend services and local
PostgreSQL/LocalStack, with failure summaries and bounded redacted excerpts when
startup fails. Use `verify --require-database --show-failures` for build/test
verification with the same diagnostic option. Prefix each command with
`node --env-file=.env scripts/local-workbench.cjs`. Run them separately.

Verification does not start the interactive application. It uses test-owned
settings and isolated database integration containers; start the application with
`start` after verification. Both commands retain percentage/heartbeat reporting.
Only successful startup prints usable application URLs. See
[CLI responsibilities and diagnostic limits](33-LOCAL-FIRST-VALIDATION.md#cli-failure-details-and-command-responsibilities-2026-11-17).

### Single-command sequential startup (2026-11-17)

After pulling the update, use the same `start --show-failures` command. It starts
PostgreSQL, LocalStack, payment, compliance/quote, blockchain, ledger,
reconciliation and Visa simulator one by one, waiting for each health check.
React starts after backend API/access checks. Each backend image is built in
sequence using the existing Dockerfile. A failure stops subsequent starts,
retains existing data and reports the failed component and diagnostic log.
There is no need to run individual Docker or Vite startup commands.

The owner's strict verification run `1794894736276-45358-verify` passed with
613 Java tests passed and one optional live-agent test skipped. Sequential startup
has passed 34 relevant Node checks locally; actual sequential Docker startup
remains to be checked here. Once startup succeeds, follow the numbered presenter
navigation with a new payment and future deadline; do not reuse the expired
PAY-52272AAC4E14 preparation for execution. Live AI/testnet acceptance is separate.


### Enable the local secret-provider service (2026-11-17)

The agent uses the shared AWS endpoint. In Compose, that endpoint is LocalStack,
whose prior fixed SERVICES list omitted Secrets Manager. The service list now
defaults to sqs,events,secretsmanager and is configurable with LOCALSTACK_SERVICES
in private .env. The committed .env.example documents this non-secret setting.

After pulling the published change, run the unified startup command, separately
from the pull and after any previous startup has ended:

```bash
node --env-file=.env scripts/local-workbench.cjs start --show-failures
```

Once startup succeeds, check metadata only:

```bash
docker compose exec -T localstack awslocal secretsmanager describe-secret --secret-id stablecoins/demo/openai-api-key --region us-east-1 --query '{Name:Name,ARN:ARN}' --output json
```

ResourceNotFoundException means the enabled service has no secret under that name;
it is not an OpenAI authentication failure. Real AWS secret metadata shown earlier
does not establish a local copy. Enter key material only through a hidden private
setup flow; never place it in this document, Git or terminal command arguments.
The change does not enable the agent or create a key. Keep the currently running
application available until applying the update; no AWS deployment is involved.
