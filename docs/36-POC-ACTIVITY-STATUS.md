# POC activity status

Recorded 2026-11-23. **Owner-observed MOCK USDC wallet walkthrough completed through RECONCILED. Overall POC acceptance remains in progress.**

## Observed milestone

The owner supplied Codespaces health output, application screenshots and text,
a two-approval quote summary, execution references, a read-only PostgreSQL query
result and a completed audited payment journey. This is an observed application
run reported by the owner, not a synthetic browser fixture. The coding agent did
not independently connect to the Codespaces database or replay the payment.

| Activity | Recorded result |
|---|---|
| Local stack readiness | Owner reported READY, 9/9 components at the startup checkpoint; not continuous monitoring |
| Payment instruction | USD 1,000 to 1,000 USDC; USDC_WALLET |
| Initial compliance | COMPLIANCE_APPROVED; screening explicitly simulated |
| Recommendation preparation | READY / Preparation completed; deterministic provider assessment ASSESSED |
| Preferred provider | SIMULATED-PROVIDER-A |
| Treasury observation | READY, MOCK; network label base-sepolia, chain ID 84532 |
| Quote | Quoted fee USD 2.21; quote version 1 |
| Review approvals | Review version 2, APPROVED, 2/2 recorded |
| Execution validation | Database output reported VALIDATED |
| Execution | Database output and execution references reported CONFIRMED, no failure code, linked internal transaction UUID |
| Payment journey | SETTLEMENT_COMPLETED followed by RECONCILIATION_PENDING and RECONCILED |
| Terminal audited event | Nov 23, 2026, 9:06:16 AM GMT+05:30 (Asia/Calcutta) |
| Optional fiat/Visa payout | Not applicable to this USDC_WALLET run |

The supplied audited journey was:

CREATED -> COMPLIANCE_PENDING -> COMPLIANCE_APPROVED -> QUOTE_ACCEPTED ->
RAIL_SELECTED -> FUNDING_PENDING -> FUNDED -> BLOCKCHAIN_SUBMITTED ->
BLOCKCHAIN_CONFIRMED -> SETTLEMENT_COMPLETED -> RECONCILIATION_PENDING -> RECONCILED.

During execution, the blockchain consumer logged COMPLIANCE_VALIDATION_PENDING.
A subsequent database read showed validation completed and execution CONFIRMED;
the journey then reported RECONCILED. The pending validation was transient for
this run. No manual database edits, approval bypass or repeat execution were
requested during diagnosis. Detailed run identifiers and the owner-supplied
timeline are retained privately in ignored .local/poc-status/ checkpoint files,
following the evidence handling rules in the
[proof package](35-HACKATHON-PROOF-PACKAGE.md#evidence-acceptance-and-remaining-completion-gates).
Raw customer evidence, credentials and full logs are not included in this record.

## Meaning and limits

The observed result supports completion of the MOCK wallet application workflow,
including its recorded approvals, asynchronous execution and terminal
reconciliation state. The application reported receipt/ledger acknowledgement and
matched reconciliation evidence. Detailed journals and the immutable
reconciliation report have not yet been exported and independently reviewed for
this checkpoint. No new accounting correctness test is claimed.

Funding, conversion and compliance screening are simulated. MOCK blockchain
evidence is synthetic even when it carries a Base Sepolia network label. An
internal transaction UUID and CONFIRMED status do not establish a public-chain
transfer. The existing real-public-testnet requirement in ADR-001 remains open.
No successful runtime AI/model invocation was supplied for this review.
Deterministic provider assessment alone is not an AI recommendation.

The runtime Git/image revision was not captured with this evidence. Do not infer
which commit ran from the current repository HEAD. This run used USD 1,000; the
primary USD 10,000 demonstration remains a separate acceptance activity.

## Application shutdown verification - 2026-11-23

The owner ran `node --env-file=.env scripts/local-workbench.cjs stop --show-failures`
in Codespaces. The supplied output reported **STOPPED, 9/9 components stopped,
Result: PASSED**. React stopped first, followed by all six backend services,
LocalStack and PostgreSQL. The command reported retaining containers, volumes
and recorded payments. This records the owner's runtime result; no independent
post-stop database inspection or restart/persistence check is claimed.

A subsequent `gh codespace list` showed the Codespace as `Available`. This is
consistent with stopping the application while leaving its development host
running. The stop command does not stop the Codespace or AWS resources.

To resume the application, use the workbench start command and check readiness.
Payment history persistence across that restart remains to be verified.

## Next activities

1. Inspect Blockchain evidence and Ledger & reconciliation, and privately export
   the completed payment's mode, receipt details, journals, report and decisions.
2. Retain this completed payment as audit history; do not execute it again.
3. Complete a separately configured real runtime-model call and capture its run
   evidence before claiming AI inference.
4. Complete an explicitly authorized public-testnet payment and independently
   verify the public hash, receipt, token Transfer log, recipient, amount and
   confirmations.
5. Capture the primary USD 10,000 scenario and the separate optional simulated
   bank/Visa payout and second-customer scenarios where required.
6. Complete the remaining database verification, recovery, AWS deployment and
   final evidence/deliverable gates in the existing runbooks.

This checkpoint supersedes earlier blocked MOCK wallet walkthrough statuses for
that path only. It does not mark the entire POC, live inference, public-testnet
settlement, optional payout, or AWS application deployment complete. No
architecture, service, database, permission or financial-rule changes are made.

## Local encrypted-keystore implementation - 2026-11-24

Local/Codespaces signing now supports an encrypted V3 keystore and a separate
password file through the existing WalletSigningService boundary. The file provider
requires local profile (rejects aws-dev), rejects mixed/incomplete sources and the
known MOCK signer, checks the decrypted public address against an explicit expected
address, and restricts signing to the two accepted public testnets. Legacy raw-key
tooling remains available; ordinary startup keeps the existing two-flag MOCK guard.
An explicit docker-compose.testnet.yml overlay selects Base Sepolia and mounts
file-backed secrets for blockchain-service only. No image contains a wallet/password.

Java 21 compilation and 16 focused signing/configuration tests passed with zero
failures/errors/skips. These tests generated disposable fixture wallets and used
no owner secrets or public RPC. The first sandboxed build failed in compiler resource
cleanup; the approved rerun outside the sandbox passed. Bash syntax validation passed
for the hidden-input password preparation helper. Docker Compose configuration was
parsed/asserted in a unit test; actual Compose merge, secret mounting/container
startup and public-testnet settlement were not executed on this Windows host.

This is a local source implementation, not evidence of GitHub publication, Codespaces
activation or AWS secret integration. See the Codespaces encrypted-keystore activation
section in the central SOP. Next: distribute/review the change in Codespaces, inspect
outstanding work before restarting consumers, create the protected password file,
validate Compose/runtime signer address and fee coverage, then execute a fresh approved
1-test-USDC payment and independently verify its receipt and reconciliation.
