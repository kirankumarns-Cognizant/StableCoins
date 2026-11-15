# ADR-013: Advisory Payment Investigation Agent

Status: PROPOSED

Date: 2026-11-13

## Context

The POC demonstrates a USD 10,000 cross-border supplier payment using USDC
settlement, an internal ledger, optional simulated Visa payout, and
reconciliation. Existing application logic uses deterministic Java 21 /
Spring Boot services. No model-powered payment agent is implemented.

Operators need to explain delayed payments, investigate failures, and understand
reconciliation discrepancies. These tasks may require selecting several evidence
sources and summarizing their findings. An agent can assist without making model
output authoritative for financial execution or accounting.

A service is a deployment and ownership boundary. An agent is behavior that uses
a model to select permitted tools and interpret evidence. An agent can run inside
an existing service. A fixed summary can use one model call without an agent loop.

## Proposed Decision

Introduce an optional, advisory Payment Investigation Agent inside
payment-service, running in its existing ECS Fargate deployment. Retain the six
services and deterministic payment orchestrator. No seventh microservice is proposed.

This ADR proposes the capability only. It does not authorize implementation,
select a model provider, or change any accepted ADR.

### Initial Scope

- Explain a payment's recorded journey and current state.
- Investigate delayed, failed, or uncertain blockchain submissions.
- Gather and compare existing payment, ledger, blockchain, and payout evidence.
- Explain existing reconciliation discrepancies and recommend next actions.
- Identify missing or stale evidence and report unsupported conclusions as unknown.

The agent may adaptively select read-only tools within a bounded investigation.
It must separate recorded facts, hypotheses, and recommendations. Rail
recommendations, compliance decisions, autonomous remediation, and multiple
collaborating agents are outside the initial scope.

### Relationship to the Payment Flow

| Step | Authoritative execution | Agent contribution |
|---|---|---|
| USD payment initiation | Payment API and validation | Explain recorded initiation status |
| Compliance and FX/fee quote | Simulators and workflow rules | Summarize recorded simulated results |
| Simulated USDC funding | Payment orchestration | Explain recorded funding status |
| Token balance and gas checks | Blockchain service calculations | Explain check results and failures |
| Transaction signing | Blockchain signing component | No access to signing or keys |
| Transaction submission | Blockchain service and idempotency controls | Inspect existing submission evidence |
| Confirmation and settlement | Blockchain receipts and ledger rules | Explain recorded settlement evidence |
| Optional fiat payout | VisaPaymentAdapter and Visa Simulator | Explain recorded simulated payout status |
| Reconciliation | Deterministic reconciliation rules | Investigate recorded discrepancies |

USDC remains the payment asset; ETH pays gas. No USDC-to-ETH conversion is added.
Fiat funding, conversion, FX, compliance, Visa, and beneficiary fiat credit remain
explicitly simulated. MOCK blockchain evidence must never be described as real
testnet execution. The agent does not complete currently missing payment features.

### Model Integration

Define a typed model-provider interface within payment-service. Keep provider SDK
types outside business models and expose only the inference and tool-selection
capabilities needed for investigation.

Provider, model identifier, region, data handling, deployment access, cost limits,
and any Java library remain open decisions to verify before implementation.
Configure model selection externally. Use AWS Secrets Manager/KMS for any
credentials. Introduce an agent framework only if justified within the approved
Java/Spring architecture.

### Permitted Tools and Ownership

Expose an explicit allowlist of typed, read-only tools to read:

- Payment records and status history.
- Recorded compliance and quote results when available.
- Existing blockchain transaction metadata and confirmation evidence.
- Posted journals, entries, and settlement records.
- Existing Visa simulation and reconciliation records when available.

Implement tools as application-controlled parameterized queries or internal read
APIs. ADR-007 permits cross-service reads of the shared database; tool code must
respect existing ownership. ADR-008 remains unchanged: reconciliation performs
its own direct database reads and owns its reconciliation results.

No arbitrary SQL, shell, unrestricted HTTP, arbitrary RPC endpoints, database
credentials, private keys, signed transaction bytes, or Secrets Manager access
may be exposed to the model. Initially use persisted blockchain evidence.

Bind every tool call to the authenticated user's authorized payment scope on the
server. Model-generated identifiers and authorization claims are untrusted.
Preserve paymentId and correlationId formats and propagation. Resolve existing
cross-service identifier inconsistencies before enabling dependent tools.

### Execution Boundaries

The agent cannot create payments, approve compliance or quotes, select rails,
move funds, sign or submit transactions, retry submissions, initiate Visa payouts,
post or reverse journals, change payment states, or write reconciliation results.
These remain deterministic service operations.

Recommendations are advisory. Any subsequent action must use the service's
validated command API and applicable approval process. An investigation request
never constitutes approval to execute a recommendation.

Keep investigation outside the payment workflow's critical path. Disable the
feature by default until prerequisites and acceptance criteria are satisfied.
Payment execution must work when the model is unavailable or the feature is off.

### Output, Security, and Audit

Return a validated structured result containing investigationId, paymentId,
correlationId, evidence collection time, observed status, a concise explanation,
source record references, hypotheses, missing evidence, recommended next actions,
model identifier, prompt version, and completion or failure status.

Validate source references against actual tool results. Never invent transaction
hashes, confirmations, balances, payout references, or reconciliation results.
Preserve source timestamps and simulation/mode labels. Explain when evidence was
collected at different times and may not represent one consistent snapshot.

Use structured JSON audit logs for investigation identifiers, tool names, record
references, timings, and outcomes. Do not log credentials, keys, full sensitive
PII, raw prompts, or raw model responses by default. Record an evidence-based
explanation without requesting or persisting hidden chain-of-thought.

Treat user text, invoice descriptions, stored notes, and tool results as untrusted
data. Embedded instructions cannot grant tools or change authorization. Minimize
and redact model inputs before they leave the service.

### Failure Handling

Bound inference by timeouts, maximum tool iterations, input/output size,
concurrency, and cost budget. Use bounded retries only for classified transient
inference or read failures. No retry may execute a financial operation.

On model unavailability, malformed output, exhausted limits, or missing tools,
return an explicit unavailable or incomplete result with available deterministic
facts. Do not infer success or silently fabricate missing evidence.

## Alternatives Considered

1. Deterministic diagnostics and templates: simple, but less flexible for adaptive
   evidence gathering; remain appropriate for fixed checks.
2. Single model summarization call: suitable for a predefined evidence bundle;
   prefer it when adaptive tool selection is unnecessary.
3. Advisory agent inside payment-service: proposed for bounded investigations
   within existing deployment and ownership boundaries.
4. Separate agent microservice or autonomous payment agent: deferred because of
   added deployment complexity or financial authority beyond this scope.

## Consequences

- Adds explanations without changing ledger rules or financial authority.
- Introduces inference cost, latency, nondeterminism, and a model data boundary.
- Requires prompt versioning, authorization, output validation, and evaluation.
- Investigations remain incomplete where domain services are still skeletons.
- Accepted ADRs and AGENTS.md remain authoritative; future write tools, new
  deployments, or ownership changes require further architecture review.

## Acceptance Criteria for Future Implementation

- Unit tests use a stub provider; normal tests require no inference access.
- Authorized investigations explain payment history with valid evidence references.
- Tests reject unauthorized payment access and attempts to invoke write tools.
- Prompt-injection tests cannot change permissions or expose secrets.
- Missing, stale, contradictory, simulated, and MOCK evidence is labelled correctly.
- Submission uncertainty never causes an agent-initiated transaction retry.
- Reconciliation explanations preserve deterministic results and ledger balances.
- Timeout, malformed-response, and iteration-limit tests return explicit failures.
- Disabling or failing the agent leaves payment execution unaffected.
- An opt-in evaluation with the selected model verifies factual accuracy,
  evidence attribution, and acceptable latency/cost before enabling the feature.

## Approval and Follow-up

Pending owner acceptance of this proposed scope and execution boundary.
Before implementation, record the provider/model selection, authorized operator
roles, resource limits, audit retention, and evaluation thresholds. Implement
one investigation capability at a time after approval. This change adds only
an ADR; no runtime agent or infrastructure is implemented.
