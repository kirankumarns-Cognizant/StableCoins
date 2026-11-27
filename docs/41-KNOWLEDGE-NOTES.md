# Personal learning knowledgebase: blockchain and stablecoins

Created: 26 November 2026. Dates use Asia/Calcutta (IST).

This is my personal learning reference, built from the questions I ask while
working on the POC and the explanations that help me understand the answers.
Entries retain plain-language definitions, examples, comparison tables and
sources so I can revisit a concept without searching the conversation.

Examples of questions to capture: "What is EVM?", "How do ERC-20, ERC-721 and
ERC-1155 differ?" and "Can EVM run on AWS?" The goal is understanding concepts.
Implementation coverage, completion percentages, test results and task status
belong in project documents, rather than in these learning entries. See the
[implementation backlog](40-IMPLEMENTATION-BACKLOG.md) and
[activity history](36-POC-ACTIVITY-STATUS.md) for those records.

Standing owner authorization, reaffirmed 26 November 2026: review every project
conversation and retain useful learning about functional domains, technology and
tools without asking each time. Summarize concepts with dates, accessible examples
and sources; enrich existing topics instead of repeating them. Routine progress
counts and operational exchanges remain in the project records.

## Scope of this learning reference

Clarified: 26 November 2026. Capture explanations of POC functionality and its
place in the stablecoin value chain, domain workflows, tools, technology,
deployment and enterprise foundations as well as definitions. A functional
explanation can describe implemented behavior with dated source references and
clear real/simulated/future boundaries. Operational instructions, current work
queues and validation results remain in their existing project records.
## K01: How does payment settlement differ from stablecoin issuance?

Captured: 26 November 2026, from the supplied role/specification comparison.

A payment platform uses an existing token to settle payments. A stablecoin issuer
also manages token creation, circulation, redemption and the relationship between
outstanding tokens and fiat reserves.

| Journey | Meaning |
|---|---|
| Payment settlement example | A business pays a supplier by transferring existing stablecoins from its treasury wallet, then records and reconciles the payment. |
| Issuer lifecycle example | An issuer accepts a fiat deposit, validates compliance, authorizes minting, delivers tokens to a customer, and later handles redemption, burning and fiat settlement. Fiat steps can be simulated in a POC if clearly labelled. |

Transferring prefunded treasury tokens does not demonstrate minting. An optional
simulated fiat payout does not demonstrate redemption and burning. A successful
blockchain receipt proves a chain operation; it is not the application's
double-entry accounting ledger or proof of fiat backing.

An easy way to remember the distinction: a payer moves existing token units;
an issuer also manages their creation and redemption. One platform may support
both roles, but demonstrating one role does not establish the other.

Sources: [demo SOP](37-DEMO-OPERATIONS-SOP.md),
[test-USDC decision](../adr/ADR-011-TEST-USDC.md),
[Besu decision](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[DEMOUSD source](../infrastructure/besu/contracts/BesuDemoUSD.sol).

### Does ERC-20 or a private chain automatically provide a regulated stablecoin lifecycle?

Learned: 27 November 2026.

No. ERC-20 defines a token interface: balances, transfers, delegated spending and events. A stablecoin issuer also needs explicit authority and workflows for creation/redemption, reserve records, compliance decisions, privileged controls and reconciliation. Burn and pause are additional components/policies, not mandatory ERC-20 operations. A deployment-time supply allocation creates initial units but does not implement an ongoing deposit-to-mint business process.

| Layer | Example responsibility |
|---|---|
| ERC-20 contract | Token balance and transfer rules; explicitly added mint/burn, pause or wallet controls. |
| Issuer application | Validate deposit/redemption references, screening and authority; coordinate replay-safe operations. |
| Reserve/accounting | Reconcile outstanding supply, reserve records and redemption obligations. Simulated reserves do not establish real backing. |
| Custody | Protect keys and enforce signing governance; encrypted files alone do not establish institutional custody. |

A permissioned blockchain controls participation; a token allowlist controls contract operations. These protect different layers. A wallet allowed to use a Besu node is not thereby KYC-approved for every issuer token. Conversely, an application rejection cannot stop a direct contract call unless the relevant restriction is also enforced at the appropriate contract/network boundary.

Sources checked 27 November 2026: [ERC-20](https://eips.ethereum.org/EIPS/eip-20), [OpenZeppelin ERC-20 extensions](https://docs.openzeppelin.com/contracts/5.x/api/token/erc20), [Besu permissioning](https://docs.besu-eth.org/private-networks/concepts/permissioning). Project example: [fixed-supply DEMOUSD](../infrastructure/besu/contracts/BesuDemoUSD.sol), distinct from a future issuer-lifecycle design.

## K02: What is EVM?

Captured: 26 November 2026.

EVM means **Ethereum Virtual Machine**. It executes smart-contract bytecode and
applies the contract's rules to blockchain state. Solidity contracts can be
compiled for an EVM-compatible environment, subject to that network's supported
features and configuration.

EVM is different from an EC2 virtual machine. EC2 supplies a computer on which
blockchain node software can run; the EVM is the contract execution environment
inside that software. EVM compatibility does not mean two networks share token
balances, transaction history or assets.

| Term | Meaning |
|---|---|
| Base Sepolia | A public EVM-compatible test network in the Base ecosystem. |
| Hyperledger Besu | Blockchain client software that can run a private EVM-compatible network. |
| Ethereum Sepolia | A separate public Ethereum test network. |
| Solidity | A programming language used to write smart contracts. |
| ERC-20 | A standard interface for fungible tokens. |

For example, a Java application sends a token-transfer request to a Besu node.
The node's EVM executes the token contract's rules, such as checking the sender's
balance and updating balances when the transaction succeeds.

Besu is software; Base Sepolia and Ethereum Sepolia are networks. The POC uses
Besu for an additional private network. Sharing EVM compatibility does not make
that private network part of either public testnet.

Sources: [AWS explanation of Ethereum/EVM](https://docs.aws.amazon.com/managed-blockchain/latest/ethereum-dev/managed-blockchain-ethereum-overview.html),
[Besu private networks](https://docs.besu-eth.org/private-networks),
[ADR-016](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md).

### What should be shared between settlement networks, and what belongs in an adapter?

Captured: 27 November 2026.

A payment has business rules that remain useful across networks: customer scope,
the approved amount and recipient, independent approvals, duplicate prevention,
balanced accounting and reconciliation. These are candidates for shared domain
components. A shared rule does not require identical database records or one
large implementation for every network.

Network adapters handle differences such as RPC calls, chain and token identity,
fee calculation, signing formats and receipt/finality interpretation. The shared
workflow must retain the selected network and asset throughout execution; it
cannot switch networks after signing or submission to recover from uncertainty.
Tokens with the same decimals or symbol on different networks are still separate
assets with separate balances and accounting evidence.

More detailed settlement safeguards do not imply a more complete stablecoin
issuer product. Issuance, fiat backing and redemption are additional business
capabilities; operating a private blockchain does not supply them automatically.
In this POC, prefunded token payment settlement and fixed constructor supply are
different from deposit-driven minting and redemption-driven burning.

Sources: [accepted network architecture](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[settlement binding model](../services/platform-flow/src/main/java/com/cognizant/stablecoins/flow/SettlementBinding.java),
[blockchain adapter contract](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/service/BlockchainAdapter.java),
[private demonstration token](../infrastructure/besu/contracts/BesuDemoUSD.sol).

## K03: Can EVM run on AWS?

Captured and AWS service notice checked: 26 November 2026.

Yes. Blockchain software such as Besu can run on AWS compute and execute EVM
contracts. AWS supplies the hosting infrastructure; Besu supplies the blockchain
node and its contract execution environment.

The following example uses the POC's intended hosting arrangement to explain
where the different parts sit:

| Component | Planned location | Purpose |
|---|---|---|
| Java/Spring business services | ECS Fargate | Payment orchestration, APIs, ledger and reconciliation. |
| Private Besu validators/RPC node | EC2 instances running containers | Private blockchain, consensus and EVM execution. |
| Solidity ERC-20 contract | Deployed on the private Besu network | Token balances and transfers. |
| Public Base Sepolia integration | Application connects through an RPC endpoint | Access to public-testnet settlement; the public network is separate from our AWS deployment. |

This table is an architecture example, rather than a claim of deployment.
The EVM is inside the node software; the Solidity contract executes there,
while the Java application communicates with the node through RPC calls.

AWS's published notice says Amazon Managed Blockchain stopped accepting new
customers on **29 October 2026** and ends support on **29 September 2027**.
AWS identifies self-managed nodes on EC2 and third-party RPC providers as
replacement options. This is a time-sensitive service fact; recheck before
making a deployment decision. Besu-on-EC2 does not depend on that managed service.
[AWS end-of-support notice](https://docs.aws.amazon.com/managed-blockchain/latest/hyperledger-fabric-dev/managed-blockchain-end-of-support.html)

## K04: How do ERC-20, ERC-721 and ERC-1155 differ?

Captured: 26 November 2026. ERC means **Ethereum Request for Comments**.
These standards define interfaces that contracts, wallets and applications use.
Fungible means interchangeable within the same token type.

| Aspect | ERC-20 | ERC-721 | ERC-1155 |
|---|---|---|---|
| Purpose | Interchangeable units | Individually identifiable assets, commonly called NFTs | Multiple token types in one contract |
| Token model | Fungible | Non-fungible | Fungible, non-fungible and semi-fungible designs |
| Analogy | Money: equivalent monetary units | A deed identifying one particular property | Inventory with quantities of different products and unique items |
| One contract manages | One token type | A collection of unique tokens | Multiple types, distinguished by token IDs |
| Holdings recorded | Quantity per wallet | Owner of each token ID | Quantity per wallet and token ID |
| Transfer example | 125.50 tokens, using the token's configured scale | Token #1001 | 50 units of type #10 |
| Standard batch transfer | No | No | Yes, multiple token IDs and quantities |
| Illustrative financial use | Stablecoins, payment tokens, interchangeable asset units | A token identifying a specific invoice or certificate | Multiple voucher classes or asset series |
| Relevance to this POC | Direct fit for the settlement token | Optional unique-asset representation | Optional multi-token business use case |

ERC-20 and ERC-721 contracts can be extended with batching; it is not a function
specified by their base standards. ERC-1155's batch operation transfers multiple
IDs/quantities from one address to one destination, rather than automatically
providing a payment to many recipients.

An illustrative B2B scenario could use ERC-20 tokens to pay an invoice and an
ERC-721 token to identify the invoice. ERC-1155 could manage several voucher or
asset classes. These examples describe possible designs, not implemented POC
features or proof of legal ownership of underlying assets.

All three standards are not required for a stablecoin lifecycle. ERC-20 fits the
token itself. Fiat backing, redemption, authorized mint/burn, KYC restrictions,
pause and freeze require additional design and implementation; selecting a token
standard does not supply those controls automatically. ERC-1155 can support
fungible types but uses a different interface, so ERC-20 integrations cannot be
assumed to work unchanged.

Sources: [ERC-20 specification](https://eips.ethereum.org/EIPS/eip-20),
[ERC-721 specification](https://eips.ethereum.org/EIPS/eip-721),
[ERC-1155 specification](https://eips.ethereum.org/EIPS/eip-1155).

## K05: Do application controls and blockchain controls do the same thing?

Captured: 26 November 2026.

They protect different layers. A rule enforced only by one application does not
automatically become a rule of the token contract or the blockchain network.

| Control | What it establishes | What it does not establish by itself |
|---|---|---|
| Application authentication/approval | Who may instruct or approve an application payment | That every transfer through the token contract is restricted |
| KYC/AML decision | Screening result used to gate a business workflow | Token-contract allowlisting or completed external screening when the decision is simulated |
| Besu node/account permissioning | Which participants/accounts may use the private network under its rules | A portable ERC-20 wallet freeze/blocklist or confidentiality from validators |
| Adapter recipient allowlist | Which destinations the application's adapter permits | Restrictions on every other route to the contract |
| Token pause/freeze logic | Contract-enforced restrictions designed for particular operations or holders | Fiat reserve backing or completed regulatory compliance |
| Encrypted wallet/signing boundary | Protection and controlled use of local signing material | Institutional custody, MPC or HSM integration |
| Compilation and library reuse | Valid source/build interface and use of an existing implementation | Executed contract security tests or a security audit |

For example, creating a fixed initial supply in a contract's constructor is an
initial mint. An issuer-controlled function that can create more tokens later
is a separate capability. Neither capability proves that fiat reserves exist.

### Does minting happen before simulated payment funding?

Follow-up learned: 27 November 2026.

In this private demonstration, deploying BesuDemoUSD creates a fixed supply of
1,000,000 DEMOUSD directly in the configured treasury wallet. Minting directly to
treasury does not require a second transfer from a separate issuer wallet. This
is a one-time chain setup action, not a per-payment activity or proof of fiat backing.

The payment workflow uses those existing treasury tokens. A reservation earmarks
capacity; simulated USD funding records a customer debit and conversion/allocation
in application accounting; the subsequent signed ERC-20 transfer moves tokens to
the beneficiary. Accounting allocation does not increase on-chain total supply.
A different issuer workflow would validate a deposit and compliance before an
authorized mint, then supply customers or treasury. That is a separate lifecycle
design, rather than something implied by calling a payment FUNDED.

Project sources: [constructor mint directly to treasury](../infrastructure/besu/contracts/BesuDemoUSD.sol),
[payment funding boundary](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/settlement/SettlementFundingService.java),
[accepted private token design](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md).


Sources: [security design](14-SECURITY.md),
[Besu runtime boundary](../infrastructure/besu/RUNTIME.md),
[ADR-016](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md).

## K06: How do ECS Fargate, Kubernetes and EKS relate?

Captured: 26 November 2026.

ECS and Kubernetes are container orchestration approaches: they help deploy and
run containerized applications. Amazon EKS is AWS's managed Kubernetes offering.
Fargate is a compute option for running containers without managing the underlying
servers. The phrase ECS Fargate means using ECS orchestration with Fargate compute.

For example, the POC's Java services can run as ECS tasks on Fargate. A Besu node
can run as a container on an EC2 instance. Packaging both as containers does not
require that they use Kubernetes or the same hosting arrangement.

Sources: [Fargate decision](../adr/ADR-006-FARGATE.md),
[AWS ECS launch types](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/launch_types.html),
[Amazon EKS](https://docs.aws.amazon.com/eks/latest/userguide/what-is-eks.html).

## K07: What are PostgreSQL migration, rollback and concurrency tests? Why migrate?

Captured: 26 November 2026.

A database schema is the structure of tables, columns and rules used to store
application records. A schema migration is a versioned change to that structure,
for example adding a table for a new feature. It need not move the database to
another server or transfer existing records. Migration tools track applied
versions so an upgrade applies the changes that are still missing.

Short project example: the Besu execution-claim migration adds a table linking a
payment to its exact approved review, quote and preparation. It also adds rules
that reject inconsistent references and duplicate claims, and a trigger rejecting
updates/deletes of captured claims. This record is application workflow evidence;
it is not a blockchain transaction or a ledger posting.

| Check | Question it answers | Example |
|---|---|---|
| Migration | Can the versioned schema changes be applied and validated? | Create the new claim table and verify its references and constraints. |
| Transaction rollback | Does an incomplete database operation leave no partial committed result? | If writing the audit entry fails, the claim inserted in that same transaction must also disappear. |
| Concurrency | Do overlapping requests preserve uniqueness and consistency? | Two workers requesting the same claim leave one claim and one audit entry; conflicting execution IDs cannot both win. |

Transaction rollback here means cancelling uncommitted database writes. It does
not mean undoing a completed blockchain transfer or automatically reverting a
schema migration. A committed financial entry requires the application's defined
compensating/reversal process; a submitted blockchain transaction requires its
own receipt and recovery handling.

Testcontainers can create a separate disposable PostgreSQL instance for these
checks using fictional records. That test instance is separate from the running
application database. Applying migrations to the application database is a
separate upgrade operation; tests do not establish that an existing populated
database has already been upgraded or that every production upgrade case passed.

Sources: [execution-claim migration](../services/payment-service/src/main/resources/db/migration/V16__settlement_execution_claim.sql),
[claim persistence tests](../services/payment-service/src/test/java/com/cognizant/stablecoins/payment/settlement/SettlementExecutionClaimPersistenceIT.java),
[local test strategy](../adr/ADR-010-LOCAL-DEVELOPMENT.md).
### Why must missing or skipped integration tests block acceptance?

Captured: 27 November 2026.

A unit test can verify a rule using substitutes for storage. A PostgreSQL integration
test checks whether real database behavior also preserves that rule: locks prevent
competing payments from overspending, constraints reject invalid records and a failed
transaction rolls back its related writes together. These are different kinds of evidence.

A passing test command does not prove every intended test ran. Build configuration can
exclude a suite, Docker-dependent tests can be skipped, and an old successful report can
remain on disk. Acceptance therefore checks fresh reports from the correct test phase
and service, requires actual execution without failures/skips and checks that required
suites are present. Database acceptance still does not establish network consensus,
wallet signing, real token delivery or an authenticated end-to-end customer journey.

Sources: [verification tooling](../scripts/local-workbench.cjs),
[database evidence regression tests](../scripts/database-evidence.test.cjs),
[network acceptance boundaries](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md).

## Maintaining these notes

For future learning questions, retain the question as a heading and explain the
answer in plain language. Include an example or comparison table when it helps,
the date learned and supporting sources. Capture useful conceptual follow-ups,
including corrections to earlier misunderstandings. Expand existing topics
rather than repeating them. Preserve the learner's question wording when useful.

Capture learning questions such as "what is", "how does it work", "why" and
"how do these differ", including conceptual clarification embedded in another
discussion. Exclude project status, coverage scores, task lists, routine
acknowledgements, tool output, credentials, private handover contents and sensitive
personal information. Keep project-specific examples short and clearly label
them as examples. Current tasks and decisions remain in their authoritative
project documents. These notes explain concepts; they do not change architecture.


## K08: When can a blockchain receipt release a payment reservation?

Captured: 26 November 2026.

A reservation prevents two payments from promising the same treasury funds while
a transfer is pending. A transaction hash or a timeout alone does not show whether
the transfer succeeded. The application needs a receipt tied to the exact signed
transaction, correct network/token and expected transfer event, plus that chain's
finality policy. Uncertain evidence keeps the funds reserved for investigation.

A reverted transaction did not complete its token transfer, but it may still have
consumed gas. Token delivery and network-fee accounting therefore have different
outcomes. A receipt is evidence for accounting; it is not the accounting ledger.

After resolving a reservation, balances must be observed at or after the receipt's
block. Otherwise an older balance can make already-spent funds appear available
again. For example, a treasury balance of ten tokens read before a one-token payment
cannot be reused as the post-payment balance merely because the hold was removed.

Follow-up captured: 26 November 2026. These safeguards also explain recovery
and database consistency:

| Concept | Plain-language meaning | Why it matters |
|---|---|---|
| Approved-transfer binding | Check that the receipt describes the exact authorized network, token, sender, recipient and amount. | A successful receipt for another transfer must not complete this payment. |
| Durable transaction identity | Save the exact signed transaction, nonce and expected hash before attempting submission. | A restarted worker can investigate the same transaction instead of creating another payment. |
| Idempotent recovery | Repeated processing preserves the original operation and result. | A retry or restart must not cause a second transfer or duplicate financial posting. |
| Conclusive outcome | Use verified success or revert evidence under the network's finality rules. | A timeout or missing receipt leaves the outcome uncertain and funds reserved. |
| Balance freshness | Read balances from a chain state that includes the resolved transaction's block. | Removing a reservation must not expose an older, higher balance as spendable funds. |
| Atomic database commit | Store the verified receipt, terminal status and audit record together, or store none of them. | A crash or audit-write failure must not leave a payment marked resolved without its supporting evidence. |

Saving a signed transaction does not prove it was broadcast or confirmed. Its
hash identifies the transaction to investigate. In this POC's conservative recovery
policy, an uncertain submission is checked using the original hash; it is not
automatically replaced or sent through another network. Signed transaction bytes
can authorize broadcast and must be protected even though they are not a private key.

Database atomicity covers the application's local records. It cannot undo a
transaction already committed on the blockchain. If the chain transaction completes
while the application is unavailable, recovery must retrieve its evidence and
complete the missing application records safely.

QBFT has immediate finality under its validator and trust assumptions. A canonical
committed receipt can therefore use a different finality policy from a public
proof-of-stake network. Trusting a configured node's receipt is different from
independently verifying all consensus signatures.

Sources: [Besu proof-of-authority consensus](https://github.com/besu-eth/besu-docs/blob/main/docs/private-networks/concepts/poa.md),
[accepted Besu decision](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[per-asset accounting decision](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md),
[receipt owner](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuReceiptService.java),
[durable submission owner](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuSubmissionService.java),
[atomic receipt constraints](../services/blockchain-service/src/main/resources/db/migration/V11__besu_final_receipt.sql).


## K09: How do token transfers, gas fees and application accounting fit together?

Captured: 26 November 2026.

A token transfer and its gas payment use different assets. Sending nine DEMOUSD
on a private Besu network changes token balances; executing that transaction may
also spend the network's native BESU_GAS. Each asset needs its own balanced
accounting entries. A debit in DEMOUSD cannot balance a credit in BESU_GAS or USD.
Token symbols alone also do not identify an asset: the network, genesis and token
contract distinguish one token deployment from another, including after a chain reset.

The POC uses a CONTROL subledger to record the payment lifecycle: allocation,
in transit and delivered. These are application accounting categories. Recording
an allocation does not mint tokens, purchase stablecoins or prove fiat reserves.
Its simulated USD leg must remain labelled simulated even when the later private
blockchain transfer actually executes.

A provider's quoted USD fee and the blockchain's actual native-asset fee are
separate observations. For the accepted plain Besu execution-only model, actual
network cost comes from verified receipt gas used multiplied by effective gas
price. A successful zero-price transaction has zero actual network cost; the
application can retain receipt evidence without inventing an expense. A reverted
transaction may consume gas while delivering no tokens, so gas expense and token
delivery must be assessed separately. Unresolved accounting needs investigation
or an explicit compensating entry; committed financial records are not edited away.

A receipt acknowledgement records that accounting consumed a particular verified
receipt, including a zero-fee outcome. Saving that acknowledgement and its related
journals in one database transaction helps retries recover safely: either all are
committed, or none are. It does not roll back an already completed chain operation.

Follow-up learned: 27 November 2026. A blockchain submission and an accounting
posting are separate durable operations. An attempted transfer with an uncertain
outcome can remain in the in-flight control account; it is not evidence that the
beneficiary received tokens. When a verified terminal receipt arrives, accounting
can record delivery and actual gas, or gas alone for a revert. A background worker
can recover after a crash by rediscovering the original execution and repeating
idempotent owner commands. Even if it first sees an already-mined transfer, it
records the submission leg before consuming the receipt. Posted journals and the
receipt acknowledgement, rather than a worker's memory, prevent duplicate effects.
This control-book recovery does not itself authorize payment-owner reconciliation.
Project source: [ledger evidence continuation](../services/settlement-ledger-service/src/main/java/com/cognizant/stablecoins/ledger/besu/BesuAccountingWorker.java).

### Does compensation after a reverted transfer mean a customer refund?

Follow-up learned: 27 November 2026.

A conclusive revert means the intended token transfer did not complete. It can
still have consumed gas. The application's earlier in-flight entry records an
attempt, so a separately authorized compensating entry can move that token
CONTROL quantity back from in transit to treasury. The original journal remains
posted and the new journal links to it. Actual gas expense remains recorded.
This changes the control ledger; it does not send tokens or undo a blockchain
transaction.

A refund is a separate business operation. Releasing a DEMOUSD in-flight control
position does not credit a customer's simulated USD account, reverse its funding
and conversion records, or prove that fiat was returned. Those outcomes need their
own authorized policy and evidence. In this POC the failed payment remains FAILED
even after its token control position is released; it must not appear as successful
supplier delivery or a reconciled successful settlement.

Recovery authorization should identify the exact failed execution and the evidence
being accepted. A source fingerprint binds that authorization to the receipt and
original accounting material. An independently authorized customer approver grants
the action; a background worker can then consume the durable grant after a restart.
The worker does not grant itself approval. A timeout or missing receipt is not a
conclusive revert and must not trigger this release.

Project sources: [failed-payment owner and authorization](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/flow/BesuPaymentFailureService.java),
[append-only control release](../services/settlement-ledger-service/src/main/java/com/cognizant/stablecoins/ledger/besu/BesuCompensationService.java).

Sources: [control-book accounting policy](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md),
[private network and asset policy](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[private accounting command](../services/settlement-ledger-service/src/main/java/com/cognizant/stablecoins/ledger/besu/BesuAccountingService.java).


### Why can balanced journals still fail reconciliation?

Learned: 26 November 2026.

Balanced entries prove that debits equal credits within an asset. They do not prove
that the right customer, payment, asset, recipient or quantity was used. For example,
a debit and credit of ten DEMOUSD balance perfectly even if the approved transfer
was nine. Reconciliation therefore compares accounting to the approved instruction
and independent transaction evidence, including identifiers and exact quantities.
A correctly recorded gas expense also does not prove token delivery: a reverted
transaction can consume gas without transferring tokens.

Project example: a private-chain comparison can match its receipt and accounting
while the payment owner still needs to validate its funding and complete the
workflow. A component's successful check and completion of the whole payment are
different claims. Recording the scope of each check keeps that distinction clear.

Follow-up learned: 27 November 2026. In the private transfer workflow, the
payment service checks the exact committed simulated funding and allocation,
then independently compares the current receipt and journals with the saved
reconciliation report. A report identifies the evidence it compared with a
canonical fingerprint: object key order and equivalent decimal formatting do not
create different evidence, while a changed journal or receipt does. Reusing an old
report after the sources change cannot prove the new sources match. The report
service owns the comparison; the payment service owns the final payment state.
The final status, business audit, outbox notifications and immutable closure
acknowledgement belong to one payment-owner database transaction. This avoids
recording only part of the completion after a crash. Simulated funding remains
simulated even when the private-chain transfer and accounting are real.

Sources: [reconciliation ownership](../adr/ADR-008-RECONCILIATION-DIRECT-READ.md),
[control-book accounting](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md),
[private settlement comparison](../services/platform-flow/src/main/java/com/cognizant/stablecoins/flow/BesuClosureEvidence.java),
[payment-owner closure](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/flow/BesuPaymentClosureService.java).


### Why are treasury reservations, simulated USD debits and blockchain transfers separate steps?

Learned: 26 November 2026.

A treasury reservation sets aside existing token and gas capacity for one approved
payment. It prevents other payments from promising the same capacity. A reservation
does not send tokens, create money or prove that the customer's funding was accepted.

A simulated USD debit reduces the customer's demonstration account balance. It is
an application record representing funding; no bank deposit, actual foreign exchange
or token mint takes place. Accounting separately records the funding and allocation
using balanced journals. The blockchain transfer then uses a signature to move
existing tokens from treasury to the beneficiary. Its receipt supplies evidence of
success or failure and actual gas consumed. Each step answers a different question.

Project example: an approved five DEMOUSD payment can reserve five existing tokens,
reduce a simulated USD account from twenty to fifteen, and later transfer five
DEMOUSD on the private network. The debit itself cannot increase the treasury's
on-chain balance. This example is a demonstration convention, not proof of fiat
reserves backing a regulated stablecoin.

### How do retries avoid charging the same payment twice?

Learned: 26 November 2026.

A stable execution identifier connects one approved instruction to its results.
Before performing a debit, the payment owner checks whether that execution already
has a committed funding acknowledgement. A retry returns that original result
instead of debiting again. Concurrent requests must also serialize their balance
updates, so an application-level check alone is insufficient.

The debit, its acknowledgement and an outgoing event can commit together in one
database transaction. The outgoing event is first saved in an outbox; a separate
publisher delivers it. This prevents a crash from leaving a committed debit with no
recorded instruction for the next service. Event delivery can still repeat, so each
receiving service must independently recognize duplicate commands. Database atomicity
does not undo a blockchain transfer that has already been broadcast.

An old acknowledgement is evidence of what happened, not fresh permission for a new
transfer. Before new financial work, the system must verify the approved terms,
current permissions, compliance state and sufficiently recent treasury evidence.

Sources for these project examples: [logical ownership](../adr/ADR-007-SHARED-SCHEMA-LOGICAL-OWNERSHIP.md),
[private network policy](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[payment funding boundary](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/settlement/SettlementFundingService.java),
[financial accounting policy](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md).


### Why must funding and accounting be acknowledged before signing a token transfer?

Learned: 26 November 2026.

Funding answers whether the customer's payment has been accepted and charged.
The ledger answers how that funding and the token allocation are recorded in
balanced accounts. A signature authorizes a blockchain operation that may be
broadcast later. Checking a wallet balance alone cannot establish either funding
or accounting authority: a treasury can hold tokens intended for other customers.

An outgoing event is a delivery instruction, not sufficient proof by itself.
The receiving service can check the payment owner's committed funding record and
outbox before posting its own journals. Committed, immutable journals then provide
durable accounting acknowledgement. All required legs must match the same approved
execution, amounts, currencies and references. An incomplete or reversed set cannot
authorize new signing. Simulated funding remains labelled simulated even when the
later token transfer actually happens on a blockchain.

Project example: for five DEMOUSD, simulated USD funding and conversion are recorded
in USD accounts, while treasury allocation is recorded separately in DEMOUSD.
These records authorize use of existing treasury tokens; they do not mint tokens
or prove that real fiat reserves were deposited.

### Why is recovery different from starting another transfer?

Learned: 26 November 2026.

Before signing or making a first broadcast attempt, current funding, accounting,
approval expiry, recipient and treasury evidence must satisfy the execution rules.
After an attempt may have reached the network, the saved transaction hash identifies
the operation being investigated. A later expired quote does not prove that operation
failed. Recovery observes that same transaction and its receipt; creating another
signature or releasing its reserved funds could cause duplicate payment or overspending.
A saved but never attempted signature still needs the first-attempt checks before
broadcast. Database rollback cannot retract a broadcast blockchain transaction.

Sources: [logical ownership](../adr/ADR-007-SHARED-SCHEMA-LOGICAL-OWNERSHIP.md),
[accounting policy](../adr/ADR-015-PER-ASSET-POC-ACCOUNTING.md),
[private network policy](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md),
[committed funding and journal checks](../services/platform-flow/src/main/java/com/cognizant/stablecoins/flow/SettlementFundingReads.java),
[transaction submission and recovery](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuSubmissionService.java).


Follow-up learned: 27 November 2026.

A background worker must distinguish **SIGNED** (bytes were saved but the app has
not committed a broadcast attempt) from **ATTEMPTED/UNKNOWN/SUBMITTED** (a network
outcome may already exist). A pause on new submissions should prevent the first
group from being broadcast while allowing the second group to be monitored. This
is why submission permission and recovery monitoring need separate controls.
Recovery should discover saved transactions independently of today's payment or
quote status: a transaction may have completed while the application was stopped.

A scheduler's memory is not the payment's durable state. On restart it can scan
again because the database retains the execution, signed transaction, attempt and
receipt identities. Database uniqueness and locks protect concurrent workers;
a single scheduler thread alone cannot protect multiple application instances.
Bounded pages and a moving cursor let later executions be examined even when an
earlier one needs investigation. These scheduling details never replace the
funding, authority and receipt checks performed by the owning services.

Sources: [private execution worker](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuExecutionWorker.java),
[submission and observation boundary](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuSubmissionService.java),
[receipt owner](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuReceiptService.java).


### How do an outbox, a message queue and a dead-letter queue protect a payment workflow?

Learned: 27 November 2026.

An **outbox** stores the instruction to notify another service in the same database
transaction as the business change. If that transaction rolls back, neither the
change nor its notification is committed. A publisher later sends the saved event
to the **message queue**, which lets the receiving service process it independently.
A successful send means transport accepted the message; it does not mean the
receiver has finished its accounting work.

Delivery can repeat, for example if a receiver commits its journals but loses the
acknowledgement. **Idempotency** makes that repeat refer to the same operation and
journals instead of debiting or paying again. The receiver should acknowledge only
after its required database transaction commits. Processing failures cause retries;
a **dead-letter queue (DLQ)** holds messages that exceed the configured receive
limit for investigation. A DLQ does not undo a debit or prove a blockchain transfer
failed, and replaying one must preserve the original operation identity.

Project example: private DEMOUSD funding has a separate message route from public
USDC funding. A ledger receiver checks the incoming event against the committed
funding record before allocating tokens in the application ledger. Accounting
allocation is separate from the actual on-chain token transfer.

Sources: [transactional messaging ADR](../adr/ADR-004-EVENTBRIDGE-SQS.md),
[outbox dispatcher](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/outbox/OutboxDispatcher.java),
[private funding handler](../services/settlement-ledger-service/src/main/java/com/cognizant/stablecoins/ledger/besu/BesuFundingHandler.java),
[transport binding](../infrastructure/besu/SETTLEMENT-BINDINGS.md).


### What is a sandboxed build, and is it related to a blockchain testnet?

Learned: 27 November 2026.

A build compiles source code and runs the configured checks, such as Maven's Java
unit tests. A sandbox is a restricted execution environment around that process.
It can limit filesystem writes, network connections and other access to the host.
For example, a build may need to write generated classes under `target` or access
a local dependency cache. Whether either action is allowed depends on the actual
sandbox policy. A generic compiler failure alone does not identify a permissions
problem; compare diagnostics before attributing the cause.

Running the same build outside the sandbox gives the process the normal access
allowed by the local account and approved command. This changes its execution
permissions, not the application source. The configured build and test actions
still matter: a normal unit-test command does not itself mean an application was
deployed or a blockchain transfer submitted. External integration tests can have
additional prerequisites and effects, which must be assessed separately.

A blockchain testnet is different: it is a blockchain network for test
transactions. A build sandbox restricts a local process. A private Besu network
is also a blockchain deployment, not the build sandbox. Neither a successful
build nor unit tests alone establish live-chain end-to-end acceptance.

Sources: session execution-policy explanation, 27 November 2026;
[project Maven configuration](../services/pom.xml),
[Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html).


### Are backend workers the same as AI agents?

Learned: 27 November 2026.

A **worker** here is an ordinary scheduled Java/Spring backend job. When its explicit feature
switches are enabled, Spring calls it periodically. It reads durable outstanding records and
asks the responsible service to perform its narrowly defined next action. It does not use a
language model or independently invent a payment decision.

| Component | Example responsibility | Authority boundary |
|---|---|---|
| Receipt worker | Poll a saved transaction hash and verify its canonical receipt | Cannot choose another chain or sign a replacement transfer |
| Accounting worker | Continue posting from verified funding/submission/receipt evidence | Must use balanced immutable journals and exact source provenance |
| Closure worker | Advance settlement after required owner acknowledgements | Cannot infer success from a hash alone |
| Recovery worker | Continue an already-authorized CONTROL release after a conclusive revert | Cannot grant its own approval or refund customer USD |
| Routing AI agent | Explain permitted routing evidence through bounded read-only tools | Cannot approve, sign, submit or post accounting entries |

**Project example:** after a transaction is submitted, a scheduled receipt worker can keep
checking its status while the browser is closed. A restart does not itself authorize another
payment: the job discovers saved execution/signature/hash records and applies idempotency and
owner checks. An unknown outcome remains unresolved until sufficient evidence exists. Automatic
scheduling is automation of a fixed workflow, not financial discretion or AI authorization.

Sources: [Besu execution worker](../services/blockchain-service/src/main/java/com/cognizant/stablecoins/blockchain/besu/BesuExecutionWorker.java),
[accounting worker](../services/settlement-ledger-service/src/main/java/com/cognizant/stablecoins/ledger/besu/BesuAccountingWorker.java),
[payment failure owner](../services/payment-service/src/main/java/com/cognizant/stablecoins/payment/flow/BesuPaymentFailureService.java),
[ADR-016](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md).

### Why do Base Sepolia and private Besu need different fees, tokens and evidence?

Learned: 27 November 2026.

Both support EVM contracts, but each network has its own state, token deployments and consensus. Moving the application between RPC URLs does not move token balances. In this POC, public test USDC/test ETH and private DEMOUSD/BESU_GAS are separate assets. A public faucet funds public test balances; a private development chain uses its explicit genesis and deployed token supply. Neither action establishes real fiat backing.

The Base path accounts for execution and applicable L1 data fees; the private Besu configuration uses execution-only fees. A USD provider quote fee is a separate business-pricing observation. Confirmation policy and evidence must follow the selected network: a private QBFT receipt is meaningful private-chain evidence, but cannot be looked up on BaseScan or described as a public Base transaction. Payment identity must retain the selected chain/genesis/contract across retries.

Sources: [accepted network/asset decision](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md), [private receipt/accounting bindings](../infrastructure/besu/SETTLEMENT-BINDINGS.md), [public receipt verifier](../scripts/demo/verify-receipt.cjs). These describe the POC design and do not claim any new runtime demonstration.


## K10: How do I preserve the application if a Codespace is deleted?

Date learned: 27 November 2026.

A Codespace is a replaceable development computer. A remote Git repository preserves committed changes that have been pushed to it; deleting the Codespace does not delete that repository. Uncommitted edits and commits that exist only inside the Codespace are not protected by the remote copy. GitHub currently documents automatic deletion of inactive Codespaces after a retention period (30 days by default), including Codespaces with unpushed changes. Organization policies can impose a shorter period.

Source control and application recovery protect different things:

| Asset | What a recovery copy must preserve |
| --- | --- |
| Source, documentation, migrations and deployment definitions | Git history, branches and tags in the primary remote plus an independent protected backup; include Git LFS objects if used. |
| Encrypted wallet keystores | Protected encrypted backups of all intentionally retained wallet generations. Passwords need separately secured recovery; a wallet address cannot recover its signing key. |
| Environment settings and credentials | Approved secret storage and a recoverable configuration inventory. Never commit passwords, private keys or secret environment files, even to a private repository. |
| PostgreSQL financial records | Consistent database backups containing payments, approvals, ledger, audit and transaction recovery records. Recreating tables from migrations does not restore business data. |
| Private Besu network | Genesis/configuration, validator identities and keys, deployment/token identity and consistent chain state. Reinitializing a new network is different from recovering the existing network. |

A practical design keeps the primary repository, a local clone and a versioned encrypted backup in an independent approved location. A second folder on the same disk is not independent protection. Synchronization alone can propagate accidental deletion; recoverable historical versions matter. A full Git mirror preserves repository history, whereas a downloaded source archive does not provide the same recovery coverage.

Restoration must be rehearsed. For a financial application, restoring code alone does not restore balances or pending transaction knowledge. Database and chain recovery must be coordinated, and pending transaction evidence reconciled before payment workers resume. Avoid copying a running database or chain directory as though it were an ordinary document folder.

Permanent hosting is separate from source backup. In this project's accepted direction, application hosting belongs on AWS ECS Fargate with PostgreSQL on Aurora, and private Besu nodes are planned on EC2. Codespaces supports development and demonstrations; hosting still needs its own persistence, backups and recovery design. This explanation does not assert that those backups or deployments have already been configured or tested.

Sources: [GitHub Codespaces retention](https://docs.github.com/en/codespaces/setting-your-user-preferences/configuring-automatic-deletion-of-your-codespaces), [Codespace lifecycle](https://docs.github.com/en/codespaces/about-codespaces/understanding-the-codespace-lifecycle), [Backing up a repository](https://docs.github.com/en/repositories/archiving-a-github-repository/backing-up-a-repository), repository [AGENTS.md](../AGENTS.md) sections 8-10 and 19, and backlog AWS deployment scope A01-A03 in [40-IMPLEMENTATION-BACKLOG.md](40-IMPLEMENTATION-BACKLOG.md). Asset separation and coordinated recovery are engineering recommendations applied to this POC.


### How do S3 and an encrypted backup tool preserve a whole application?

Follow-up learned: 27 November 2026. S3 is durable object storage; a backup tool such as Restic organizes encrypted, recoverable snapshots in it. Client-side encryption protects file contents before upload. S3 versioning adds protection against accidental overwrite/deletion; neither replaces an independently recoverable encryption password or a restore drill. A backup stored in a Codespace that disappears with that Codespace is not an independent recovery copy.

One application's state may live on several machines. A Windows folder backup cannot capture a PostgreSQL Docker volume or private-chain state that exists only in Codespaces. Recoverability requires an inventory and a coordinated checkpoint across the database, blockchain and queued events. A database dump can be internally consistent while being out of step with another system. Public-chain transactions continue independently; recovery must query their current outcome before allowing retries.

A practical PoC solution uses a protected S3 destination, encrypted snapshots from each source environment, separate secret/password recovery, retained historical versions and a tested restoration into an isolated environment. Scheduling, retention and recovery-time targets are operating decisions; storage alone does not establish recoverability. Sources: [Restic S3 repository documentation](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html), [S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html), [PostgreSQL consistent exports](https://www.postgresql.org/docs/16/app-pgdump.html), [Besu backup/restore](https://docs.besu-eth.org/private-networks/how-to/backup).


### Does a successful AWS login mean backups are protected permanently?

Follow-up learned: 27 November 2026. Authentication establishes which AWS identity is making requests; authorization determines which operations that identity can perform. A successful STS identity response does not prove permission to create or read a backup bucket. An assumed-role session uses temporary credentials: after expiry, a new authorized session is needed. An unattended backup therefore needs an approved credential-renewal mechanism and failure/age alerts; a one-time interactive login is insufficient.

Long-term recovery also depends on continued access to the account and the independent encryption password. Organization-managed account access can change with role assignments or retention policy. Agree recovery ownership and access with the account administrator, and test that an authorized recovery operator can restore using independently secured credentials.

Storage controls have different jobs: public-access blocking prevents public exposure; TLS protects transport; server-side encryption protects stored objects; client-side encryption protects contents before upload; versioning retains older object versions; infrastructure retention keeps resources when their deployment stack is removed. None alone proves that a complete, readable backup exists. For a deduplicating backup repository, indiscriminate age-based deletion can remove data still referenced by retained snapshots.

Sources: [AWS temporary security credentials](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html), [STS identity API](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetCallerIdentity.html), [CDK S3 bucket controls](https://docs.aws.amazon.com/cdk/api/v2/docs/aws-cdk-lib.aws_s3.Bucket.html), [Restic repository preparation](https://restic.readthedocs.io/en/stable/030_preparing_a_new_repo.html).


## K12: Why can Java tests report a missing class when its source exists?

Date learned: 27 November 2026.

Java source files are inputs to compilation. Maven normally writes application bytecode to target/classes and test bytecode to target/test-classes; it also copies application configuration resources into the build output. The test runner uses a runtime classpath to locate these outputs and dependencies. A source file existing in Git does not prove its compiled class is available to that runner.

NoClassDefFoundError can have several causes, including a missing runtime class or a previous failure to initialize a class. Inspect its cause rather than treating every instance as missing source. A ClassNotFoundException cause specifically indicates that the relevant classloader could not locate the requested class. Files found after a test failure establish their presence at inspection time, not necessarily their earlier presence or inclusion on the failing classloader's path.

A clean rebuild regenerates Maven build outputs and is useful for investigating inconsistent generated files. It does not by itself prove why the earlier run failed. Under this project's current Maven configuration, clean removes generated build directories; it is separate from database migration, deleting Docker volumes or reinitializing wallets and blockchain state. Passing focused tests establishes only their scope, not complete payment or chain acceptance.

Sources: [Maven standard directory layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html), [Surefire test classpath](https://maven.apache.org/surefire/maven-surefire-plugin/examples/configuring-classpath.html), [Maven Clean directories](https://maven.apache.org/plugins/maven-clean-plugin/clean-mojo.html), and [project Maven configuration](../services/pom.xml).

### Why can test database cleanup fail after adding a foreign key?

Follow-up learned: 27 November 2026.

A foreign key preserves a relationship between records. For example, a funding record can reference the exact outbox event that authorized it. PostgreSQL normally refuses to truncate a referenced table unless the same statement includes its referencing tables; emptying those tables in an earlier statement does not remove the relationship. In a disposable test database, deliberate cascading cleanup can reset that related fixture together. It is destructive test preparation, not a recovery operation for application financial records. Production corrections still require authorized compensating entries and preserved audit evidence.

Also distinguish the initiating SQL error from a later cleanup error. If an SQL operation fails and its connection is then closed, rollback can fail too. The final exception may therefore describe rollback rather than the first fault; inspect preceding structured logs and nested causes before changing connection settings. A failure in a test's setup method means its business assertions have not yet been exercised.

Project example: a Besu funding record links to its durable outbox event. That relationship requires the older public-flow test fixture to reset related test data consistently; it does not require weakening the production foreign key. Sources: [PostgreSQL TRUNCATE semantics](https://www.postgresql.org/docs/16/sql-truncate.html), [funding schema](../services/payment-service/src/main/resources/db/migration/V18__besu_simulated_funding.sql), [isolated persistence fixture](../services/payment-service/src/test/java/com/cognizant/stablecoins/payment/flow/PaymentFlowPersistenceIT.java).

## K11: Why can a test work from one folder and fail from another?

Date learned: 27 November 2026.

The current working directory belongs to the command that started a program; it is not necessarily the folder containing its source file. A test launched inside frontend and the same test launched by a repository-level verification tool can therefore resolve the same relative path differently. Resources tied to a module should be located relative to that module (for example, import.meta.url), while intentionally user-selected input may be relative to the working directory. This distinction makes automation portable across local terminals and CI/Codespaces.

Tests that create servers, watchers or child processes also need a defined setup and cleanup lifecycle. An exception during top-level module loading can leave resources open before the test runner can report an ordinary test failure. Setup hooks keep failures inside the test lifecycle; teardown hooks close resources that were successfully created, including when later setup fails. A periodic heartbeat proves only that a supervising process is alive, not that a test is advancing. A timeout is a failure boundary, not evidence that assertions passed.

Project example: a Vite rendering fixture locates frontend from its own test file and closes its test-owned server after execution. This affects test infrastructure, not blockchain confirmation or payment settlement. Sources: [settlement rendering tests](../frontend/tests/settlement.test.mjs) and [verification runner](../scripts/local-workbench.cjs).
