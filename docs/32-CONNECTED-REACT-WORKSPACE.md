# Connected React payment workspace - step 9

Updated 2026-11-15. This increment connects React and TypeScript to the existing
payment workflow. It adds no deployable service, financial engine or money-moving
agent. See the cumulative [architecture](28-SOLUTION-ARCHITECTURE-AND-DESIGN.md),
[delivery plan](21-AGENTIC-STABLECOIN-DELIVERY-PLAN.md) and
[complete demonstration script](29-PROTOTYPE-EXECUTION-RUNBOOK.md).

## What is implemented

StableFlow replaces the Vite counter with a responsive payment workspace. The
browser reads the customer catalog, creates a confirmed USD instruction, requests
server-side preparation, displays deterministic provider comparisons and actual
bounded-agent runs, records quote acceptance/independent approvals and requests
execution through the existing APIs. It follows audited lifecycle records through
reconciliation. Fiat endpoints always carry simulation labels.

The application has no embedded successful-payment fallback and no browser wallet
key, signer, rail scoring, ledger posting or automatic mutation retry. Missing,
failed or pending evidence stays visible as such. The old offline HTML prototype
remains illustrative; these are separate connected application screens.

| Navigation | Actual route | Purpose and API evidence |
|---|---|---|
| Sign in | `/` or configured callback | Configured public identity client; server validates access token |
| Overview | `#/overview` | Customer accounts, server policy and latest 50 payments |
| New payment | `#/payments/new` | Confirmed USD intent, outcome, deadline, fee cap and routing priority |
| Intent & routing | `#/payments/{PAY}/routing` | Preparation, immutable reviews, seven-signal comparison, bounded agent |
| Treasury readiness | `#/payments/{PAY}/treasury` | Most recent preparation's captured USDC/ETH balances, reserves, gas estimate and expiry |
| Quote & approvals | `#/payments/{PAY}/approvals` | Versioned quote and recorded independent decisions |
| Execution journey | `#/payments/{PAY}/journey` | Explicit execution authorization, execution/closure failures, audited transitions |
| Blockchain evidence | `#/payments/{PAY}/blockchain` | Bound canonical transaction, fee completeness, status and supported testnet explorer |
| Ledger & reconciliation | `#/payments/{PAY}/ledger` | Immutable journals, exact per-asset balance, reconciliation report and findings |
| Optional fiat payout | `#/payments/{PAY}/payout` | Optional simulated provider payout or explicit wallet-only outcome |

Append `?review={recommendation UUID}` after a payment-screen hash route to inspect
an older review. Selection stays in navigation. Commands bind to that review and
quote; the backend rejects stale material. Treasury readiness explicitly shows the
most recent preparation, while blockchain evidence follows the pinned execution.
It does not pretend those observations belong to an older selected review.

## Existing services reused

Preparation, recommendations, acceptance/approvals/rejection, agent runs and
execution use the pre-existing payment-service command endpoints. The backend
retains all authorization, eligibility, expiry, quote/version binding, idempotency,
funding/reservation and submission controls. Agent execution still uses the
configured OpenAI or AWS Bedrock runtime from step 6 with read-only tools.
LedgerPostingService remains authoritative. VisaPaymentAdapter and its simulator
remain behind the existing service boundary.

Two read endpoints are added under the existing payment-service gateway prefix:

- `GET /api/v1/payments/workbench`: trusted customer ID, subject and server roles,
  configured policy, at most 100 active owned accounts/beneficiaries and latest 50
  owned payments. The limits are returned and described in the UI; these are not
  global statistics. Older payments can be opened by ID with an ownership check.
- `GET /api/v1/payments/{paymentId}/evidence`: preparation/review/decision/agent
  references, execution and closure, captured treasury, bound chain record,
  journals, optional payout and latest immutable reconciliation report.

Both require verified PAYMENT_OPERATOR membership. PaymentService.getPayment
checks ownership before any cross-owner evidence SQL. The projection uses a
read-only repeatable-read transaction and the existing FlowReadModels and
LifecycleReadModels. Queries are parameterized; read projections never mutate
owner tables or trigger payout/transfer. This follows accepted ADR-007/009; the
private Visa HTTP endpoint is not exposed to the browser. A report is application
evidence, not a claim that the browser independently reconciled the sources.

BigDecimal amounts become decimal strings in the projection, including wei and
18-decimal ETH. Browser BigInt arithmetic checks displayed journal totals per
asset; it never authorizes or posts accounting entries. Cross-asset netting cannot
make an imbalanced journal look balanced. Source evidence is expandable and the
explicit export contains current customer-authorized payment evidence, excluding
access tokens, environment configuration and credentials.

## Identity, configuration and hosting

Connection settings stay in [shared configuration](23-SHARED-CONNECTION-CONFIGURATION.md).
Only its explicit `public.*` allowlist enters the browser build:

| Environment value | Meaning |
|---|---|
| `PUBLIC_API_BASE_URL` | Default `/api/v1`; same-origin gateway is preferred |
| `IDENTITY_ISSUER_URI` | Existing server issuer validation setting |
| `IDENTITY_CLIENT_ID` | Cognito public application client; no browser client secret |
| `IDENTITY_AUTHORIZATION_URI` | HTTPS authorize endpoint, e.g. configured Cognito domain `/oauth2/authorize` |
| `IDENTITY_TOKEN_URI` | HTTPS token endpoint on the configured identity domain |
| `IDENTITY_REDIRECT_URI` | Registered callback on the exact frontend origin, normally `/auth/callback` |
| `IDENTITY_SCOPES` | Existing requested identity scopes |
| `BLOCKCHAIN_EXPLORER_BASE_URL` | Existing configured HTTPS explorer base, exposed with configured testnet chain ID; no RPC settings enter the browser |
| `PAYMENT_SERVICE_URL` | Vite development proxy target only; never exported to the browser |
| `FRONTEND_DEV_ALLOWED_HOSTS` | Comma-separated exact Vite hosts for forwarded Codespaces URLs; server-only |

Authorization-code flow uses S256 PKCE. Only the short-lived verifier/state is
stored in sessionStorage; it is consumed once and rejected after ten minutes or
on mismatch. Callback code is removed from the address bar before exchange.
Access tokens stay in React memory. The owner has applied the API cookie fix in the
active Codespace: API fetch uses `credentials: same-origin`,
refuses redirects, has a timeout, and requires same-origin or explicitly configured
HTTPS API access. Same-origin cookies let the private Codespaces forwarding
gateway recognize the signed-in GitHub browser. Cross-origin API requests do not
include cookies. Cookies do not grant application access: the API still requires
the Cognito bearer token and server-owned customer membership. The cross-origin
Cognito token exchange continues to omit cookies.
Server JWT validation and membership remain the authorization boundary. A 401 or
local signout clears all displayed customer data and cancels pending read requests.
Local signout does not claim to revoke the identity provider's global SSO session.
Refresh requires signing in again. No refresh token is persisted.

For a rehearsal, expand **Rehearsal: use an existing access token** and paste an
access token already accepted by the API. It is not decoded into a browser role
or stored persistently. The API validates it. The manual session has a maximum
15-minute local lifetime and is cleared sooner on an API 401. Use distinct
verified subjects for each approver; an approver inspecting these screens also
needs PAYMENT_OPERATOR. Narrative treasury/controller/auditor personas are not
additional implemented security roles.

The authorization-code + PKCE protocol follows
[AWS Cognito PKCE documentation](https://docs.aws.amazon.com/cognito/latest/developerguide/using-pkce-in-authorization-code.html)
and its [token endpoint contract](https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html).
Configure a public client with authorization-code grants and the exact callback.
No client secret belongs in frontend configuration.

## Codespaces execution

First complete the existing identity, membership, policy, database migrations,
six-service and prefunding setup in [step 8](31-SETTLEMENT-PAYOUT-RECONCILIATION.md).
That procedure is cumulative and is not replaced by this UI increment. Keep
`app.flow.enabled` enabled. Model reasoning additionally needs the existing
configured and enabled agent runtime. TESTNET needs test USDC and test ETH plus
the configured secret-backed signer. Do not use production funds.

Use Node 22.18 or newer (native TypeScript stripping is used by the unit tests).
From the repository in Codespaces:

```bash
cd frontend
npm ci
npm test
npm run lint
npm run build
npm run dev
```

Keep port 5173 private and open its forwarded URL from the Codespaces Ports view.
Set `FRONTEND_DEV_ALLOWED_HOSTS` to that exact hostname in the shared configuration
environment, and `IDENTITY_REDIRECT_URI` to its HTTPS origin plus `/auth/callback`.
Register that callback in the identity client, then restart Vite after changing
settings. Set PAYMENT_SERVICE_URL to the payment-service address reachable from
the Vite process. The development-only same-origin proxy forwards `/api/v1` there;
JWT validation is still performed by payment-service. It does not proxy the
private Visa simulator directly. Production uses a built bundle and the API
Gateway path, not Vite's development server. AWS hosting/edge wiring is step 10.
Public settings are build-time configuration: rebuild the bundle when they change.

## Recovery behavior

Reads poll every five seconds while the tab is visible and no command is busy.
A poll skips an already active read instead of aborting and restarting it. Slow
APIs can therefore complete within the existing per-request timeout. Selecting a
different payment or signing out still cancels stale reads so a late response
cannot overwrite the newly selected customer/payment.

Use **Refresh** or **Refresh evidence** to fetch current server records. Both are
disabled during a refresh and show **Refreshing...**; the live status reports
progress, success time or failure time. **Last successful refresh** changes only
after a complete successful read. On failure, previously loaded records stay
visible with an explicit out-of-date warning and commands remain disabled.
HTTP 502/503/504, connection failures and timeouts explain that API availability
must be restored before retrying. No success or empty balance is fabricated.
Refresh performs reads only: it does not restart servers, seed missing accounts,
repeat a failed payment command or submit a replacement blockchain transaction.

Before a mutation needing a key, the UI saves its key and exact body in
customer-scoped sessionStorage. An uncertain payment-creation outcome locks its
instruction. **Retry original instruction** replays the same body/key, including
after page refresh and reauthentication in the same browser tab. Do not clear
that storage, close the tab or create a replacement while a result is uncertain.
A definitive 400/422 creation rejection unlocks correction; the corrected body
uses a new key. Creation does not silently choose a new key after transport errors.
Other keyed commands similarly retain keys until acknowledged; approval commands
reuse existing server actor/version idempotency. There is no automatic POST retry.

Insufficient liquidity, missing signer configuration, expired evidence, rejected
approvals, incomplete receipt fees and reconciliation findings remain backend
controls. A submitted or UNKNOWN operation stays pinned; the UI has no blind
resubmit, refund, resolution or prefunding button. Use the existing privileged
runbook procedures where authorized. Expanded deployment/operations UX remains
step 10. A malformed/incompatible response produces an explicit rendering error,
not a replacement transaction or a silent mock fallback.

## Verification and outstanding live acceptance

Refresh regression check, 2026-11-16: frontend build, lint and all 14 unit checks
passed. The headless browser rehearsal passed 23/23 checks, including a read held
longer than the five-second polling interval, disabled duplicate refresh controls,
HTTP 502 feedback, retained last-successful evidence and successful read-only
recovery. The first browser attempt was blocked by Windows sandbox GPU-process
permissions; the rerun outside that sandbox passed. These are synthetic UI checks,
not live service, identity, model, ledger or public-chain acceptance. Architecture,
server authorization and financial mutation responsibilities are unchanged.

See [step 9 verification](step9-build-results.json) for executed checks and scope.
The browser rehearsal in `frontend/tests/browser-smoke.mjs` uses only a synthetic
HTTP API fixture and an isolated headless browser profile. Its model output,
journals, balances and hashes are fixtures, never settlement proof. Screenshots
are saved under ignored `.local/step9-ui`, with synthetic names. Running this
fixture does not establish integration with PostgreSQL or any real provider.

Actual acceptance still requires Codespaces/container database tests, authenticated
live UI walkthrough, a configured model inference and an independently checked
public-testnet transfer. Capture the three headline screens only from that actual
run if presenting live financial/agent evidence. Complete AWS deployment and the
final Word architecture/design document remain steps 10 and 11 respectively.

## Codespaces launch and hackathon preparation

Start with [the cumulative Codespaces launch guide](34-CODESPACES-DEMO-LAUNCH.md)
for the selected GitHub/AWS destination, identity prerequisites, private forwarded
UI address and configuration order. [The proof package](35-HACKATHON-PROOF-PACKAGE.md)
adds two customer policies, baseline measurements, controlled simulated disruption
and live-evidence gates. Step 11 preparation now precedes AWS application deployment.
These additions do not claim completed Codespaces, model or testnet execution.
