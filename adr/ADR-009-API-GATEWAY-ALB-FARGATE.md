# ADR-009: API Gateway HTTP API, VPC Link, Shared ALB, ECS Fargate

Status: ACCEPTED

## Context

The POC needs a network path from the React frontend to the backend
microservices running on ECS Fargate in private subnets.

Options considered:

Option A: HTTP API Gateway + VPC Link → internal shared ALB (path-based routing).
Option B: REST API Gateway + VPC Link → internal NLB.
Option C: ALB only (no API Gateway).

## Decision

Use:

CloudFront → AWS WAF → API Gateway HTTP API → VPC Link →
internal shared Application Load Balancer → ECS Fargate

Path-based routing on the ALB routes requests to the correct Fargate service.

Visa Simulator is not exposed through the API Gateway or external ALB.
It is accessible only within the VPC by payment-service.

## Rationale

- API Gateway HTTP API provides JWT authorizer (Cognito), throttling and
  managed TLS termination at the edge without additional infrastructure.
- VPC Link connects API Gateway to the internal ALB securely without NAT.
- A shared internal ALB with path-based routing reduces ALB cost and
  simplifies the CDK network stack for a POC.
- HTTP API Gateway is lower cost and lower latency than REST API Gateway
  for the Spring Boot proxy use case.
- CloudFront with WAF provides DDoS protection and caching at the edge.

## Path Routing

| Path Prefix | Target Service |
|---|---|
| /api/v1/payments/* | payment-service |
| /api/v1/blockchain/* | blockchain-service |
| /api/v1/ledger/* | settlement-ledger-service |
| /api/v1/reconciliation/* | reconciliation-service |
| /api/v1/quotes/* | compliance-quote-service |

## Consequences

- VPC Link requires a VPC endpoint service; adds minor CDK complexity.
- Shared ALB is a single point of failure (acceptable for POC).
- Production: consider a dedicated ALB per service or a service mesh for
  fine-grained traffic control and independent scaling.

## Step 9 implementation note - 2026-11-15

The connected React workspace reuses existing owner command APIs and adds a
customer-authorized, read-only payment-service evidence projection. Shared-schema
reads follow the accepted ownership boundary; the private Visa endpoint stays
private. UI decimal-string/BigInt display checks preserve per-asset precision,
while the existing ledger, agent authority and approval/execution services remain
authoritative. No architectural decision or accepted status changes. See
[connected UI design](../docs/32-CONNECTED-REACT-WORKSPACE.md).

## Open-tab demo session continuity note - 2026-11-16

The existing public-client PKCE flow now retains refresh tokens only in memory
and renews access tokens through the configured HTTPS token endpoint when the
shared IDENTITY_AUTO_REFRESH_ENABLED flag is true (default). There is no extra
application idle/absolute timeout. Provider expiry/revocation, API JWT validation,
customer membership and financial approval/quote deadlines remain authoritative.
Same-screen return after reauthentication retains only an allowlisted application
route in one-time PKCE state. No new service, persistent token store or alternate
authentication boundary is introduced; the accepted decision is unchanged. See
[connected UI design](../docs/32-CONNECTED-REACT-WORKSPACE.md).
