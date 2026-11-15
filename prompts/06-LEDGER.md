Implement the double-entry ledger.

Read:

docs/10-LEDGER.md

Requirements:

Total Debit = Total Credit.

Posted entries are immutable.

Corrections use reversals.

Implement:

ledger accounts
journal transaction
journal entries
posting service
balance query
reversal operation

Prevent unbalanced posting.

Associate ledger transactions with:

paymentId
correlationId

Add tests for:

balanced transaction
unbalanced rejection
duplicate posting
reversal
blockchain settlement posting

STOP.