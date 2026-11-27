# Besu application integration and operating commands

The application API and private screens are staged behind default-off switches.
The immutable private instruction-creation lock remains in place: **BESU_PAYMENT_ACCEPTANCE_PENDING**.
This guide does not establish acceptance. Current closure criteria remain only in
[B07-B10](../../docs/40-IMPLEMENTATION-BACKLOG.md). Base Sepolia remains the public default.

The application overlay does not deploy a contract, initialize/reset a chain, create wallets,
change passwords or enable customer Besu selection. Chain setup remains a separate project.
Private setup uses the existing dedicated encrypted Besu wallets. Keep the public wallets too.

## Codespaces prerequisites

Use Bash from the repository root, a non-root Codespaces user, Node 22.18+ and Docker Compose v2.
Bring this revision to Codespaces through the normal reviewed publication process first.
Complete [dedicated credentials](CREDENTIAL-SETUP.md) and the separate [chain setup](README.md),
including explicit genesis pin, DEMOUSD deployment and protected manifest. Never repeat init
against existing state. Have `.env` and the existing protected public treasury/password files
when retaining REAL_TESTNET. Do not paste password contents into a terminal command or chat.

The five-node chain and six services need sufficient Codespaces memory/disk; the setup CLI
checks infrastructure prerequisites. Local source tests do not establish resource adequacy.

```bash
cd /workspaces/StableCoins
node --version
docker compose version
npm --prefix infrastructure/besu ci
npm --prefix frontend ci
node infrastructure/besu/cli.cjs status
node infrastructure/besu/cli.cjs preflight 1
```

`preflight` must identify the already deployed private chain/token and sufficient balances.
A failing check is a reason to inspect preserved setup state, not to initialize again.

## Database evidence before private activation

Run the strict database verification in [the Codespaces validation order](../../docs/42-NETWORK-COMPARISON-AND-ISSUER-SCOPE.md#github---codespaces-validation-order)
after installing its prerequisites. The gate requires fresh integration reports for all nine
Besu persistence suites alongside the existing application/public suites and Visa wallet
persistence. The report identifies missing or incomplete suites; a unit-only success is
insufficient. Ordinary Maven verify includes the private liquidity persistence suite.
These are disposable PostgreSQL tests, separate from live private/public chain acceptance.
The private instruction lock remains in place after a database pass.

## Prepare a persistent variable file

Choose the public mode explicitly. For the existing Base Sepolia application, use `testnet`:

```bash
node infrastructure/besu/application.cjs prepare testnet 11111111-1111-1111-1111-111111111111
node infrastructure/besu/application.cjs config
```

For a deliberately MOCK public rehearsal, substitute `mock` in the prepare command.
The helper never infers MOCK as a fallback. It writes ignored owner-only
`.local/besu-network/application.env` and `application.properties`. The environment file
holds public identity, protected file references and **false** private capability switches,
not password contents. UID/GID are recomputed in Codespaces. It verifies the deployed identity
and expected wallet-file address; preparation does not unlock a wallet or submit a transaction.
Exact reruns are idempotent; differing existing files are preserved and cause a structured failure.
`config` validates Compose without printing the resolved secret-bearing configuration.

The generated registry binds token and native gas to the same private genesis. It supplies
explicitly simulated quote terms (0.10 USD budgeting fee); these are not actual gas fees or a
redemption/exchange promise. It does not amend customer policy or identity membership. The hard
private creation lock still applies even if the registry entry is enabled for preparing historical
engineering fixtures. Do not edit policies or seed instructions to bypass that lock for a demo.

## Build, start and inspect

First inspect pending work using the central SOP. Starting services can resume already-authorized
public work and any deliberately enabled private workers. This helper does not purge queues.

```bash
node infrastructure/besu/application.cjs build
node infrastructure/besu/application.cjs start
node infrastructure/besu/application.cjs status
node --env-file=.env scripts/local-workbench.cjs status --show-failures
```

`build` builds the existing six application images. `start` starts only the existing chain,
rechecks its deployed identity, validates the explicit Compose combination, starts the existing
services and starts/reuses the repository-owned Vite frontend. It does not install dependencies.
The application overlay attaches only blockchain-service to `stablecoins-besu_besu-private` and
uses `http://besu-rpc:8545`. The host endpoint remains `http://127.0.0.1:8547`; keep it Private.
The existing public signer overlay is retained when the prepared mode is `testnet`.

Open port **5173** using the Codespaces Ports tab. `status` reports containers plus frontend
readiness; the existing workbench status checks application/proxy health. Neither proves a private
payment or migration rollback/concurrency acceptance. No new Besu selection should be available yet.

## Stop and resume without resetting

```bash
node infrastructure/besu/application.cjs stop
node infrastructure/besu/cli.cjs stop
```

Stop halts the repository-owned frontend and six business containers. It leaves PostgreSQL,
LocalStack, chain files, volumes, financial records, saved signatures and wallets intact.
The second command halts the chain after application workers stop. It does not delete data.
If an unrecognized frontend owns the port, it is not killed; backends are still stopped and the
helper reports that frontend shutdown needs review. Do not use `down -v` for normal operation.

```bash
node infrastructure/besu/application.cjs resume
node infrastructure/besu/application.cjs status
node --env-file=.env scripts/local-workbench.cjs status --show-failures
```

Resume verifies the same deployed identity and suppresses **private first-submission** and
**new private reservation/funding** workers for that invocation, regardless of saved true values.
Other private monitoring/accounting/closure workers run only if explicitly configured; generated
files leave all of them false. Saved SIGNED transactions are not automatically broadcast by recovery.
Public workers retain their existing configuration: inspect public queues before resuming.
Returning to new private work later requires a deliberate `start` with accepted configuration;
this guide does not authorize enabling it or removing the instruction lock.

## Authenticated API and screen boundary

`BESU_APPLICATION_API_ENABLED` defaults false. When deliberately enabled for integration validation,
all endpoints below require the existing verified JWT and server-owned customer membership:

| Endpoint under `/api/v1/payments/{paymentId}` | Authority and behavior |
|---|---|
| GET `/private-settlement` | Operator; customer-bound selected identity, preparation/claim references, submission status, QBFT receipt, closure/failure/recovery acknowledgements. No signed bytes or secret paths. |
| POST `/settlement-observation-preparations` | Existing separate observation switch and operator; UUID Idempotency-Key; asynchronous fresh observation. |
| POST `/private-settlement/recommendations` | Operator; UUID Idempotency-Key and preparationId; server-owned quote plus versioned review in one transaction. |
| POST `/recommendations/{id}/quote-acceptance`, `/approvals`, `/rejection` | Existing independent approver APIs; exact private quote/version; browser roles cannot grant permission. |
| POST `/private-settlement/execution` | Operator; UUID Idempotency-Key and exact recommendation/quote versions; captures one canonical claim. Workers require all existing fresh authority/capacity checks. |
| POST `/private-settlement/recovery-authorization` | Approver; executionId and current sourceHash; independent from funding operator. Existing owner validates conclusive accounted revert; no automatic approval. |

Private screens branch on the persisted private identity, not a browser label. They show DEMOUSD,
BESU_GAS and PRIVATE_DEMO across review, treasury, execution, receipt and journals. They never emit
a BaseScan link. Missing private evidence disables commands. A retained claim disables second execution.
Recovery grants only in-flight CONTROL release; it never refunds USD, removes gas expense or turns
FAILED into RECONCILED. Customer new-payment selection stays unavailable pending B07-B10 acceptance.

The application helper never auto-enables switches. Reservation/funding, first submission,
receipt polling, accounting, successful closure and reverted recovery remain separately controlled.
This separation allows inspection of pinned work while new spending is disabled.
