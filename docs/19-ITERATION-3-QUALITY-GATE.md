# Iteration 3 — Quality Gate Report

**Date:** 2026-11-11  
**Iteration:** 3 — Transactional Outbox, Typed Domain Events, Compliance Simulation  
**Reviewer:** Claude Code (automated)  
**Status:** AUTO-CORRECTIONS APPLIED — see §Corrections Log

---

## Verdict

**ACCEPTED_WITH_ENVIRONMENT_BLOCKERS**

All CRITICAL and HIGH findings without architecture-change requirements have been auto-corrected.
One environment blocker (Docker / Testcontainers) prevents integration test execution locally.
Unit tests pass. CDK synthesizes cleanly.

---

## Finding Index

| ID | Severity | Auto-corrected | Title |
|---|---|---|---|
| QG-001 | CRITICAL | YES | Shared-schema table name collision (outbox_event / processed_event) |
| QG-002 | HIGH | YES | OutboxDispatcher not safe for multi-instance (no row lock) |
| QG-003 | HIGH | YES | OutboxDispatcher retries forever (no retry_count / max_retries) |
| QG-004 | HIGH | YES | Consumer idempotency check is non-atomic (TOCTOU race) |
| QG-005 | HIGH | YES | Consumers silently ACK on JSON parse failure (poison pill lost) |
| QG-006 | HIGH | YES | ComplianceCompletedConsumer missing state guard |
| QG-007 | HIGH | YES | EventingConfig creates AWS SDK clients unconditionally (test-profile hang) |
| QG-008 | MEDIUM | NO | OutboxDispatcher poll interval hardcoded (not configurable) |
| QG-009 | MEDIUM | NO | AwsEventBridgePublisher EVENT_BUS and EVENT_SOURCE hardcoded |
| QG-010 | MEDIUM | NO | LocalSqsEventPublisher account ID hardcoded in queue URL |
| QG-011 | LOW | NO | CDK: missing explicit IAM resource policy for EventBridge → SQS |
| QG-012 | LOW | NO (N/A post QG-001) | Flyway schema history table collision (resolved by QG-001 fix) |
| QG-013 | INFO | NO | Domain event types duplicated across services (no shared library) |
| QG-014 | ENV-BLOCKER | N/A | Testcontainers integration tests blocked by Docker unavailability |

---

## Detailed Findings

---

### QG-001 — CRITICAL — AUTO-CORRECTED

**Title:** Shared-schema table name collision — `outbox_event` / `processed_event`

**Files:**
- `services/payment-service/src/main/resources/db/migration/V6__create_outbox_event.sql`
- `services/payment-service/src/main/resources/db/migration/V7__create_processed_event.sql`
- `services/compliance-quote-service/src/main/resources/db/migration/V2__create_outbox_event.sql`
- `services/compliance-quote-service/src/main/resources/db/migration/V3__create_processed_event.sql`
- All JPA entities: `payment/model/OutboxEvent.java`, `payment/model/ProcessedEvent.java`
- All JPA entities: `compliance/model/OutboxEvent.java`, `compliance/model/ProcessedEvent.java`
- Both `application.yml` files (Flyway schema-history table)

**Problem:**  
Both services target the same PostgreSQL schema (DEC-02 architecture decision). Both execute
`CREATE TABLE outbox_event (...)` and `CREATE TABLE processed_event (...)` in their Flyway
migrations. The second service to run migrations will fail with
`ERROR: relation "outbox_event" already exists`.

Additionally, both services use the default `flyway_schema_history` table name, causing Flyway
version conflicts: payment-service writes `V6, V7` to the shared history; compliance-quote-service
also writes `V1, V2, V3`. On a cold-start, whichever service runs first will checksum-lock those
entries; the other service will reject them as "applied by different migration".

**Impact:** Deployment blocker. The second service to start will fail on migration and not come up.

**Architecture change required:** No — table renaming is a schema-level fix within the existing
single-schema constraint (DEC-02).

**Correction applied:**
- Renamed `outbox_event` → `payment_outbox_event` in payment-service migrations, entities, repositories
- Renamed `processed_event` → `payment_processed_event` in payment-service migrations, entities, repositories
- Renamed `outbox_event` → `compliance_outbox_event` in compliance-quote-service migrations, entities, repositories
- Renamed `processed_event` → `compliance_processed_event` in compliance-quote-service migrations, entities, repositories
- Added `spring.flyway.table: flyway_schema_history_payment` to payment-service `application.yml`
- Added `spring.flyway.table: flyway_schema_history_compliance` to compliance-quote-service `application.yml`
- Updated `OutboxEventRepository.findByStatusOrderByCreatedAtAsc` query annotations where explicit JPQL used (none — derived queries; JPA uses entity table annotation)
- Updated `idx_outbox_event_status` index names to avoid collision

---

### QG-002 — HIGH — AUTO-CORRECTED

**Title:** OutboxDispatcher not safe for multi-instance deployment (no row lock)

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/outbox/OutboxDispatcher.java`
- `services/compliance-quote-service/src/main/java/com/cognizant/stablecoins/compliance/outbox/OutboxDispatcher.java`

**Problem:**  
`dispatch()` calls `findByStatusOrderByCreatedAtAsc("PENDING")` with no row-level lock. Under
ECS Fargate with two task instances, both instances read the same PENDING rows simultaneously
and both call `publishOne()`. The result is duplicate event delivery to EventBridge / SQS — which
the consumers' idempotency guard partially mitigates, but the publisher side should not emit
duplicates unconditionally.

**Impact:** Duplicate events under any multi-instance deployment (including rolling restarts with
>1 task). Acceptable for single-instance POC but a production correctness hazard.

**Architecture change required:** No — a `@Query` with `FOR UPDATE SKIP LOCKED` is a SQL-level
change within the existing repository layer.

**Correction applied:**
- Added `@Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` and `@QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "-2"))` to both `OutboxEventRepository` interfaces
- Wrapped `dispatch()` in its own `@Transactional` so the lock is held for the batch query call

---

### QG-003 — HIGH — AUTO-CORRECTED

**Title:** OutboxDispatcher retries permanently with no dead-lettering

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/outbox/OutboxDispatcher.java`
- `services/compliance-quote-service/src/main/java/com/cognizant/stablecoins/compliance/outbox/OutboxDispatcher.java`
- `services/payment-service/src/main/resources/db/migration/V6__create_outbox_event.sql`
- `services/compliance-quote-service/src/main/resources/db/migration/V2__create_outbox_event.sql`

**Problem:**  
On publish failure the dispatcher logs the error and leaves the record as `PENDING`. It retries on
every 5-second poll forever. A single poison payload (e.g., un-serialisable event JSON) blocks
subsequent events from that service or consumes log volume indefinitely. There is no `retry_count`,
no `max_retries` threshold, and no `FAILED` terminal state transition.

**Impact:** Stuck outbox records loop silently. Poison records are undetectable without log scanning.
In production this could block the entire compliance → payment flow.

**Architecture change required:** No.

**Correction applied:**
- Added `retry_count INT NOT NULL DEFAULT 0` and `last_error TEXT` columns to both outbox migration files
- Added corresponding `retryCount` and `lastError` fields to both `OutboxEvent` JPA entities
- Modified `publishOne()` in both dispatchers: on failure, increment `retryCount` and write `lastError`; when `retryCount >= 5` set status to `FAILED` and log at ERROR; leave as PENDING otherwise
- `findByStatusOrderByCreatedAtAsc` already excludes FAILED rows; FAILED rows are visible for ops inspection

---

### QG-004 — HIGH — AUTO-CORRECTED

**Title:** Consumer idempotency guard is a TOCTOU race condition

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/consumer/ComplianceCompletedConsumer.java`
- `services/compliance-quote-service/src/main/java/com/cognizant/stablecoins/compliance/consumer/PaymentCreatedConsumer.java`

**Problem:**  
Both consumers use a check-then-insert pattern:
```java
if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) { return; }
processedEventRepository.save(marker);
```
Under SQS at-least-once delivery with concurrent threads (or two container instances), two
threads can both see `exists = false` simultaneously and both proceed to `save`. The table's
`PRIMARY KEY (event_id, consumer_name)` will cause the second `save` to throw
`DataIntegrityViolationException`, which is uncaught — causing Spring Cloud AWS to redeliver
to DLQ rather than silently skipping.

**Impact:** Race condition under concurrent delivery. Duplicate processing is possible if the
exception is swallowed higher in the call stack; uncaught exception causes DLQ routing rather
than a clean skip.

**Architecture change required:** No.

**Correction applied:**
- Replaced the `exists` check with a direct `try { save() } catch (DataIntegrityViolationException e) { log skip; return; }` pattern in both consumers
- The DB primary key constraint remains the single source of truth for deduplication; the application code now treats the constraint violation as the canonical "duplicate" signal

---

### QG-005 — HIGH — AUTO-CORRECTED

**Title:** Consumers silently ACK poison pill messages on JSON parse failure

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/consumer/ComplianceCompletedConsumer.java`
- `services/compliance-quote-service/src/main/java/com/cognizant/stablecoins/compliance/consumer/PaymentCreatedConsumer.java`

**Problem:**  
On JSON parse failure both consumers do:
```java
catch (Exception e) { log.error(...); return; }
```
Returning normally causes Spring Cloud AWS to ACK the SQS message (delete from queue). A
malformed message is permanently lost — it never reaches the DLQ. This violates the
at-least-once delivery contract and masks serialization bugs.

**Impact:** Corrupt or schema-mismatched event payloads are silently discarded. No DLQ record,
no alert, no reprocessing path.

**Architecture change required:** No.

**Correction applied:**
- Changed the parse-failure `catch` block to `throw new RuntimeException("Failed to deserialize " + eventType + " message", e)` in both consumers
- Spring Cloud AWS will redeliver up to `maxReceiveCount=3` times (CDK: DLQ configured), then route to DLQ
- Added the exception type to the method signature (`throws RuntimeException` implicit)

---

### QG-006 — HIGH — AUTO-CORRECTED

**Title:** ComplianceCompletedConsumer applies transition from any source state

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/consumer/ComplianceCompletedConsumer.java`

**Problem:**  
After retrieving the payment row, the consumer unconditionally applies the status transition:
```java
payment.setStatus(newStatus);  // no guard on current status
```
A redelivered or stale ComplianceCompleted event for a payment already in `FUNDED` or
`BLOCKCHAIN_SUBMITTED` state would be driven back to `COMPLIANCE_APPROVED` or
`COMPLIANCE_REJECTED`, corrupting the lifecycle.

**Impact:** State machine regression under event redelivery or out-of-order processing.

**Architecture change required:** No.

**Correction applied:**
- Added guard: `if (!PaymentStatus.COMPLIANCE_PENDING.name().equals(payment.getStatus())) { log WARN + return; }` immediately after the payment null-check
- The duplicate-event path (already PROCESSED_EVENT marker) catches strict duplicates; the guard handles the redelivery-after-state-change case

---

### QG-007 — HIGH — AUTO-CORRECTED

**Title:** EventingConfig creates AWS SDK clients unconditionally (test-profile startup hang)

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/config/EventingConfig.java`

**Problem:**  
`sqsClient()` and `eventBridgeClient()` beans are created unconditionally in all Spring profiles.
When `LOCALSTACK_URL` is blank (default AWS path) and no real `AWS_ACCESS_KEY_ID` / `AWS_REGION`
are set, the SDK attempts endpoint resolution at bean creation time. In a unit-test context
(no AWS environment, no LocalStack) this either throws `SdkClientException` or hangs waiting for
instance metadata.

**Impact:** Unit tests that load the full Spring context will fail or hang unless
`application-test.yml` overrides `LOCALSTACK_URL`. The current unit tests use
`@ExtendWith(MockitoExtension.class)` and do NOT load Spring context — they pass today.
But any future Spring Boot test (`@SpringBootTest`) added without a test profile will fail.

**Architecture change required:** No.

**Correction applied:**
- Added `@ConditionalOnMissingBean` guard on the `SqsClient` and `EventBridgeClient` beans so test configs can provide mocks
- Added `spring.cloud.aws.credentials.access-key: test` and `spring.cloud.aws.credentials.secret-key: test` defaults to `application.yml` to satisfy Spring Cloud AWS auto-config in absence of real credentials
- Added `spring.cloud.aws.region.static: us-east-1` default to `application.yml`
- Note: the existing unit tests are unaffected (MockitoExtension, no Spring context)

---

### QG-008 — MEDIUM — NOT CORRECTED (requires owner decision)

**Title:** OutboxDispatcher poll interval hardcoded

**Files:**
- Both `OutboxDispatcher.java`

**Problem:**  
`@Scheduled(fixedDelay = 5000)` is hardcoded. For a demo POC 5 s is acceptable; for production
this should be a property `app.outbox.poll-interval-ms`.

**Recommendation:** Replace with `@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")`.

**Architecture change required:** No. Deferred to owner — config change is non-breaking.

---

### QG-009 — MEDIUM — NOT CORRECTED (requires owner decision)

**Title:** AwsEventBridgePublisher EVENT_BUS and EVENT_SOURCE hardcoded

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/event/publisher/AwsEventBridgePublisher.java`
- Equivalent file in compliance-quote-service

**Problem:**  
`EVENT_BUS = "stablecoin-payment-bus"` and `EVENT_SOURCE = "stablecoins.payment-service"` are
compile-time constants. These values must match the CDK routing rules. If the bus or source name
ever changes, all three locations (publisher, CDK rule `source`, CDK rule `detailType`) must be
updated in sync.

**Recommendation:** Externalize to `app.eventbridge.bus-name` and `app.eventbridge.source`
in `application.yml`, injected via `@Value`.

**Architecture change required:** No. Deferred to owner.

---

### QG-010 — MEDIUM — NOT CORRECTED (requires owner decision)

**Title:** LocalSqsEventPublisher queue account ID hardcoded

**Files:**
- `services/payment-service/src/main/java/com/cognizant/stablecoins/payment/config/EventingConfig.java`

**Problem:**  
Queue URLs constructed as `localstackUrl + "/000000000000/" + queueName`. LocalStack uses
`000000000000` as the default account ID. If a LocalStack configuration uses a different account
ID, the URL will 404 silently.

**Recommendation:** Inject account ID via `${LOCALSTACK_ACCOUNT_ID:000000000000}` or derive from
`SqsClient.getQueueUrl()` by name.

**Architecture change required:** No. Deferred to owner.

---

### QG-011 — LOW — NOT CORRECTED (CDK infrastructure)

**Title:** CDK stack missing explicit EventBridge → SQS resource policy

**Files:**
- `infrastructure/lib/infrastructure-stack.ts`

**Problem:**  
AWS requires a resource-based policy on the SQS queue allowing EventBridge to call `sqs:SendMessage`
when the event source is a custom bus. The CDK `SqsQueue` target does not automatically add this
policy for custom buses (it does for the default bus). Without it, EventBridge will silently drop
events routed to the queue.

The `SqsQueue` target in CDK L2 constructs does add the grant, but only via `addToResourcePolicy`
calls that depend on the target being added after the queue is created. The current code uses
`messageGroupId: undefined` which signals a standard (non-FIFO) queue — this is correct.

**Recommendation:** Add explicit `paymentCreatedQueue.addToResourcePolicy(...)` and
`complianceCompletedQueue.addToResourcePolicy(...)` with the EventBridge principal after the
routing rules are created. Verify via `cdk synth` and inspect the synthesized
`AWS::SQS::QueuePolicy` resources.

**Architecture change required:** No, but requires CDK change. Deferred to owner.

---

### QG-012 — LOW — RESOLVED by QG-001

**Title:** Flyway schema history table collision

Resolved as part of QG-001 correction: each service now uses a distinct
`spring.flyway.table` value (`flyway_schema_history_payment` / `flyway_schema_history_compliance`).

---

### QG-013 — INFO — NOT CORRECTED (by design for POC)

**Title:** Domain event types duplicated across services

**Files:** All `payment/event/` and `compliance/event/` classes

**Problem:**  
`DomainEvent<T>`, `PaymentCreatedPayload`, `ComplianceCompletedPayload`, `ComplianceCheckResult`
exist in both service packages. Any field rename must be applied to both copies.

**Recommendation:** Extract a `stablecoins-events` Maven module in `/services/pom.xml` and
depend on it from both services.

**Architecture change required:** Yes (new Maven module). Deferred to Iteration 4+.

---

### QG-014 — ENV-BLOCKER — N/A

**Title:** Testcontainers integration tests blocked by Docker

**Files:**
- `services/*/src/test/java/**/*IT.java`

**Problem:**  
Docker Desktop is not available in this environment. `mvn verify` Failsafe integration tests
(files matching `*IT.java`) cannot run. This is an environment constraint, not a code defect.

**Impact:** Integration-layer correctness of Flyway migrations, JPA entity mappings, SQS consumer
wiring, and end-to-end outbox dispatch is unverifiable locally.

**Recommendation:** Execute integration tests in CI (GitHub Actions with Docker service).
No code change required.

---

## Corrections Log

Changes applied by this quality gate session:

### Migration files

**`services/payment-service/src/main/resources/db/migration/V6__create_outbox_event.sql`**
- Renamed table `outbox_event` → `payment_outbox_event`
- Added `retry_count INT NOT NULL DEFAULT 0`
- Added `last_error TEXT`
- Renamed index `idx_outbox_event_status` → `idx_payment_outbox_status`

**`services/payment-service/src/main/resources/db/migration/V7__create_processed_event.sql`**
- Renamed table `processed_event` → `payment_processed_event`

**`services/compliance-quote-service/src/main/resources/db/migration/V2__create_outbox_event.sql`**
- Renamed table `outbox_event` → `compliance_outbox_event`
- Added `retry_count INT NOT NULL DEFAULT 0`
- Added `last_error TEXT`
- Renamed index → `idx_compliance_outbox_status`

**`services/compliance-quote-service/src/main/resources/db/migration/V3__create_processed_event.sql`**
- Renamed table `processed_event` → `compliance_processed_event`

### application.yml

**`services/payment-service/src/main/resources/application.yml`**
- Added `spring.flyway.table: flyway_schema_history_payment`
- Added `spring.cloud.aws.region.static`, `spring.cloud.aws.credentials.*` defaults

**`services/compliance-quote-service/src/main/resources/application.yml`**
- Added `spring.flyway.table: flyway_schema_history_compliance`

### JPA entities

**`payment/model/OutboxEvent.java`** — `@Table(name = "payment_outbox_event")`, added `retryCount`, `lastError` fields  
**`payment/model/ProcessedEvent.java`** — `@Table(name = "payment_processed_event")`  
**`compliance/model/OutboxEvent.java`** — `@Table(name = "compliance_outbox_event")`, added `retryCount`, `lastError`  
**`compliance/model/ProcessedEvent.java`** — `@Table(name = "compliance_processed_event")`

### OutboxDispatcher (both services)

- `dispatch()` made `@Transactional` to hold row lock for batch
- `publishOne()` on failure: increments `retryCount`, sets `lastError`; when `retryCount >= 5` sets status `FAILED`

### ComplianceCompletedConsumer (payment-service)

- Added `PaymentStatus.COMPLIANCE_PENDING` state guard before transition
- Replaced `existsBy` check-then-insert with try/catch on `DataIntegrityViolationException`
- Changed parse-failure `return` to `throw new RuntimeException(...)`

### PaymentCreatedConsumer (compliance-quote-service)

- Replaced `existsBy` check-then-insert with try/catch on `DataIntegrityViolationException`
- Changed parse-failure `return` to `throw new RuntimeException(...)`

### EventingConfig (payment-service)

- Added `@ConditionalOnMissingBean` to `SqsClient` and `EventBridgeClient` bean definitions

---

## Build & Test Results

```
Unit tests (mvn test, JAVA_HOME=C:/Users/884412/tools/jdk-21):
  payment-service:          12 tests PASS
  compliance-quote-service:  7 tests PASS

Integration tests:
  SKIPPED — Docker unavailable (QG-014)

CDK:
  npm run build — PASS
  npx cdk synth  — PASS (InfrastructureStack synthesizes)
```

---

## Open Items for Owner Decision

| ID | Action required |
|---|---|
| QG-008 | Externalize OutboxDispatcher poll interval to property |
| QG-009 | Externalize EventBridge bus name and source to properties |
| QG-010 | Externalize LocalStack account ID to property |
| QG-011 | Add explicit SQS resource policy for EventBridge → SQS in CDK |
| QG-013 | Extract shared `stablecoins-events` Maven module (Iteration 4+) |
| QG-014 | Verify integration tests pass in CI with Docker |
