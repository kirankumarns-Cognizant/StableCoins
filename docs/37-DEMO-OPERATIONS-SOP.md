# StableFlow demo standard operating procedure

Updated: 2026-11-23. Audience: demo operator, independent approvers and supporting engineers.
Environment for command examples: Bash, repository root in GitHub Codespaces.

## 1. Context, purpose and scope

Demonstrate a corporate cross-border instruction progressing through controlled
routing, two independent approvals, stablecoin settlement, application accounting
and reconciliation. The primary business scenario is USD 10,000. The first
public-testnet connectivity/payment milestone is a separate 1-test-USDC run.

This SOP explains both what the operator does and why. It combines the functional
walkthrough with links to authoritative scripts, SQL and engineering runbooks.
It does not automatically run commands, change permissions or authorize a new
architecture. A procedure being documented is not proof it has executed.

| Boundary | Treatment in this POC |
|---|---|
| Corporate USD account and funding | Simulated fiat capacity and debit |
| KYC/KYB/AML/sanctions, FX and provider quote | Explicitly simulated |
| USD-to-USDC allocation | Simulated conversion; treasury must already hold test tokens |
| Payment APIs, stored approvals, events, ledger and reconciliation | Real application processing; verify runtime evidence |
| MOCK blockchain | Synthetic balances, hashes, blocks and receipts |
| REAL_TESTNET blockchain | Actual public-testnet transaction when deliberately configured and independently verified |
| USDC-to-fiat, Visa and beneficiary bank credit | Optional simulated payout |
| Runtime AI | Separate opt-in evidence gate; deterministic ranking is not an AI call |

A blockchain receipt is not the application ledger. Accounting must balance per
asset, and corrections use reversals/compensating entries. Posted entries stay
immutable. Test assets have no intended financial value.

## 2. Application architecture and processing boundaries

### 2.1 Current connected Codespaces deployment

~~~mermaid
flowchart TB
  Person[Operator or approver browser] --> UI[React and TypeScript UI]
  UI -->|Hosted sign-in and code exchange with PKCE| Cognito[Real AWS Cognito]
  UI -->|API calls with access token via Vite proxy| Payment[payment-service]
  subgraph Stack[Codespaces Docker Compose stack]
    Payment
    Compliance[compliance-quote-service]
    Chain[blockchain-service]
    Ledger[settlement-ledger-service]
    Reconcile[reconciliation-service]
    Visa[visa-simulator]
    Messaging[LocalStack EventBridge and SQS]
    DB[(PostgreSQL with service-owned tables)]
  end
  Payment <-->|Asynchronous events and commands| Messaging
  Compliance <--> Messaging
  Chain <--> Messaging
  Ledger <--> Messaging
  Reconcile <--> Messaging
  Visa <--> Messaging
  Payment --> DB
  Compliance --> DB
  Chain --> DB
  Ledger --> DB
  Reconcile --> DB
  Visa --> DB
  Chain --> Adapter[Configured blockchain adapter]
  Adapter --> Mock[MOCK synthetic evidence]
  Adapter -->|Explicit real-testnet configuration only| Base[Base Sepolia RPC and blockchain]
~~~

The adapter branches are configuration alternatives in the current implementation,
not simultaneous per-payment network routing. The existing service is configured
for one adapter/network. Private Besu support is a future additive proposal.
The diagram summarizes messaging/data boundaries; internal authenticated HTTP
calls also exist. Cognito is external to the local stack.

### 2.2 AWS target deployment

~~~mermaid
flowchart LR
  Browser[React browser] -->|Sign in| Identity[Amazon Cognito]
  Browser --> Gateway[Amazon API Gateway]
  Gateway --> ALB[Application Load Balancer]
  ALB --> Services[Six application services on ECS Fargate]
  Services <--> Bus[Amazon EventBridge and SQS]
  Services --> Aurora[(Aurora PostgreSQL)]
  Services --> Secrets[AWS Secrets Manager and KMS]
  Services -->|Blockchain service RPC| Public[Base Sepolia public testnet]
~~~

This is the approved target, not a statement that AWS application deployment or
Secrets Manager/KMS-backed signing has been accepted. Current local signing uses
an environment key; direct encrypted-keystore support and AWS signing remain
explicit work items. Application lifecycle commands here operate the local stack,
not AWS resources.

### 2.3 Financial journey and optional branch

~~~mermaid
flowchart TD
  Corporate[Corporate USD account - simulated] --> Instruction[Payment instruction and compliance - simulated checks]
  Instruction --> Review[Prepared route and quote plus two independent approvals]
  Review --> Allocation[Simulated USD to USDC allocation]
  Allocation --> Treasury[Prefunded treasury test-token wallet]
  Treasury --> Chain[MOCK adapter or deliberately enabled real Base Sepolia transfer]
  Chain --> Beneficiary[Beneficiary blockchain wallet]
  Beneficiary --> WalletEnd[USDC_WALLET outcome]
  Beneficiary --> OffRamp[Optional simulated USDC to fiat and Visa payout]
  OffRamp --> Bank[Simulated beneficiary USD or INR bank credit]
  WalletEnd --> Reconcile[Application ledger and reconciliation]
  Bank --> Reconcile
~~~

Ledger postings occur at defined processing stages; the final box represents
completion/evidence checks, not postponing all accounting until the end.
The beneficiary blockchain wallet is on the blockchain side of the boundary.
An on-ramp buys/converts fiat into crypto; our on-ramp remains simulated.
A real Base Sepolia transfer proves the settlement leg, not a real fiat purchase.

## 3. Entry prerequisites, roles and scenario choice

| Prerequisite | Why it is needed | Ready evidence |
|---|---|---|
| Current source and reviewed local changes | Avoid operating an older image or overwriting local edits | Pull succeeds; capture runtime revision/image when available |
| Docker, Compose, Node and existing private configuration | Start services and apply the intended environment | Lifecycle checks pass; no secrets printed |
| Customer/account/beneficiary configuration | Bind instruction to permitted records and recipient | Authorized UI shows expected customer and payment options |
| Operator login | Create and execute authorized instructions | Server verifies operator membership and role |
| Two distinct approver logins | Independent decisions on one reviewed version | Each identity has the server-assigned approval role |
| Valid future payment deadline | Permit a fresh execution within the confirmed instruction | Actual deadline is in the future |
| Mode chosen before preparation | Prevent confusing synthetic and on-chain proof | MOCK or explicitly configured real-testnet runtime is known |
| Real-testnet wallets and funds, for that scenario | Sign and pay gas/transfer tokens on the intended chain | Verified signing address, chain, contract, actual balances and gas readiness |

Operator: demo-a-operator. Approvers: demo-a-approver-one and demo-a-approver-two.
These labels do not grant roles; backend-verified identity and customer policy do.
Credentials are entered on Cognito's hosted page. Do not store passwords or tokens
in this SOP. For USD/INR bank payout choose the applicable simulated bank outcome;
for direct token delivery choose USDC_WALLET.

## 4. Functional execution procedure

Each step has a prerequisite, screen action, purpose, expected postcondition and
recovery rule. Complete one step's postcondition before continuing.

### 4.1 Open the application as operator

- **Prerequisite:** Start/check the stack using section 6; all nine components are ready.
- **Action:** Open the application URL printed by startup. Select Sign in securely;
  authenticate as demo-a-operator. Confirm the displayed identity/customer.
- **Why:** Establish a Cognito-authenticated operator session and server authorization.
- **Postcondition:** Authorized workspace/catalog loads for the intended customer.
- **Next/recovery:** Continue to creation. If sign-in fails, check the actual pool,
  browser client and endpoint; stack health does not prove correct login credentials.

### 4.2 Create the payment instruction

- **Prerequisite:** Authorized operator, configured sender account and beneficiary;
  intended amount, outcome and future deadline agreed. For the first live test use
  a NEW 1-test-USDC wallet instruction with sufficient funded capacity.
- **Action:** Select New payment/Create USD payment. Review the beneficiary wallet,
  amount, maximum fee, priority and delivery deadline; confirm and submit.
- **Why:** Persist an immutable, customer-authorized instruction with idempotent creation.
- **Postcondition:** Payment ID appears and journey records CREATED/COMPLIANCE_PENDING.
- **Next/recovery:** Retain its ID privately. If response is uncertain, inspect the
  existing operation before creating another instruction; do not duplicate it.

### 4.3 Wait for compliance approval

- **Prerequisite:** Payment created and background workers processing events.
- **Action:** Refresh displayed evidence and inspect the recorded payment state/journey.
- **Why:** Compliance gates routing/execution; the POC screening itself is simulated.
- **Postcondition:** COMPLIANCE_APPROVED recorded; rejection is not bypassed.
- **Next/recovery:** Prepare routing only after approval. For prolonged pending or
  failure inspect section 7 diagnostics; do not edit state directly in PostgreSQL.

### 4.4 Prepare the recommendation

- **Prerequisite:** Approved compliance, future payment deadline and configured flow.
- **Action:** Open Intent & routing; click Prepare / refresh recommendation once.
  Wait for READY / Preparation completed; inspect assessed candidates and reasons.
- **Why:** Capture provider quotes and treasury observations and create a versioned
  review. Deterministic eligibility/ranking is distinct from optional AI reasoning.
- **Postcondition:** New recommendation ID/version with usable, unexpired evidence
  and an eligible route. Preserve the exact reviewed version privately.
- **Next/recovery:** Check treasury. Do not repeat preparation just to refresh a
  screen: another preparation may replace the version and require fresh approvals.

### 4.5 Verify treasury readiness

- **Prerequisite:** Preparation complete for the intended review.
- **Action:** Open Treasury readiness. Inspect evidence mode, network/chain,
  treasury wallet, token contract, available USDC/ETH, gas reserve and expiry.
- **Why:** Treasury must already have the settlement asset and gas; simulated USD
  conversion cannot fund the chain wallet. Reserved capacity must be respected.
- **Postcondition:** READY for the intended amount and network, with no blocking
  reasons. For real acceptance, observations must be actual RPC-backed evidence.
- **Next/recovery:** Continue to quote review. If insufficient funds, fund the test
  treasury and obtain fresh evidence/review; do not override balances or reservations.

### 4.6 Inspect the proposed quote

- **Prerequisite:** Eligible recommendation and usable treasury evidence.
- **Action:** Open Quote & approvals. Review amount, provider, fees, outcome,
  version and actual expiry. Confirm the beneficiary/instruction in the review.
- **Why:** Both approvers must authorize the same material and version.
- **Postcondition:** PROPOSED review identified, normally 0/2 initially; no expiry.
- **Next/recovery:** Switch to approver one. Do not assume a fixed 15-minute window;
  use the actual displayed expiry and complete approvals AND execution before it.

### 4.7 Record the first independent approval

- **Prerequisite:** Same unexpired proposed review; approver one has verified permission.
- **Action:** Sign out/switch user to demo-a-approver-one. Open that payment's Quote
  & approvals; click Accept this quote, then Approve this version.
- **Why:** Acceptance and approval are separate persisted decisions.
- **Postcondition:** 1/2 approvals on the intended version; decisions show that actor.
- **Next/recovery:** Switch to approver two. Acceptance alone or repeated clicks by
  one actor cannot substitute for two independent approvals. Inspect recorded
  decisions and refresh evidence if the count does not match the expected action.

### 4.8 Record the second independent approval

- **Prerequisite:** First approval recorded; same unexpired recommendation/version.
- **Action:** Switch to demo-a-approver-two; accept that quote and approve that version.
- **Why:** Satisfy the distinct-approver policy without changing reviewed material.
- **Postcondition:** APPROVED, 2/2, same recommendation ID/version.
- **Next/recovery:** Return to operator for execution. If material/version changed
  or expired, obtain the required fresh review/approvals; do not transplant votes.

### 4.9 Authorize and execute once

- **Prerequisite:** APPROVED 2/2; unexpired instruction/review; correct network and
  sufficient treasury; operator permission. Testnet setup must be complete first.
- **Action:** Switch to demo-a-operator. Open Execution journey; read/check the
  authorization statement and click Execute approved USDC payment once.
- **Why:** Explicitly request backend execution of the approved instruction.
- **Postcondition:** Execution reference exists; PREPARING can be an intermediate
  state. Real-testnet mode submits a real public transaction with test assets.
- **Next/recovery:** Inspect this execution's progress. An acknowledged command
  is not completion. For an unknown outcome do not create a replacement payment
  or blindly resubmit; resolve the pinned execution/transaction first.

### 4.10 Verify blockchain settlement

- **Prerequisite:** Execution requested and asynchronous processing advancing.
- **Action:** Use the page's Refresh control near evidence metadata; inspect
  Execution references and Blockchain evidence. For real mode independently check
  the public hash/receipt, exact token Transfer and required confirmations.
- **Why:** Separate an internal UUID or synthetic hash from actual chain settlement.
- **Postcondition:** CONFIRMED execution plus matching confirmed chain evidence;
  for real acceptance, correct chain/contract/sender/recipient/amount and receipt.
- **Next/recovery:** Check accounting/reconciliation. PREPARING alone is not a
  transfer; diagnose validation, outbox and transaction records using read-only SQL.

### 4.11 Verify ledger, applicable payout and reconciliation

- **Prerequisite:** Confirmed settlement and downstream processing available.
- **Action:** Inspect Ledger & reconciliation and audited journey. For bank outcomes
  also inspect Optional fiat payout and its explicit simulated evidence.
- **Why:** Chain confirmation alone does not establish fee-complete balanced
  accounting, simulated beneficiary bank credit or matched reconciliation.
- **Postcondition:** SETTLEMENT_COMPLETED and RECONCILED with matched immutable
  receipt/journal/report references. Wallet outcome has no required bank payout.
- **Next/recovery:** Export evidence. A ledger discrepancy or failed payout requires
  investigation/approved compensation; never mutate posted entries to force success.

### 4.12 Preserve evidence and close the session

- **Prerequisite:** Terminal outcome known, or unresolved execution explicitly recorded.
- **Action:** Privately save mode, runtime revision/image, payment/correlation,
  review/version/decisions, execution/transaction, hash/receipt, journals and report.
  Update the redacted status and next action. Stop the application when finished.
- **Why:** Make the result reproducible and resumable without relying on chat history.
- **Postcondition:** Evidence location and remaining work documented; application
  stop verified if requested. Unresolved work remains clearly marked pending.
- **Next/recovery:** Retain completed payments as audit history. For a future run
  use a new instruction, not a duplicate execution of the completed one.

## 5. Current activity checkpoint

Read [POC activity status](36-POC-ACTIVITY-STATUS.md) before choosing the next action.
Owner evidence establishes a USD 1,000 MOCK wallet payment through RECONCILED,
CONFIRMED execution and two approvals. A subsequent stop reported 9/9 components
stopped. Later PostgreSQL output explicitly identified that transaction as MOCK;
its hash and block were synthetic. These observations are historical snapshots,
not a claim about the stack's present running state.

The active next milestone is a new, initially 1-test-USDC payment on Base Sepolia.
The owner reports two disposable encrypted wallets created. Funding, signer
integration, explicit testnet activation and public receipt verification are
pending. Keep actual run IDs, wallet addresses and private evidence in ignored
.local/poc-status/ or the owner's approved private record, not this shared SOP.

The existing local signer accepts an environment private key; direct encrypted
keystore loading has not been implemented. Normal Compose explicitly forces MOCK
and testnet-off. No ready-to-run live activation command is claimed here.
Besu is a future additive network proposal; it is not currently implemented or
an alternative proof of the public-testnet requirement. An ADR is required first.


## 6. Operational command reference

Wait for any lifecycle command to finish before pulling. Inspect local changes;
do not discard them to force an update.

~~~bash
git status --short
git pull --ff-only origin main
node --env-file=.env scripts/local-workbench.cjs status --show-failures
~~~

| Action | Command suffix after node --env-file=.env scripts/local-workbench.cjs | Effect |
|---|---|---|
| Start | start --show-failures | Build/start the stack; reuse unchanged backend containers; ensure React is available |
| Restart after pulled code/config changes | restart --show-failures | Build and force-recreate six backends sequentially; ensure infrastructure and React are available |
| Status | status --show-failures | Inspect existing stack and health/access boundaries; does not start or execute payments |
| Backend smoke | smoke | Repeat read-only backend health/access probes |
| Stop application | stop --show-failures | Stop owned React, six backends, LocalStack, then PostgreSQL; verify shutdown |

~~~bash
node --env-file=.env scripts/local-workbench.cjs start --show-failures
~~~

Use only the command needed, one lifecycle action at a time. Stop retains
containers and volumes and reports STOPPED only when all nine components are
confirmed stopped. A failure or unavailable inventory is not successful shutdown.
Starting services resumes normal workers and may continue pending work.

READY with 9/9 components is infrastructure/application readiness. It does not
prove successful Cognito login, an AI invocation or a completed real transfer.
Application stop leaves the Codespace running; gh codespace list may still show
Available. Stop a separate keep-alive terminal with Ctrl+C and stop the Codespace
separately when finished. LocalStack in-memory state persistence is not guaranteed
merely by retaining its container/volume. See the authoritative
[lifecycle details](33-LOCAL-FIRST-VALIDATION.md#rebuilding-a-running-application-2026-11-23).

~~~bash
gh codespace list
gh api "user/codespaces/$CODESPACE_NAME" --jq '{name: .name, state: .state, idleTimeoutMinutes: .idle_timeout_minutes}'
~~~

The personal GitHub Codespaces default timeout applies to newly created
Codespaces; inspect the existing instance rather than assuming the default is
its effective value. VS Code terminal/chat timeout settings are separate.


## 7. Read-only SQL and diagnostic commands

The reusable [inspection SQL](../scripts/demo/inspect-payment.sql) collects
execution, compliance validation, blockchain transaction and recent payment
outbox records in a read-only transaction. It is a SQL script, not a stored
procedure; it does not install database objects or repair records.

Set PAYMENT_ID to the payment being inspected, then run:

~~~bash
PAYMENT_ID='PAY-REPLACE-WITH-YOUR-PAYMENT-ID'
docker compose exec -T -e DEMO_INSPECT_PAYMENT_ID="$PAYMENT_ID" postgres sh -c 'exec psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v payment_id="$DEMO_INSPECT_PAYMENT_ID"' < scripts/demo/inspect-payment.sql
~~~

The script uses psql's quoted SQL variable form for the payment ID. Review
results privately. An internal transaction UUID differs from tx_hash. MOCK
hashes, explorer links and confirmed statuses remain synthetic evidence.

For infrastructure inspection:

~~~bash
docker compose --profile services ps
~~~

Health endpoint example (read-only; default payment-service port):

~~~bash
curl --max-time 5 -i http://127.0.0.1:8080/health
~~~

For bounded, redacted CLI failure excerpts use status --show-failures. Raw Docker
logs and stack reports can contain operational data; review/redact before sharing.
A previous COMPLIANCE_VALIDATION_PENDING error was transient in the completed
MOCK run; inspect current database evidence before diagnosing a new run from an
old log. The obsolete Compose version warning alone is not a failed payment.


## 8. Real Base Sepolia preparation and acceptance

This is the next activity, not a record of executed public-testnet acceptance.

1. Use dedicated testnet wallets and privately retain encrypted keystores/passwords.
   Never use the known dummy MOCK signing key or real financial assets.
2. Fund treasury with Base Sepolia test ETH for gas and official Base Sepolia
   test USDC. Record faucet funding hashes separately from application hashes.
3. Read chain ID, token contract/decimals and actual on-chain balances via RPC.
   Expected chain ID is 84532. Positive ETH alone does not prove sufficient gas.
4. Configure the local encrypted-keystore signer and wallet-file location (see below).
   Keep secret input out of command arguments, terminal output, Git and PostgreSQL.
5. Prepare and review an explicit Compose/profile override for the intended
   containers. REAL_TESTNET plus TESTNET_ENABLED=true are required, and the local
   profile must be deliberately overridden. Setting .env alone is insufficient
   because the base Compose file forces MOCK/testnet-off. Use the explicit local overlay below. Verify effective runtime
   mode without dumping environment secrets. Do not use a fictional override file.
6. Verify on-chain treasury readiness and a new 1-test-USDC instruction. Complete
   the same two-person approval/execution flow above within the actual expiry.
7. Require persisted LIVE evidence and independently verify the chain ID,
   successful receipt, token contract, Transfer sender/recipient/amount, block
   and required confirmations. A wallet address or explorer URL alone is not proof.
8. Inspect actual network fee journals and terminal reconciliation. Record runtime
   commit/image, payment/correlation, review, execution, hash and report references.
9. Complete the primary USD 10,000 scenario separately after the small transfer,
   subject to sufficient test funds. Keep fiat funding/conversion labelled simulated.

### Read-only RPC funding check

The cross-platform preflight checks chain ID, official test-USDC contract code,
6 decimals, native ETH and token balance at one observed block. Replace the two
public-address placeholders; the amount defaults to 1 test USDC.

~~~bash
BLOCKCHAIN_RPC_URL=https://sepolia.base.org node --env-file=.env scripts/base-sepolia-preflight.cjs --treasury 0xYOUR_TREASURY --beneficiary 0xYOUR_BENEFICIARY --amount 1
~~~

RPC_BALANCES_READY means positive ETH and at least the requested token amount;
FUNDING_REQUIRED means one of those checks failed. FAILED means configuration or
RPC validation could not complete. Funding/validation failure exits with code 2.
This script never loads signing keys, changes runtime mode or submits a transfer.
It does not estimate complete gas fees or verify signing access; those checks
remain necessary in application treasury readiness before execution. The explicit
RPC environment variable above affects this command only, not Compose startup.
The default shared network/token tuple is validated; no unofficial token is substituted.

Reference [testnet setup](20-BLOCKCHAIN-TESTNET-SETUP.md),
[treasury controls](25-TREASURY-AND-EXECUTION-READINESS.md),
[connected flow and automated runner](30-PAYMENT-FLOW-INTEGRATION.md), and
[proof acceptance](35-HACKATHON-PROOF-PACKAGE.md).
The existing run-wallet-flow.cjs is a payment-mutating automated runner for its
specified USD 10,000 scenario; do not run it as a read-only balance check or as a
substitute for the separately planned 1-test-USDC UI run.
The existing PowerShell testnet-preflight.ps1 is read-only, but assumes its
variables are already loaded and does not unlock encrypted wallets or activate
Compose. Its PASS alone is not complete payment acceptance.

Public funding references (verify current network selection and contract):
[Base network](https://docs.base.org/get-started/connect-to-base),
[Base funding](https://docs.base.org/get-started/get-funds),
[Circle faucet](https://faucet.circle.com),
[Circle USDC contracts](https://developers.circle.com/stablecoins/usdc-contract-addresses).


## 9. Setup and recovery references

| Need | Authoritative reference |
|---|---|
| Shared connection settings | [Configuration](23-SHARED-CONNECTION-CONFIGURATION.md) |
| Cognito identity and approval permissions | [Identity setup](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md) |
| Runtime AI/model configuration | [Agent runtime](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md) |
| Accounting meaning and invariants | [ADR-015](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md) |
| Start/restart/stop and diagnostic reports | [Local operations](33-LOCAL-FIRST-VALIDATION.md) |
| Immutable evidence and acceptance gates | [Proof package](35-HACKATHON-PROOF-PACKAGE.md) |

Cognito login uses the hosted authorization-code + PKCE flow. The browser sends
user credentials to Cognito and uses its access token for application APIs.
Enabled/Confirmed does not prove a supplied password is correct. Check the
configured pool, browser client and sign-in endpoint before resetting credentials.
Never record credentials or access/refresh tokens in this SOP.

Administrative configuration helpers, seeding, password resets and payment runners
change state. Consult their runbooks and preview capabilities; they are not part
of routine startup. Do not reset balances, mutate posted journals, remove volumes
or bypass approvals to recover a demo. Inspect unknown submissions first.


## 10. Evidence recording and session handover

Conversation compaction summaries help continuity, but are not a guaranteed full
transcript or substitute for repository evidence. A new session must read
AGENTS.md, this SOP and the current activity status. On the owner's machine also
read available ignored .local/poc-status/ handover records; these are not pulled
into Codespaces and are not automatically available on another computer/session.
If a referenced private record is absent, ask for the missing public facts rather
than inventing a completed step or requesting secrets.

After an activity, record date/timezone, runtime commit/image when known,
environment/mode, action, observed result, evidence location, outstanding issues
and the exact next step. Distinguish owner-pasted output from independently
executed verification. Update shared summaries only with reviewed redacted facts;
keep customer IDs, wallet details and raw evidence private. Never mark code,
MOCK runs, real-model inference and public-chain acceptance as interchangeable.

Pending preparation/execution commands must be promoted into this SOP only after
the corresponding implementation exists and the command has been checked. Keep
authoritative commands in scripts or SQL files and link them here to avoid drift.

### Codespaces encrypted-keystore activation

This is the existing local testnet signer boundary, extended to accept an encrypted
Ethereum V3 keystore. It is not AWS Secrets Manager/KMS or institutional custody.
The file provider requires the local profile and rejects aws-dev. The legacy raw-key
provider remains available for existing test tooling; do not configure both sources.
The keystore and password are read only at process startup; changing either requires
a restart. Decrypted signing credentials remain in JVM memory for the process lifetime.

Before activation, inspect unresolved executions/queues using the read-only payment
inspection steps. Restarting consumers can resume prior work; startup is not a
promise that no previously authorized execution will be processed. Do not reuse a
MOCK review or expired instruction for the real test. Preserve payment history.

1. Upload the verified encrypted treasury file into ignored `.local/wallets/`.
   Confirm its public address and checksum privately. Keep mode 600, owned by the
   Codespaces user. The beneficiary keystore is not needed for sending to its address.
2. From the repository root in an interactive Codespaces Bash terminal, create
   the ignored password file using hidden input and confirmation:

~~~bash
bash scripts/prepare-testnet-password.sh
~~~

The helper refuses to overwrite an existing password file. It writes the password
exactly, without a newline, under `.local/secrets/` with owner-only permissions.
Do not paste the password into command arguments, .env, chat, logs or Git. Compose
file-backed secrets are local protected files, not an encrypted secret store; anyone
with host/Docker administrative access can read them. Never run `docker compose config`
with secret contents interpolated into environment variables.

3. Export NONSECRET settings in the same terminal; substitute the expected public
   treasury address. These paths describe the uploaded file, not a new wallet:

~~~bash
export TESTNET_KEYSTORE_FILE="$PWD/.local/wallets/treasury-wallet.json"
export TESTNET_PASSWORD_FILE="$PWD/.local/secrets/treasury-password"
export TESTNET_TREASURY_ADDRESS=0xYOUR_VERIFIED_TREASURY_ADDRESS
export TESTNET_LOCAL_UID="$(id -u)"
export TESTNET_LOCAL_GID="$(id -g)"
export TESTNET_RPC_URL=https://sepolia.base.org
export COMPOSE_FILE=docker-compose.yml:docker-compose.testnet.yml

git check-ignore -- .local/wallets/treasury-wallet.json .local/secrets/treasury-password
docker compose --profile services config --quiet
~~~

The overlay explicitly selects local,testnet, REAL_TESTNET, TESTNET_ENABLED=true,
Base Sepolia chain 84532 and official six-decimal test USDC. Only blockchain-service
receives the two secret mounts; the raw-key environment input is cleared. Required
host UID/GID preserve owner-only file access under the non-root container process.
The expected address is checked against decrypted credentials, never trusted from
keystore JSON metadata alone. Incorrect secrets/configuration stop startup with
bounded error codes; no automatic fallback to another signing key occurs.

4. After reviewing outstanding work, rebuild/restart through the existing helper:

~~~bash
node --env-file=.env scripts/local-workbench.cjs restart --show-failures
~~~

Keep COMPOSE_FILE and the nonsecret settings exported for subsequent status/restart/
stop commands in this session. A new terminal needs the same exports. Normal startup
without this opt-in overlay still selects MOCK; do not alternate modes mid-payment.
The overlay overrides only blockchain-service; funding, compliance, FX and payout
simulation boundaries are unchanged. It does not enable disabled agent/operator
feature flags or bypass approvals.

5. Inspect only the relevant startup lines:

~~~bash
docker compose logs --no-color --tail 200 blockchain-service |
  rg 'keystore_signer_verified|mode_real_testnet|TESTNET_|PUBLIC_TESTNET_|FILE_KEYSTORE_'
~~~

Require keystore_signer_verified for the expected public address and
mode_real_testnet network=base-sepolia chainId=84532. Health alone does not establish
these facts. Then perform a fresh preparation, inspect non-MOCK treasury evidence,
validate fee coverage and continue the two-approver 1-USDC flow. Receipt verification
remains authoritative. Existing treasury readiness uses buffered gas plus a configured
allowance; it is not a measured complete Base L1/operator fee quote.

AWS execution remains a separate gate requiring the approved Secrets Manager/KMS
integration; do not mount this local password file into ECS or bake it into an image.
