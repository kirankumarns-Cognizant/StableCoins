Implement Visa Simulator.

Read:

docs/07-VISA-SIMULATOR.md
ADR-003

Create:

VisaPaymentAdapter
VisaSimulatorAdapter

Implement:

POST /visa-simulator/v1/prefund
POST /visa-simulator/v1/payouts
GET /visa-simulator/v1/payouts/{id}
POST /visa-simulator/v1/payouts/{id}/cancel
GET /visa-simulator/v1/balances

States:

ACCEPTED
PROCESSING
SENT
COMPLETED
DECLINED
FAILED
TIMEOUT
COMPLIANCE_REVIEW
INSUFFICIENT_PREFUND

Support deterministic failure injection.

Every response must be clearly identifiable as a Visa simulation.

Add integration tests.

STOP.