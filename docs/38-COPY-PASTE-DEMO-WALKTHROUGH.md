# Copy-and-paste demo: UI, database and real Base Sepolia evidence

Updated: 2026-11-25. Read with the [central SOP](37-DEMO-OPERATIONS-SOP.md).
Terminal: **Codespaces Bash**, repository `/workspaces/StableCoins`.
Browser: StableFlow and BaseScan. Do not paste Bash commands in Windows PowerShell.
Run one numbered step at a time; proceed only when its postcondition passes.

## 1. Context and architecture

Demonstrate **1 USD simulated funding -> 1 test USDC real transfer -> real application
ledger -> reconciliation**. The primary USD 10,000 business scenario is separate:
a simulated USD balance cannot fund an on-chain wallet.

~~~mermaid
flowchart LR
  UI[React: operator instruction] --> P[Payment service]
  P --> C[Simulated compliance and quotes]
  C --> R[Versioned routing plus live treasury observation]
  R --> A[Two independent approvals]
  A --> E[Operator executes once]
  E --> F[Simulated fiat funding and conversion]
  F --> B[Blockchain service: encrypted signer]
  B --> Base[Real Base Sepolia USDC transfer]
  Base --> L[Real application journals and receipt fees]
  L --> Reconcile[Owner reconciliation]
~~~

See the [central diagrams](37-DEMO-OPERATIONS-SOP.md#2-application-architecture-and-processing-boundaries)
for six Java services, React, PostgreSQL, LocalStack messaging, external Cognito
and the AWS target. Ledger writes also happen before submission; the diagram
summarizes evidence flow. LocalStack is emulated AWS, not deployed AWS infrastructure.

**Real:** APIs, authenticated roles, approvals, events, database, application ledger,
testnet signature/transaction/receipt, reconciliation.
**Simulated:** fiat funding/conversion, compliance checks, FX/provider pricing,
optional Visa and bank credit. Test assets have no intended financial value.
Deterministic ranking is not an AI invocation. Besu remains future work.

This procedure uses observed lessons from the owner's successful Nov 25 run.
It is not a claim that every newly added helper has already been rerun in Codespaces.
See [validation status](36-POC-ACTIVITY-STATUS.md). Commands do not bypass policy,
reset balances, alter financial states, remove volumes or blindly resubmit transfers.

## 2. Prerequisites and terminal settings

Before the demo, confirm existing Docker/Compose, Java 21, supported Node,
frontend dependencies, private root `.env`, working Cognito and backend roles:
`demo-a-operator`, `demo-a-approver-one`, `demo-a-approver-two`.
Passwords are entered on hosted sign-in, never in this SOP.

Use the **verified new wallet pair**. Upload only its encrypted treasury keystore
to ignored `.local/wallets/treasury-wallet.json`. A beneficiary needs no signing
file to receive tokens. Preserve old files/history. Matching password file:
`.local/secrets/treasury-password`. If absent, create it interactively with:

~~~bash
cd /workspaces/StableCoins
bash scripts/prepare-testnet-password.sh
~~~

Run that helper only if the password file is missing; it refuses overwrites.
Never print secrets. Local protected file mounts are not AWS Secrets Manager/KMS.

### 2.1 Get the published source

Wait for any lifecycle command to finish. Inspect changes; do not discard them:

~~~bash
cd /workspaces/StableCoins
git status --short
git pull --ff-only origin main
test -f scripts/demo/db-check.sh && test -f scripts/demo/verify-receipt.cjs
~~~

If helpers are absent, distribute/publish the reviewed changes first. Files written
on Windows are not automatically available on GitHub or Codespaces.

### 2.2 Repeat exports in EACH new terminal / after Codespace restart

Paste this block and enter **public addresses only** at the two prompts.
Use the verified new pair from your private record.

~~~bash
cd /workspaces/StableCoins
read -r -p "Verified treasury public address (0x...): " DEMO_TREASURY
read -r -p "Intended beneficiary public address (0x...): " DEMO_BENEFICIARY
export DEMO_TREASURY DEMO_BENEFICIARY
export TESTNET_KEYSTORE_FILE="$PWD/.local/wallets/treasury-wallet.json"
export TESTNET_PASSWORD_FILE="$PWD/.local/secrets/treasury-password"
export TESTNET_TREASURY_ADDRESS="$DEMO_TREASURY"
export TESTNET_LOCAL_UID="$(id -u)"
export TESTNET_LOCAL_GID="$(id -g)"
export TESTNET_RPC_URL=https://sepolia.base.org
export COMPOSE_FILE=docker-compose.yml:docker-compose.testnet.yml

node -e 'for(const k of ["DEMO_TREASURY","DEMO_BENEFICIARY"]){if(!/^0x[0-9a-fA-F]{40}$/.test(process.env[k]||"")||/^0x0{40}$/i.test(process.env[k]))throw Error("Invalid address: "+k)} if(process.env.DEMO_TREASURY.toLowerCase()===process.env.DEMO_BENEFICIARY.toLowerCase())throw Error("Wallets must differ");'
test -f "$TESTNET_KEYSTORE_FILE" && test -f "$TESTNET_PASSWORD_FILE"
chmod 600 -- "$TESTNET_KEYSTORE_FILE" "$TESTNET_PASSWORD_FILE"
stat -c '%a %U %n' "$TESTNET_KEYSTORE_FILE" "$TESTNET_PASSWORD_FILE"
git check-ignore -- .local/wallets/treasury-wallet.json .local/secrets/treasury-password
docker compose --profile services config --quiet
~~~

**Gate:** files exist, expected owner, both mode 600 and ignored, valid addresses,
Compose passes. If any command fails, stop and correct it before startup.
The explicit overlay selects REAL_TESTNET and TESTNET_ENABLED=true.
Ordinary Compose without this overlay selects MOCK. Do not alternate modes mid-payment.

## 3. Inspect existing work, then start

### 3.1 Dependencies and pending work

~~~bash
docker compose up -d --no-deps --wait postgres localstack
bash scripts/demo/db-check.sh pending
~~~

[pending.sql](../scripts/demo/pending.sql) shows unfinished executions, chain
transactions, closures and unpublished payment-owner events.
Starting dependencies does not stop backends that were already running.
Review existing authorized work before resuming consumers; startup may process it.

Read queue/DLQ counts without receiving or deleting messages:

~~~bash
docker compose exec -T localstack sh -ec '
region="${DEFAULT_REGION:-us-west-2}"
queues="$(awslocal sqs list-queues --region "$region" --query QueueUrls --output text)"
if [ -z "$queues" ] || [ "$queues" = "None" ]; then
  echo "NO_QUEUES_FOUND: owners must provision the expected topology"
  exit 0
fi
for queue in $queues; do
  printf "\nQueue: %s\n" "${queue##*/}"
  awslocal sqs get-queue-attributes --region "$region" --queue-url "$queue" --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible ApproximateNumberOfMessagesDelayed --query Attributes --output json
done'
~~~

Empty results mean only that those queries found no matching work. Queue counts
are approximate; missing queues or service outboxes are not covered by the
payment-outbox check. LocalStack can lose topology on restart; owners bootstrap
it during application startup. Inspect unknown submitted hashes before new execution.

### 3.2 Start and verify actual runtime

**Prerequisite:** reviewed pending work and intend to resume it.

~~~bash
node --env-file=.env scripts/local-workbench.cjs start --show-failures
node --env-file=.env scripts/local-workbench.cjs status --show-failures
~~~

**Gate:** **9/9 ready**, including frontend and proxy. Start builds/starts in order;
status does not start anything. Health alone does not establish the signer/mode:

~~~bash
docker compose logs --since 30m --no-color --tail 300 blockchain-service |
  grep -E 'keystore_signer_verified|mode_real_testnet|mode_mock|TESTNET_|PUBLIC_TESTNET_|FILE_KEYSTORE_'

docker inspect stablecoins-blockchain-service --format 'running_image={{.Image}}{{println}}{{range .Mounts}}mount={{.Destination}} writable={{.RW}}{{println}}{{end}}'
docker image inspect stablecoins/blockchain-service:latest --format 'latest_image={{.Id}}'
~~~

Require verified intended treasury and `mode_real_testnet network=base-sepolia
chainId=84532`, with read-only secret mounts. No recent log line may mean startup
fell outside the time window; inspect this container's startup evidence.
Desired Compose config does not prove an old running container adopted it.
Never dump full container environment. Recovery branches are in section 11.

Open the **Application URL printed by start**, keep forwarded ports Private:

~~~bash
printf 'https://%s-5173.%s/\n' "$CODESPACE_NAME" "${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
~~~

## 4. Show catalog and pre-transfer chain balances

~~~bash
bash scripts/demo/db-check.sh catalog
BLOCKCHAIN_RPC_URL="$TESTNET_RPC_URL" node --env-file=.env scripts/base-sepolia-preflight.cjs --treasury "$DEMO_TREASURY" --beneficiary "$DEMO_BENEFICIARY" --amount 1
~~~

[catalog.sql](../scripts/demo/catalog.sql) shows demo customer A accounts,
beneficiary addresses and recent payments. For another authorized demo customer,
set `export DEMO_CUSTOMER_ID=...` to its UUID first.
Correct relationship: payment.sender_account_id -> account.customer_id.
**Payment has no customer_id column.** Local administrative psql is not browser authorization.

**Gate:** intended active beneficiary/address and account; RPC chain 84532,
official USDC `0x036CbD53842c5426634e7929541eC2318f3dCF7e`, at least 1 test USDC,
positive ETH. Save a private before snapshot. RPC_BALANCES_READY does not verify
signer, runtime or complete network fees; application readiness still gates execution.

If funds are missing, use [Circle faucet](https://faucet.circle.com/) for Base
Sepolia test USDC and the Coinbase developer faucet or a **completed/claimed**
OpenFaucet Base Sepolia request for test ETH. Mining alone is not funding.
Check actual balances; no wallet password is needed to receive funds.
If beneficiary binding is absent, follow the existing controlled
[flow setup](30-PAYMENT-FLOW-INTEGRATION.md) and
[seed](../scripts/seed-wallet-demo.sql) with an unused beneficiary ID.
Do not rebind historical payments, reset balances or recreate verified wallets.

## 5. Create the instruction; show simulated compliance

Sign in as **demo-a-operator**. Choose **New payment**.

| Field | Demonstration value |
|---|---|
| Corporate USD account | Existing permitted demo USD account, verified in catalog |
| Supplier beneficiary | Base Sepolia Test Supplier, bound to intended new wallet |
| Payment amount USD | **1.00** (replace 10,000 default) |
| Delivery outcome | **USDC_WALLET** |
| Maximum fee USD | **3.00**, if accepted and within customer policy |
| Deliver by | Fresh future UTC ISO timestamp from command below |
| Priority | **LOWEST_COST** |
| Requested route | **STABLECOIN** |
| Confirmation | Checked after reviewing all values |

~~~bash
node -e 'console.log(new Date(Date.now()+24*60*60*1000).toISOString())'
~~~

Copy that timestamp into the form. Delivery deadline does not extend review
expiry. The $3 ceiling accommodated the observed $2.02 simulated provider quote;
fresh costs/eligibility may differ. Under ADR-015 quoted provider costs are
observations, **not automatically charged USD fees or revenue**. Actual network
fee expense is receipt-derived ETH, separate from that quote.

Click **Create payment intent once**, then enter its displayed ID:

~~~bash
read -r -p "New payment ID shown by UI (PAY-...): " PAYMENT_ID
export PAYMENT_ID
bash scripts/demo/db-check.sh payment "$PAYMENT_ID"
~~~

[payment.sql](../scripts/demo/payment.sql) shows the instruction, recipient,
correlation, payment status/history, compliance checks and payment outbox.

**Postcondition:** one saved instruction for 1 USD, intended wallet, maximumFee 3.
CREATED/COMPLIANCE_PENDING may pass between polls: demonstrate their persisted
history rows and subsequent COMPLIANCE_APPROVED with simulation=true checks.
PUBLISHED outbox status means published, not downstream completion.
Creation/compliance alone is not a chain transfer.

**404/uncertain creation:** preserve browser tab, run `catalog`, inspect the newest
saved instruction. If UI says **Retry original instruction**, resolve that same
retained idempotency key; do not clear browser storage or create a duplicate.
HTTP 404 alone does not establish a Codespace restart; inspect status and the
failed browser Network request. If no record exists, resolve the retained request
before choosing a fresh instruction.

## 6. Prepare; show routing and treasury snapshots

**Prerequisite:** COMPLIANCE_APPROVED and future deadline.
Click **Intent & routing -> Prepare / refresh recommendation once**.
Wait for READY / Preparation completed, then:

~~~bash
bash scripts/demo/db-check.sh preparation "$PAYMENT_ID"
~~~

[preparation.sql](../scripts/demo/preparation.sql) reads:

- payment_flow_preparation: request/status/recommendation binding;

- compliance_flow_quote: immutable explicitly simulated provider snapshots;

- blockchain_flow_observation: captured treasury document;

- routing_recommendation: exact version, assessment, quote, state and expiry.

**Gate:** latest PROPOSED review unexpired; eligible candidate within fee cap.
Open **Treasury readiness**: LIVE observations, READY, intended treasury,
chain 84532/official token, sufficient **available** USDC and ETH after reservations,
gas estimate, no blocking reasons and unexpired observations.
Historical snapshots differ from fresh RPC balances; execution revalidates.

If NO_ELIGIBLE_ROUTE, inspect findings. The observed $0.10 fee ceiling excluded
both provider costs ($2.02/$5.02); preparation was READY but review EXPIRED with
assessment expiry equal to assessment time. Changing an immutable fee limit or
deadline requires a new instruction. Repeated preparation cannot fix such a limit.

Do not click Prepare / refresh merely to update the screen; it creates a new
review. UI **Refresh** near evidence metadata is the read-only refresh control.
No AI run is required here. For a separate live model demonstration follow the
[agent guide](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md) and capture actual run provenance.

## 7. Acceptance and each independent approval

Open **Quote & approvals**. Record recommendation ID/version, quote ID/version,
amount/fee and actual expiry. Both approvals AND execution must fit that window.

After **each action** below, run:

~~~bash
bash scripts/demo/db-check.sh approvals "$PAYMENT_ID"
~~~

[approvals.sql](../scripts/demo/approvals.sql) shows exact review/version, distinct
accepting/approving actors and persisted actor/action/decided_at rows.

| Browser action | Expected DB change |
|---|---|
| Before approvals | Latest PROPOSED review; 0 approving actors |
| Sign in as demo-a-approver-one; Accept this quote | QUOTE_ACCEPTED decision for actor one |
| Same actor; Approve this version | APPROVED decision; 1 approving actor |
| Switch to demo-a-approver-two; Accept this quote | Separate QUOTE_ACCEPTED decision |
| Second actor; Approve this version | Two DISTINCT APPROVED actors on SAME review; state APPROVED |

Server membership grants roles; changing browser labels does not.
Acceptance alone is not approval. Inspect timestamps if actions progress between
queries; do not pause consumers or modify DB states to stage the demonstration.

**Gate:** APPROVED with required approvals (normally 2/2), same exact version and
unexpired. Do not prepare again during approval. If expired before execution,
operator obtains a fresh review and the two identities approve its new version.

## 8. Execute once and demonstrate backend progress

**Prerequisite:** approved current review, unexpired, correct live treasury ready.
Switch back to **demo-a-operator -> Execution journey**.
Review authorization, check it and click **Execute approved USDC payment once**.
This authorizes a real 1-test-USDC public transaction, spending test ETH for fees.

~~~bash
bash scripts/demo/db-check.sh execution "$PAYMENT_ID"
~~~

Repeat after 15-30 seconds. [execution.sql](../scripts/demo/execution.sql) shows
execution, execution-specific validation, transaction, reservation/hash/receipt
fields, exact fee components and payment-owner outbox.

| Observed state/evidence | Meaning |
|---|---|
| Execution PREPARING | Request persisted; transfer not yet established |
| Compliance VALIDATED | Execution-specific validation persisted |
| PREPARED / SUBMISSION_REQUESTED | Reservation/preparation and submission request; may have no hash |
| SUBMITTING / SUBMISSION_UNKNOWN | Resolve outcome; never blindly retry chain submission |
| SUBMITTED with tx_hash | Recorded submission; receipt still required |
| CONFIRMED, LIVE, block/receipt | Application confirmation recorded; independent chain check next |
| REVIEW_REQUIRED / failure_code | Inspect current reason, no state edit or bypass |

Compare updated_at/status/hash; PREPARING alone is not proof of active progress.
A historical transient COMPLIANCE_VALIDATION_PENDING error is not the current
outcome if validation subsequently succeeded. Some states pass too fast for a
live query; show audit history instead. Do not execute again or change network.

## 9. Independent blockchain receipt and transfer verification

**Prerequisite:** confirmed non-MOCK transaction with hash. Copy this payment's
tx_hash, not its internal UUID or a faucet funding hash:

~~~bash
read -r -p "Application transaction hash (0x...): " TX_HASH
export TX_HASH
node scripts/demo/verify-receipt.cjs --hash "$TX_HASH" --treasury "$DEMO_TREASURY" --beneficiary "$DEMO_BENEFICIARY" --amount 1 --confirmations 1
printf 'https://sepolia.basescan.org/tx/%s\n' "$TX_HASH"
~~~

**VERIFIED** requires read-only RPC evidence of:
chain 84532; successful receipt; correct hash/sender/USDC contract; matching
canonical block at query time; one official USDC Transfer for **1,000,000
smallest units (1 USDC)** to the exact beneficiary; required inclusion depth.
No signer is loaded and no transaction is submitted.
A pending receipt/failure is an explicit nonzero result, not permission to execute again.

Current default application confirmation requirement is 1 unless overridden.
Use `--confirmations N` for a configured stricter requirement; do not lower it
to force a pass. L2 depth is **not independent Ethereum finality verification**.
Printed raw fee components are not a substitute for the application's complete-fee gate.

Open the printed BaseScan link and verify:

1. Base Sepolia Testnet, Success, matching hash/block.

2. From = treasury; Interacted With (To) = official USDC contract.

3. ERC-20 Tokens Transferred: **1 USDC** from treasury to beneficiary.

4. Value **0 ETH** is correct: the payment asset was USDC, but ETH still paid fees.

5. Gas used/effective gas price, L2 and L1 fees. Gas limit is a ceiling.

6. Logs contain Transfer; sequencer confirmation alone is not Ethereum finality.

Rerun the balance command from section 4. With no concurrent movements, treasury
USDC decreases by 1 and ETH by actual network fees. A before snapshot and other
wallet activity must be considered before claiming exact balance deltas. The
exact receipt event is the evidence tying this transfer to these addresses.

## 10. Ledger, settlement and reconciliation

~~~bash
bash scripts/demo/db-check.sh ledger "$PAYMENT_ID"
bash scripts/demo/db-check.sh closure "$PAYMENT_ID"
~~~

[ledger.sql](../scripts/demo/ledger.sql) shows immutable journal headers/lines,
account codes and **per-journal, per-asset** debit-credit equality, plus settlement
and fee-journal links. Zero rows is not proof of balanced completion.
Never offset USD against USDC or ETH.

Expect approved wallet-flow postings: FUNDING, CONVERSION_USD, CONVERSION_USDC,
BLOCKCHAIN_SUBMISSION, BLOCKCHAIN_CONFIRMATION, NETWORK_FEE. Inspect actual
types/evidence: additional approved fee component/correction postings may exist.
[ADR-015](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md) is authoritative.
Funding/conversion is simulated; actual receipt-complete ETH fees are the network
expense. Provider quote $2.02 is not automatically debited or recognized as revenue.

[closure.sql](../scripts/demo/closure.sql) shows payment_closure, report IDs,
latest reconciliation document/findings, optional simulated payout and audit history.

**Gate:** payment RECONCILED; execution/chain CONFIRMED; complete fee evidence;
required journals POSTED and balanced per asset; closure COMPLETED; latest
reconciliation MATCHED with no unresolved findings. Historical earlier reports
can remain; inspect the latest. For USDC_WALLET, zero Visa payout rows is expected.
Execute remains disabled after completion, preventing another transfer.

### Optional bank/Visa branch: separate demonstration

Use a new instruction with configured bank beneficiary, permitted outcome and
simulated provider liquidity. Follow [payout runbook](31-SETTLEMENT-PAYOUT-RECONCILIATION.md)
and [bank seed](../scripts/seed-bank-demo.sql). Choose permitted
SIMULATED_BANK_USD/INR outcome, quote, two approvals and execution.
Use a new PAYMENT_ID with these same DB checks.
Expect visa_transaction SIMULATED/SUCCEEDED and matching payout journals/report.
This does not sell beneficiary's actual testnet tokens or prove real bank credit.
Do not reuse the completed wallet execution.

## 11. Export proof, stop, resume and diagnose

### 11.1 Export the complete private evidence package

~~~bash
mkdir -p ".local/demo-evidence/$PAYMENT_ID"
bash scripts/demo/db-check.sh all "$PAYMENT_ID" > ".local/demo-evidence/$PAYMENT_ID/database.txt"
node scripts/demo/verify-receipt.cjs --hash "$TX_HASH" --treasury "$DEMO_TREASURY" --beneficiary "$DEMO_BENEFICIARY" --amount 1 --confirmations 1 > ".local/demo-evidence/$PAYMENT_ID/rpc-receipt-check.json"
git rev-parse HEAD
docker inspect stablecoins-blockchain-service --format '{{.Image}}'
~~~

Check each exit status before accepting its output file. Use the stricter
confirmation count from section 9 if configured.
In StableFlow click **Export evidence**. In BaseScan expand details and use
**Ctrl+P -> Save as PDF**, including hash/status/Transfer/fees.
Browser downloads may land on Windows: manually collect them with the private
evidence package. A PDF is an explorer snapshot, not a signed receipt object.

Retain payment/correlation, review/version/actors, execution/hash/block,
modes/wallets/amount, timestamps, journal/report IDs and source/runtime image.
Do not publish private raw reports, passwords, tokens or keystores.
Actual successful historical run identifiers belong in the ignored handover,
not the shared generic instructions.

### 11.2 Stop application; retain data

**Prerequisite:** completed outcome, or unresolved authorized work explicitly
reviewed/recorded. Keep TESTNET exports active for consistent Compose resolution.

~~~bash
node --env-file=.env scripts/local-workbench.cjs stop --show-failures
docker compose --profile services ps --all
~~~

**Gate:** 9/9 STOPPED: React -> six backends -> LocalStack -> PostgreSQL.
Containers/volumes/payments retained. Status NOT_READY is expected while stopped.
Do not use down -v, remove volumes, reset balances, or edit financial state.
Retaining a container/volume does not guarantee LocalStack in-memory persistence.

App stop does not stop the Codespace. Stop any keep-alive terminal using Ctrl+C.
If also ending the host session, save evidence and stop app first, then:

~~~bash
gh codespace stop -c "$CODESPACE_NAME"
~~~

This disconnects the Codespace. Resume it before further terminal/UI work.

### 11.3 Resume after host restart / rebuild after code changes

1. Reconnect; repeat exports in section 2.

2. Start dependencies, inspect pending work (3.1).

3. Start/status (3.2); verify actual signer/network.

4. Run catalog, set PAYMENT_ID to saved completed payment and run all checks.

5. Show persisted history. Do not execute the completed payment again.

If pulled code/config needs forced recreation, review pending work, then:

~~~bash
node --env-file=.env scripts/local-workbench.cjs restart --show-failures
node --env-file=.env scripts/local-workbench.cjs status --show-failures
~~~

Start builds/starts and may reuse unchanged containers. Restart builds and
force-recreates six backends sequentially, ensures infrastructure and frontend.
Both resume workers; neither rolls back a payment or requests a new transfer.
Do not run lifecycle actions concurrently.

### 11.4 Only UI missing; all eight backend/infrastructure components healthy

~~~bash
mkdir -p .local/validation
node --env-file=.env -e '
const {startFrontend}=require("./scripts/local-frontend.cjs");
startFrontend({root:process.cwd(),logPath:".local/validation/frontend-only.log",paymentUrl:"http://127.0.0.1:8080"})
.then(console.log).catch(error=>{console.error(error.message);process.exitCode=1;});
'
node --env-file=.env scripts/local-workbench.cjs status --show-failures
~~~

This uses default payment port 8080. If overridden privately, supply the configured
loopback payment origin. Do not recreate healthy backend containers just for UI.

### 11.5 Stalled/failed/unknown execution

Run execution, ledger, closure and status first. If needed, decode JSON logs before
bounded redaction (Codespaces does not require rg):

~~~bash
docker compose logs --since 15m --no-color --tail 300 payment-service compliance-quote-service blockchain-service settlement-ledger-service reconciliation-service visa-simulator 2>&1 |
node --env-file=.env -e '
const fs=require("node:fs");
const {excerpt}=require("./scripts/failure-diagnostics.cjs");
const expanded=fs.readFileSync(0,"utf8").split("\n").map(line=>{
 const start=line.indexOf("{"); if(start<0)return line;
 try{const entry=JSON.parse(line.slice(start));return line.slice(0,start)+(entry.message||"")+"\n"+(entry.stack_trace||"");}
 catch{return line;}
}).join("\n");
console.log(excerpt(expanded).join("\n")||"No matching errors in this bounded log window.");
'
~~~

Review/redact before sharing. No excerpts is not proof of no failure.
Inspect current rows, queue/DLQ counts and report log paths. Do not receive/delete
messages, purge queues, force states, mutate journals or retry unknown submissions.
The obsolete Compose version warning alone is nonblocking.

Optional second-terminal foreground activity helper:

~~~bash
node scripts/codespaces-demo-keepalive.cjs
~~~

It prints activity every 28 minutes; no guarantee against host/org timeouts,
no application health check, no approval-expiry extension. Compute usage continues.

## 12. Presenter checklist and validating these helpers

| Stage | What to show |
|---|---|
| Start | 9/9 ready, actual signer and Base Sepolia mode |
| Create | Immutable instruction, correct amount/recipient/ceiling and correlation |
| Compliance | Simulated checks and append-only status history |
| Prepare | READY snapshots, eligible provider, LIVE treasury, unexpired review |
| Each approval | QUOTE_ACCEPTED and APPROVED rows for distinct actors, same version |
| Execute | One execution, validation, reservation, submission hash |
| Public chain | Successful official-token Transfer for exact sender/recipient/amount |
| Ledger | POSTED lines balanced per asset; complete actual ETH fee evidence |
| Close | Latest MATCHED reconciliation, closure COMPLETED, payment RECONCILED |
| Stop/resume | 9/9 STOPPED; saved history still present after controlled resume |

All presenter SQL is versioned in [scripts/demo](../scripts/demo/). These are
**SQL scripts, not stored procedures**; they create no database objects.
Wrapper validates payment/customer identifiers and passes quoted psql variables.
Every file runs ON_ERROR_STOP in a read-only transaction, with IST display timezone.
Phase names match filenames, so the exact queries can be opened during presentation.
All snapshots use committed data; no artificial delay or automatic mutation is added.

For developer/owner verification against initialized Codespaces PostgreSQL, use
an existing payment ID, then:

~~~bash
bash -n scripts/demo/db-check.sh
bash scripts/demo/db-check.sh catalog
bash scripts/demo/db-check.sh pending
bash scripts/demo/db-check.sh all "$PAYMENT_ID"
node --test scripts/demo/verify-receipt.test.cjs
~~~

Require zero exit codes AND expected postconditions. No rows means missing
evidence, not successful completion. Missing tables/columns mean migrations,
schema drift or service readiness needs correction. Receipt fixture tests alone
do not establish a live transaction; section 9 checks the actual run's hash.

Existing legacy [inspect-payment.sql](../scripts/demo/inspect-payment.sql) remains
available as a short execution/chain/outbox view. The phased files add the full
presentation. Keep queries aligned with owner migrations after future changes.

Separate gates remain: runtime AI, primary amount, optional simulated payout,
broader recovery/database tests, AWS deployment and future Besu integration.
