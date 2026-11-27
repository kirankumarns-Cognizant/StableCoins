# Besu private-demo setup

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


This isolated component implements ADR-016's local chain/bootstrap foundation.
It is **not yet wired into payment execution or automatic orchestration**.
Do not point the existing public-testnet configuration at Besu. The existing
Base Sepolia application path, public wallets and completed payments are preserved.

## Component boundary

- This folder owns the Compose topology, genesis/node setup, development token,
  explicit deployment, read-only preflight and setup tests.
- The application integration belongs in a dedicated `besu` Java package inside
  the existing blockchain-service, behind its adapter boundary. It adds no seventh
  application microservice.
- The orchestrator will consume network health, permission readiness, token/gas
  liquidity, fee estimates and observed confirmation/reconciliation latency.
  Policy must first permit the selected network, asset and delivery outcome.
- The network is initialized and started beforehand. Routing never deploys
  validators or tokens. A signed/pending/unknown payment never falls back to
  another chain.

## Current implementation and gates

The private payment code now includes typed pinned identities, isolated infrastructure,
reservations, encrypted signing/submission recovery, receipt verification, per-asset
accounting/reconciliation and staged customer UI/API plus lifecycle tooling.
These are code-integrated increments, not accepted runtime behavior. Private switches
remain default-off and new customer private instructions are hard-gated until B01-B10
acceptance. Automatic selection B11 and issuer lifecycle I01-I10 are separate scope.
See [application commands](APPLICATION.md), [dated activity evidence](../../docs/36-POC-ACTIVITY-STATUS.md)
and [the current backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).

## Prerequisites

Use Bash at the repository root in Linux Codespaces/WSL with Docker Compose,
Node 22+ and Java 21. This Windows development host does not establish a running
Docker chain. Keep the Codespaces 8547 port **Private**; it is bound to loopback.
The isolated bridge uses 172.30.76.0/24; resolve an overlap before starting.

The provisional setup floor is 12 GiB Docker memory and 6 GiB free disk, checked
before initialization/start. Five nodes each have a 1.5 GiB memory limit, 768 MiB
Java heap and 1 CPU limit. This is a conservative development gate, not a measured
production sizing result. Record actual host/container usage during runtime
validation. All validators share one host and do not establish consortium HA.

Pinned versions: Besu 26.9.0, ethers 6.16.0, Solidity 0.8.30, OpenZeppelin 5.4.0.
Genesis and compiler both target London. Gas price is positive (minimum 1000 wei
per gas); zeroBaseFee removes the base fee, not the configured execution fee.
The gas asset is **BESU_GAS**, with no asserted USD/public-ETH value.

## 1. Install and compile source

Why: install the isolated reproducible tooling and compile the reviewed token.
No wallet is read, chain started or transaction sent by these commands.

~~~bash
cd /workspaces/StableCoins
npm --prefix infrastructure/besu ci --ignore-scripts --no-fund
npm --prefix infrastructure/besu test
npm --prefix infrastructure/besu run compile
~~~

DEMOUSD is a fixed-supply, six-decimal demonstration token. Its constructor
allocates 1,000,000 tokens to the dedicated Besu treasury. It has no later mint,
owner, proxy, transfer tax, fiat backing or redemption promise. It is not Circle
USDC. Genesis allocates 1000 native BESU_GAS each to treasury and beneficiary;
public faucets, Coinbase and Circle are not involved.

## 2. Create dedicated encrypted wallets once

Why: private development keys stay separate from the verified public wallets.
The existing wallet-generator JAR supplies its tested encryption implementation.
This launcher confirms passwords and then reopens both saved files.

~~~bash
bash services/mvnw -f scripts/generate-test-wallets/pom.xml -DskipTests package
java --class-path scripts/generate-test-wallets/target/generate-test-wallets.jar infrastructure/besu/CreateBesuWalletPair.java
~~~

Enter and privately record each password when prompted; passwords require at
least 12 characters. Expect `BESU_PAIR_VERIFIED`, two public addresses and
`.local/besu-wallets`. The directory must not already exist. Failed verification
means preserve the directory for inspection; do not initialize/fund from it.

## 3. Initialize and start the private chain once

Why: generate four validator identities and a separate RPC identity, write
the genesis/allowlists and retain all data under ignored `.local/besu-network`.

~~~bash
node infrastructure/besu/cli.cjs init
node infrastructure/besu/cli.cjs start
node infrastructure/besu/cli.cjs status
~~~

Initialization refuses an existing state directory, including an incomplete
attempt. There is no automatic reset/delete command. A Docker generation failure
requires inspection of the preserved directory; do not repeatedly delete/recreate
it. Keep generated keys and encrypted wallet/password files out of Git.

Validators expose no host ports or RPC. The RPC node exposes ETH/NET/WEB3/QBFT
on 127.0.0.1:8547; ADMIN/DEBUG/PERM and unlocked-account signing are not enabled.
Node/account allowlists are installed on all five nodes. This is local
permissioning; validators can inspect chain data. It does not provide transaction
confidentiality. An actual rejection test with a non-allowlisted account is still
a runtime acceptance requirement.

## 4. Pin chain identity

Why: chain ID 20261125 does not distinguish a reset. This explicit, one-time,
local trust bootstrap checks the generated genesis validator data/set and saves
the observed block-zero hash before deployment or application use.

~~~bash
node infrastructure/besu/cli.cjs pin
~~~

Wait for nodes to connect if the first read is unavailable, then retry this
read/enrollment step. Subsequent operations require the same genesis hash. Pinning
does not make an untrusted remote node trusted; this tool only targets the local
RPC endpoint. Retain the manifest and identity record across stop/start.

## 5. Prepare and verify the dedicated password file

Run the interactive helper, which unlocks the actual dedicated keystore before
creating an owner-only password file. It preserves existing files and never
prints the password. See [credential setup and runtime paths](CREDENTIAL-SETUP.md).

~~~bash
java --class-path scripts/generate-test-wallets/target/generate-test-wallets.jar infrastructure/besu/PrepareBesuPassword.java
node infrastructure/besu/credential-status.cjs
~~~

## 6. Explicitly deploy DEMOUSD once

Why: the private chain does not contain Circle's public USDC contract. This command
uses only the dedicated private treasury after chain/genesis checks. It signs a
single contract deployment, records the hash and signed bytes in a protected
ignored file, then broadcasts once. Application startup does not invoke it.

~~~bash
BESU_PASSWORD_FILE="$PWD/.local/secrets/besu-treasury-password" node infrastructure/besu/cli.cjs deploy --confirm-private-demo
~~~

A pending receipt may return `DEPLOYMENT_PENDING_OR_UNKNOWN` with exit code 2.
Rerun the same command to **observe the recorded hash**. Recovery does not select
a new nonce, resend or deploy another token. Preserve deployment.json if RPC
fails after signing; never delete it to force a retry. A reverted deployment
requires investigation. Success records contract, runtime code hash, deployment
hash and block in the ignored manifest.

## 7. Read-only preflight

Why: establish the same chain/contract, six decimals, fixed supply, advancing
blocks, both wallet token balances, treasury native balance and transfer gas.

~~~bash
node infrastructure/besu/cli.cjs preflight 1
~~~

`PRIVATE_CHAIN_PREFLIGHT_READY` is a chain/setup check, not a completed payment
or authorization. The report explicitly says application integration is not
enabled. This command estimates a transfer without sending it.

## Stop and resume

Why: preserve network identity, token supply, balances and submitted history.

~~~bash
node infrastructure/besu/cli.cjs stop
node infrastructure/besu/cli.cjs start
node infrastructure/besu/cli.cjs preflight 1
~~~

These commands target only the standalone `stablecoins-besu` project and do
not use or replace the application's COMPOSE_FILE. Do not use `down -v` or remove
`.local/besu-network`. An intentional chain reset requires a separate operator
decision and a new identity/deployment; old payment contexts must remain pinned.

## Acceptance and current implementation status

Current implementation and acceptance criteria are maintained only in B01-B12 of
[the consolidated backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
Dated runtime/binding notes describe the progression of individual increments;
compilation and unit tests do not establish private/public runtime acceptance.

Sources: [ADR-016](../../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[Besu QBFT](https://docs.besu-eth.org/private-networks/tutorials/qbft),
[local permissioning](https://docs.besu-eth.org/private-networks/how-to/use-local-permissioning),
[Besu 26.9.0](https://github.com/besu-eth/besu/releases/tag/26.9.0),
[OpenZeppelin fixed supply](https://docs.openzeppelin.com/contracts/5.x/erc20-supply).

## Java adapter increment

The dedicated adapter, encrypted signer and opt-in runtime holder are now implemented.
See [runtime integration and read-only acceptance](RUNTIME.md). Application payment
bindings, accounting, reconciliation and UI selection remain pending.

## Staged application integration update - 27 November 2026

The separate default-off private application API, persisted private evidence UI,
and isolated Compose/lifecycle helper are now code-integrated. Earlier dated
statements about missing HTTP/UI wiring describe their respective increments.
See [the application operator guide](APPLICATION.md) for build, start, stop,
resume and protected variable-file procedures. Resume suppresses private first
submission and new funding; it does not change the selected public runtime mode.
New private customer instructions remain hard-gated. Executed local checks and
unexecuted runtime boundaries are recorded in
[activity status](../../docs/36-POC-ACTIVITY-STATUS.md); current acceptance and
remaining work live only in B07-B10 of
[the consolidated backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
