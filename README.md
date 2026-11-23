# Stablecoin Intelligent Payment Orchestration POC

Current delivery status (2026-11-15): **9 of 11 steps code-complete**.
Connected UI setup and exact screen actions are in [the step-9 guide](docs/32-CONNECTED-REACT-WORKSPACE.md).
Live AWS/provider/testnet acceptance and the final Word document remain in steps 10-11.

## Demo operations and current status

Start with the [central demo SOP and command index](docs/37-DEMO-OPERATIONS-SOP.md)
and [latest POC activity checkpoint](docs/36-POC-ACTIVITY-STATUS.md).
The dated delivery statement above is historical; current evidence and remaining
acceptance gates are recorded in those documents.

## Overview

This repository contains a Proof of Concept demonstrating an AWS-native
cross-border B2B payment platform using stablecoins.

The POC demonstrates:

- AWS-native payment orchestration
- Cross-border B2B payments
- Simulated compliance and FX processing
- Real stablecoin/token transfer on a public blockchain testnet
- Internal double-entry financial ledger
- Settlement processing
- Visa integration through a Visa Simulator
- End-to-end reconciliation
- Executive-facing React UI
- Infrastructure as Code using AWS CDK

The primary business scenario is a USD 10,000 cross-border supplier payment.

## Connection configuration

All six services share [connections.properties](services/config/connections.properties).
See [configuration and integration status](docs/23-SHARED-CONNECTION-CONFIGURATION.md) for environment overrides, identity settings, browser-safe settings, and Docker/AWS usage.

## Configurable routing agent

The bounded read-only routing agent runs inside payment-service and supports
configured direct OpenAI or compatible OpenAI models on Amazon Bedrock. Provider,
model, Region, endpoint, authentication and limits use the shared connection
configuration. Connection changes require a service restart. Bedrock supports
AWS task-role authentication; bearer credentials are resolved from Secrets
Manager. Inference is disabled until explicitly configured and customer-allowed.

See [runtime setup, API and limits](docs/27-RUNTIME-INTENT-AND-ROUTING-AGENT.md)
and the [delivery plan with executed verification and remaining gates](docs/21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md).
Live model access, public-testnet execution and the full end-to-end journey are
not established by stub tests. [ADR-015](adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md)
is accepted; approved accounting controls are implemented, with database and
end-to-end integration verification still pending.

Final deliverables are tracked in the living [architecture/design](docs/28-SOLUTION-ARCHITECTURE-AND-DESIGN.md)
and [prototype execution runbook](docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## Core Principle

The blockchain portion of the POC MUST be real.

The following are REAL:

- AWS application infrastructure
- Application microservices
- Aurora PostgreSQL database
- EventBridge/SQS event processing
- Internal ledger
- Reconciliation
- Public blockchain testnet
- Testnet wallets
- On-chain token transfer
- Transaction hash
- Blockchain confirmation

The following are SIMULATED:

- Corporate USD funding
- Fiat-to-stablecoin conversion
- FX provider
- KYC/KYB
- AML
- sanctions screening
- Visa
- beneficiary fiat credit

The UI and logs must never represent simulated functions as real.

## Primary Payment Journey

Corporate Treasurer
→ Payment Initiation
→ Compliance
→ FX/Quote
→ Rail Selection
→ Stablecoin Funding
→ Real Blockchain Transaction
→ Blockchain Confirmation
→ Settlement
→ Optional Visa Payout
→ Ledger Posting
→ Reconciliation

## Technology Stack

### Frontend

- React
- TypeScript

### Backend

- Java 21
- Spring Boot 3.x
- Maven

### AWS

- Amazon CloudFront
- AWS WAF
- Amazon Cognito
- Amazon API Gateway
- Amazon ECS Fargate
- Amazon ECR
- Amazon EventBridge
- Amazon SQS
- Amazon Aurora PostgreSQL
- Amazon S3
- AWS KMS
- AWS Secrets Manager
- Amazon CloudWatch
- AWS CloudTrail

### Blockchain

Preferred:

Base Sepolia

Fallback:

Ethereum Sepolia

Token:

Test USDC where supported, or clearly identified test ERC-20 token.

### Infrastructure

AWS CDK using TypeScript.

### Containers

Docker.

## Services

The initial POC contains six logical application services:

1. payment-service
2. compliance-quote-service
3. blockchain-service
4. settlement-ledger-service
5. reconciliation-service
6. visa-simulator

Do not create additional deployable services unless justified through an ADR.

## Repository

/docs
    Architecture and functional specifications

/adr
    Architecture Decision Records

/prompts
    Instructions for AI coding agents

/frontend
    React application

/services
    Spring Boot services

/infrastructure
    AWS CDK

/contracts
    Optional smart contracts

/scripts
    Setup and operational scripts

/tests
    End-to-end tests

## Development Approach

The implementation is incremental.

Do NOT attempt to generate the entire system in one iteration.

Recommended order:

1. Architecture validation
2. Repository bootstrap
3. Payment domain
4. Event architecture
5. Ledger
6. Blockchain integration
7. Real testnet transaction
8. Visa Simulator
9. Reconciliation
10. UI
11. AWS CDK
12. End-to-end testing
13. Executive demo

## Security

NEVER commit:

- AWS access keys
- blockchain private keys
- RPC API keys
- database passwords
- secrets
- Visa credentials

Use `.env.example` for documented configuration.

Use AWS Secrets Manager and KMS for deployed secrets.

## Development using GitHub Codespaces

The recommended way to contribute to this POC is via GitHub Codespaces.
The repository ships a fully configured dev container that provisions
Java 21, Node 22, Maven, AWS CLI, GitHub CLI, and Docker-in-Docker
automatically.

### Opening a Codespace

1. Push the repository to GitHub.
2. Click **Code → Codespaces → Create codespace on main**.
3. Wait for the post-create script to complete (~2 min).

### Quick verification after Codespace opens

```bash
java -version          # openjdk 21.x
javac -version         # javac 21.x
cd services && ./mvnw --version
node --version         # v22.x
docker version         # client + server must both appear
```

### Running tests in the Codespace

```bash
# Unit tests (no Docker required)
cd services && ./mvnw test

# Integration tests (Testcontainers — requires Docker daemon)
cd services && ./mvnw verify

# Frontend
cd frontend && npm ci && npm run build

# CDK synthesis (no deployment)
cd infrastructure && npm ci && npm run build && npx cdk synth
```

### Codespaces Secrets

Set the following in repository **Settings → Secrets and variables → Codespaces**
for development with real blockchain testnet or AWS:

- `BLOCKCHAIN_RPC_URL`
- `BLOCKCHAIN_TOKEN_CONTRACT`
- `DB_PASSWORD`

See [docs/18-CONTAINERIZATION-CODESPACES.md](docs/18-CONTAINERIZATION-CODESPACES.md)
for the full workflow.

---

## Definition of Done

The POC is complete when a developer can:

1. deploy infrastructure
2. start the application
3. initiate a simulated USD 10,000 payment
4. execute simulated compliance
5. execute simulated FX/funding
6. submit a REAL blockchain testnet transaction
7. obtain a REAL transaction hash
8. confirm the transaction using a blockchain explorer
9. execute a simulated Visa payout
10. post double-entry ledger transactions
11. reconcile payment, ledger, blockchain and Visa records
12. view the entire journey through the UI
13. destroy the AWS POC infrastructure safely

## Living solution documentation

The [architecture and design](docs/28-SOLUTION-ARCHITECTURE-AND-DESIGN.md) and
[complete feature demonstration runbook](docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md)
cover previous, current and subsequent implementation increments. Applicable
technical documents and ADR implementation notes are updated with each change.
A consolidated, visually verified Word architecture/design document is a tracked
final-delivery requirement; it has not yet been generated. Use
[GitHub Codespaces](docs/18-CONTAINERIZATION-CODESPACES.md) for container/database
verification because the current local workspace cannot run Docker.

## Approved wallet workflow and demonstration

Step 7 code connects the approved USDC-wallet path through simulated funding,
ledger-gated submission and chain confirmation using existing services. See
[configuration, Codespaces seed and opt-in runner](docs/30-PAYMENT-FLOW-INTEGRATION.md).
Actual connected identity/PostgreSQL/model/testnet execution remains unverified;
fee-complete settlement, optional payout and reconciliation are step 8. React
screens, full AWS deployment and final evidence/Word delivery remain steps 9-11.

The [presenter runbook](docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md#opening-narrative-the-complete-payment-story)
starts with the overall payment story, then explains each proposed screen, its
roles, why each action occurs, where to navigate/click and the expected outcome.
Proposed UI paths are clearly separated from currently executable API checks.

## Step 8 implementation update

The existing services now connect actual receipt-fee accounting, final settlement,
optional **simulated** USD/INR bank payout and immutable reconciliation. Connected
React views are the next step; public testnet, live identity/model and AWS proof
still require execution. See the [delivery status](docs/21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md),
[cumulative architecture](docs/28-SOLUTION-ARCHITECTURE-AND-DESIGN.md) and
[complete lifecycle demo](docs/31-SETTLEMENT-PAYOUT-RECONCILIATION.md).

## Connected payment workspace - step 9

The React application now connects payment intent, seven-signal routing and actual
agent runs, independent approvals, treasury readiness, execution, blockchain,
ledger/reconciliation and optional simulated payout screens to existing services.
Use the [setup/security/screen guide](docs/32-CONNECTED-REACT-WORKSPACE.md) and
[complete action-by-action demonstration](docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md).
The offline HTML prototype remains illustrative. Live integration, AWS deployment
and final public-testnet/model evidence are distinct from local UI verification.

## Local validation before AWS

The requested order is local testing first, AWS deployment later. Use the
[local-first validation guide](docs/33-LOCAL-FIRST-VALIDATION.md) for prerequisite,
code verification, Docker startup and authenticated payment gates. Step 10 remains
in progress; nine of eleven steps are code-complete.

## Codespaces demonstration before AWS deployment

Use [the launch guide](docs/34-CODESPACES-DEMO-LAUNCH.md) for repository
kirankumarns-Cognizant/StableCoins, AWS account 640237069320 in us-east-1, SSO
and resource prerequisites, container startup and the private connected UI.
[The presenter walkthrough](docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md) supplies exact
screen/role/context/click/outcome instructions. [The proof package](docs/35-HACKATHON-PROOF-PACKAGE.md)
covers two customer policies, a comparable baseline, simulated disruption and
commercial hypotheses. Step 11 preparation is in progress before AWS deployment;
genuine connected evidence and the rendered final Word document remain pending.
