# Step 8 - Settlement, simulated payout and reconciliation

Updated: 2026-11-15. Code increment under accepted ADR-007, ADR-008,
ADR-014 and ADR-015. No additional deployable service or financial model tool.
Verification results are recorded in the delivery plan after the checks finish.

## Complete story and boundaries

A corporate customer instructs a USD 10,000 payment. A bounded routing agent can
reason over captured, typed evidence using the configured OpenAI or Bedrock runtime.
Deterministic services validate eligibility, immutable approvals and execution.
Funding, compliance, conversion, FX, provider signals, Visa and bank credit are
SIMULATED. The application ledger and reconciliation are real application code.
The blockchain adapter is MOCK by default; TESTNET must be explicitly enabled,
funded and demonstrated before claiming a public transfer. USDC is transferred;
ETH is consumed for gas. No USDC-to-ETH conversion is performed.

Supported beneficiary outcomes are USDC_WALLET, SIMULATED_BANK_USD and
SIMULATED_BANK_INR. Bank routes compare equivalent simulated payout providers,
including their fiat capacity, rather than competing against incomplete wallet
routes. Source USD and transfer USDC remain notionally 1:1 in this connected POC.
USD payout FX is 1.00; INR defaults to an illustrative 83.00, never a market quote.
Payout is source USD times the captured rate, HALF_EVEN to two decimal places.
Quote cost estimates are assessment inputs, not an additional customer fee debit
or recognized fee revenue. This is an operational control subledger, not a claim
of complete statutory accounting, custody or production banking integration.

## Owner sequence and acknowledgements

1. Existing compliance-quote-service captures compliance, rate, provider terms and
   available simulated fiat. Treasury captures USDC, ETH and gas readiness. The
   recommendation, quote acceptance, distinct approvals and fresh validation remain
   mandatory. The runtime model can explain evidence, never authorize money.
2. StablecoinExecutionRequested fans out to validation, treasury reservation and
   visa-simulator owner queues. For bank outcomes, the VisaPaymentAdapter reserves
   and pins the exact provider/currency/payout amount after validation. Payment
   funding waits for this owner acknowledgement, preventing an unbacked bank path.
3. Existing payment-service simulates the USD debit exactly once. The ledger owner
   posts FUNDING, CONVERSION_USD and CONVERSION_USDC. Submission waits for those
   three immutable journals and the existing bound treasury reservation.
4. Existing blockchain-service signs and submits the USDC transfer. Existing
   submission/confirmation consumers post BLOCKCHAIN_SUBMISSION and
   BLOCKCHAIN_CONFIRMATION. Unknown signed submissions remain pinned; no new
   transaction, provider switch or refund is inferred.
5. The blockchain owner persists receipt fee components and confirmation depth.
   BlockchainReceiptObserved triggers the ledger NETWORK_FEE posting. A reverted
   transaction can still incur a real fee, but cannot create successful settlement.
6. For a confirmed execution, the payment closure worker emits SettlementRequested.
   The ledger owner verifies all six expected journals and records completed
   settlement linked to the confirmation and fee journals. Payment waits for that
   acknowledgement before SETTLEMENT_COMPLETED and SettlementCompleted.
7. Wallet outcomes request reconciliation directly. Bank outcomes enter
   VISA_PAYOUT_PENDING and emit VisaPayoutInitiated. The simulator reuses the
   pinned reservation and captures one immutable scenario/reference. The ledger
   atomically posts OFFRAMP_USDC and PAYOUT_INITIATION_USD/INR. Only after those
   entries exist does the simulator complete or decline its payout.
8. Success consumes the provider's simulated fiat once. Payment observes the
   canonical success and emits VisaPayoutCompleted; the ledger posts
   PAYOUT_COMPLETION_USD/INR. Payment waits for that journal before
   VISA_PAYOUT_COMPLETED and RECONCILIATION_PENDING. Wallet paths omit these legs.
9. ReconciliationRequested produces an immutable owner report from a repeatable-read
   snapshot. Exact amounts, accounts, per-asset balance, references, policy/quote
   provenance, receipt fees, confirmation depth and simulated payout consumption
   must agree. Payment accepts MATCHED only if a fresh evidence fingerprint still
   agrees, then transitions to RECONCILED and emits ReconciliationCompleted.

Payment state/history/outbox changes share the payment transaction. Journal writes
remain exclusively in settlement-ledger-service through LedgerPostingService and
PaymentAccountingService. Reports do not repair financial data. Corrections use
reversals or compensating entries under ADR-015. Shared-schema cross-owner reads
follow ADR-007/008; nobody writes another service's financial tables.

The separate payment_closure row tracks the asynchronous business phase after the
existing execution becomes CONFIRMED. Its phases are SETTLEMENT_REQUESTED,
SETTLED, PAYOUT_REQUESTED, PAYOUT_COMPLETION_REQUESTED,
RECONCILIATION_REQUESTED, COMPLETED and REVIEW_REQUIRED. A blockchain-confirmed
payment is not yet a reconciled payment.

## Exact receipt fee evidence

The raw RPC receipt is parsed because standard receipt models may omit L2 fields.
Execution cost is gasUsed times effectiveGasPrice. On Base Sepolia, the actual
l1Fee is also required; actual operatorFee is included when present. Nonzero
operator fee parameters without an actual supported fee amount remain incomplete;
the application does not guess a fork-dependent formula. Ethereum Sepolia uses
its supported execution fee fields. All integer wei values are preserved exactly.
See the official [Base fee specification](https://docs.base.org/specifications/transactions/network-fees)
and [Optimism execution specification](https://specs.optimism.io/protocol/jovian/exec-engine.html).

Only complete receipt evidence can support NETWORK_FEE or final reconciliation.
The estimate used before signing remains an estimate. In MOCK mode all receipt
values, including complete synthetic fees, are explicitly MOCK evidence. Existing
legacy receipts start incomplete; migrations do not invent historical fees.
The RPC client reuses its configured connection pool. Web3j 6.1 uses Jackson 3.2
internally, so blockchain-service explicitly aligns its shared annotations to
2.22 while retaining Spring Boot Jackson 2 databind. A synthetic loopback HTTP
test checks both Web3j response parsing and preservation of raw receipt fees;
this is transport compatibility evidence, not a public chain transaction. See
[Jackson 3.2 annotation compatibility](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.2).

The public runner independently verifies the successful receipt, configured
network, exact USDC Transfer log, confirmation depth and receipt fee total.

## API and permissions

All customer GETs require verified identity, PAYMENT_OPERATOR membership and
payment ownership. Narrative treasury/controller/auditor personas do not imply
additional implemented RBAC roles.

| Owner | Method and path | Purpose |
|---|---|---|
| payment | GET /api/v1/payments/{paymentId}/closure | Business phase and blocking reason |
| blockchain | GET /api/v1/blockchain/transactions/{id} | Bound receipt fields and exact fee wei strings |
| ledger | GET /api/v1/payments/{paymentId}/ledger | Immutable journals; exactDebit/exactCredit strings preserve tiny ETH amounts |
| visa | GET /api/v1/payments/{paymentId}/payout | Labeled SIMULATED provider result |
| reconciliation | GET /api/v1/payments/{paymentId}/reconciliation | Latest immutable report, findings and source fingerprint |
| visa | POST /visa-simulator/v1/prefund | Explicit fictional provider funding |
| visa | POST /visa-simulator/v1/payouts/{id}/resolution | Conclusive UNKNOWN payout resolution |
| reconciliation | POST /api/v1/payments/{paymentId}/reconciliation-runs | Explicit new report after evidence repair |
| blockchain | POST /api/v1/blockchain/transactions/{id}/receipt-evidence | Refresh missing fee components for the same mined receipt |

Internal operations require the configured service client and internal scope.
Reconciliation reruns additionally require payment ownership/customer membership.
Visa write operations additionally require VISA_SIMULATOR_OPERATIONS_ENABLED=true,
which defaults false. Prefund, payout resolution and reconciliation runs require
UUID Idempotency-Key headers. Resolution body is {"succeeded":true} or false;
prefund body is {"providerId":"SIMULATED-PROVIDER-A","currency":"INR","amount":830000.00}.
Receipt refresh also requires the existing blockchain execution API gate. It
checks the original hash, block, status, gas and any already-known price and never
signs or submits. A previously missing gas price may be filled from that same
receipt; conflicting known evidence is rejected.
Do not use customer tokens for privileged operations or put tokens in evidence.

## Configuration and delivery

All connections continue through services/config/connections.properties and the
private Compose allowlist. FLOW_ENABLED and FLOW_EXECUTION_ENABLED default false.
Existing blockchain execution/security gates still apply. New settings:

| Environment setting | Default | Meaning |
|---|---|---|
| FLOW_SIMULATED_FX_USD | 1.00 | Captured simulated USD payout rate; must remain one |
| FLOW_SIMULATED_FX_INR | 83.00 | Illustrative simulated INR rate |
| VISA_SIMULATOR_PAYOUT_SCENARIO | SUCCEEDED | SUCCEEDED, DECLINED or TIMEOUT; captured once per payout |
| VISA_SIMULATOR_OPERATIONS_ENABLED | false | Explicit internal simulation operations gate |

Five new queue/DLQ pairs: receipt-observed, settlement-requested, visa-payout,
ledger-payout and reconciliation-requested. Names are configurable through the
SQS_*_QUEUE_NAME and SQS_*_DLQ_NAME aliases in the central file. CDK now defines
14 EventBridge rules and 32 SQS queues including DLQs. Queue targets consume
$.detail, the domain envelope, with eventId/eventType/eventVersion/paymentId/
correlationId/timestamp. Queues use encryption, TLS, 120-second visibility and
maxReceiveCount=3. Local fanout reproduces owner isolation; all delivery is
idempotent and tolerates out-of-order acknowledgements through retries.

## Codespaces demonstration: setup and screen-by-screen narrative

Start with the overall story above. Actual connected React screens arrive in
step 9. Until then use the Codespaces terminal/API responses; proposed UI names
below are narration contracts, not working links. The offline HTML prototype
cannot demonstrate live ledger, model or blockchain evidence.

1. **Open Codespaces terminal / service health views. Role: deployment operator.**
   Explain that six existing services and their owner migrations must be running
   before any demo instruction. Follow [container setup](18-CONTAINERIZATION-CODESPACES.md)
   and [identity, seed and wallet setup](30-PAYMENT-FLOW-INTEGRATION.md#codespaces-demonstration).
   Supply verified operator and distinct approver identities with matching
   customer memberships. Configure internal service credentials separately.
   Expected: six healthy services, migrations applied and no secrets in output.
2. **Open proposed Payment Intent. Role: customer operator.** Explain the intended
   beneficiary outcome before creating anything. Wallet delivery uses the existing
   fictional USD beneficiary. For INR bank delivery apply scripts/seed-bank-demo.sql
   after the wallet seed, supplying the same approved nonzero supplier wallet.
   The script preserves existing balances and rejects conflicting beneficiary data.
   Allow the chosen outcome in the customer's server-owned policy. USD bank delivery
   uses the USD beneficiary. Expected: the requested outcome and exact recipient
   remain bound to the payment, quote and approval.
3. **Open proposed Provider Liquidity / internal API terminal. Role: authorized
   simulation operator.** Explain that a simulated bank route needs explicit
   fictional provider fiat before it can be eligible. Enable the operations gate
   in the private environment, then POST /visa-simulator/v1/prefund with the
   internal token, a stable UUID Idempotency-Key, provider/currency and sufficient
   amount. Prefund each candidate being compared; never edit database totals.
   Repeating the same operation must not add funds twice. INR at the example rate
   needs INR 830,000 for a USD 10,000 payment. Disable privileged operations when
   no longer needed. Expected: labeled simulated balances, not a real bank deposit.
4. **Open proposed Recommendation and Approval. Roles: operator, then independent
   approvers.** Explain why comparable providers and the seven signals matter.
   Set DEMO_OUTCOME to USDC_WALLET, SIMULATED_BANK_USD or SIMULATED_BANK_INR.
   Use the existing runner variables and tokens documented in docs/30, selecting
   the matching beneficiary. To demonstrate actual agent reasoning set
   DEMO_RUN_AGENT=true only with a working configured runtime; deterministic
   assessment by itself does not prove model inference. Expected: captured terms,
   eligible recommendation, immutable quote acceptance and distinct approvals.
5. **Open proposed Execution / Blockchain Evidence. Role: treasury operator.**
   Explain why test USDC and test ETH must already exist. After setting a new
   DEMO_RUN_ID UUID for a new instruction (retain it for replay), run:

   ```bash
   node scripts/run-payment-lifecycle.cjs
   ```

   This reuses the wallet runner with full-lifecycle verification enabled. MOCK
   is the initial connectivity rehearsal. For public proof explicitly configure
   TESTNET, approved RPC/network/token/wallet secrets and funded test balances;
   set DEMO_EVIDENCE_MODE=TESTNET and DEMO_RPC_URL in the private environment.
   Expected: one USDC transaction; in TESTNET independently verified public receipt
   and USDC log. Do not present MOCK output as public blockchain proof.
6. **Open proposed Ledger. Role: finance controller.** Explain that each asset
   balances independently. Inspect the ledger GET response for six wallet journals
   or nine bank journals. Read exactDebit/exactCredit for ETH amounts. Expected:
   original funding/conversion/submission/confirmation entries plus actual receipt
   NETWORK_FEE; bank paths also have USDC off-ramp and fiat initiation/completion.
   No cross-asset netting or mutable posted entry is permitted.
7. **Open proposed Payout. Role: payout operator.** Explain the optional off-ramp
   before opening the payout GET response. Wallet outcomes omit this scene.
   Expected: one SIMULATED-VISA reference, exact accepted USD/INR amount and
   SUCCEEDED status. This demonstrates simulated beneficiary bank credit only.
8. **Open proposed Reconciliation. Role: auditor.** Explain why success at one
   external system is insufficient. Read the report, closure and final payment.
   Expected: MATCHED report with source IDs/findings/fingerprint, COMPLETED closure
   and RECONCILED payment. The runner independently checks exact fee/journal/payout
   evidence and exports only if its existing evidence-output option is configured.
   Store this actual artifact and its mode/date as demo proof after a real run.

## Failure and recovery demonstration

Use a separate instruction/run ID for each scenario; retain its IDs for retries.
Do not modify posted production journals to manufacture a mismatch.

- TIMEOUT captures UNKNOWN and retains the provider's PINNED fiat capacity.
  The closure exposes PAYOUT_UNKNOWN_PINNED. Investigate first, then use the
  privileged resolution endpoint with a stable operation UUID and conclusive
  succeeded/failed outcome. Repeating it is idempotent; a conflicting outcome is
  rejected. The demo runner stops on UNKNOWN rather than silently starting over.
- DECLINED produces FAILED and REVIEW_REQUIRED with no asserted bank credit,
  automatic USD refund or fabricated compensating entries. Accounting treatment
  of the resulting operational exception requires explicit reviewed compensation.
- Incomplete receipt fees block final settlement/reconciliation. Refresh evidence
  for the original mined transaction only. If the original command has exhausted
  retries, inspect and redrive that original DLQ event after repair. A pending or
  mismatched report can be rerun using the privileged reconciliation endpoint;
  retain all earlier reports, rather than overwriting them.
- A provider hold pinned before funding is deliberately not automatically released
  after an uncertain chain outcome. An abandoned pre-funding instruction currently
  needs reviewed owner-side reservation resolution; a general operator UI for
  orphan holds and financial compensation is an operational follow-up for step 10.
- Use the evidence-validator negative tests as the current safe discrepancy
  demonstration. An authenticated UI discrepancy-injection workflow is not shipped.
  Missing, extra, reversed, cross-asset, incorrectly bound or wrong-amount journals
  never become a successful reconciliation just because totals happen to net.

## Source inventory and remaining acceptance

Shared platform-flow contains typed canonical read models and the read-only
ControlLedgerEvidence/ClosureEvidence comparators. Payment owns closure worker,
state and outbox. Blockchain owns raw receipt parsing and fee refresh. Ledger
owns LifecycleAccountingConsumer and uses the existing posting templates. Visa
owns its adapter, payout lifecycle and audit. Reconciliation owns reports/details.
Migrations: payment V12, blockchain V6, ledger V8, visa V2, reconciliation V1.
All six Docker build contexts include their required shared libraries.

Unit checks exercise comparison, fee parsing, closure and gating. PostgreSQL
Testcontainers checks are executable in Codespaces; local Docker is unavailable.
Reconciliation persistence tests use real PostgreSQL with a stubbed source capture,
so they do not establish a full six-service source-join or end-to-end proof.
No live identity, model, AWS deployment or public transfer is claimed by coding.
Step 9 supplies connected React views; step 10 completes deployment/operations;
step 11 records full proof and creates the final Word architecture/design document
from the cumulative documentation. The owner has selected GitHub repository kirankumarns-Cognizant/StableCoins;
publication and live acceptance must be recorded when executed.

## Verification record

Final Java reactor verification and targeted blockchain verification succeeded: 546 cases passed, zero failures/errors, 54 skipped (53 database/container, one opt-in model). Node 30/30 and CDK 11/11 passed; CDK TypeScript compiled. Counts include each latest module/test class once. See [exact scopes](step8-build-results.json). Live six-service/testnet acceptance remains unexecuted.

## Connected UI continuation - step 9

The connected React workspace now implements the proposed payment screens.
Use [its setup/API/security guide](32-CONNECTED-REACT-WORKSPACE.md) and the
[complete role/context/action/outcome runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md).
Earlier verification records above remain historical; current scope is recorded
in [step-9 verification](step9-build-results.json).
