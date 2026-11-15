# Shared connection configuration

The canonical connection file is [`services/config/connections.properties`](../services/config/connections.properties).
It is packaged, without Maven filtering, into each of the six Spring Boot jars.
Vite and CDK read the same source file using the small Node reader next to it.
This is configuration externalization within the existing architecture; no additional service or provider is introduced.

## What is connected today

| System | Settings | Behavior |
| --- | --- | --- |
| PostgreSQL / Aurora | `DB_URL` or `DB_HOST`, `DB_PORT`, `DB_NAME`; `DB_USER` / `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | Existing database-backed services consume these through Spring datasource configuration. |
| EventBridge / SQS | `AWS_REGION`, `EVENTBRIDGE_BUS_NAME`, `SQS_*_QUEUE_NAME`, `SQS_*_DLQ_NAME` | Publishers, listeners, local queue creation and existing CDK resources share names. CDK currently provisions only payment/compliance queues and their DLQs. |
| LocalStack | `LOCALSTACK_URL`, `LOCALSTACK_ACCOUNT_ID`, local AWS credential environment variables | Explicit endpoint override; local profile supplies credentials to async listeners too. Local initialization refuses to create queues when the endpoint is blank. |
| AWS | Blank `LOCALSTACK_URL`; standard SDK credential chain | Clients use the default credential chain, including an ECS task role. Dummy local keys are not forced onto AWS clients. Remove local test credentials when running against AWS. |
| Blockchain RPC | `BLOCKCHAIN_RPC_URL`, network / chain ID / USDC contract / explorer / confirmations and RPC timeout variables | Existing adapter uses the configured RPC with bounded connect/read timeouts. Transport connection retries are disabled to avoid repeating transaction submission. RPC URL is not logged. |
| Browser | `PUBLIC_API_BASE_URL` and explicitly selected public identity fields | `frontend/src/config/connections.ts` exports typed settings. Vite includes only the allowlist in the browser build. The current React starter does not yet call payment APIs or authenticate. |

A `local` blockchain profile deliberately retains MOCK mode. To run real testnet work, activate `testnet` after `local` (or use `testnet` with correctly injected database/messaging settings), and explicitly set both `BLOCKCHAIN_MODE=REAL_TESTNET` and `TESTNET_ENABLED=true`. Selecting real mode requires a nonblank RPC URL. Unit tests do not submit transactions.

Base Sepolia is the default. Ethereum Sepolia requires replacing the complete primary network tuple, including RPC, chain ID and Circle token contract. Fallback metadata in the example environment is reserved documentation; there is no automatic network switch or retry of uncertain submissions.

## Settings prepared for later integrations

These entries are configuration contracts, not working integrations:

- `connections.identity.*`: issuer URI, JWKS URI, audience, public client ID, authorization/token URIs, redirect URI and scopes. Cognito remains the accepted AWS provider under ADR-009. Generic OIDC endpoint settings prepare customer-specific deployments; replacing the accepted provider still requires an architecture decision. Existing identity-protected intent operations remain closed until authentication and authenticated customer mapping are implemented.
- `connections.ai.*`: configurable runtime model/provider/endpoint/authentication settings, implemented in step 6 with bounded read tools and secret resolution. See [runtime setup](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md); live provider access remains unverified.
- `connections.services.*` and `connections.visa.base-url`: destinations for forthcoming internal HTTP clients. They do not bypass event-driven processing or implement payout. Visa remains simulated, with no switch to a real Visa adapter.
- `blockchain.wallet-secret-name`: secret reference for the planned Secrets Manager signing integration. Current local testnet signing still reads `BLOCKCHAIN_WALLET_PRIVATE_KEY` from the environment. Do not put the private key in any properties file. Managed Secrets Manager/KMS retrieval remains pending.

## Configuring a deployment

Change a non-secret default in the shared file, or inject the documented environment variables at startup. Java reads process environment variables, not `.env` automatically.

For example, in PowerShell before starting a service:

```powershell
$env:DB_HOST = 'customer-aurora.example.internal'
$env:DB_NAME = 'stablecoin_poc'
$env:IDENTITY_ISSUER_URI = 'https://identity.example.com/customer-pool'
$env:IDENTITY_CLIENT_ID = 'customer-public-client'
$env:EVENTBRIDGE_BUS_NAME = 'customer-payment-bus'
```

Database passwords, RPC credentials, signing keys and future model keys must be injected securely. In AWS, use Secrets Manager/KMS and ECS secret injection with task roles. The shared file contains names and placeholders, never actual credentials. It is a backend file and must not be served as a browser asset.

For an external backend override file:

```powershell
$env:CONNECTIONS_CONFIG = 'file:C:/secure-config/customer-connections.properties'
```

The packaged file is loaded first; the supplied external file can replace selected properties. A deliberately specified missing file fails startup. With no setting, an optional `./config/connections-local.properties` is checked relative to the service working directory; this local override filename is gitignored. Spring's higher-priority process environment / command-line property overrides still apply. Use the documented uppercase aliases when overriding placeholders; use direct canonical Spring property names for an override that must win regardless of a placeholder alias.

Future React API/auth clients import `connections` from `src/config/connections.ts`; current starter screens have no backend connections. The offline `prototype/` remains entirely illustrative and does not connect to these systems.

Frontend settings are read at Vite startup/build time. Restart the dev server or rebuild after a change. Vite reads the repository `.env` using an explicit public allowlist; it never publishes the full backend configuration. Backend external override files do not configure Vite or CDK: use the shared source or the corresponding environment variables for those tools. CDK reads process environment at synthesis time; synthesize/deploy and runtime settings must agree. No deployment occurs automatically.

## Local Docker Compose

Compose mounts the shared properties file read-only into all six service containers. It forwards an explicit allowlist of connection variables from the Compose environment, keeping secrets out of images. The repository `.env` can supply Compose values; the full file is not injected into every container. The local signing-key variable is passed only to blockchain-service. Set `CONNECTIONS_CONFIG_FILE` to mount another host-side properties file. Unset optional variables are omitted, preserving the shared-file defaults, following [Compose environment semantics](https://docs.docker.com/reference/compose-file/services/#environment). Container DNS defaults use `postgres`, `localstack`, and service names; service destination URLs can be overridden with the corresponding environment variables.

Keep `LOCALSTACK_URL` unset in `.env` for Compose's `http://localstack:4566` default, or explicitly set that container-resolvable value. `http://localhost:4566` is appropriate for Java processes on the host, not service containers. Database host/port and service ports also differ between host and container: container listening ports remain fixed; Compose's `*_SERVICE_PORT` settings select published host ports. The local Compose definition deliberately pins MOCK/false; enabling testnet requires a deliberate Compose override of profile and both flags.

## Verification and remaining work

Configuration tests load the actual Spring imports under local, aws-dev and testnet profiles, exercise nested database placeholders and compatibility aliases, verify external-file loading and missing-file failure, and check real SDK client credential selection and local publisher destinations without network requests. Node tests verify shared resolution and secret exclusion from browser settings. CDK tests verify configured bus and queue names.

Real database migration/integration tests, deployed AWS connectivity, real identity authentication, model invocation, managed signer secret retrieval, future HTTP clients, and public-testnet execution remain separate work. Changing a connection setting is not evidence that its destination has been reached.

Validation on 2026-11-14: all six Java services compiled and packaged with 141 unit/HTTP/configuration tests passing; the shared file was verified in every jar. React production build and four Node configuration tests passed. CDK tests also cover the six Compose service imports, mounts, mock flags, ports and signing-key scope. Docker is unavailable here, so Compose startup and live destination connectivity were not exercised.

The step 3 increment also externalizes server-owned deterministic assessment
thresholds, history/freshness requirements and input limits through the same file
and Compose allowlist. See [signal assessment configuration](24-SIGNAL-EVIDENCE-AND-ROUTE-ASSESSMENT.md).


## Treasury and simulated provider controls

Step 4 adds reservation/readiness TTLs, gas multipliers and additional test ETH,
an execution API gate (default false), and explicitly allowlisted simulated
provider IDs/TTL to the same canonical properties and Compose allowlist. These
remain server-only. Visa simulator now uses shared PostgreSQL for its owned
liquidity/funding/reservation/audit records, with Flyway history isolated from
other services. See [step 4 contracts and settings](25-TREASURY-AND-EXECUTION-READINESS.md).

## Payment identity and review notification configuration

Step 5 activates the existing identity settings in payment-service, with explicit
HTTPS JWKS/client/issuer configuration and bounded IDENTITY_HTTP_TIMEOUT_MS.
Its APIs require verified Cognito access tokens and server-managed membership/
roles; no anonymous local bypass is added. Earlier descriptions of identity as
prepared/inactive are historical. Other services and the React starter do not
acquire this integration automatically. Shared configuration also supplies
SQS_ROUTING_DECISIONS_QUEUE_NAME and SQS_ROUTING_DECISIONS_DLQ_NAME. See
[step 5 configuration and boundaries](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md).


Step 6 implements the AI connection contract in payment-service: configurable
OPENAI/BEDROCK Responses endpoints, model, Region, bearer secret reference or IAM
role authentication, plus explicit agent/customer/resource/cost limits. See
[runtime agent settings](27-RUNTIME-INTENT-AND-ROUTING-AGENT.md). No live connection
is implied by configuration or unit-test success.


## Shared backend identity library

The six backend applications now depend on the `platform-security` Java library;
it reuses the payment service's Cognito claim checks and membership contract.
`IDENTITY_SERVICE_CLIENT_ID` selects a distinct confidential internal caller and
`IDENTITY_SERVICE_SCOPE` defaults to `stablecoins/internal`. Both remain server-only
and have Compose aliases. A browser client's scope cannot grant internal command
authority. Missing identity fails closed; health checks remain public. Identity
provisioning and outbound service-token acquisition are still pending.

Supply identical server-owned subject/customer/role membership configuration to
services that serve customer evidence using the existing external configuration
file mechanism. Payment ownership reads use the shared schema without writes to
payment-owned tables, under ADR-007. See the
[shared access design](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md#shared-identity-and-evidence-access-design)
and [verification/demo steps](29-PROTOTYPE-EXECUTION-RUNBOOK.md#shared-access-control-verification-f10).
The earlier step-5 note limiting integration to payment-service is historical.

## Step 7 private workflow and simulation configuration

The same canonical file and explicit Compose allowlist now supply these settings.
None are browser configuration. Changing them requires service restart/review.

| Environment variable | Default / purpose |
|---|---|
| PAYMENT_FLOW_ENABLED | false; creates the owner workflow consumers, coordinator and APIs |
| PAYMENT_EXECUTION_ENABLED | false; permits new reservation/funding/submission; keep flow enabled to continue monitoring signed work after disabling new execution |
| PAYMENT_FLOW_POLL_MS | 3000; coordinator and receipt scheduling |
| PAYMENT_FLOW_PREPARATION_TIMEOUT_SECONDS | 120; missing evidence becomes REVIEW_REQUIRED |
| SCHEDULING_POOL_SIZE | 4; receipt polling does not monopolize the outbox scheduler |
| FLOW_SIMULATION_VERSION | simulation-v1; immutable captured assumption version |
| FLOW_SIMULATION_PROVIDER_IDS | SIMULATED-PROVIDER-A,SIMULATED-PROVIDER-B; intersected with customer permission |
| FLOW_QUOTE_TTL | PT5M; bounded by instruction deadline |
| FLOW_SIMULATED_FEE_USD | 2.00 platform-fee assumption |
| FLOW_SIMULATED_COMPLETED / FLOW_SIMULATED_ATTEMPTS | 990 / 1000; history assumption |
| FLOW_SIMULATED_LATENCY_MS / FLOW_SIMULATED_FRAUD_SCORE | 120000 / 10; latency/risk assumptions |
| FLOW_SIMULATED_AVAILABLE | true; simulated provider availability |
| FLOW_SIMULATED_ETH_USD_BUDGET | 2000; simulated gas-budget conversion, not a market price feed |
| FLOW_PROVIDER_B_FEE_USD / FLOW_PROVIDER_B_LATENCY_MS / FLOW_PROVIDER_B_AVAILABLE | 5.00 / 60000 / true; second candidate overrides |
| SQS_FLOW_QUOTE_QUEUE_NAME / SQS_FLOW_QUOTE_DLQ_NAME | stablecoin-flow-quote-queue / stablecoin-flow-quote-dlq |
| SQS_FLOW_TREASURY_QUEUE_NAME / SQS_FLOW_TREASURY_DLQ_NAME | stablecoin-flow-treasury-queue / stablecoin-flow-treasury-dlq |
| SQS_FLOW_VALIDATION_QUEUE_NAME / SQS_FLOW_VALIDATION_DLQ_NAME | stablecoin-flow-validation-queue / stablecoin-flow-validation-dlq |
| SQS_FLOW_RESERVATION_QUEUE_NAME / SQS_FLOW_RESERVATION_DLQ_NAME | stablecoin-flow-reservation-queue / stablecoin-flow-reservation-dlq |
| SQS_FLOW_SUBMISSION_QUEUE_NAME / SQS_FLOW_SUBMISSION_DLQ_NAME | stablecoin-flow-submission-queue / stablecoin-flow-submission-dlq |

For a deliberate interactive demo, BLOCKCHAIN_READINESS_TTL=PT5M can provide time
for independent human review within existing evidence-age limits. It is not a
permission to execute stale evidence. Default readiness remains short-lived.
Default Compose still pins MOCK/false regardless of a testnet environment flag;
real chain work requires an explicit profile/flag override and funded test wallet.

Per-provider properties under `app.flow.simulation.providers.<ID>.*` override the
global assumptions in the external connection file. Immutable capture and later
validation prevent changed simulation values from silently repricing an accepted
instruction. Provider A is cheaper; provider B is faster by default. These are
controlled scenarios, not measured market/rail performance. Central service URLs
are also reused by the Node wallet runner. See [step-7 procedure](30-PAYMENT-FLOW-INTEGRATION.md).

## Step 8 configuration additions

Central private settings now include FLOW_SIMULATED_FX_USD (1.00),
FLOW_SIMULATED_FX_INR (illustrative 83.00), VISA_SIMULATOR_PAYOUT_SCENARIO
(SUCCEEDED/DECLINED/TIMEOUT) and VISA_SIMULATOR_OPERATIONS_ENABLED (false).
Compose uses an explicit private allowlist. Browser configuration excludes them.
Five configurable SQS_*_QUEUE_NAME / SQS_*_DLQ_NAME pairs cover RECEIPT_OBSERVED,
SETTLEMENT_REQUESTED, VISA_PAYOUT, LEDGER_PAYOUT and RECONCILIATION_REQUESTED.
Connection destinations still use the common service URLs and AWS endpoint/region.
See [lifecycle configuration](31-SETTLEMENT-PAYOUT-RECONCILIATION.md#configuration-and-delivery).

## Step 9 public identity and development proxy

The browser allowlist now includes the configured public authorization/token URLs
as well as existing client/issuer/redirect/scopes and API base. Identity endpoints
already defined under `connections.identity` are reused; no new secret setting is
introduced. `frontend.dev.allowed-hosts` / `FRONTEND_DEV_ALLOWED_HOSTS` is a
server-only comma-separated exact Vite host allowlist for Codespaces. Vite's
same-origin `/api/v1` proxy uses existing PAYMENT_SERVICE_URL; it is development-only.
Public values enter the build bundle and must contain no secrets. See
[configuration and PKCE behavior](32-CONNECTED-REACT-WORKSPACE.md#identity-configuration-and-hosting).

The public allowlist also exposes the existing blockchain chain ID and explorer
base URL. Explorer links require a matching supported testnet ID, canonical hash
and configured HTTPS URL without credentials; RPC URLs remain private.


## Local validation controls

The [local-first helper](33-LOCAL-FIRST-VALIDATION.md) reuses existing host service
URLs for health/access probes. `LOCAL_VALIDATION_HEALTH_TIMEOUT_MS` controls their
bounded timeout (10000 default, 100-30000 accepted). `LOCAL_SCHEMA_BOOTSTRAP`
controls owner Flyway baseline-on-migrate and defaults false centrally; only the
dedicated local Compose stack explicitly enables it, at baseline version 0 so V1
runs. These server/tool settings never enter `publicConnections`. Node does not
implicitly load `.env`; use its explicit `--env-file=.env` option when matching
Compose overrides for startup/probes. Do not print resolved Compose configuration
or connection objects containing secrets.
