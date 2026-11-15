Implement the event architecture.

Read:

docs/11-EVENT-CATALOG.md

Implement:

EventBridge configuration
SQS queues
DLQs

Implement event envelope:

eventId
eventType
eventVersion
paymentId
correlationId
timestamp
payload

Implement idempotent consumer infrastructure.

Implement retry/error handling.

Start with:

PaymentCreated
ComplianceCompleted
QuoteAccepted
RailSelected

Create CDK resources.

Test duplicate delivery.

STOP.