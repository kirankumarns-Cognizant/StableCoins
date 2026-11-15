# ADR-010: Local Development and Test Strategy

Status: ACCEPTED

## Context

Services depend on Aurora PostgreSQL, Amazon EventBridge and Amazon SQS.
Developers need to run and test services locally without an AWS account.

## Decision

Use the following tooling by test type:

| Test Type | Database | EventBridge/SQS | Blockchain |
|---|---|---|---|
| Unit tests | Mocks/stubs | Mocks/stubs | Mocked RPC |
| Integration tests | Testcontainers (PostgreSQL) | LocalStack | Mocked RPC |
| Testnet integration | Testcontainers or RDS | LocalStack or AWS | Real Base Sepolia |

Spring profiles:

| Profile | Purpose |
|---|---|
| test | Unit tests; no external dependencies |
| local | Local development; Testcontainers + LocalStack |
| aws-dev | Shared dev AWS account; real AWS services |
| testnet | Real Base Sepolia execution; requires explicit flags |

Real testnet execution requires both environment variables to be set:

```
BLOCKCHAIN_MODE=REAL_TESTNET
TESTNET_ENABLED=true
```

If either is absent or set to any other value, the blockchain adapter uses
a mock implementation. This prevents accidental testnet transactions during
normal development.

Testnet integration tests are annotated @TestnetIntegration and excluded from
the default Maven test phase. Run explicitly with:

```
mvn test -P testnet
```

## Rationale

- Developers can run unit and integration tests without AWS credentials.
- LocalStack provides a close emulation of SQS and EventBridge for local
  integration tests.
- Testcontainers spins up a real PostgreSQL instance per test suite; no
  shared state between test runs.
- Two-flag testnet guard prevents accidental token transfers.

## Consequences

- LocalStack free tier has limitations; some EventBridge features may differ
  from AWS behaviour. Verify critical event routing in the aws-dev profile.
- Testnet integration tests are slower (~10–30 seconds for confirmation) and
  depend on Base Sepolia availability and testnet token balance.


## Implementation note (2026-11-14)

The current Windows workspace has no container engine. GitHub Codespaces with the existing Docker-in-Docker dev-container is the intended environment for Docker Compose and real PostgreSQL Testcontainers verification. Local skips do not establish database correctness. This is a development execution choice within this accepted decision, not a production architecture change.

## Step 7 verification note (2026-11-14)

The opt-in Node runner and fictional psql seed in scripts support the approved
wallet demonstration in Codespaces using configured identity and existing services.
A new PostgreSQL integration suite covers concurrent funding, rollback and ledger
acknowledgement with actual owner migration directories. Its three cases compile
but were skipped on this container-free Windows machine. Unit/fixture success is
not a substitute for running these SQL/lock tests or a public-testnet transfer.

## Local validation implementation note (2026-11-15)

Local testing now precedes AWS deployment by owner instruction.
`scripts/local-workbench.cjs` reuses existing verification suites and Compose,
records fresh results/skips, and checks six health/access boundaries. It does not
substitute fixture success for database/payment acceptance or bypass identity.
See [the staged local runbook](../docs/33-LOCAL-FIRST-VALIDATION.md). Accepted decision
and status remain unchanged.
