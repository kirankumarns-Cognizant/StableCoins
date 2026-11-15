# ADR-004: Event Architecture

Status: ACCEPTED

## Decision

Use Amazon EventBridge + Amazon SQS.

## Rationale

The POC requires:

- business event routing
- asynchronous delivery
- retries
- DLQ
- manageable AWS-native operations

Kafka/MSK is unnecessary for the scope of this POC.

## Consequence

Consumers must implement idempotency because messages/events may be
delivered more than once.