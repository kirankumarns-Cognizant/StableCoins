# StableFlow offline HTML prototype

Open `index.html` directly in a modern browser. No server, installation, internet connection or credentials are required. Files are self-contained in this directory; the ADR link opens a repository document.

## Walkthrough

1. Open Payment journey, select a scenario, then create a sample payment; alternatively use Initiate payment to choose amount, supplier and delivery route.
2. Select Run walkthrough. It pauses at compliance / quote for explicit quote acceptance.
3. Accept the illustrative quote to continue through USDC allocation, balances / gas, signing, submission, confirmations / ledger, optional fiat payout and reconciliation.
4. Inspect Blockchain evidence, Financial ledger and Reconciliation at any time. Navigation does not stop the running walkthrough; Pause does.
5. Inspect the Investigation agent scripted preview. It is not an AI runtime.
6. Reset the session to choose a different scenario. For uncertain submission, inspect the existing receipt to continue without another submission.

Scenarios: success; simulated compliance rejection; uncertain submission with receipt inspection; illustrative 25 USDC receipt discrepancy. Routes: supplier retains test USDC, or USDC settlement followed by simulated USD off-ramp / Visa / bank credit. No local-currency payout FX is modeled.

## Scope and assumptions

Every value, event, journal and outcome is illustrative, including blockchain activity. Nothing calls the application, a blockchain RPC, a bank, Visa or an AI provider. No real hash, contract, address, block or explorer link is fabricated. All state is in memory and disappears on reload. Creating another payment replaces the previous session payment. Demo idempotency/correlation identifiers illustrate the intended contract; durable idempotency is not implemented here.

This is the requested separate HTML design prototype. Production frontend remains React + TypeScript; backend, infrastructure, six service boundaries and accepted ADRs are unchanged. No package dependency or microservice is added. ADR-013 remains proposed; the agent view is fixed scenario-based text with no inference, tool selection, autonomous action or key access.

Default principal: USD 10,000 at notional 1:1 USDC; zero simulated platform fee. Opening illustrative balances: USD 250,000 customer account, 25,000 test USDC treasury, 0.05 test ETH treasury. The token balance reflects confirmed transfer; the ETH card shows opening balance, not a live post-transaction balance. Gas teaching values (65,000 units x 0.1 gwei) are not network quotes; a real estimator must include applicable data fees, execution fees and safety margins. Three confirmations is a prototype assumption, not a mandated backend configuration.

All ledger examples balance per asset and use approved chart codes. The mixed USD/USDC acquisition example in docs/10-LEDGER.md conflicts with that document's per-asset rule. Conversion is consequently an allocation memo here, not a posted cross-asset journal. Full conversion clearing, fee, gas and optional payout postings require accounting design resolution; this prototype does not silently change that architecture. Journal rows cannot be edited; no financial corrective action is implemented.

Events include eventId, eventType, eventVersion, paymentId, correlationId and timestamp. Additional UI audit events are illustrative; the prototype does not publish EventBridge/SQS messages. Submission uncertainty is a transaction condition; the payment stays FUNDED until illustrative submission evidence is recovered. Compliance rejection stops execution. Reconciliation mismatch retains RECONCILIATION_PENDING.

## Remaining implementation work

- Wire real APIs, asynchronous consumers, identifiers and state transitions across the existing services.
- Implement protected signing, durable submission idempotency / recovery and actual public-testnet evidence.
- Resolve and complete accounting mappings, optional simulated payout and reconciliation integration.
- Implement the production React / TypeScript screens and accessible operational controls.
- Complete AWS deployment, security, observability and meaningful public-testnet end-to-end validation.
- Accept / refine ADR-013, select a provider and evaluate the bounded read-only investigation agent before implementing it.

## Validation performed

- `node --check prototype/app.js` passed.
- Headless Chrome: 30 checks passed across all eight screens, quote acceptance, play/pause, both delivery routes, input validation, escaped user input, event correlation, balanced example journal amounts, all four scenarios, recovery without rebroadcast and scripted discrepancy explanation.
- No browser JavaScript exceptions were recorded.
- All eight screens checked at 390px viewport width: no horizontal page overflow.
- Desktop (1440px) and mobile (390px) overview screenshots rendered and visually reviewed; see `preview-desktop.png` and `preview-mobile.png`.
- Java / Spring / React builds and real-testnet tests were not run: those applications were not modified or invoked by this standalone artifact.

## Current application documentation

The earlier accounting-resolution notes above describe this offline prototype's
original limitations. ADR-015 is now accepted and application accounting templates
are implemented; this static prototype has not acquired that integration. Use the
[cumulative screen/role/action runbook](../docs/29-PROTOTYPE-EXECUTION-RUNBOOK.md)
for the planned connected presentation and the
[step-7 Codespaces runner](../docs/30-PAYMENT-FLOW-INTEGRATION.md) for current opt-in
wallet API execution. Neither this page nor its scripted agent is real inference
or blockchain evidence.
