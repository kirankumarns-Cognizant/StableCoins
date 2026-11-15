# Runtime intent and routing agent

Date: 2026-11-14. Implements the bounded runtime component of accepted
[ADR-014](../adr/ADR-014-STABLECOIN-AGENTIC-ROUTING.md) inside payment-service.
No service, financial authority, chain or deployment count changes.

## Runtime behavior

A typed AgentModel port invokes a real Responses API endpoint. The model chooses
which permitted tools to call, reads their results and can gather further
observations or revise a recommendation. This is an actual model/tool loop;
scripted AgentModel implementations exist only in tests. The seven functions are
read_intent, read_policy, list_candidates, read_quote, read_signals, assess_routes
and refresh_evidence. Tools receive no payment/customer/URL/SQL parameters.
Authentication and scope come from the server-owned run context on every source
read. No tool can approve, create a payment, reserve, sign a blockchain transfer,
submit, initiate Visa, retry a transaction or write a ledger journal.

The agent reuses the existing intent, customer policy, immutable recommendation,
seven-signal evidence and deterministic routing assessor. A recommendation must
match the deterministic preferred candidate, deliver the required outcome, inspect
all seven selected-candidate signals and cite only inspected evidence. Missing or
expired quotes, invalid output, refusal, model failure or exhausted limits fail
closed. A refresh that changes the source invalidates earlier reads; all needed
material must be read again. Final source verification rejects concurrent changes.
Unknown/submitted chain operations and prior approvals block new routing runs.

Financial amounts, success ratios, fraud scores and delivery estimates are
rendered by application code from assessed evidence. The model selects explanation
topics and references, rather than generating unrestricted financial claims or
confidence scores. Availability, available liquidity after reservations and
external observations appear alongside explicit modes and eligibility findings.
Hidden reasoning is not the explanation. Encrypted reasoning items required for
stateless continuation remain in run memory only, in the original response order.

## Natural-language interpretation

The same final structured response can contain an optional IntentDraft: source
USD amount, outcome, absolute deadline, USD fee cap, priority and alternatives.
Every populated field needs character-span evidence in the redacted instruction.
Amounts require an explicit USD/$ source span. The application validates numbers,
dates, source grounding and existing customer policy; it reuses the policy gate
used by structured payment creation. Missing draft fields remain unknown.
confirmed is always false. A changed requirement forces clarification with no
selected path; it cannot overwrite the confirmed payment instruction. Relative
dates need clarification. New payment creation still requires explicit structured
confirmation through the existing payment API. The current endpoint enriches an
owned, confirmed payment with captured recommendation evidence; it is not a
pre-payment free-text payment-creation endpoint.

## Authenticated asynchronous API

POST /api/v1/payments/{paymentId}/recommendations/{id}/agent-runs

Required: verified Cognito access token, configured PAYMENT_OPERATOR membership,
enabled/allowlisted customer, Idempotency-Key and JSON instruction (1?2000 chars).
A new claim returns 202/RUNNING. Poll GET at the same URL with /{runId} appended.
Terminal replay returns 200 with the original run; changed input with the same key
returns 409. Customer membership is checked again during execution and reads.
No caller-supplied identity header grants access.

V10 creates payment-service-owned routing_agent_run. It retains paymentId,
correlationId, customer/actor, source recommendation, hashed idempotency/fingerprint,
configured model/provider/region, prompt version, deadline, terminal status and a
validated report. Reports contain source/intent/policy/rule versions, candidate
assessments, tool traces, provider response IDs and observed token usage. No raw
instruction, provider body, private reasoning or credentials are persisted.

A short transaction claims a run under the parent payment lock. Model calls run
outside the transaction using a bounded executor with no task queue. Partial
uniqueness permits one active run per payment; a total run cap limits repeated
inference. Replay never schedules inference again. Expired/crashed runs time out
on access/admission and never automatically restart. Late completions cannot
replace a terminal timeout. Capacity errors and model failures have constant,
sanitary error codes. No financial payment state advances.

Reports supplement the existing immutable human-review recommendation. They do
not rewrite approved material, accept a quote or grant execution permission. A
new quote/evidence revision still needs its own existing review lifecycle.

## Shared provider configuration

All properties use services/config/connections.properties, with optional existing
CONNECTIONS_CONFIG overrides and explicit Compose aliases. The same artifact can
select either endpoint without changing business logic or recompiling. Connection
changes currently require a service restart; there is no unvalidated automatic
provider failover or per-request arbitrary endpoint selection.

| Setting | Meaning |
|---|---|
| AI_PROVIDER | OPENAI or BEDROCK; default OPENAI |
| AI_AUTH_MODE | SECRET_BEARER or AWS_SIGV4; SigV4 is Bedrock-only |
| AI_BASE_URL | HTTPS Responses API root; default https://api.openai.com/v1 |
| AI_MODEL | Exact model ID; blank by default and required before enabling |
| AI_REGION | Bedrock Region, matching its endpoint |
| AI_API_KEY_SECRET_NAME | Secrets Manager secret holding the raw bearer credential; required only for bearer mode |
| AI_TIMEOUT_MS | Per-request bound, default 30000; maximum 120000 |

Bedrock Runtime example: provider BEDROCK, auth AWS_SIGV4, Region us-east-2,
base URL https://bedrock-runtime.us-east-2.amazonaws.com/openai/v1.
The official OpenAI guide uses openai.gpt-5.6-terra in that Region as an example;
this is a candidate for evaluation, not proof of access, suitability or a
hardcoded model selection. The configured model must support strict function
calling, structured output and required stateless continuation behavior. Record
its evaluation and current price assumptions before enabling it for customers.

Bedrock SigV4 uses the AWS SDK signer and default credential chain, including an
ECS task role. Runtime and Mantle signing services remain distinct. Only the exact
configured canonical Bedrock endpoint/Region/path is accepted for AWS signing.
Credentials are never available to the model. Direct OpenAI and Bedrock bearer
keys use Secrets Manager; AWS handles its KMS decrypt boundary. No environment
variable containing a raw model API key is introduced. Private settings never
enter the browser allowlist. AWS IAM, model access, quotas and endpoint feature
support remain deployment gates. Additional model families would need their own
compatible adapter/evaluation; they are not silently substituted.

## Resource and cost limits

ROUTING_AGENT_ENABLED defaults false and ROUTING_AGENT_ALLOWED_CUSTOMER_IDS is
empty. An enabled agent requires an explicit customer allowlist, configured
provider/model/auth and positive model price assumptions.

Defaults: 12 rounds, 24 tools, 4096 output tokens per request, 250000 total observed
tokens, 65536 request bytes, 131072 response bytes, two concurrent runs per task,
ten total runs per payment and a 90000 ms absolute run deadline. Each setting is
an explicit ROUTING_AGENT_* alias in the shared file. A run spanning a changed
revision may require a deliberately configured larger round limit (maximum 20).

ROUTING_AGENT_INPUT_USD_PER_MILLION and OUTPUT_USD_PER_MILLION default zero;
set both to positive values for the selected provider/model before enabling.
MAXIMUM_INFERENCE_COST_USD defaults 1.00. Conservative preflight allowances and
observed input/output usage enforce token/cost limits before further requests.
Estimated inference cost is labeled CONFIGURED_TOKEN_RATES_NOT_PROVIDER_BILLING;
it is not a measured invoice or the simulated route's inference-cost assumption.
Concurrency is per ECS task; the durable per-payment admission limit is shared.
Organization-wide AWS budget/account throttles belong to deployment operations.

The transport has no automatic retry or redirects. It caps headers/body wait,
body size, context size and usage, cancels timed-out requests and sanitizes
provider failures. Known credential/address/email patterns are redacted from text;
this is not a comprehensive PII detector. Do not place full invoices, secrets or
sensitive PII in this optional instruction. DTO logging masks instruction/report
contents. Keep request-body tracing and provider request/response logging disabled.
store:false controls API response storage, not a claim of zero provider retention;
review provider-specific account/project retention and regional routing settings.

## Verification and rollout gates

Step 6 adds unit/HTTP/provider/source tests and PostgreSQL integration tests, plus
RoutingAgentLiveTest behind RUN_REAL_MODEL_TESTS=true. Ordinary verification
explicitly keeps that flag false. Stub tests include adaptive provider outage,
required evidence reads, policy/ranking mismatch, invented references/confidence,
financial tools, customer scope, stale revisions, time/cost/iteration limits,
source-grounded drafts, replay, capacity, timeout and credential sanitization.

Live evaluation requires an explicitly selected model, pricing, allowed customer,
correct provider authentication and actual account access. It invokes the real
model using clearly simulated scenario evidence and no financial command. A
successful stub test is not evidence of a model call. Database tests use public
PostgreSQL Testcontainers and skip if Docker is unavailable; skips are not proof
of migrations, locks or constraints. Final executed counts are in the delivery
plan's step 6 verification entry.

Trusted quote and live readiness producers, execution-time refresh/reservations,
complete testnet transfer, accounting/payout/reconciliation, React screens, AWS
deployment and hackathon/commercial evidence remain subsequent delivery steps.
Captured evidence here is labeled CAPTURED_SERVER_EVIDENCE_NOT_LIVE_POLL; no
simulated fixture source is registered as a production fallback.

Official API review (2026-11-14):
[function calling](https://developers.openai.com/api/docs/guides/function-calling),
[structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs),
[stateless reasoning](https://developers.openai.com/api/docs/guides/reasoning),
[OpenAI on Bedrock](https://developers.openai.com/api/docs/guides/amazon-bedrock),
[AWS endpoints](https://docs.aws.amazon.com/bedrock/latest/userguide/endpoints.html).
