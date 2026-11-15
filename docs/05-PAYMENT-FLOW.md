# Payment Flow

## Transaction

Amount:

USD 10,000

Payment purpose:

Supplier Invoice

## Flow

1. Corporate user authenticates.
2. User creates payment.
3. Payment Service creates paymentId.
4. correlationId is generated.
5. PaymentCreated event is emitted.
6. Compliance simulator checks customer/payment.
7. ComplianceCompleted event emitted.
8. Quote service produces simulated FX/fee quote.
9. User/system accepts quote.
10. Stablecoin rail selected.
11. USD-to-test-token funding is simulated.
12. Blockchain Service prepares transaction.
13. Gas requirement is checked.
14. Token transfer is signed.
15. Transaction submitted to real testnet.
16. txHash stored immediately.
17. BlockchainTransactionSubmitted event emitted.
18. Service monitors transaction.
19. Transaction becomes confirmed.
20. BlockchainTransactionConfirmed emitted.
21. Financial settlement is posted.
22. Optional Visa payout initiated.
23. Visa Simulator processes payout.
24. Internal ledger updated.
25. Reconciliation compares all legs.
26. Payment becomes RECONCILED.

## Important

A timeout after blockchain submission must NOT result in blindly sending
the transaction again.

The system must determine whether a transaction was already submitted
before retrying.
## Step 8 connected lifecycle

The detailed implemented [owner sequence](31-SETTLEMENT-PAYOUT-RECONCILIATION.md#owner-sequence-and-acknowledgements)
adds validated provider fiat reservation before bank-path funding, ledger
acknowledgements before submission/settlement/payout, complete actual receipt fees
and an immutable matching reconciliation report before RECONCILED. TESTNET is an
explicit opt-in; default MOCK is synthetic. Compliance, funding, conversion, FX,
Visa and bank credit remain simulated. React integration and live proof follow.
