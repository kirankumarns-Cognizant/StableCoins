# Logical Architecture

## Major Layers

### Experience Layer

React / TypeScript

Functions:

- dashboard
- payment initiation
- payment tracking
- blockchain visibility
- reconciliation

### API Layer

CloudFront
WAF
API Gateway
Cognito

### Application Layer

Six logical services:

- Payment Service
- Compliance/Quote Service
- Blockchain Service
- Settlement/Ledger Service
- Reconciliation Service
- Visa Simulator

### Event Layer

EventBridge
SQS
DLQs

### Data Layer

Aurora PostgreSQL
S3

### Blockchain Layer

Blockchain Adapter
→ RPC
→ Base Sepolia
→ Test token

### Visa Layer

VisaPaymentAdapter
→ VisaSimulatorAdapter
→ Visa Simulator

Future:

VisaPaymentAdapter
→ Authorized Visa integration

### Security/Operations

IAM
KMS
Secrets Manager
CloudWatch
CloudTrail
WAF

## Architectural Principle

Payment orchestration and settlement rail execution are separate concerns.

Blockchain provides settlement evidence.

Blockchain does not replace:

- payment orchestration
- operational database
- accounting ledger
- reconciliation
- compliance