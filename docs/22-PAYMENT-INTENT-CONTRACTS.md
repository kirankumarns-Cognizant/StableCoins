# Payment Intent and Customer Policy Contracts

Implemented increment: ADR-014, delivery step 2.

## What this increment provides

payment-service accepts an optional typed `intent` in its request model. The
internal authorized creation method validates and stores a versioned intent and
an immutable server-selected customer policy snapshot atomically with PAYMENT,
its status history and the existing PaymentCreated outbox event.

This is a deterministic foundation, not a routing engine or model integration.
No new service, model call, quote, reservation, token transfer or fiat payout is
implemented by this increment. INR payout and funding remain explicitly simulated.

## Public HTTP availability and trusted identity boundary

Updated by step 5: all payment-service HTTP routes except health now require a
verified Cognito access token and server-owned customer membership/role. The
controller passes AuthorizedCustomer derived from that identity to existing
service overloads. Account/beneficiary ownership and legacy payment reads/replays
are customer scoped. Missing identity configuration denies API access; there
is no trust in customer headers/claims or a local authentication bypass.

See [recommendation, approval and access](26-RECOMMENDATION-APPROVAL-AND-ACCESS.md)
for configuration, role mapping, HTTP compatibility changes and verification.
The JSON and legacy request fingerprint remain compatible. Existing internal
legacy overloads remain for service/tests; they do not grant HTTP access.

## Structured request contract

Example optional intent object (future authenticated HTTP integration):

```json
{
  "requiredOutcome": "SIMULATED_BANK_INR",
  "deliverBy": "2099-01-01T00:00:00Z",
  "maximumFee": 25.00,
  "feeCurrency": "USD",
  "priority": "MEET_DEADLINE",
  "allowedAlternatives": [],
  "confirmed": true
}
```

The distant example deadline is a test/documentation value, not a delivery SLA.
Use the actual customer deadline when the authenticated API is connected.
The parent payment retains senderAccountId, beneficiaryId, amount, source currency
USD, purpose and requestedRail. Existing fields are unchanged.

- requiredOutcome: USDC_WALLET, SIMULATED_BANK_USD or SIMULATED_BANK_INR.
- deliverBy: an absolute timestamp deserialized to Instant, stored in UTC; it must
  be later than the server clock for a new payment. A previously accepted replay
  remains a replay after its deadline; it does not execute another payment.
- maximumFee: nonnegative USD amount, at most two decimal places and 16 integer
  digits. It cannot exceed the configured customer fee cap. No fee is collected.
- feeCurrency: USD only in this increment; FX conversion of fee limits is deferred.
- priority: LOWEST_COST or MEET_DEADLINE. Ranking is not implemented yet.
- allowedAlternatives: explicit distinct alternative outcomes, possibly empty;
  AUTO is required to permit alternatives. Every outcome must be allowed by
  customer policy and supported by beneficiary configuration.
- confirmed: true is required. Natural-language extraction is not implemented;
  an unconfirmed draft cannot become a payment instruction.

Wallet delivery requires an EVM-format beneficiary wallet. This is format
validation, not live ownership, sanctions or network validation. Simulated bank
credit requires the beneficiary's configured currency to match the payout.
STABLECOIN cannot satisfy a bank-credit requirement; STABLECOIN_PLUS_VISA cannot
satisfy a wallet-only requirement. VISA-only is unsupported for a new stablecoin
intent. AUTO does not mean a route has been chosen.

## Server-side policy configuration

No default customer policy is installed. Configuration is owned by payment-service
and maps the verified customer UUID to an immutable policy, for example:

```yaml
app:
  routing:
    policies:
      "11111111-1111-1111-1111-111111111111":
        version: demo-v1
        allowed-outcomes:
          - USDC_WALLET
          - SIMULATED_BANK_INR
        allowed-provider-ids:
          - SIMULATED-PROVIDER-A
          - SIMULATED-PROVIDER-B
        maximum-fee-usd: 50.00
        required-approvals: 2
```

The UUID and provider names are fictional configuration examples; providers are
not yet integrated. Version, nonempty outcomes/providers, valid monetary limits
and at least one approval are required. Store policy configuration server-side;
requests cannot select their policy version, providers or approval count.
Approval execution and provider assessment are later increments. A recorded
required-approvals value does not mean approvals have occurred.

## Persistence and compatibility

V8__add_payment_intent.sql adds nullable JSONB intent_snapshot to PAYMENT and
allows destination_currency to be null only when an intent snapshot exists.
The snapshot stores intentId, intentVersion, customer scope, confirming actor,
confirmation time, USDC settlement asset, simulation flags, confirmed instruction
and the full policy snapshot. Payment/correlation identifiers remain on PAYMENT.
Policy configuration changes do not mutate historical snapshots.

For new bank-credit intents, PAYMENT.destination_currency and the existing event
payload use the requested USD or INR, rather than copying the source currency.
For wallet-only intents, destination_currency is null and the settlement asset
is explicitly USDC. USDC is not inserted into a three-character fiat field.
Legacy rows/requests keep their previous USD destination semantics. Legacy GET
responses omit the new intent property when absent. No existing event fields
are renamed, and the current consumer tolerates a null fiat destination.

The legacy SHA-256 fingerprint is byte-for-byte unchanged when intent is absent.
For new intent requests, the fingerprint adds a versioned canonical representation
of required outcome, deadline, normalized fee, fee currency, priority, sorted
alternatives and confirmation. Equivalent decimal/set representations replay;
material changes conflict. Policy version is server-selected, not a client
fingerprint field: an identical replay returns the original policy snapshot.

Validation is performed before writes. Creation continues to use the existing
transaction and outbox boundary; this increment does not resolve concurrent
first-write idempotency races in the legacy payment implementation.

## Files and verification

- api/dto/PaymentIntentRequest.java and intent/*: typed intent, policy, trusted
  customer boundary, validation and immutable snapshots.
- config/RoutingPolicyProperties.java: server-owned policy registry.
- service/PaymentService.java: authorized overloads, snapshot persistence,
  destination projection, canonical fingerprint and scoped reads.
- api/dto/CreatePaymentRequest.java, api/dto/PaymentResponse.java,
  model/Payment.java: optional request/response and persisted JSONB mapping.
- exception/PaymentAccessException.java, api/GlobalExceptionHandler.java:
  structured 401/403 errors without changing existing legacy error mappings.
- db/migration/V8__add_payment_intent.sql: additive schema migration.
- New unit/HTTP tests cover policy binding, scope, validation, destination
  semantics, replay, historical policy, immutable collections and JSON mapping.
- integration/PaymentIntentPersistenceIT.java exercises actual PostgreSQL
  migrations and JSONB reload with Testcontainers when Docker is available.

Run the payment-service unit/HTTP suite with Java 21:

```powershell
$env:JAVA_HOME = 'C:\Users\884412\tools\jdk-21'
.\services\mvnw.cmd '-Dmaven.repo.local=C:\Users\884412\.m2\repository' -o -f services/pom.xml -pl payment-service test
```

The local Maven cache path is an environment-specific example. Database integration
requires Docker and PostgreSQL Testcontainers. Compilation of an integration
test is not evidence that its database execution passed.

## Next increment

Implement the seven-signal evidence contracts, freshness/scope checks,
itemized comparable cost assessments and labeled scenario fixtures. Connect
verified customer authentication before exposing intent-bearing HTTP creation
or reads. Routing, approvals, reservations and actual agent tools remain pending.


## Recorded verification (2026-11-14)

Java 21 compilation and packaging passed. Command: the Maven invocation above
with `-Dit.test=PaymentIntentPersistenceIT verify` in place of `test`.
All 61 unit/HTTP tests passed (zero failures/errors/skips). The single targeted
PostgreSQL integration test was skipped: Testcontainers reported Docker unavailable.
The JSON serializer round trip passed as a unit test; actual migration and JSONB
reload still require running that integration test with Docker. No claim is made
that existing PaymentApiIT, real inference or testnet execution passed.

Step 5 supersedes the earlier authentication/approval TODOs above. Runtime agent and trusted service integration remain later steps; see [current delivery status](21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md).
