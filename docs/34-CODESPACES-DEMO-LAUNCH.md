# Codespaces launch and demonstration

Updated 2026-11-15. GitHub destination: [StableCoins](https://github.com/kirankumarns-Cognizant/StableCoins).
AWS destination: account `640237069320`, application Region `us-east-1`, IAM Identity Center authentication.
The owner requested step 11 preparation before AWS deployment. Codespaces is the
container host for acceptance; deploying the application to AWS is a later gate.

## What is ready and what still needs execution

The connected React application and six Java services are implemented. The
commercial rehearsal, two customer configurations and evidence checklist are
in [the proof package](35-HACKATHON-PROOF-PACKAGE.md). The complete presenter
narrative, roles, exact clicks and expected outcomes are in
[the screen walkthrough](29-PROTOTYPE-EXECUTION-RUNBOOK.md).

An HTML prototype or synthetic browser screenshot is not a connected demo.
Actual Codespaces startup, PostgreSQL tests, Cognito sign-in, inference and a
public-testnet payment must be recorded separately. Required identity and wallet
resources have not yet been supplied. No completed live run is asserted here.

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

Expected: all six services healthy, migrations applied, and 18 health/access-denial
probes pass. Inspect a failing service privately; do not paste complete environment
or logs containing secrets. Preserve volumes on failure. Apply the existing
[wallet and bank seeds](30-PAYMENT-FLOW-INTEGRATION.md#codespaces-demonstration)
with the approved nonzero beneficiary testnet wallet. For the second customer,
use the optional identifiers documented in the proof package. Seeds never refill
an existing balance or replace an approved recipient. Run them after migrations.

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
3. The Vite process runs on the Codespace host; its payment proxy target must be
   `http://localhost:8080`. Container-to-container URLs must retain their service
   DNS names. Do not globally replace `PAYMENT_SERVICE_URL` in Compose with host
   localhost. Set the proxy target only for the frontend launch:

```bash
PAYMENT_SERVICE_URL=http://localhost:8080 npm --prefix frontend run dev -- --host 0.0.0.0
```

4. Use **Open in Browser** for port 5173. Click **Sign in securely**. Expected:
   Cognito authenticates the operator and **Overview** shows only their customer.
   Restart Vite after changes to public settings. A private forwarded port may
   first require GitHub sign-in; that is distinct from the application's Cognito login.

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
Spring profile, `BLOCKCHAIN_MODE=TESTNET`, `TESTNET_ENABLED=true`, matching RPC/
chain/token settings and the secret-backed signer. Check the effective mode before
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
