Implement Reconciliation Service.

Correlate:

Payment
Ledger
Blockchain Transaction
Visa Simulator Transaction

using:

paymentId
correlationId

and respective external/internal references.

Statuses:

MATCHED
PARTIALLY_MATCHED
MISMATCH
PENDING

Implement:

GET /api/v1/payments/{paymentId}/reconciliation

Do not compare only status.

Where applicable validate:

amount
asset
references
blockchain transaction status
ledger posting
Visa simulator status

Add integration tests.

STOP.