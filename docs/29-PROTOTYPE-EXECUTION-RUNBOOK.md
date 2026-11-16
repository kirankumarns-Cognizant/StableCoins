# Prototype Execution and Feature Demonstration - living runbook

Status: IN PROGRESS. This is the required final demonstration deliverable and is
updated alongside each implementation step. Pending steps are not executable proof.

## Opening narrative: the complete payment story

Tell the audience: "A corporate customer wants to pay a supplier USD 10,000.
The platform understands the intended outcome, compares eligible stablecoin paths
using seven signals, and explains its recommendation. Independent people approve
the payment. The application simulates USD funding and conversion, checks treasury
USDC and ETH, and authorizes a USDC transfer. In TESTNET mode, that transfer happens
on Base Sepolia. ETH pays the network fee. The application keeps its own balanced
ledger and reconciles it with blockchain evidence. If the customer requests bank
delivery, the final off-ramp, Visa payout and bank credit are simulated."

Explain the three worlds before opening payment details:

1. Traditional finance: corporate USD instruction, simulated compliance, quote,
   funding and conversion; real application records and control-ledger journals.
2. Blockchain: treasury test USDC plus test ETH, authorized USDC transfer,
   beneficiary wallet and confirmed public receipt when TESTNET is enabled.
3. Traditional finance: optional simulated USDC-to-fiat payout and bank credit;
   final application reconciliation across the records.

State the selected mode aloud. MOCK uses synthetic chain evidence. TESTNET uses
public test tokens, not production money. Provider history, prices and external
signals currently remain simulated assumptions even when the chain is real.
OpenAI/Bedrock is a runtime agent only when an actual configured model run succeeds;
the deterministic recommendation alone does not prove AI inference. Codex's role
in developing the application is separate from that runtime model invocation.

## Screen walkthrough status and reading convention

Step 9 implements the connected React routes and exact button labels below.
Use the [step-9 setup and recovery procedure](32-CONNECTED-REACT-WORKSPACE.md)
after the cumulative [step-8 environment setup](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
The separate [offline HTML prototype](../prototype/README.md) remains illustrative.
Expected outcomes below are live acceptance criteria until captured in a real
configured run. Synthetic browser fixtures are never public-chain or model proof.

Each scene names the screen first, then the role/purpose, reason before the
navigation/click, expected outcome and evidence. Treasury operator, finance
controller and auditor are narrative personas. The implemented grants remain
PAYMENT_OPERATOR and PAYMENT_APPROVER. Independent approvers need distinct verified
subjects; approvers reading these screens also need PAYMENT_OPERATOR.

## Numbered presenter navigation: URL, login, roles, actions and outcomes

Updated 2026-11-16. Use this sequence for the connected application, then use the
scenes below for the detailed narrative on each screen. The current Codespaces UI is:

<https://zany-space-barnacle-97pjwprrpw7wh7459-5173.app.github.dev/>

This address belongs to the current Codespace; use the application URL printed by
successful startup when the Codespace changes. Keep the forwarded port Private.
GitHub may first ask for GitHub sign-in to access that private forwarded port.
That is separate from the application's Cognito sign-in.

**Observed checkpoint (2026-11-16):** the owner's browser confirms Customer A's
operator subject, `PAYMENT_OPERATOR`, customer ID, `hackathon-a-v1` policy and
successful refresh. The initial empty account list was followed by a successful
administrator seed: customer, USD account and beneficiary inserts committed, with
USD 100,000 of `DEMO_SIMULATED_USD` capacity. A subsequent New payment screenshot
confirms account `22222222-2222-2222-2222-222222222222` and **Fictional Demo Supplier | USD**
are selectable under the correct operator/customer. The form shows USD 1,000 and
a USD 5 fee cap, `USDC_WALLET`, `LOWEST_COST`, `AUTO` and an unchecked confirmation.
The following screenshot verifies creation of **PAY-52272AAC4E14** for USD 1,000,
with the USD 5 cap, `USDC_WALLET`, `LOWEST_COST` and the confirmed immutable intent.
The routing page shows **COMPLIANCE_APPROVED** and **Recommendation pending**.
The correlation reference is visible in the UI; copy it from the application or
export rather than transcribing the small screenshot text. The primary USD 10,000
scenario remains a future separate demonstration; continue this existing USD 1,000
payment while its deadline remains valid. After expiry, preserve it as history
and follow the dated recovery instructions below for a new confirmed intent.
There is currently no account/beneficiary onboarding screen in this workspace.

### Guided UI rehearsal and evidence record

Use the UI navigation below to drive the remaining acceptance work. For each
screen, state its role and business context before clicking, compare the visible
result with the expected result, and record the outcome. If a UI/API defect blocks
the step, retain the same payment and evidence, fix the underlying implementation,
verify it, and update this runbook's navigation/recovery notes before resuming.
Backend commands are preparation or explicit diagnosis; record them as such
instead of presenting them as completed UI actions. Do not execute a payment by
CLI merely to claim that the corresponding UI path passed.

The completed SQL seed provisions fictional starting capacity; it does not fund
a particular payment, debit its account, post that payment's funding journal,
convert USD, or prefund the treasury's blockchain wallet. Those payment-specific
steps must be observed later through the approved execution journey. USD 100,000
allows multiple USD 10,000 demonstration instructions; it is not a real deposit.

| Checkpoint | Screen / role / action | Expected result and current evidence |
|---|---|---|
| Environment | Administrator setup; unified startup | Owner report `1794830715419-85490-start`: 9/9 components ready and all 18 backend probes passed. This is setup evidence. |
| Authentication | Sign in securely; `demo-a-operator`; Overview | Owner screenshot confirms Customer A, `PAYMENT_OPERATOR` and policy `hackathon-a-v1`. |
| Starting records | Administrator seed; see Codespaces setup | Owner psql output confirms three inserts committed and account `22222222-2222-2222-2222-222222222222` has simulated USD 100,000. This is setup, not a UI funding transaction. |
| Account visibility | New payment; `demo-a-operator`; Corporate USD account selector | VERIFIED by owner screenshot: seeded account selected. The Overview balance panel after seeding has not been separately captured. |
| Navigation and supplier | Overview > Create USD payment, or left menu > New payment | VERIFIED: Supplier payment instruction form displays Fictional Demo Supplier with USD currency. |
| Intent creation | New payment > confirm instruction > Create payment intent | VERIFIED by owner screenshot: PAY-52272AAC4E14, USD 1,000, USD 5 cap, USDC_WALLET; routing view shows COMPLIANCE_APPROVED and Recommendation pending. |
| Recommendation preparation | Intent & routing > Prepare / refresh recommendation; demo-a-operator | BLOCKED: owner screenshots show accepted PENDING followed by REVIEW_REQUIRED / PREPARATION_TIMEOUT. Automatic refresh displayed the transition. No completed recommendation, runtime inference or settlement is established. |
| Current next step | Retain the same payment; diagnose preparation evidence | Determine whether provider quotes, treasury observation or event delivery/processing failed. Do not repeatedly prepare or execute while blocked. |
| Payment lifecycle | Continue numbered steps 8-28 below under their named roles | Intent, real runtime agent, approvals, execution, ledger, reconciliation, optional payout and public-testnet evidence each require their own observed results. Pending connected acceptance. |

For every completed step retain: run date/code revision and mode; screen/path;
username and server-resolved role/customer; business context; exact values and
clicks; expected versus observed result; payment/correlation IDs and evidence
location when available. Mark each as VERIFIED, BLOCKED or NOT RUN. Keep future
expected outcomes separate from actual evidence. Use an explicit recovery note
for issues, including what changed and how the same step was rechecked.

**Historical checkpoint (superseded by the dated recovery instructions below):** remain signed in as `demo-a-operator` on
`#/payments/PAY-52272AAC4E14/routing` (**Intent & routing**). Retain this URL and
payment ID for both approvers and later evidence. Explain: "The confirmed request
is USD 1,000 for USDC wallet delivery with a USD 5 fee ceiling. Simulated compliance
has approved it; the system now needs quotes and current routing evidence."
The owner already clicked **Prepare / refresh recommendation** in **Prepare a
governed recommendation**. The request moved from **PENDING** to
**REVIEW_REQUIRED**, reason **PREPARATION_TIMEOUT**. Automatic polling displayed
this transition; the text-styled **Refresh** control also requests a read, and
does not restart preparation. Keep this payment open and diagnose event delivery,
provider quotes and treasury observation before retrying preparation. The service
normally waits up to the configured preparation timeout (default 120 seconds);
the screenshot alone does not identify the failing dependency. The owner's
subsequent database check confirms two READY quotes recorded at +2.84 seconds and
one READY treasury observation at +3.21 seconds from the preparation record.
This points toward recommendation processing rather than a missing evidence row;
creation timestamps do not establish transaction commit time. Payment worker logs
are the next diagnostic. Do not increase timeouts or manufacture a recommendation
as a substitute for finding the cause. On success inspect the
provider comparison, eligible/excluded paths, quote fees and evidence freshness.
A pending state, no eligible route or an explicit error is an outcome to inspect;
do not claim a usable recommendation before it appears. The button prepares
quotes, treasury evidence and seven signals through existing services. It does
not prove a real model call or execute funding, signing or blockchain settlement.
If the fee cap excludes all routes, record that constraint rather than changing
this immutable intent or inventing a cheaper quote. Continue to the separately
recorded **Run reasoning agent** step only when the recommendation is ready.

### Complete navigation map and current position

The current rehearsal uses USD 1,000 with a USD 5 fee cap. These are the observed
instruction values; USD 10,000 remains the primary future presenter scenario.

| Order | UI path and actor | Purpose, action and expected outcome | Current rehearsal |
|---|---|---|---|
| 1 | Application URL > Sign in securely; demo-a-operator | Cognito authenticates; API resolves Customer A and PAYMENT_OPERATOR. | VERIFIED |
| 2 | Overview > Corporate funding accounts | Inspect simulated USD capacity and governed customer policy. Account selector and policy verified; post-seed Overview balance capture remains pending. | PARTLY VERIFIED |
| 3 | New payment > Supplier payment instruction > confirm > Create payment intent | Pin account, beneficiary, amount, fee ceiling, deadline and wallet outcome; obtain payment ID. | VERIFIED: PAY-52272AAC4E14 |
| 4 | Intent & routing > Prepare / refresh recommendation; operator | Gather provider quotes, treasury observation and routing signals; produce an eligible deterministic recommendation. | BLOCKED: PREPARATION_TIMEOUT |
| 5 | Intent & routing > Deterministic provider comparison > Run reasoning agent; operator | Inspect seven signals, then record actual configured model inference and its explanation. | NOT RUN; requires step 4 |
| 6 | Treasury readiness; operator | Inspect USDC liquidity, ETH gas budget, network, mode and evidence freshness. | NOT RUN |
| 7 | Quote & approvals; demo-a-approver-one | Review exact valid version, Accept this quote, Approve this version. Verify returned identity. | NOT RUN |
| 8 | Same payment > Quote & approvals; demo-a-approver-two | Independently review and approve the same valid version; require two distinct approvers. | NOT RUN |
| 9 | Execution journey; return to demo-a-operator | Verify approvals, select authorization checkbox, Execute approved USDC payment. Follow simulated funding/conversion, real application journals and configured chain execution. | NOT RUN |
| 10 | Blockchain evidence; operator | Inspect hash, receipt, wallets, USDC amount, gas and confirmations; independently inspect public transaction only for actual TESTNET evidence. | NOT RUN |
| 11 | Ledger & reconciliation; operator, acting as finance reviewer | Inspect immutable per-asset balanced journals, reconciliation findings and final recorded closure. | NOT RUN |
| 12 | Optional fiat payout | For this USDC_WALLET payment, explain that payout is not required. Demonstrate simulated bank delivery with a separate properly configured payment and fresh approvals. | NOT RUN |
| 13 | Export evidence | Retain matching payment, model, ledger and chain evidence plus screenshots. | NOT RUN |

Steps 5 onward describe the intended continuation, not completed acceptance.
Diagnostic backend commands do not bypass these UI controls. The detailed numbered
walkthrough below supplies the narrative, role checks and recovery conditions.

| Demo login | Customer | Implemented permissions | Activity |
|---|---|---|---|
| `demo-a-operator` | A: `11111111-1111-1111-1111-111111111111` | `PAYMENT_OPERATOR` | Create intent, prepare routing, run agent, inspect evidence and request authorized execution |
| `demo-a-approver-one` | A | `PAYMENT_OPERATOR` + `PAYMENT_APPROVER` | Independently review and approve the pinned version |
| `demo-a-approver-two` | A | `PAYMENT_OPERATOR` + `PAYMENT_APPROVER` | Supply the second distinct approval of the same version |
| `demo-b-operator` | B: `55555555-5555-5555-5555-555555555555` | `PAYMENT_OPERATOR` | Demonstrate customer isolation and configuration reuse |
| `demo-b-approver-one`, `demo-b-approver-two` | B | `PAYMENT_OPERATOR` + `PAYMENT_APPROVER` | Approve B's separate instruction under two distinct identities |

Passwords are entered only on the Cognito sign-in page. Do not put them in this
document, screenshots, terminal output or exported evidence. Treasury operator,
finance controller and auditor are presentation personas; they are not additional
implemented permission grants.

1. **Open the URL above.** Introduce the corporate USD instruction, USDC treasury
   settlement and optional simulated bank payout. Explain the demonstration
   boundaries banner before interacting. Expect **Connect to your payment workspace**.
2. **Click Sign in securely.** This is the actual button label (rather than
   "Secure Login"). Expect redirection to the configured Amazon Cognito login.
3. **Sign in as `demo-a-operator`.** Enter that user's privately configured password
   and complete any challenge presented by Cognito. This user initiates Customer A's
   supplier payment; an approver identity is not needed for creation.
4. **Explain authentication while the application returns.** The browser uses an
   authorization code with PKCE against the public Cognito browser client; it has
   no client secret. Cognito authenticates the user and issues tokens. The API
   validates the bearer access token and resolves the verified subject through
   server-managed customer membership and permissions. A Cognito login alone does
   not grant customer access; browser-selected roles are not authoritative. The
   separate internal client and `stablecoins/internal` scope are for service calls,
   not for human sign-in. Tokens stay in browser memory. The open tab renews its
   access token automatically while Cognito permits when IDENTITY_AUTO_REFRESH_ENABLED
   is true (default); there is no additional application idle timeout. A fresh
   sign-in after this update is required to obtain its memory-only refresh token.
   Reloading still requires sign-in and restores the same payment screen.
   Provider revocation/refresh expiry still ends the session; payment deadlines,
   quote expiry and approval validity continue to apply. Live renewal remains
   an acceptance check, not a completed demo claim.
5. **On Overview, verify the identity strip.** Expect actor
   `247824c8-3061-70d6-0d45-6955c0015bb6`, `PAYMENT_OPERATOR`, Customer A's ID,
   and policy `hackathon-a-v1`. Explain USD 50 maximum fee, permitted wallet/bank
   outcomes and two distinct approvals. If these do not match, stop this customer's
   demo and correct identity/membership configuration.
6. **Inspect Corporate funding accounts.** Explain that the displayed USD balance
   represents simulated funding capacity. If the list is empty, pause the payment
   sequence and complete the administrator's fictional account/beneficiary setup
   in [the Codespaces guide](34-CODESPACES-DEMO-LAUNCH.md#4-start-the-six-services-and-seed-fictional-data).
   Refresh **Overview** and confirm an active USD account with enough capacity.
   This does not deposit real USD or prefund a blockchain wallet.
7. **Click New payment** in the left menu, or **Create USD payment** on Overview.
   Expect **Create a payment intent** and the **Supplier payment instruction** form.
8. **Complete the supplier instruction.** Select the configured **Corporate USD
   account** and **Supplier beneficiary**. Enter **Payment amount (USD)** =
   `10000.00`, **Required delivery outcome** = `USDC_WALLET`, **Maximum fee (USD)**
   within policy, a future **Deliver by (UTC, ISO 8601)**, **Routing priority** =
   `LOWEST_COST`, and **Requested route** = `AUTO` or `STABLECOIN`. Explain that the
   intended beneficiary outcome constrains the routes that may be recommended.
9. **Review and confirm, then click Create payment intent.** Select the explicit
   confirmation checkbox first. Expect a payment ID, correlation ID and confirmed
   intent, followed by **Intent & routing**. Save the payment ID and page URL so
   approvers can reopen the same instruction. An uncertain creation response uses
   **Retry original instruction**, preserving the original idempotency key.
10. **On Intent & routing, click Prepare / refresh recommendation.** Explain that
    the existing services perform simulated compliance/quote preparation and gather
    treasury/provider evidence. Wait for the recorded preparation/recommendation;
    a pending or rejected result does not permit the next financial action.
11. **Inspect Deterministic provider comparison.** Expand the seven-signal details:
    success rate, latency, transaction cost, fraud risk, rail availability, liquidity
    conditions and external data. Explain eligibility and provenance. Simulated
    observations remain simulated even if the later settlement uses a public chain.
12. **Click Run reasoning agent.** Explain that the configured OpenAI or Bedrock
    model reasons over permitted read-only tools and evidence. Expect a successful
    recorded model run, provider/model, recommendation and concise explanation.
    A disabled/failed run is not AI proof. The agent cannot approve, sign, submit
    transactions or write ledger entries. Capture headline screenshot 1 here.
13. **Click Treasury readiness.** Explain prefunding and inspect available/reserved
    USDC, available/reserved ETH, network, contract, observation time and gas estimate.
    USDC is the supplier's settlement principal; ETH pays blockchain computation
    fees. In TESTNET mode the treasury must already hold enough test USDC and test
    ETH. Simulated USD conversion does not mint or acquire those test tokens.
14. **Click Quote & approvals.** Explain the exact provider, amount, fees, expiry
    and immutable review/quote versions. Retain this payment's URL. Both approvers
    must decide on the same valid version. If it expires, prepare a fresh version
    and obtain fresh approvals; do not reuse decisions from the earlier version.
15. **Open the same payment as `demo-a-approver-one`.** Use an independently signed-in
    browser profile for each demo identity where practical; private Codespaces
    forwarding may require GitHub sign-in in each profile. If using **Sign out /
    switch user**, verify the returned subject before proceeding: the current
    button clears the local session, but Cognito may retain its SSO session and
    return the previous user. Full provider logout remains a pending UI improvement.
    Expect the first approver's subject `34888428-6031-70b4-4976-83af1d3c9c8b` and
    both permissions in the identity strip, scoped to Customer A.
16. **As approver one, review, click Accept this quote, then Approve this version.**
    Explain that this records an independent decision on the pinned financial
    obligation. Expand **Recorded approval decisions** and check actor/version/time.
    One approval does not satisfy Customer A's two-person threshold.
17. **Open the same payment as `demo-a-approver-two`.** Confirm subject
    `c47804e8-8011-7069-bec0-261bdc17db22`, Customer A and both permissions. Review
    the same valid version, then click **Accept this quote** and **Approve this
    version**. Expect two distinct approvers; signing in twice as one user is not
    a second independent approval.
18. **Return to `demo-a-operator` and refresh the payment.** Confirm the operator
    identity and that the current review meets the approval threshold. Explain
    that the initiating operator requests execution after independent authorization;
    the runtime model does not authorize the financial transfer.
19. **Click Execution journey.** Review the execution context, select its explicit
    authorization checkbox and click **Execute approved USDC payment**. Expect one
    version-bound execution reference. Execution gates, liquidity and valid approvals
    must all be configured; a disabled action is a prerequisite to resolve, not
    an instruction to bypass controls.
20. **Follow Audited payment journey and Execution status.** Explain simulated
    USD funding/conversion, real application journal posting, treasury balance/gas
    checks, signing, submission and confirmation handling in that order. In TESTNET
    mode, signing authorizes the USDC transfer, submission produces its transaction
    hash, and confirmation checks its receipt/status/confirmations. In MOCK mode,
    chain evidence is synthetic. Preserve uncertain execution references; never
    blindly resubmit or create a replacement payment to hide an unknown result.
21. **Click Blockchain evidence.** Inspect mode, chain ID, token contract, wallets,
    amount, hash, receipt, block, gas and confirmations. For actual LIVE public-testnet
    evidence, click **Independently inspect public transaction** and compare the
    public receipt and USDC Transfer log. Capture headline screenshot 3. A MOCK
    hash or explorer-shaped URL does not prove a blockchain transfer.
22. **Click Ledger & reconciliation.** Explain that the blockchain is settlement
    infrastructure and the application maintains separate immutable double-entry
    journals. Inspect each journal, asset, account, reference and its balanced
    debits/credits. Funding/conversion, principal, gas and optional payout are
    accounted for separately; USD cannot balance against USDC or ETH.
23. **Inspect Reconciliation and closure.** Expand **Immutable reconciliation report
    and findings** and compare ledger, payment, chain and any payout references.
    Expect server-recorded `RECONCILED` and closure `COMPLETED` only when all required
    evidence matches. Pending, mismatched or review-required outcomes remain visible.
    Capture headline screenshot 2, including the actual report and journal evidence.
24. **Click Export evidence.** Retain the authorized evidence JSON and the three
    screenshots from this same payment with code revision, mode, payment/correlation
    IDs and timestamps. Do not include access tokens, passwords or private keys.
25. **Demonstrate optional fiat delivery using a separate payment.** As Customer A's
    operator, select a matching bank beneficiary and `SIMULATED_BANK_INR` or
    `SIMULATED_BANK_USD`, with `AUTO` or `STABLECOIN_PLUS_VISA`. Repeat steps 8-23,
    including both independent approvals. Open **Optional fiat payout** to explain
    the simulated conversion, Visa reference, payout status and bank-credit outcome.
    Recheck the payout journals and final reconciliation. On the wallet-only payment,
    explain **A fiat payout is not required**; do not claim a bank credit occurred.
26. **Demonstrate reuse as `demo-b-operator`.** Verify Customer B, its wallet-only
    policy and USD 25 fee limit. Expect only B's catalog/payments; A's payment must
    not become readable by entering its URL. After B's fictional data is configured,
    repeat the wallet flow with B's two distinct approvers. The same services and
    agent implementation are reused through customer-specific configuration.
27. **Show controlled failures on separate rehearsal instructions.** Use the scene-10
    procedures for stale quotes, insufficient liquidity, unavailable providers,
    model failure or reconciliation mismatch. Explain the observed blocked/pending
    result and recovery without mutating posted entries or retrying transfers blindly.
28. **Close on the successful payment's three evidence screens.** State exactly which
    model, identity, database and chain integrations actually executed, which
    financial endpoints were simulated, and which acceptance items remain open.
    The current app runs in Codespaces; AWS application deployment is a later gate.

For recording, write the **actual observed result** beside each completed numbered
step. An expected result in this guide is not a completed test. See the detailed
scenes below for recovery behavior and additional narration.

**Refresh and recovery checkpoint:** click **Refresh evidence** in an API-error
banner, or **Refresh** in the identity strip, to reload the current authorized
records. Expect **Refreshing...** and a progress message, then a success time or
an explicit failed-refresh message. Repeated clicks are disabled while the read
is active; five-second polling does not cancel a slow refresh. On failure, the
last successful refresh time stays unchanged and previously loaded data may be
out of date. Restore API connectivity before retrying an HTTP 502/503/504. Refresh
does not start a stopped frontend/backend, create an account or retry a financial
command. An empty account list after a successful refresh requires the separate
fictional-data setup in step 6.

## Scene 1 - Treasury Dashboard

**Open:** the React application, then **Overview** (`#/overview`).

**Role and purpose:** a corporate payment operator establishes the customer and
funding context; the treasury persona explains why stablecoin resources are needed.

1. To establish trusted identity, click **Sign in securely**. Complete the configured
   identity-provider login. For an existing verified rehearsal token, expand
   **Rehearsal: use an existing access token**, enter the access token and click
   **Connect with access token**. Expect server-resolved subject, customer and roles;
   a browser role label or caller-supplied customer cannot grant access.
2. To explain funding and governance, inspect **Corporate funding accounts** and
   **Governed customer policy**. Expect owned active accounts with simulated USD
   balances and the configured outcomes, fee limit and distinct-approval requirement.
   The recent-payment table contains at most the latest 50 records, not global KPIs.
3. To begin the supplier instruction, click **Create USD payment** or **New payment**.
   Expect the confirmed-intent form. Capture customer/policy evidence without tokens.

**Observed result to record:** verified subject/customer, policy version, simulated
funding label and the actual environment URL. Do not record credentials.

## Scene 2 - Initiate Payment

**Open:** **New payment** (`#/payments/new`).

**Role and purpose:** the corporate payment operator confirms what the supplier
must receive before any model explanation or financial execution.

1. To bind the instruction to owned records, select the **Corporate USD account**
   and **Supplier beneficiary** configured during setup. Set **Payment amount (USD)**
   to `10000.00`. Beneficiary wallet/currency must match the chosen outcome.
2. To define a wallet settlement, choose **Required delivery outcome** =
   `USDC_WALLET`. Set **Maximum fee (USD)** within policy, a future ISO UTC
   **Deliver by** deadline, **Routing priority** = `LOWEST_COST` or `MEET_DEADLINE`,
   and **Requested route** = `AUTO` or `STABLECOIN`. The one-hour default deadline
   is relative to form opening; use a suitable future value for the actual run.
3. To make the intent explicit, review all fields and select the confirmation
   checkbox, then click **Create payment intent**. Expect a server-created PAY ID,
   correlation ID and confirmed immutable intent, followed by **Intent & routing**.
   Compliance remains simulated and progresses asynchronously on the backend.
4. If the response is uncertain, preserve the tab/storage and instruction. Click
   **Refresh evidence** if needed, then **Retry original instruction**. Expect the
   same request body/idempotency key and original payment, not an invented retry.

**Observed result to record:** PAY/correlation/intent IDs, source amount, outcome,
policy, deadline and actual API/state evidence. Funding has not become a real bank debit.

## Scene 3 - Payment Intent and Routing

**Open:** **Intent & routing** (`#/payments/{PAY}/routing`).

**Role and purpose:** the payment operator requests governed routing; a routing
analyst explains why a provider is recommended and which evidence supports it.

1. To collect current provider/treasury evidence after simulated compliance is
   approved, click **Prepare / refresh recommendation**. Expect a preparation ID,
   then an immutable recommendation/version. Pending work remains pending. A
   compliance failure or missing configuration must not be narrated as success.
2. To explain deterministic controls, inspect **Deterministic provider comparison**.
   Compare eligibility, costs, latency, success, fraud and evidence modes. Expand
   each candidate's **seven-signal evidence, findings and itemized fees** and
   **All seven signal observations**. Discuss availability, liquidity and external
   data as well as the four summary metrics. Inspect observation source, age,
   expiry and simulated/mock/live provenance. These values come from backend services.
3. To demonstrate actual agentic reasoning, review **Ask the routing agent**, then
   click **Run reasoning agent**. Expect a recorded run with the configured OpenAI
   or AWS Bedrock provider/model, status, outcome, recommendation, explanation,
   tool calls and expiry. A deterministic preferred candidate alone is not an AI run.
4. To show boundaries, expand **Read-only tool calls and clarification evidence**.
   The agent cannot accept a quote, approve, fund, sign, submit or post accounting.
   A clarification/intent draft is unconfirmed and needs a new confirmed instruction.
5. To explain immutable review, use **Inspect a saved recommendation** if versions
   exist. Expect the selected review and quote to remain bound to decisions. Expired,
   rejected or superseded material requires fresh preparation and review.

**Observed result to record:** recommendation/version, signal/provenance evidence,
actual successful model run/provider/model/prompt/tool trace and explanation. If
no model was invoked, explicitly state that no runtime AI claim is established.
This is headline screenshot 1, captured only from the actual configured run.

## Scene 4 - Treasury Readiness

**Open:** **Treasury readiness** (`#/payments/{PAY}/treasury`).

**Role and purpose:** the treasury operator explains whether prefunded test USDC
and test ETH support the reviewed transfer and its estimated network fee.

1. To verify the settlement resource, inspect captured network, chain ID, token
   contract and treasury wallet. Explain that this screen shows the most recent
   preparation, including observation/expiry times, rather than a live wallet ticker.
2. To distinguish transferable principal from fees, compare **USDC total/reserved/
   available** with the USD 10,000 notional USDC amount, then **ETH total/reserved/
   available** with **Required ETH estimate**. USDC moves to the supplier; ETH pays
   gas. No USDC-to-ETH exchange is part of this supplier transfer.
3. To explain gas estimation, inspect estimated gas units, gas limit and gas price
   in wei. These estimate computing/network work and cost, not petrol. Actual fees
   arrive later from the receipt, including additional Base fees when applicable.
4. If insufficient or stale, use the existing authorized setup/recovery procedure
   to fund/refresh the same intended resources, then prepare a fresh review. The UI
   does not bypass liquidity or create a production funding operation.

**Observed result to record:** mode, balances/reserves, required fee, source time,
expiry and readiness/failure reasons. A wallet balance is not fiat-conversion proof.

## Scene 5 - Quote Review and Independent Approval

**Open:** **Quote & approvals** (`#/payments/{PAY}/approvals`).

**Role and purpose:** independent authorized approvers govern the exact quote and
version; the finance controller persona explains the separation of responsibilities.

1. To review the actual obligation, inspect recommendation/version, quote ID/version,
   provider, USD/USDC amounts, fee, optional payout amount/currency and expiry.
   Explain that quote/FX preparation is simulated and the reviewed terms are pinned.
2. To record the first independent decision, sign in as a verified approver with
   both read/operator and approver membership. Click **Accept this quote**, inspect
   acknowledgement, then **Approve this version**. Expand **Recorded approval
   decisions** and verify the actor, action, review and timestamp.
3. To satisfy the configured multi-approval policy, click **Sign out / switch user**.
   Expect the local customer view to clear. Sign in as the next distinct authorized
   approver, reopen this payment if necessary and repeat acceptance then approval.
   A normal operator's approval buttons remain disabled; API authorization is decisive.
4. To explain rejection, use a separate rehearsal payment and click **Reject** with
   an authorized identity. Expect rejection to block execution; never reject the
   payment intended for the successful settlement capture.

**Observed result to record:** actual distinct subject decisions and required/current
approval count. Screens are personas, not browser-granted roles. Expired or changed
material cannot be rescued by reusing an earlier approval.

## Scene 6 - Payment Journey and Authorized Execution

**Open:** **Execution journey** (`#/payments/{PAY}/journey`).

**Role and purpose:** the treasury/payment operator requests execution only after
the required independent approvals. The backend owns money and transaction controls.

1. To confirm authorization, inspect the approved review and recorded state. Read
   the execution context. Select the explicit authorization checkbox, then click
   **Execute approved USDC payment**. The command sends review/quote versions plus
   an idempotency key; the browser neither signs nor chooses a replacement rail.
2. To follow progress, inspect **Execution status**, **Closure phase**, failure codes,
   **Execution references** and **Audited payment journey**. Reads refresh every five
   seconds while visible; **Refresh** requests current evidence. Explain simulated
   funding/conversion, authoritative ledger postings, USDC signing/submission,
   confirmation, settlement, optional payout and reconciliation as records arrive.
3. To establish completion, wait for server-recorded `RECONCILED` and closure
   `COMPLETED`, then inspect the evidence screens. Pending/unknown/review-required
   states remain visible and must not be narrated as completed delivery.
4. If a submission result is uncertain, preserve its existing execution/hash and use
   the authorized recovery procedure. Do not create a replacement transaction,
   reroute, release reserved resources or blindly retry signing/submission.

**Observed result to record:** execution and transaction IDs, ordered audited
states/reasons/times, actual terminal state and any review/failure code.

## Scene 7 - Blockchain Transaction / Base Sepolia Evidence

**Open:** **Blockchain evidence** (`#/payments/{PAY}/blockchain`).

**Role and purpose:** a treasury operator and technical reviewer independently
inspect the real public-testnet settlement leg when TESTNET mode is enabled.

1. To establish provenance, inspect evidence mode, network/chain ID, USDC contract,
   sender, recipient and amount. MOCK is a rehearsal and is explicitly labeled
   **NOT PUBLIC CHAIN PROOF**. TESTNET uses test assets; fiat remains simulated.
2. To inspect settlement, compare the recorded hash, block, success status and
   confirmation count with the intended transfer. Inspect gas used, effective price,
   additional fee, total fee and completeness. Missing fee evidence remains incomplete.
3. For a LIVE record on a supported testnet, click **Independently inspect public
   transaction**. In the explorer, verify successful receipt and the USDC Transfer
   log's contract, from/to and amount on Base Sepolia (or configured Ethereum Sepolia).
   A displayed hash alone is insufficient. The Node lifecycle runner also independently
   checks the public receipt/log/fees in TESTNET mode; retain its actual output.

**Observed result to record:** explorer URL, matching receipt/log/confirmations and
fees, PAY/correlation association and runner output. This is headline screenshot 3.
Never use a synthetic browser-fixture hash as evidence of a real public transfer.

## Scene 8 - Ledger and Reconciliation

**Open:** **Ledger & reconciliation** (`#/payments/{PAY}/ledger`).

**Role and purpose:** the finance controller inspects application accounting;
the auditor persona explains traceability and independent reconciliation controls.

1. To distinguish accounting from blockchain settlement, inspect each immutable
   posted journal and its debit/credit entries, asset, account, reference and ID.
   Expand **Journal source evidence**. Explain funding, paired USD/USDC conversion,
   principal movement, network fee and optional off-ramp/payout journals.
2. To verify the central ledger rule, inspect **Per-asset debit ? credit** for each
   journal. Expect zero separately for USD, USDC, ETH and optionally INR. The display
   uses exact decimal/BigInt arithmetic; the Java ledger remains authoritative.
   USD debits cannot cancel USDC credits. Corrections require compensating entries.
3. To prove closure, inspect **Reconciliation and closure**, then expand **Immutable
   reconciliation report and findings**. Compare actual status/source fingerprint
   and findings with payment and closure state. Explain that backend closure accepts
   a matching report only against fresh canonical source evidence.
4. For repeatable evidence, click **Export evidence**. The downloaded JSON contains
   the current authorized payment/review/model/ledger/chain/report evidence without
   tokens or secrets. Treat customer and wallet metadata appropriately when sharing.

**Observed result to record:** journal IDs, per-asset zero totals, immutable source
references, actual report/fingerprint/findings and `RECONCILED` state. This is headline
screenshot 2. A balanced synthetic fixture is only a UI test, not real integration proof.

## Scene 9 - Optional Fiat Payout (alternate bank-outcome run)

**Open:** a separate **New payment**, then **Optional fiat payout**
(`#/payments/{PAY}/payout`).

**Role and purpose:** the operator demonstrates the supplier's desired bank outcome;
the controller explains the simulated off-ramp and pinned payout liquidity/reference.

1. To request bank delivery explicitly, select `SIMULATED_BANK_USD` or
   `SIMULATED_BANK_INR` with a matching configured beneficiary and policy. Choose
   AUTO or STABLECOIN_PLUS_VISA. Repeat preparation, model explanation, exact quote
   review and independent approvals. INR uses the configured simulated FX quote.
2. To follow the optional leg after confirmed settlement, execute the approved
   payment and open **Optional fiat payout**. Inspect provider, amount/currency,
   simulator reference, mode, status and linked chain hash. All conversion/Visa/bank
   credit evidence remains simulated; it is not a real Visa authorization or bank credit.
3. To show safe uncertainty, use the configured TIMEOUT/UNKNOWN simulator scenario
   on a separate run. Expect the same reference and liquidity to stay pinned for
   review, with no blind refund or replacement payout. Use existing privileged
   resolution procedures only after conclusive evidence. Decline is also a review outcome.
4. For the wallet-only instruction, open the same screen and explain **A fiat payout
   is not required**. The application must not invent a bank-credit completion.

**Observed result to record:** selected outcome, quote/currency/rate, reservation,
payout reference/status/mode and the resulting ledger/report. See step 8 for terminal
setup and privileged recovery commands; the UI does not expose those admin endpoints.

## Scene 10 - Reuse and Controlled Failure Demonstrations

**Open:** the applicable payment screen above; use separate rehearsal instructions.

**Role and purpose:** the presenter demonstrates reusable configuration and safe
failure handling without changing financial-control code or granting agent authority.

1. To compare model backends, configure OpenAI or AWS Bedrock through the existing
   central runtime settings, restart/redeploy as required and run the agent for a
   prepared review. Inspect the actual provider/model record. No browser model switch
   or fictional inference is substituted for a configured successful call.
2. To explain intelligent routing, vary supported configured evidence/scenarios for
   success, latency, cost, fraud, availability, liquidity or external data. Prepare
   fresh evidence and compare assessment findings and the agent explanation. Preserve
   the original immutable review; never edit posted financial records.
3. To exercise controls, rehearse invalid identity/foreign ownership, expired quote,
   insufficient USDC/ETH, unavailable provider, model failure, lost acknowledgement,
   unknown submission/payout, incomplete receipt fees and reconciliation mismatch.
   Inspect specific errors/findings on the relevant screen, plus existing runbook
   service/event/DLQ evidence. Use privileged recovery only through authorized paths.
4. To close the story, return to the actual successful payment's three headline
   screens and exported evidence. State which systems ran and which remain simulated.
   The commercial case and final proof pack are step 11; operational deployment is step 10.

**Observed result to record:** scenario/configuration version, actual error or finding,
safe pinned state, corrective action and later observed outcome. Synthetic browser
coverage does not replace real provider, concurrency, AWS or public-chain validation.

## Recording a scene after implementation

For every scene, retain this order in presenter notes and the final Word runbook:

1. Exact application screen name, verified URL/navigation path and starting state.
2. Signed-in identity role/persona, customer scope and business purpose.
3. The reason for the next action, then exact field values and navigation/click.
4. Expected outcome followed by the actual observed result, timing and evidence ID.
5. The next action and its reason, repeated until the screen's objective is met;
   include blocked/failure behavior and a recovery path where relevant.

Attach observed screenshots to the corresponding scene, with code revision,
mode, payment/correlation, model run and timestamps. Replace proposed labels with
actual UI labels in step 9, then validate the entire walkthrough in step 11. Keep
the initial overall story and cumulative coverage when adding later features.

## Preparation and evidence rules

Record the code/artifact version, non-secret configuration, customer policy,
identity roles, model/provider/version, pricing assumptions and enabled mode.
Use separately identified fictional operator/approver identities. Never export
credentials, keys, full PII, raw prompts or hidden reasoning. Capture real results
only after commands execute; preserve failures and explicit simulation labels.

Final setup must document build/start commands, configured identity/login,
database migrations/seeding, treasury test USDC/ETH funding, RPC/model access,
AWS deployment or local equivalents, health checks, fixtures and safe reset.
The [delivery plan](21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md) is the source of actual
verification status. Full end-to-end setup and execution are still pending.

## Required demonstration coverage

| Sequence | Feature to demonstrate | Expected evidence / distinction | Status |
|---|---|---|---|
| 1 | Corporate USD 10,000 intent and explicit confirmation | Customer scope, beneficiary outcome, deadline, fee limit, policy and idempotent payment ID | Contracts implemented; connected UI/end-to-end pending |
| 2 | Compliance and FX/fee quote | Explicit SIMULATED labels, quote version/expiry and approved compliance state | Wallet-path producer integration implemented; live demonstration pending |
| 3 | Real runtime routing agent | Configured OpenAI/Bedrock model call, permitted tool trace, seven signal types, grounded rationale and provider/cost provenance | Runtime implemented; live account evaluation pending |
| 4 | Review and independent approval | Quote acceptance, distinct approver threshold, expired/changed evidence rejection | Controls implemented; connected demonstration pending |
| 5 | Simulated conversion and treasury readiness | Separate USD/USDC journals, actual test-USDC balance, estimated ETH gas and reservations | Accounting templates implemented; integrated demonstration pending |
| 6 | Authorized testnet USDC settlement | Signed/submitted hash, network/contract/wallet binding and receipt confirmations; ETH gas only | Public-testnet end-to-end execution pending |
| 7 | Immutable ledger | Balanced journals by asset, quote/policy provenance, actual fee evidence and linked reversal | Templates implemented; PostgreSQL/integrated demonstration pending |
| 8 | Optional simulated USD/INR payout | VisaPaymentAdapter, captured payout amount, provider evidence and labeled simulated bank credit; omitted for wallet outcome | Backend implemented in step 8; connected execution/UI pending |
| 9 | Reconciliation and evidence export | Payment/ledger/chain/payout match, incomplete fees and discrepancies remain non-reconciled | Backend and full-lifecycle runner implemented in step 8; actual execution pending |
| 10 | Reusable customer configuration | Second fictional customer/policy using same services and permission boundaries | Demonstration pending |

## Required failure and disruption demonstrations

Capture expected response/state, audit/event reference, recovery command and actual
result for each: provider outage/congestion; stale signals; low USDC/ETH/fiat
liquidity; policy/fraud rejection; expired or replayed approval; changed idempotent
request; duplicate/out-of-order events; uncertain blockchain submission without
blind retry/reroute; reverted receipt with gas; missing network fee components;
payout failure/unknown outcome; and a ledger/reconciliation amount mismatch.
These scenarios must not cause duplicate transfers or speculative reversals.

## Evidence bundle to complete

Three primary screenshots: payment intent/routing with actual AI trace; immutable
ledger and reconciliation; genuine Base Sepolia explorer/receipt evidence.
Also retain non-sensitive API responses, IDs/correlation, timestamps, measured
application/testnet timing/cost, test results and run configuration. Clearly
separate simulated provider signals and projected commercial outcomes from
measured results. Codex development assistance is separate from runtime inference.

Before delivery add exact reproducible commands, login/role steps, screen actions,
expected outcomes, example redacted response shapes, evidence locations, supported
reset/recovery instructions and observed limitations for every implemented feature.


## Current reproducible accounting verification

From repository root in PowerShell with Java 21 configured:

```powershell
.\services\mvnw.cmd -f services/pom.xml -pl settlement-ledger-service -am verify
node --test services/config/read-connections.test.cjs
```

Initial accounting-only verification: 67 unit/API checks passed; ten database checks
skipped because Docker is unavailable. With Docker, LedgerServiceIT exercises a
fresh real PostgreSQL container, migrations, atomic rollback, duplicate delivery,
SQL immutability, reversals, exact ETH precision and simulated INR payout.
These are verification commands, not a claim that the whole payment demonstration
can currently execute. No credentials or actual payment/testnet calls are required.


## Feature traceability and per-increment updates

The ten required demonstrations above map in order to F01-F10 in the
[architecture coverage table](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md#documentation-coverage-and-final-delivery-gate).
For every implementation increment, record: feature ID; source/API/event changes;
non-secret configuration; build/verification commands and actual result; required
identity/testnet/provider prerequisites; screen/API actions; expected artifacts;
failure/recovery procedure; and any unexecuted step. Keep implementation status
separate from demonstration status. The three headline captures remain F03 AI
routing, F07/F09 ledger/reconciliation, and F06 genuine Base Sepolia evidence.

## Shared access-control verification (F10)

From repository root with Java 21 configured:

```powershell
.\services\mvnw.cmd -f services/pom.xml -pl platform-security,blockchain-service,settlement-ledger-service -am verify
node --test services/config/read-connections.test.cjs
```

The shared library checks missing/invalid authentication, browser versus internal
client/scope, server-owned membership, foreign/missing ownership, and a failed
ownership store. Controller regressions check payment and journal/transaction-ID
lookups before exposing evidence. Local HTTP tests use a stub JWT decoder; they
are not evidence of signed-token verification against a live Cognito pool.
Existing claim-validator tests exercise the extracted validator separately.

For the eventual connected identity demo, configure issuer/JWKS/public client and
a distinct internal client/scope in the shared configuration. Supply server-owned
memberships for two fictional customers and the independent approver. Obtain real
access tokens through the configured provider; never paste them into evidence.
Demonstrate: anonymous evidence request returns 401; customer A cannot read
customer B's ledger/chain records; an operator cannot approve independently as a
second identity; browser tokens cannot issue internal blockchain commands even
with a matching scope; an internal token missing its scope is denied; a correctly
scoped internal token reaches the command's application guards. An authenticated
internal request is still subject to disabled execution/manual-write gates and
financial validation. Live setup/token issuance/execution remains pending.

## Ledger event transport verification (F05-F07)

The funding event must carry version 2.0, SIMULATED mode and immutable accounting
terms. It does not itself prove a conversion or a blockchain transfer occurred.
Submission/confirmation events must match an existing accounting context; a
missing funding leg is retried/reviewed rather than silently created. For an
integrated demo, retain the domain event IDs, payment/correlation, journal IDs,
chain mode/hash and DLQ/retry evidence. Do not publish invented LIVE events for
screenshots. Step 7 now connects the trusted wallet funding producer and three-leg
accounting acknowledgement gate. Fee-complete settlement, payout and reconciliation
remain step 8; actual connected execution still needs Codespaces verification.

Infrastructure verification from `infrastructure`:

```powershell
npm.cmd run build
npm.cmd test -- --runInBand
```

These verify messaging definitions and domain-body forwarding, not deployed AWS
resources. LocalStack/real PostgreSQL and a complete payment are separate
execution prerequisites. The six Dockerfiles include the shared security library;
Docker is unavailable in the current environment, so images have not been built.


## Final Word document requirement

At final implementation delivery, produce a separate Word document named
`StableCoins-Solution-Architecture-and-Design.docx`, consolidated from all relevant
repository documentation, accepted ADRs and actual implementation evidence. It
must cover earlier, current and subsequent increments, not only the latest step.
Include revision/date, source-document/ADR index, clear current-versus-target
status, diagrams, configuration/security, APIs/events/data, agent boundaries,
accounting, deployment/operations, verification and limitations. Resolve obsolete
examples against accepted decisions; do not silently present proposed ADR-013 as
accepted. Preserve the Markdown documents as living sources and refresh the Word
artifact for final delivery. Render and visually inspect every page before release.
The full demonstration runbook remains separately maintained and is cross-referenced.
This Word artifact is a tracked final deliverable; it has not yet been generated.

GitHub Codespaces is the intended container execution environment because the
current local machine lacks container capability. Use the existing dev-container
and [container/Codespaces guide](18-CONTAINERIZATION-CODESPACES.md); actual remote
container, database and integrated feature runs must be recorded when executed.


## Coverage of earlier implementation increments

The final demonstration includes all of the following, not just the most recent
security/accounting changes. These references are living implementation sources;
features lacking connected producers/UI remain explicitly pending.

| Existing increment | Required demonstration/verification | Source |
|---|---|---|
| Original payment APIs and event foundation | Owned payment creation, idempotency conflict, correlated state/history, compliance event and duplicate handling | API spec, event catalog, test plan |
| Intent/customer contracts | Explicit intent confirmation, beneficiary binding, customer policies and precision | 22-PAYMENT-INTENT-CONTRACTS.md |
| Central connections | One shared configuration source, external overrides, frontend secret exclusion, configured destinations | 23-SHARED-CONNECTION-CONFIGURATION.md |
| Seven-signal assessment | Success, latency, cost, fraud, availability, liquidity, external evidence; stale/missing data refusal | 24-SIGNAL-EVIDENCE-AND-ROUTE-ASSESSMENT.md |
| Treasury/provider readiness | Balance/gas observations, concurrent capacity reservation, expiry, unknown-submission pinning and provider liquidity | 25-TREASURY-AND-EXECUTION-READINESS.md |
| Quote/recommendation review | Server-owned access, quote acceptance, distinct approvals, changed/stale evidence rejection and audit | 26-RECOMMENDATION-APPROVAL-AND-ACCESS.md |
| Runtime intent/routing agent | Configurable OpenAI/Bedrock runtime, bounded read tools, unconfirmed draft, durable async run/replay, provenance and safe failure | 27-RUNTIME-INTENT-AND-ROUTING-AGENT.md |
| Approved accounting and event increment | Immutable per-asset templates, precise replay, reversals, funding/chain consumers, proper SQS event body and DLQs | 10-LEDGER.md; 11-EVENT-CATALOG.md; ADR-015 |
| Current access/container increment | Shared backend auth, customer evidence ownership, internal client/scope, six image build contexts and Codespaces | 14-SECURITY.md; 18-CONTAINERIZATION-CODESPACES.md |

Run the full Java reactor and frontend/configuration/infrastructure commands in the
[Codespaces guide](18-CONTAINERIZATION-CODESPACES.md#compile-and-execute-checks)
to exercise earlier as well as new checks. Preserve test failures and skip reasons.
The final Word design document must consolidate this same cumulative scope and
link back to the complete runbook and source/ADR index.


## Earlier verification record - access/container increment (2026-11-14)

All six Java applications and the shared security library compiled/packaged in a
full Java 21 Maven verify run. 453 Java cases passed, zero failures/errors, 39 skipped (PostgreSQL/Docker and one opt-in real-model check).
Totals deduplicate Surefire/Failsafe reports by module/test class, selecting the
latest report. Eleven shared configuration checks and nine CDK checks passed.
The six packaged JARs include byte-identical current shared configuration and the
security library. Dev-container JSON, six Docker build contexts and updated local
documentation links were checked without starting containers.

This increment reuses identity/membership validation, protects internal commands
and customer ledger/chain evidence, and updates applicable API/security/service/ADR
notes. Codespaces retains the existing Docker-in-Docker setup; global placeholder
AWS credentials and the incorrect container localhost default were removed.
The sample LocalStack endpoint is blank so Compose uses its service DNS default.

Assumptions: six deployables; accepted ADR-007 permits read-only ownership joins;
internal callers use a distinct Cognito client plus scope; ADR-015 is an operational
control ledger, not a full custody GL. Remaining work includes service-token
acquisition, trusted quote/funding producer and ledger acknowledgement, payout and
reconciliation wiring, connected React UI, complete AWS infrastructure, and actual
Cognito/model/PostgreSQL/public-testnet execution. The final Word deliverable is
tracked and not yet generated. That record preceded step 7; see the current step-7 section below for updated status.


## Step 7 connected wallet demonstration

Use [the complete wallet procedure](30-PAYMENT-FLOW-INTEGRATION.md#codespaces-demonstration)
with the existing preparation instructions in this runbook. The new opt-in runner
creates/replays the structured USD 10,000 intent, waits for simulated compliance
and per-provider quote/treasury capture, optionally requests the actual bounded
agent, records independent quote acceptance/approvals, executes and verifies
wallet confirmation plus per-asset ledger journals. Stable run ID and intent
values prevent accidental new-payment creation during recovery. The optional SQL
seed inserts fictional data and never silently resets an account or recipient.

Record MOCK or TESTNET explicitly. In TESTNET the runner independently validates
the public receipt, confirmation depth and exact USDC Transfer log. In MOCK the
hash/receipt are simulation evidence only. A successful step-7 run ends at
BLOCKCHAIN_CONFIRMED; it is not the full ten-feature demonstration above. Step 8
must add complete network fee/settlement, optional simulated payout and final
reconciliation evidence. Steps 9-11 still supply connected React screenshots,
AWS runtime deployment/operations, two-customer/commercial proof and final Word.

Additional commands (repository root):

```bash
node --test scripts/run-wallet-flow.test.cjs services/config/read-connections.test.cjs
./services/mvnw -f services/pom.xml verify
```

The Node tests validate the runner's verifier using fixtures. Maven database tests
must actually execute in Codespaces; skipped cases do not validate SQL/locks or
transactional integration. Preserve reports, failures/DLQ references, configuration
version, payment/correlation/execution IDs, ledger journal IDs and actual chain
receipt when capturing a successful run. No credentials belong in that bundle.

## Step 7 verification (2026-11-14)

Java 21 full-reactor `verify` completed successfully and packaged all six services
plus the two shared libraries. Latest Surefire/Failsafe reports, deduplicated by
module/test class, contain 544 cases: **502 passed, 0 failures/errors, 42 skipped**.
The skips are 41 database/container cases and one opt-in real-model evaluation.
These skips do not validate migrations, SQL concurrency or live inference.

The Node wallet-verifier/shared-configuration checks passed **21/21**. CDK TypeScript
build passed and its messaging/container tests passed **10/10**. Receipt tests use
fixtures; they do not prove a public transfer. No Docker images, PostgreSQL stack,
Cognito token flow, model call, AWS deployment or public-testnet wallet flow ran.
See [wallet integration](30-PAYMENT-FLOW-INTEGRATION.md) for opt-in Codespaces setup.

Assumptions remain the accepted six-service/shared-schema/operational-subledger
architecture. No new money-authorizing agent or deployable was added. Next: step 8
fee-complete settlement, optional simulated payout and reconciliation; steps 9-11
cover the connected UI, AWS runtime and final hackathon/documentation delivery.

## Step 8 cumulative demonstration update - 2026-11-15

The opening story and scenes above remain the complete presentation order. Earlier
step-specific entries are historical verification records. Step 8 now supplies
backend APIs for actual receipt fees, immutable six-journal wallet settlement,
nine-journal simulated bank payout and exact reconciliation; those capabilities
are no longer merely planned. React screen navigation remains proposed until step 9.
Follow [the complete step-8 walkthrough](31-SETTLEMENT-PAYOUT-RECONCILIATION.md)
for setup, each persona, context before action, terminal/API procedure, expected
outcome and recovery scenarios. Run scripts/run-payment-lifecycle.cjs to verify
RECONCILED; scripts/run-wallet-flow.cjs retains its wallet-confirmation scope.
Do not present expected results as an executed live demonstration. Retain the
actual evidence artifact, mode/date and public transaction proof after execution.

Coverage carried forward: intent/policy/identity (2/5), all seven signals (3),
treasury and liquidity (4), durable distinct approvals (5), configurable bounded
OpenAI/Bedrock runtime (6), connected execution and provider comparison (7), and
fees/payout/reconciliation (8). Step 9 adds connected screens, step 10 deployment
and operational recovery, step 11 the recorded proof and cumulative Word document.

## Step 8 coding and verification - 2026-11-15

Steps **1-8 of 11** now have code-level implementations. Three delivery steps
remain: connected React experience (9), completed AWS deployment/operations (10),
and recorded end-to-end proof, commercial/demo package and final Word architecture
and design document (11). Step 10 already has partial shared security/messaging
foundations. Code completion does not establish live acceptance.

The final Java 21 full reactor `verify` succeeded. Combined with the targeted
blockchain verification, latest XML reports counted once per module/test class
contain **600 cases: 546 passed, zero failures/errors, 54 skipped**. Those skips
are **53 container/database cases and one opt-in real-model evaluation**. Visa
and reconciliation database suites are now bound to `verify`; blockchain DB
suites were explicitly discovered in the targeted run. None of these skipped
cases establishes SQL migration, locking, rollback or six-service acceptance.

The synthetic loopback HTTP test actually exercised Web3j/Spring Jackson
compatibility and raw receipt fee parsing. Recovery tests verify filling a missing
price only for the same mined transaction, rejection of conflicting known evidence,
and no additional signing/submission. All data in that HTTP test is synthetic.

Node verifier/configuration tests passed **30/30**. CDK TypeScript build passed;
its infrastructure/configuration tests passed **11/11**. All six packaged JARs
were checked for the current common configuration and shared workflow library.
Markdown relative-file links and fenced-code balance were checked.

Docker images, PostgreSQL execution, live Cognito, real model inference, AWS
deployment and a public-testnet end-to-end transfer did not execute locally.
[Machine-readable results](step8-build-results.json) retain the exact test scopes.
[Implementation, configuration, APIs and demo/recovery procedure](31-SETTLEMENT-PAYOUT-RECONCILIATION.md)
cover the completed increment. The existing agent runtime and deterministic
financial services are reused; the model receives no money-authorizing tools.

## Step 9 coding and verification - 2026-11-15

Steps **1-9 of 11** are code-complete. Two delivery steps remain: completed AWS
deployment/operations (10), then live acceptance/proof, the commercial/hackathon
demo package and final cumulative Word architecture/design document (11).

The connected React workspace implements authenticated customer views, confirmed
USD intents, seven-signal provider evidence, actual bounded-agent run records,
quote/version acceptance and independent approvals, treasury gas readiness,
authorized execution, chain receipts/fees, immutable journals, reconciliation and
optional simulated payout. Existing owner services and financial controls are reused.
The new payment-service read projection checks ownership before cross-owner reads;
the Visa simulator stays private. Connection settings remain centralized, including
public identity endpoints and the configured chain/explorer metadata.

The Java 21 full-reactor `verify` succeeded: **602 cases, 555 passed, zero failures/
errors, 47 skipped**. Those skips are 46 Docker/database cases and one opt-in model
evaluation. The latest combined historical reports contain 609 cases with the
same 555 passes and 54 skips; the extra seven are previously discovered/skipped
blockchain container cases, not new execution evidence. All six service packages
were refreshed after the final public configuration update and checked for the
current shared configuration and workflow library.

The frontend production build and lint pass without warnings. The combined Node
suite passes **45/45**. The headless browser rehearsal passes **19/19**
checks against an explicitly synthetic HTTP fixture. It exercises an uncertain
creation response, persistent warning and same-key replay; server-role UI gates;
separate approvers; agent display; execution; honest MOCK evidence; exact ledger
amounts; safe configured explorer links; mobile layout and session clearing.
Desktop/mobile synthetic screenshots were inspected; they are not live proof.

No Docker stack, PostgreSQL integration, live Cognito/model, AWS deployment or
public-testnet end-to-end transfer executed. See the [machine-readable scopes](step9-build-results.json),
[UI implementation/setup](32-CONNECTED-REACT-WORKSPACE.md) and
[complete role/screen/action runbook](29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## Local-first prerequisite for the connected demonstration

Before the screen-by-screen scenes above, execute the staged checks in
[Local-first validation](33-LOCAL-FIRST-VALIDATION.md). Start with the complete
payment story, then retain each scene's screen, role, context, actions and outcomes.
Record actual local results separately from synthetic browser rehearsal and from
real model/public-testnet evidence. AWS deployment follows local acceptance.

## Codespaces launch and hackathon preparation

Start with [the cumulative Codespaces launch guide](34-CODESPACES-DEMO-LAUNCH.md)
for the selected GitHub/AWS destination, identity prerequisites, private forwarded
UI address and configuration order. [The proof package](35-HACKATHON-PROOF-PACKAGE.md)
adds two customer policies, baseline measurements, controlled simulated disruption
and live-evidence gates. Step 11 preparation now precedes AWS application deployment.
These additions do not claim completed Codespaces, model or testnet execution.

### Preparation diagnosis and recovery - 2026-11-16

The supplied records for PAY-52272AAC4E14 / CORR-17EF6A110D4B were replayed locally
at the original attempt time. Both provider documents and the MOCK treasury
observation deserialize, assemble all fourteen observations (seven per provider),
and survive a JSON round trip. The deterministic assessment selects Provider A:
USD 2.00 simulated provider fee + USD 0.21 simulated ETH budgeting cost = USD 2.21,
within the pinned USD 5 limit. Provider B's total is USD 5.21. This replay is a
regression test, not a live quote, real wallet balance or model execution.

A transaction-ordering defect was found in the recommendation handoff: JPA may
queue the new recommendation INSERT while JDBC immediately links its ID from
payment_flow_preparation. An explicit JPA flush now precedes the JDBC handoff,
inside the same transaction; failure still rolls back recommendation/audit/outbox
writes. This addresses a plausible cause of the observed retries. The supplied
logs omit the exception, so the original Codespaces failure is not conclusively
attributed until the database test and a fresh UI attempt execute.

Worker deferrals now report exception class, root cause class and a validated
SQLSTATE (for example, 23503 identifies a foreign-key violation). Exception text,
SQL parameters and stack traces are withheld because they can contain secrets or
PII. The previous 120-second timeout remains; extending it is not the fix.

Verification: 44 targeted Java tests passed, zero failures/errors/skips. These
include the captured-data replay, routing assembler, payment flow, recommendation
controls and sanitized failure logging. Two new PostgreSQL regression cases
compile but have not executed locally; Docker is unavailable on this laptop.
Actual Cognito renewal, model inference and public-testnet acceptance remain open.

The existing intent deadline, 2026-11-16T13:31:45.861Z, has now elapsed. Preserve
that payment and its timed-out preparation. Do not edit its immutable intent or
manually mark it READY. For the next UI rehearsal after updating/restarting:

1. Open the application URL and sign in securely as demo-a-operator. Cognito
   authenticates; the API resolves Customer A / PAYMENT_OPERATOR. A fresh sign-in
   obtains memory-only refresh credentials for the new session behavior.
2. Overview > New payment. Select the seeded USD account and Fictional Demo
   Supplier. Use USD 1,000, USDC_WALLET, maximum fee USD 5, LOWEST_COST and AUTO.
   Set Deliver by to a future UTC timestamp with enough time for the demonstration.
3. Confirm the instruction and click Create payment intent. Record the NEW payment
   and correlation IDs; retain the old IDs separately as diagnostic history.
4. Intent & routing > Prepare / refresh recommendation. Wait for a recorded READY
   result and inspect deterministic provider comparison, eligibility and freshness.
   Refresh only reloads evidence; it does not start a replacement preparation.
5. If preparation fails again, retain the new request ID and inspect payment-service
   logs for errorType/causeType/sqlState. Stop before approvals or execution. Do not
   rerun payment creation to work around an uncertain financial outcome.
6. Once ready, continue the existing complete navigation map: Intent & routing >
   Run reasoning agent (explicit live-provider setup required) > Treasury readiness >
   Quote & approvals as approver one and then approver two > Execution journey as
   operator > Blockchain evidence > Ledger & reconciliation > optional fiat payout.
   Each step must use the same valid recommendation and payment IDs. Quote/approval
   and treasury evidence expiry remain enforced independently of login continuity.

The captured observation is MOCK and uses the default mock treasury address. It
does not establish balances or funding of the separately generated real wallets.
