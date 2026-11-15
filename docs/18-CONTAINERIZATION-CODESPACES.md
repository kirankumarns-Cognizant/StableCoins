# Containerization and GitHub Codespaces

## Current execution plan

The current Windows machine cannot run containers. Use GitHub Codespaces for
Docker Compose and real PostgreSQL Testcontainers checks. The existing
`.devcontainer/devcontainer.json` provides Java 21, Node 22, Maven, AWS/GitHub CLI
and Docker-in-Docker. This is a development environment, not the AWS deployment.
No Codespace or Docker image has been started by the current local verification.

All six application Dockerfiles use Java 21 multi-stage builds and a non-root
runtime. They now copy every reactor module POM and the platform-security library
sources so Maven can resolve the shared dependency. Only the application's JAR
enters its runtime image. There are still six deployable services.

## GitHub preparation and Codespace creation

1. Push the intended source branch to your GitHub repository. Include `.devcontainer`,
   `.gitattributes`, all service/module POMs and sources, shared configuration,
   Dockerfiles, Compose, frontend, infrastructure, documentation and accepted ADRs.
2. Keep actual `.env`, `.local`, wallets/private keys, connection override files,
   credentials and generated build caches out of the commit. Do not publish a
   Codespace secret in a screenshot or command transcript.
3. In GitHub use **Code > Codespaces > Create codespace** on that branch. The
   repository post-create script verifies the tools, attempts a Maven dependency
   prefetch, installs frontend/CDK dependencies and creates `.env` from the sample
   only when absent. Prefetch is best effort; the real build is authoritative.
4. Keep forwarded ports private. Use Codespaces Secrets for external development
   credentials and configure provider references through the shared connections
   mechanism. No testnet/model/AWS action is enabled merely by bootstrap.
5. If configuration changed on an existing Codespace, rebuild its development
   container before relying on the new toolchain/environment.

The container no longer globally sets placeholder AWS credentials or a host-only
LocalStack URL. Local SDK clients use the shared local defaults when an explicit
local endpoint is selected; external AWS credentials remain a deliberate choice.

## Verify the remote toolchain

Run from the repository root inside the Codespace:

```bash
java -version
node --version
docker version
docker compose version
chmod +x services/mvnw
./services/mvnw -f services/pom.xml --version
```

Require Java 21 and a reachable Docker daemon before interpreting database tests.
A Docker client installed without a daemon is insufficient. If Docker is not
reachable, fix the Docker-in-Docker environment and rerun; a skipped test is not
PostgreSQL evidence.

## Compile and execute checks

```bash
./services/mvnw -f services/pom.xml verify
node --test services/config/read-connections.test.cjs
npm --prefix frontend ci
npm --prefix frontend run build
npm --prefix infrastructure ci
npm --prefix infrastructure run build
npm --prefix infrastructure test -- --runInBand
```

For a selected service include `-am` because of the shared library:

```bash
./services/mvnw -f services/pom.xml -pl settlement-ledger-service -am verify
```

With working Docker, Testcontainers creates its own isolated PostgreSQL databases;
Compose infrastructure need not already be running for these tests. Review both
Surefire and Failsafe summaries and actual skipped cases. PostgreSQL cases that
skipped locally must really execute remotely. The separately gated real-model
case may remain skipped without an explicitly configured provider. Real testnet
transactions require the separate deliberate setup in
[the testnet guide](20-BLOCKCHAIN-TESTNET-SETUP.md).

## Compose environment and startup

The sample `.env` keeps `LOCALSTACK_URL` blank. Compose supplies
`http://localstack:4566` to application containers. `http://localhost:4566` is for
Java processes on the Codespace host; it is incorrect inside application containers.
Use a process-scoped setting when running Java on the host, rather than exporting
it globally before starting Compose. Shared external connection files must also
be container-resolvable. See [shared configuration](23-SHARED-CONNECTION-CONFIGURATION.md).

```bash
# Infrastructure only, in the background

docker compose up -d

# Build and start the six applications

docker compose --profile services up -d --build

# Check application/container startup without printing environment values

docker compose --profile services ps
curl --fail http://localhost:8080/health
curl --fail http://localhost:8081/health
curl --fail http://localhost:8082/health
curl --fail http://localhost:8083/health
curl --fail http://localhost:8084/health
curl --fail http://localhost:8086/health
```

These are intended execution commands; a fresh remote full-stack run still needs
to be performed and recorded. Investigate migration ordering and startup errors
rather than treating a healthy infrastructure container as a working payment.
Backend protected APIs require configured identity and membership; health success
does not establish a connected end-to-end demo. The default Compose mode is MOCK,
testnet false and runtime inference disabled. Blockchain execution/manual ledger
write gates remain explicit and off by default.

| Component | Host port |
|---|---|
| payment-service | 8080 |
| compliance-quote-service | 8081 |
| blockchain-service | 8082 |
| settlement-ledger-service | 8083 |
| reconciliation-service | 8084 |
| visa-simulator | 8086 |
| PostgreSQL | 5432 |
| LocalStack | 4566 |
| React/Vite development server | 5173 |

For an individual image build use `services/` as the context:

```bash
docker build -f services/payment-service/Dockerfile -t stablecoins/payment-service:dev services
```

Frontend development uses `npm --prefix frontend run dev -- --host 0.0.0.0`.
The connected React workspace is implemented; its APIs require real configured
identity and the running backend. The separate HTML prototype remains illustrative.

Stop the stack with `docker compose --profile services down`; this preserves named
data volumes. A deliberate demo reset may use `down -v`, which deletes local demo
database volumes. Record and export non-sensitive evidence first. Never reset a
wallet nonce/submission claim to retry an uncertain public-testnet transfer.

## Local Windows alternative without admin installation

A ZIP containing the Docker CLI or an application image does not provide the
Linux container engine/VM required to run this stack. Docker documents a current
per-user Desktop installation that does not require administrator privileges for
installation/update, provided the required backend is available. Enabling WSL 2
for the first time can require administrator rights; this is not a portable ZIP
workaround for a machine without those prerequisites. No local installation was
attempted. Codespaces remains the intended container execution path here.

Sources checked 2026-11-14: [Docker Windows installation](https://docs.docker.com/desktop/setup/install/windows-install/),
[Docker permission requirements](https://docs.docker.com/desktop/setup/install/windows-permission-requirements/),
[GitHub development containers](https://docs.github.com/en/codespaces/setting-up-your-project-for-codespaces/adding-a-dev-container-configuration/introduction-to-dev-containers).

## Demonstration and remaining verification

Use the continuously maintained
[full feature execution runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md), not only this
container startup guide. It covers prior/current/future features F01-F10, real
versus simulated evidence, authentication, runtime inference, approval, treasury,
chain, ledger, payout, reconciliation and failure cases. Record the code revision,
actual commands/results, migrations and screenshots after execution. Current local
Docker/PostgreSQL skips, live Cognito/model access, AWS deployment and the full
public-testnet payment remain unverified; none are implied by these instructions.

## Step 7 build context and opt-in execution

All six service Dockerfiles include the platform-flow reactor POM; consumers also
copy its sources. This preserves six deployable images. The current Maven build
packages both shared libraries, but no container image has been built locally.
After the existing identity/database/LocalStack setup, follow the
[wallet runner and fictional seed procedure](30-PAYMENT-FLOW-INTEGRATION.md#codespaces-demonstration).
Use the [presenter walkthrough](29-PROTOTYPE-EXECUTION-RUNBOOK.md#opening-narrative-the-complete-payment-story)
for the required overall narrative, screen/role/context/action/outcome format;
those connected React scenes are now implemented in step 9; live acceptance remains pending.

## Full lifecycle after step 8

Use [the cumulative step-8 setup and demonstration](31-SETTLEMENT-PAYOUT-RECONCILIATION.md#codespaces-demonstration-setup-and-screen-by-screen-narrative)
after the existing container/identity/wallet setup. The full runner is
scripts/run-payment-lifecycle.cjs; DEMO_OUTCOME selects wallet or labeled simulated
USD/INR bank delivery. Bank candidates require explicit internal simulated prefund.
All owner migrations must have completed before starting a new payment.
Local Docker is still unavailable; no portable Docker zip replaces a functioning
container runtime/VM. Codespaces remains the intended container execution path.
GitHub destination is to be provided by the owner; no project push was attempted.

## Connected UI - step 9

After the cumulative six-service setup, run the frontend on private forwarded
port 5173. Set the exact forwarded hostname in FRONTEND_DEV_ALLOWED_HOSTS and its
HTTPS origin plus `/auth/callback` as IDENTITY_REDIRECT_URI; register the same
callback with the public identity client. Existing PAYMENT_SERVICE_URL supplies
the development proxy. See [step-9 setup](32-CONNECTED-REACT-WORKSPACE.md#codespaces-execution)
and [exact role/screen/click demonstration](29-PROTOTYPE-EXECUTION-RUNBOOK.md).
No local Docker installation is needed for static UI checks. Database/container
and actual six-service integration still require Codespaces or another Docker host.

## Local-first startup and verification

Follow [the staged local guide](33-LOCAL-FIRST-VALIDATION.md) before AWS deployment.
The helper reuses this Compose stack, retains volumes and records prerequisites,
fresh tests and health/access probes separately from full payment acceptance.
All published Compose ports now bind to loopback. Dedicated local shared-schema
bootstrap enables owner histories at version 0; the central production default is off.
