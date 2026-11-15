# Security Architecture

## Implemented authentication and authorization

Backend APIs use a reusable `platform-security` Java library in the existing six
services. Payment-service retains its review-specific filter chain and reuses the
shared Cognito validator/membership implementation. Missing identity configuration
fails closed; `/health` is public. Configure HTTPS issuer/JWKS, public client,
optional audience and bounded timeout through the shared connections file.
Validated access tokens must have a valid signature, issuer/time claims,
`token_use=access`, subject and allowed client. ID tokens do not grant API access.

Customer and role membership is server-owned configuration keyed by verified
subject. Current roles are PAYMENT_OPERATOR and PAYMENT_APPROVER. JWT group claims,
request parameters and model output do not grant application roles. Independent
review/approval controls are in [recommendation access](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md).

Blockchain execution and internal commands require a distinct confidential client
(`IDENTITY_SERVICE_CLIENT_ID`) plus `IDENTITY_SERVICE_SCOPE` (default
`stablecoins/internal`). A browser token with that scope is insufficient. Manual
ledger writes also require the disabled-by-default application flag. Customer
ledger/chain reads check payment ownership, including journal/transaction-ID
lookups. Ownership-store failures deny access with a structured 503.

Cross-service ownership lookup is read-only under ADR-007. See
[shared design](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md#shared-identity-and-evidence-access-design).
Live Cognito connectivity and outbound client-credentials acquisition are not yet
verified/complete.

## Deployment and frontend target

Cognito Hosted UI authorization code with PKCE, the React auth client, API Gateway
JWT authorizer, private ALB/Fargate network and corresponding IAM/provisioning
remain deployment work. Earlier illustrative TREASURY_USER/OPERATIONS_USER/ADMIN
group mapping was not implemented authorization. Current authoritative membership
and approval rules are those above and accepted ADR-014.

The runtime model only receives scoped read tools. It cannot approve, sign,
submit, pay out or write journals. Do not persist hidden reasoning or log raw
prompts, credentials, secret responses or sensitive PII. Keep audit provenance and
bounded tool traces distinct from secrets. Unknown blockchain submissions require
state recovery before another financial action.

## AWS

Use:

IAM least privilege
Security Groups
private subnets
KMS
Secrets Manager
WAF
TLS
CloudTrail
CloudWatch

## Wallet Security

POC:

Testnet wallet only.

Secret material protected using AWS Secrets Manager/KMS.

Never store private key in application database.

Never log private key.

## Production Evolution

Evaluate:

MPC
HSM
Institutional custodian
transaction policy engine
multi-party approval
wallet allowlisting

## Application Security

Validate all API input.

Protect against:

injection
invalid amounts
duplicate requests
unauthorized payment access
parameter tampering
secret leakage

## Blockchain Risks

Consider:

wrong chain
wrong wallet
wrong token
incorrect decimals
gas exhaustion
nonce collision
duplicate submission
transaction failure
chain reorganization
RPC outage

## POC Data

Use fictional customer information only.

No real customer/account data.