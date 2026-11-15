# Test Plan

## Functional

1. successful payment
2. compliance rejection
3. quote expiration
4. insufficient simulated funding
5. successful blockchain transaction
6. invalid destination wallet
7. insufficient gas
8. blockchain RPC failure
9. transaction timeout
10. duplicate payment submission
11. duplicate event
12. Visa decline
13. Visa timeout
14. ledger failure
15. reconciliation mismatch

## Critical Scenario

USD 10,000

→ simulated compliance PASS
→ simulated quote
→ stablecoin rail
→ real testnet token transfer
→ transaction confirmed
→ simulated Visa payout
→ ledger posted
→ reconciliation MATCHED

## Idempotency Test

Submit same request twice with identical:

Idempotency-Key

Expected:

single Payment.

## Event Duplicate Test

Deliver BlockchainTransactionConfirmed twice.

Expected:

single settlement posting.

## Blockchain Safety Test

Simulate timeout after transaction submission.

Expected:

system checks txHash/status.

System MUST NOT blindly submit another transfer.

## Ledger Test

For every transaction:

Total Debit = Total Credit.

## Reconciliation

Test:

MATCHED
PARTIALLY_MATCHED
MISMATCH
PENDING
## Step 8 verification scope

Add exact per-asset closure/provenance tests, Base/Ethereum receipt fee parsing,
same-transaction fee recovery without signing/submission, synthetic loopback RPC
JSON compatibility, payment closure acknowledgement gates, bank provider capacity,
simulated payout idempotence/UNKNOWN resolution, and reconciliation mismatch/
rollback checks. Visa and reconciliation PostgreSQL *IT classes are bound to
Maven verify; no Docker means explicit skips. Reconciliation persistence tests
stub canonical capture and do not replace full six-service acceptance. The full
opt-in scripts/run-payment-lifecycle.cjs checks RECONCILED and canonical journals;
TESTNET additionally requires independent public receipt, USDC log and exact fees.
See [execution and limitations](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
