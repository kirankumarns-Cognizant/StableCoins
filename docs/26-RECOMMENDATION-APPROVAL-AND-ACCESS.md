# Durable recommendation, human approval and customer access

Date: 2026-11-14. Implements the step 5 control boundary under accepted
[ADR-014](../adr/ADR-014-STABLECOIN-AGENTIC-ROUTING.md), inside existing payment-service.
The six-service architecture and financial ledger remain unchanged.

## Reuse and ownership

RecommendationService reuses Payment/PaymentRepository, PaymentIntentSnapshot,
CustomerRoutingPolicy, the seven-signal RoutingEvidence contract,
DeterministicRoutingAssessmentService, DomainEvent and the transactional outbox.
There is no second route scorer or payment execution service. Existing treasury
and Visa liquidity adapters retain ownership of reservations and execution.

The current producer is explicitly **DETERMINISTIC**, with rule version in its
producerVersion. Its explanation describes configured priority and references
stored assessments. It is not a runtime AI agent. Step 6 adds the bounded model
integration; its tools must remain read-only and cannot invoke approval methods.

## Verified access and configuration

All payment-service HTTP routes except `/health` require a verified bearer token.
This also secures the legacy payment HTTP API; legacy JSON/fingerprints and
internal overloads remain compatible. Unauthenticated clients must migrate to
Cognito access tokens. There is no anonymous local-development bypass.

Spring Security verifies RS256 signatures using configured HTTPS JWKS, issuer,
expiry/not-before, issued-at, subject, Cognito `token_use=access` and `client_id`.
An optional audience adds an aud check. ID tokens and wrong-client tokens are
rejected. JWKS retrieval has bounded connect/read timeouts and supports normal
key rotation. Identity endpoints are deployment configuration, never supplied
by a request. See the [official JWT resource-server documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

Use the existing shared connection file or external `CONNECTIONS_CONFIG`:

| Setting | Behavior |
|---|---|
| IDENTITY_PROVIDER | COGNITO; another provider requires an architecture decision |
| IDENTITY_ISSUER_URI | Exact issuer; legacy COGNITO_ISSUER_URI alias retained |
| IDENTITY_JWK_SET_URI | HTTPS JWKS URL, explicitly required with issuer/client |
| IDENTITY_CLIENT_ID | Cognito app client; legacy COGNITO_APP_CLIENT_ID alias retained |
| IDENTITY_AUDIENCE | Optional additional API audience |
| IDENTITY_HTTP_TIMEOUT_MS | 5000 default; valid range 100?30000 |

Entirely absent identity settings deny API tokens without a network request.
Partial or insecure configuration fails startup. No Cognito resources/users were
provisioned or contacted in this increment. Other services do not acquire an
identity boundary merely because they import these common properties.

`app.access.memberships[<verified-sub>].customer-id` and `.roles[n]` establish a
server-owned, single-customer membership for each verified subject. Roles are
PAYMENT_OPERATOR and PAYMENT_APPROVER. No customer claim, identity header or
caller role grants membership. Configure both roles for an approver who also
reads payments/recommendations. Unmapped subjects and missing roles return 403;
missing/invalid tokens return 401. Every request resolves membership afresh.
Membership configuration replacement currently requires deployment/restart;
there is no administration UI or production multitenancy claim.

[customer-access.example.properties](../services/config/customer-access.example.properties)
contains fictional subjects and an illustrative policy. It is not auto-imported.
Copy appropriate keys into the same server-only external connection file and
replace subjects/customer UUIDs with actual configured values. Do not put these
memberships, policies or private connection settings in frontend configuration.

## Immutable review material and lifecycle

V9 introduces payment-service-owned routing_recommendation, routing_decision and
routing_audit tables. A recommendation stores the full immutable intent/policy,
context, evidence snapshot with provenance and modes, complete deterministic
assessment, selected candidate/cost/delivery estimates, quote, account/beneficiary
binding, rationale/producer version, creation/expiry and required approval count.
The posted financial ledger is untouched.

`propose` is an internal typed boundary only. It validates payment/correlation,
customer, source amount/rail, current policy, active owned account/beneficiary,
wallet and payout currency. The quote must match payment/customer/candidate/
provider, transfer/payout amounts and the assessed total fee, have a valid
version and time window, and be explicitly simulated. There is **no HTTP API**
for callers to insert a quote, evidence or recommendation. Controlled fixtures
are not production feed beans or a missing-credentials fallback.

The typed quote snapshot is a contract for trusted compliance-quote-service
output, not a new quote implementation. That service currently implements
screening only. Its quote producer and evidence/readiness connectors remain
step 7 integration work; no runnable end-to-end proposal pipeline is claimed.
The same intent/quote/provider/fee binding will constrain the step 6 agent.

Creation atomically advances DRAFT to PROPOSED. A new proposal supersedes the
previous PROPOSED version, preserving its document and decisions. Approved or
executed unresolved review blocks replacement. No eligible route/insufficient
evidence can be stored with findings and no selection/quote; it cannot acquire
approval. Such a result can expire immediately when no valid evidence window
exists. Terminal states retain their records.

Quote acceptance and recommendation approval are separate actor decisions. Each
approver must first accept the exact quote for that exact recommendation/version.
Distinct actors each contribute at most one approval; the captured customer
policy sets the required count. Until the threshold is reached the state remains
PROPOSED. The final distinct decision advances it to APPROVED. Rejection is
allowed while PROPOSED; exact replay returns the existing result.

All decisions lock the parent payment **before** loading mutable recommendation
state. Database uniqueness enforces recommendation version, one active review
per payment and one actor/action. Replacement flushes old status before inserting
the next active version. Decision, audit and outbox writes share the transaction.
An outbox/database failure rolls back the approval. Expiry changes are retained
when a business conflict is returned (noRollbackFor ApprovalConflictException);
these conflicts are raised before new decisions are written.

Expiry applies to PROPOSED and APPROVED records at the earliest quote/evidence/
assessment validity limit. Reads and decisions evaluate it. Exact decision
replay returns current state, including EXPIRED/SUPERSEDED, rather than adding a
vote or restoring authority. Conflicting versions return structured 409 errors.

## Approval APIs

All paths use `/api/v1/payments/{paymentId}/recommendations/{recommendationId}`:

| Method/path | Role | Input |
|---|---|---|
| GET | PAYMENT_OPERATOR | No request body |
| POST /quote-acceptance | PAYMENT_APPROVER | expectedVersion, quoteId, quoteVersion |
| POST /approvals | PAYMENT_APPROVER | expectedVersion, quoteId, quoteVersion |
| POST /rejection | PAYMENT_APPROVER | expectedVersion |

Membership restricts reads/commands to the stored customer. UUIDs and positive
versions are validated. Errors omit decoder/provider internals. Approval APIs
intentionally have no reserve, sign, submit or payout operation.

Before acceptance, the service checks the current COMPLIANCE_APPROVED payment
state, complete intent/policy equality, owned active account and unchanged
beneficiary, quote binding/expiry and the deterministic assessor. Reassessment
rechecks all seven stored signal types, freshness, availability, liquidity,
fees and deadline under the current rule/network configuration. Changed rules,
material assessment or intent/policy require a new review.

This checks the **captured, still-valid evidence**, not a new live provider/RPC
observation. APPROVED means human review recorded, not financial authorization.
No payment states advance and no funds are reserved. Before commitment, step 7
must refresh authoritative compliance/quote/provider/treasury evidence, compare
against the approved material, coordinate reservations and protected execution,
and stop for changed material or an unresolved prior operation. No EXECUTED
transition/command is exposed here. Managed signing and full fee evidence remain
open release gates from step 4.

## Events and delivery

Every new event is a review notification, never a financial command. The common
DomainEvent v1 envelope retains eventId, eventType, eventVersion, paymentId,
correlationId and timestamp. Its typed DecisionEvent payload includes
recommendationId/version, intentId/version, policyVersion, evidenceSnapshotId,
quoteId/version when present, actorId, state and expiresAt. The outbox row and
envelope share eventId. Consumers must deduplicate by eventId and must not treat
approval notifications as an instruction to execute a payment.

Types: RoutingRecommendationProposed, RoutingQuoteAccepted,
RoutingApprovalRecorded, RoutingRecommendationApproved,
RoutingRecommendationRejected, RoutingRecommendationExpired,
RoutingRecommendationSuperseded. RoutingQuoteAccepted deliberately does not
publish the separate business-payment QuoteAccepted event or advance its state.

`SQS_ROUTING_DECISIONS_QUEUE_NAME` and `SQS_ROUTING_DECISIONS_DLQ_NAME` configure
review notification destinations in shared configuration/Compose/CDK. CDK adds
one EventBridge rule and SQS queue/DLQ with 14-day retention; there is no execution
consumer. LocalStack startup creates the notification queue; local publishing
maps all seven types there. Unknown local event types now throw rather than
silently return and be marked published. Existing dispatcher retry/FAILED
behavior remains. The database audit remains the durable application record;
queue retention is not a permanent audit archive. No AWS deployment was run.

## Verification and remaining work

Java 21 compilation/packaging succeeded for all six services. Aggregate results:
291 Java tests passed, zero failures/errors, 24 PostgreSQL tests skipped without
Docker; seven Node configuration tests passed; CDK compiled and seven Jest tests
passed. Final jar resources were refreshed after the last shared-file edit.
Detailed commands/results are in the sequential delivery plan. Tests cover signed RSA JWT claims/signatures, membership/role
spoofing, missing identity, HTTP validation, immutable JSON round trip, quote
acceptance, distinct approvers/replays, expiry, policy/intent/beneficiary/compliance
changes, blocked replacement, event binding and configured publication.

Five new real PostgreSQL tests cover V9/JSONB round trip, concurrent duplicate
approvals, concurrent distinct approvals, concurrent replacement and rollback
on outbox failure. They use Testcontainers, never H2. Docker is unavailable in
this workspace, so database/migration/concurrency execution remains unverified.
The updated existing PostgreSQL HTTP tests use a mocked decoder; cryptographic
validation is tested separately with actual RSA signatures.

Next: step 6 actual bounded runtime agent. Trusted quote/evidence connectors,
execution-time refresh, complete payment-state/event flow, PostgreSQL verification,
Cognito deployment, managed signing, real testnet transfer, React integration and
approved ledger/payout/reconciliation mappings remain pending. No model, live
identity, public-testnet transaction, fiat movement or bank credit was invoked.

## Modified files

- payment-service: new security and approval packages, RecommendationController,
  V9 migration; existing PaymentController, PaymentService, PaymentRepository,
  GlobalExceptionHandler, EventingConfig, LocalStackInitializer,
  LocalSqsEventPublisher and pom.xml extended.
- Tests: new approval/security/persistence/publication tests; existing health,
  intent HTTP, payment ownership, messaging and PostgreSQL HTTP tests updated.
- Shared/deployment: connections.properties, fictional customer-access example,
  configuration tests, .env.example, Compose allowlist, CDK queue/rule and tests.
- Documentation: delivery plan, intent/shared-configuration notes and this file.

Assumptions: accepted Cognito provider; one configured customer per subject;
server-trusted quote/evidence producers; snapshots have bounded validity;
approval and execution are separate; no new microservice or accounting policy.
