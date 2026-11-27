# Besu Java runtime integration and acceptance

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](../../docs/40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


This is the next increment after [isolated chain setup](README.md).
It adds a dedicated `besu` package inside blockchain-service. It does not replace
the public adapter or change public signer guards. DEMOUSD and BESU_GAS are
private demonstration assets; LIVE RPC evidence also carries PRIVATE_DEMO.

## Implemented boundary

The adapter checks chain ID, pinned genesis, four configured current validators,
token runtime code hash, symbol, decimals and fixed supply. New operations require
a recent non-genesis head. Estimates and signing accept only the configured
treasury, token and recipient allowlist, with exact six-decimal amounts.

Signing uses a separate encrypted keystore and private chain ID. Submission
validates recovered sender, chain, token, method, recipient, quantity and gas,
then attempts one broadcast. The caller must persist its nonce/hash claim before
calling submission; the adapter does not create a second transaction owner.

Receipt observation verifies canonical block hash, transaction identity and
exactly one matching DEMOUSD Transfer event. Fee completeness uses execution
fees only, including evidenced zero fees. Public Base L1 fee parsing is unchanged.

The additive BesuRuntime has a server-owned registry and read-only observation.
New payment creation is intentionally disabled in this registry until immutable
intent/review/approval bindings, reservations, ledger and reconciliation are
integrated and accepted. No Besu HTTP payment endpoint or UI selection is enabled.

## Local compile and fixture tests

From repository root, Java 21 and existing Maven dependencies are required:

~~~bash
bash services/mvnw -f services/pom.xml -pl blockchain-service -am test
npm --prefix infrastructure/besu test
~~~

These exercise fixture wallets and synthetic RPC/loopback HTTP. They are not
proof of a running Besu chain or completed payment.

## Real read-only Java adapter acceptance in Codespaces

Preconditions: these source changes are present in the Codespace; Docker is
available; sections 1-7 of the setup guide have completed successfully, including
dedicated wallets, chain startup, explicit genesis pin and DEMOUSD deployment.
Run from repository root in Bash. The public application may remain unchanged.

~~~bash
cd /workspaces/StableCoins
node infrastructure/besu/cli.cjs preflight 1
node infrastructure/besu/runtime-context.cjs
source .local/besu-network/readiness.env
bash services/mvnw -f services/pom.xml -pl blockchain-service -am -Pbesu-readiness verify
~~~

Why these steps:

1. Preflight checks private chain/token identity, progressing blocks and capacity.
2. The exporter rechecks deployed identity against the protected manifest and
   writes only validated metadata to an ignored owner-only file. No password,
   keystore material, enablement flag or RPC credential is exported.
3. The explicit Maven profile calls the real Java adapter against loopback Besu.
   It verifies fresh advancing blocks and capacity to transfer one DEMOUSD.
   It contains a signer that always throws, so it cannot sign or submit.

Expected result: BesuReadinessIT passes without skips and Maven reports BUILD
SUCCESS. Missing configuration or unreachable/wrong chain fails. Do not combine
this profile with the public testnet profile. The explicit profile selects this
readiness IT instead of the normal PostgreSQL persistence IT; ordinary verify
retains the existing persistence suite.

This is a read-only chain acceptance gate. It does not prove account rejection,
validator interruption, stop/resume persistence, two approvals, financial journals,
reconciliation or a public-testnet transaction. Those remain separate gates.

## Opt-in application runtime configuration

The Spring bean is disabled by default. Only explicit
BESU_PRIVATE_DEMO_ENABLED=true with the local profile can create it; aws-dev is
rejected. Existing REAL_TESTNET settings do not grant this private capability.

| Server setting | Meaning |
|---|---|
| BESU_RPC_URL | Reachable private RPC URL; never a payment/model argument |
| BESU_GENESIS_HASH | Pinned block-zero hash |
| BESU_TOKEN_CONTRACT / BESU_TOKEN_CODE_HASH | Recorded DEMOUSD deployment identity |
| BESU_TREASURY_ADDRESS | Dedicated private treasury public address |
| BESU_VALIDATOR_ADDRESSES | Four comma-separated configured validator addresses |
| BESU_RECIPIENT_ADDRESSES | Dedicated private recipient allowlist |
| BESU_KEYSTORE_FILE / BESU_PASSWORD_FILE | Protected local files, read only; no passwords in arguments |

The bean checks the RPC identity before unlocking and rechecks after the KDF.
It rejects reuse of the active public signer address. POSIX secret files must be
owner-readable with no group/other permissions. Password bytes are preserved
exactly. Decrypted credentials stay in JVM memory for this local POC; production
custody remains the separate Secrets Manager/KMS/MPC/HSM evolution.

For a Java process on the Codespace host, the setup endpoint is
http://127.0.0.1:8547. A container cannot use its own loopback to reach Besu.
A future application overlay must attach only blockchain-service to the existing
stablecoins-besu_besu-private Docker network and use http://besu-rpc:8545.
No application Compose overlay is enabled by this increment.

## Next implementation gate

After actual chain acceptance, carry the exact selected context through the
payment instruction, quote, approvals, durable execution/reservations, receipt
ownership, DEMOUSD/BESU_GAS journals and reconciliation. Enable explicit
TOKEN_WALLET selection only after that complete path passes. Policy routing
follows the private end-to-end and separate public regression gates in ADR-016.

## Settlement binding increment

See [SETTLEMENT-BINDINGS.md](SETTLEMENT-BINDINGS.md) for the immutable owner
contracts/storage, activation boundary and separate PostgreSQL acceptance.
This does not enable Besu payment execution.

## Owner intent integration update

The owner now captures an explicitly selected new instruction atomically with
payment creation. Selection resolves server configuration and customer permission;
it is not live network readiness. All such instructions remain blocked from the
legacy USDC workflow, and private selection has a hard acceptance gate. No
TOKEN_WALLET UI or Besu execution has been enabled. See
[SETTLEMENT-BINDINGS.md](SETTLEMENT-BINDINGS.md) for the implemented boundary and
remaining review, approval, event, accounting and reconciliation work.

## Instruction-bound observation increment

BesuRuntime.observe(Instruction) resolves no defaults and requires exact equality
with the configured context before read-only RPC. It captures full owner material
and a maximum 15-second observation clipped to the customer's delivery deadline.
The returned SettlementObservation validates exact instruction reuse, freshness
and gas arithmetic. Gas is denominated in BESU_GAS; no USD valuation is supplied.
Its totals omit reservations. Calls use bounded-window latest state, not a single
atomic block snapshot. Base L1 fees remain on the existing public path.

The later read-only owner preparation handoff below adds request/event/API
plumbing; payment selection and execution remain gated.
BesuReadinessIT now also exercises the bound observation, without signing or
submission. Live execution remains a separate, explicitly invoked acceptance gate.

## Owner preparation observation handoff - 2026-11-25

The Besu runtime now has a dedicated asynchronous observation consumer. It accepts
only SettlementObservationRequested 1.0 tied to a canonical saved owner request,
verifies the complete instruction around RPC, and persists an immutable first
result in besu_preparation_observation. Requests belong to payment-service;
observations belong to blockchain-service. Shared-schema joins are read-only
across owners, consistent with ADR-007.

The observation API is disabled by default. Private instruction creation and
execution remain hard-gated. Captured results are OBSERVED_NOT_READY, never READY:
scoped reservation integration is still required. No wallet creation, token
deployment, signing, transfer, ledger mutation or production activation is part
of this increment. See SETTLEMENT-BINDINGS.md for ownership and acceptance limits.

## Reservation-aware observation increment - 2026-11-25

Preparation now locks the private treasury scope, observes RPC, subtracts scoped
commitments and stores immutable availability evidence. Explicit READ_COMMITTED
transactions and post-lock freshness checks protect the commitment read. These
changes do not enable private execution: hold/pin remain internal storage methods
awaiting exact approved execution claims. Pinned commitments are never released
by quote expiry; receipt/nonce recovery remains pending. See
[settlement bindings](SETTLEMENT-BINDINGS.md#scoped-commitments-and-availability---2026-11-25)
for semantics, operational assumptions and the PostgreSQL acceptance command.
No change to public USDC runtime, signer, posting rules or reconciliation.

## Credential creation is an explicit setup step

BESU_RPC_URL is a URL; BESU_KEYSTORE_FILE and BESU_PASSWORD_FILE are references
to separately created protected local files. Runtime configuration does not
create them. Use [the checked credential setup](CREDENTIAL-SETUP.md) and the
interactive helper; existing files are never overwritten.
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
