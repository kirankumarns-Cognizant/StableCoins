# Hackathon proof and reusable offering

> Remaining and deferred work is tracked only in the [consolidated implementation backlog](40-IMPLEMENTATION-BACKLOG.md).
> Dated checklists below retain historical context; update backlog IDs and closure evidence there.


Updated 2026-11-15. Status: **PREPARATION IN PROGRESS**. This package moves step 11
ahead of application deployment to AWS at the owner's request. It does not mark
unexecuted live acceptance complete. Launch prerequisites are in
[the Codespaces guide](34-CODESPACES-DEMO-LAUNCH.md), and exact screen actions are
in [the cumulative demo runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## Activity update - 2026-11-23

The owner-observed MOCK USDC wallet walkthrough reached RECONCILED with a
CONFIRMED execution and 2/2 approvals. See [the latest POC activity checkpoint](36-POC-ACTIVITY-STATUS.md)
for evidence provenance, limits and next activities. The earlier preparation
status above is historical; overall acceptance remains in progress. Real runtime
inference, independently verified public-testnet settlement and the other
completion gates below are not established by this MOCK run.

## Product proposition and prospective buyer

StableFlow is a configurable stablecoin payment and treasury control layer for a
bank or payment platform serving corporate cross-border suppliers. A corporate
instruction remains bound to its beneficiary outcome, customer policy, independently
approved quote, USDC settlement evidence and internal reconciliation. Seven
captured signals support deterministic eligibility and a bounded model explanation.
The application moves test USDC; ETH pays gas. It does not exchange USDC for ETH.

The commercial hypothesis is that a bank needs a reusable integration and control
layer rather than separately rebuilding treasury reservations, approval, evidence,
exception handling and reconciliation for every corridor. Proposed Cognizant
offerings are implementation/integration, customer and corridor onboarding,
managed operations, and a platform subscription. Pricing, customer willingness to
pay, licensing model, addressable market, production permissions and margins are
unvalidated. This POC is not evidence of revenue, regulatory clearance, real bank
connectivity, real FX, Visa certification or mainnet economics.

Interview bank payment/treasury owners and corporate finance teams about their
existing supplier-payment volumes, exceptions, reconciliation effort and approved
stablecoin use. Obtain a permitted, comparable baseline before estimating savings.
Do not present invented market statistics or testnet fees as customer savings.

## Two customer profiles using the same services

[Server-only policy examples](../services/config/hackathon-customer-access.example.properties)
define fictional customers A and B. Replace all identity placeholders with verified
Cognito subjects. Neither example authenticates a user or creates database data.

| Profile | Fictional customer ID | Intent and control distinction |
|---|---|---|
| A Corporate supplier payments | 11111111-1111-1111-1111-111111111111 | USDC wallet or simulated USD/INR bank outcome; USD 50 maximum fee; two approvals |
| B Treasury wallet delivery | 55555555-5555-5555-5555-555555555555 | USDC wallet only; USD 25 maximum fee; two approvals |

Both retain two distinct approvers; no customer can override a policy using a
browser role or model output. Narrative treasurer, controller and auditor roles
are presenter personas. Implemented permissions remain PAYMENT_OPERATOR and
PAYMENT_APPROVER. Approvers opening the UI need both permissions.

Seed A using the existing wallet/bank procedure. Seed B with the same wallet seed
and its optional identifiers, after replacing the explicit public wallet placeholder:

```bash
docker compose exec -T postgres psql -U stablecoin -d stablecoin_poc \
  -v supplier_wallet=0xYOUR_APPROVED_BENEFICIARY_TESTNET_WALLET \
  -v customer_id=55555555-5555-5555-5555-555555555555 \
  -v account_id=66666666-6666-6666-6666-666666666666 \
  -v beneficiary_id=77777777-7777-7777-7777-777777777777 \
  < scripts/seed-wallet-demo.sql
```

Use the configured database user/name if different. The seed preserves existing
balances and rejects an existing account/beneficiary with conflicting ownership,
asset, status or recipient. Both profiles begin with explicitly simulated USD
capacity, not deposits into real corporate bank accounts.

Demonstrate A's supported bank outcome, switch to B's distinct operator identity,
and show only B's catalog and policy. B's bank outcome must not become executable;
a foreign payment URL must not disclose A's evidence. Then run B's wallet intent
through the same services. Capture actual results before marking reuse accepted.

## Presenter sequence and three headline screens

Open with the complete USD instruction to test-USDC settlement to optional
simulated bank-credit story. Explain the real/simulated boundary before any click.

| Scene | Screen and role | Context, action and expected evidence |
|---|---|---|
| 1 | Overview; operator | Establish customer/account/policy; sign in and choose Create USD payment |
| 2 | New payment; operator | Define supplier outcome first; enter USD 10000.00, deadline, fee cap and explicit confirmation |
| 3 | Intent & routing; operator | Prepare evidence, compare providers and all seven signals, then Run reasoning agent; retain actual provider/model/run and concise explanation |
| 4 | Treasury readiness; treasury persona | Explain prefunding; inspect available test USDC, test ETH, reservations, gas estimate and freshness |
| 5 | Quote & approvals; two independent approvers | Review exact versions/fees/expiry, accept quote and approve under two distinct subjects |
| 6 | Execution journey; operator | Explain authority before Execute approved USDC payment; track one bound execution and audited states |
| 7 | Blockchain evidence; treasury persona | Inspect actual mode/hash/receipt/gas/confirmations and independently inspect the public transaction |
| 8 | Ledger & reconciliation; controller/auditor persona | Show immutable journals balanced per asset, actual fee and matching canonical reconciliation |
| 9 | Optional fiat payout; operator | On a separate bank-outcome payment show SIMULATED conversion/Visa/bank credit; wallet delivery has no payout |
| 10 | Overview and routing; second customer | Show policy/identity reuse and denied foreign reads; repeat the relevant wallet scenes |

The [full walkthrough](29-PROTOTYPE-EXECUTION-RUNBOOK.md) supplies exact fields,
button labels, hashes/routes, recovery paths and the reason before each action.
Its three headline captures are routing with actual inference, ledger/reconciliation,
and genuine public-chain evidence. Codex's engineering contribution is distinct
from the configured OpenAI/Bedrock runtime model call. Neither provider name in a
configuration file nor a synthetic screenshot proves an actual call.

## Comparable baseline and controlled disruption

Use bank outcome A with two simulated payout providers prefunded for the same
currency/amount. Fix amount, recipient outcome, customer policy, deadline and
quote assumptions. Compare the existing deterministic recommendation and the
agent's explanation of the **same** captured evidence; the agent cannot override
eligibility or claim a better score without evidence. This baseline is a
deterministic platform baseline, not an implemented SWIFT/bank-rail benchmark.

Before funding, change only `FLOW_PROVIDER_B_AVAILABLE=false`, advance
`FLOW_SIMULATION_VERSION` and recreate the affected service with the intended
configuration. Prepare a **fresh** review of an unexecuted payment and run the
agent. Expected: B is ineligible, reasons identify the configured outage and a
remaining eligible provider can be explained. If no candidate is eligible, stop
for review. Capture old/new review and signal versions. Do not edit an immutable
snapshot, migrate an in-flight transfer or blindly reroute uncertain submission.
Restore availability deliberately for later runs. This is an explicit simulated
provider disruption, not evidence of an actual external outage.

| Measurement | Required basis | Current result |
|---|---|---|
| Route fee and expected latency | Same beneficiary outcome and versioned simulated quotes/signals | Not measured in a connected run |
| Inference usage/cost | Actual provider/model usage; configured token rates labeled as estimates | Not measured |
| Submission and confirmation time | Application timestamps and independent public receipt/block evidence | Not measured |
| Actual network fee | Complete receipt components in ETH; separate from estimated gas | Not measured |
| Reconciliation completeness | Canonical six wallet or nine bank journals, report and payment closure | Not executed end to end |
| Tenant reuse | Second customer run and denied cross-customer evidence access | Configuration prepared; acceptance pending |

Any later business case must distinguish measured results, simulated inputs and
projected commercial assumptions. Do not extrapolate testnet gas into production
settlement costs, or equate a successful blockchain receipt with real bank credit.

## Evidence acceptance and remaining completion gates

Keep artifacts under ignored `.local` or `artifacts`, with a reviewed redacted copy
only when publication is intended. Do not commit customer evidence, tokens, wallet
keystores, private keys, raw prompts or RPC credentials. Retain payment/correlation,
revision, customer policy, review/quote, actor decision IDs, model run/provider,
network/contract/hash/receipt, journals, payout mode and immutable report IDs.

Step 11 is complete only after all of the following have actual evidence:

1. Codespaces/container verification executes PostgreSQL tests without database skips.
2. Authenticated UI walkthrough and both customer policies work against actual APIs.
3. A successful configured real inference and permitted read-only tool trace are captured.
4. A real test-USDC transfer is independently verified against its public receipt,
   expected token Transfer log, recipient, amount, chain and required confirmations.
5. Per-asset journals and final reconciliation match; the bank extension remains simulated.
6. Controlled disruption and comparable-baseline results are recorded without inventing savings.
7. The cumulative Word architecture/design document is generated from current Markdown,
   rendered and visually checked on every page; it includes the full demo and limitations.

Code-only preparation does not meet these gates. AWS application deployment stays
in step 10 after Codespaces acceptance; the agreed six-service architecture and
accepted ADRs are unchanged. A final genuine evidence bundle and Word artifact
remain pending until the missing resources and execution gates are satisfied.
