# Base Sepolia, private Besu and issuer-lifecycle scope

Reviewed: 27 November 2026. This is a dated comparison, not a second backlog.
Current status and closure criteria remain only in [the central backlog](40-IMPLEMENTATION-BACKLOG.md).
Five payment integration increments being code-integrated does not mean the issuer lifecycle is complete or that private runtime acceptance passed.

## Counting convention

- Eight comparison dimensions below describe differences in the current application paths, not eight missing features or inherent limitations of Besu.
- Sixteen functional capability groups below compare the broader supplied intent to the present implementation: nine new core issuer groups, six groups requiring extensions/acceptance, and one deferred institutional integration group. Each row counts once even when several backlog IDs apply.
- Three delivery/assurance groups are counted separately: contract testing/security, issuer deployment/lifecycle demonstration, and documentation/evidence. These are essential deliverables, not additional token operations.
- I01 is a design/ADR prerequisite, not another user-facing function. ERC-721/ERC-1155 and Quorum remain separate scope decisions P02/P03, not requirements implied by ERC-20.

## Eight differences between the current paths

| # | Dimension | Base Sepolia path | Private Besu path |
|---|---|---|---|
| 1 | Network participation/operation | Public Base test network accessed through RPC; application does not operate its validators. | Separate permissioned local QBFT network with four validators and a dedicated RPC node; application operator manages it. |
| 2 | Settlement asset/supply | Existing Circle test USDC; application is a token user and has no issuer authority. | Separate fixed-supply DEMOUSD contract; constructor allocates supply to treasury. No later mint/burn workflow. |
| 3 | Treasury replenishment | Test USDC faucet and test ETH faucet; these do not create fiat reserves. | Dedicated genesis development gas allocation and explicit DEMOUSD deployment supply; no public faucet required. |
| 4 | Network fee model | Base execution fees plus applicable L1 data fee evidence; paid in test ETH. | Execution-only private fee model, accounted as BESU_GAS. Simulated USD quote fees are separate. |
| 5 | Confirmation policy | Configured public-chain receipt/confirmation policy. A sequencer receipt is not automatically proof of ultimate Ethereum finality. | Canonical private receipt and accepted QBFT finality policy. No public-chain confirmation claim. |
| 6 | Durable identity | Public network/chain and official configured token identity. | Explicit private network/chain/genesis, deployed token and bytecode identity; a reset must not reuse historical payment identity. |
| 7 | Customer outcome/payout scope | USDC_WALLET settlement and existing optional simulated fiat/Visa flow, according to outcome. | Stage A TOKEN_WALLET delivery using DEMOUSD. No automatic Visa/fiat payout or cross-asset conversion is inferred. |
| 8 | Evidence presentation | Public BaseScan links and public receipt verification. | PRIVATE_DEMO labels, private receipt/validator identity and separate DEMOUSD/BESU_GAS journals; no BaseScan link for private transactions. |

Shared requirements include customer isolation, compliance decisions (labelled simulation), two independent approvals, exact quote binding, fresh token/gas capacity, immutable financial posting, idempotency, signed-transaction recovery and reconciliation. Runtime implementations differ where chain policy requires it.

Acceptance is a separate distinction: an earlier public payment was observed end to end; the current private path remains default-off and hard-gated pending PostgreSQL, authenticated UI/API, private-chain and public regression checks. Neither historical evidence nor local unit totals validate this revision end to end.

## Sixteen functional groups against the supplied issuer intent

| # | Functional capability | Required change | Current foundation / authoritative backlog |
|---|---|---|---|
| 1 | Controlled ongoing mint | New core function | Constructor supply allocation is not deposit-driven minting; I01/I02. |
| 2 | Controlled burn | New core function | No issuer burn/redemption operation; I02/I05. |
| 3 | Policy-controlled transfers | Extend and validate | Existing transfer/sign/receipt foundation; add issuer restrictions across direct/delegated transfers; B01-B10/I02/I06. |
| 4 | Balance and supply management | Extend and validate | Existing scoped balances/reservations; add circulating supply, restrictions and issuer views; I03/I07. |
| 5 | Pause/unpause | New core function | Authorized contract control and operational workflow; I02/I06. |
| 6 | Token wallet allowlist/blocklist | New core function | Network permissioning does not establish token policy; I02/I06. |
| 7 | Emergency individual-wallet freeze | New core function | Define exact freeze and recovery semantics; I02/I06. |
| 8 | Issuer roles and privileged-operation governance | New core function | Existing payment operator/approver roles do not grant mint/burn/admin authority; I01-I03. |
| 9 | Issuer Java/Spring APIs and ethers.js operations | New core function | Payment APIs/RPC tooling exist; issuer endpoints, operation recovery and SDK tooling remain; I03. |
| 10 | Deposit -> screening -> mint -> customer wallet | New core function | Existing payment funding is simulated allocation of prefunded tokens; I04. |
| 11 | Redemption -> burn -> simulated fiat settlement | New core function | Ordinary transfer/payout is not redemption; I05. |
| 12 | Issuer KYC/AML/sanctions integration points | Extend and validate | Existing simulated payment screening; add issuer operations, expiry and wallet eligibility; I06. |
| 13 | Issuer transaction/customer limits | Extend and validate | Apply explicit issuer limits to mint/burn/transfer, including bypass/negative cases; I03/I06. |
| 14 | Reserve, supply, treasury and redemption reconciliation | Extend and validate | Existing payment asset journals/reconciliation; add simulated reserves, issued supply and redemption liabilities; I04/I05/I07. |
| 15 | Issuance/redemption/admin event monitoring and audit | Extend and validate | Existing transfer receipts/correlation/recovery; add mint/burn/pause/role/wallet-control events; I07. |
| 16 | Institutional custody and external enterprise integration | Deferred integration | Encrypted local wallets are a POC foundation, not MPC/HSM custody or a connected bank/compliance provider; I10/A03/E06. |

Rows 1, 2, 5-11 are nine new core functions. Rows 3, 4, 12-15 are six extensions. Row 16 is a separate later integration group, giving sixteen overall. This is a scope grouping, not an estimate of implementation tasks or completion percentage.

Three additional delivery groups: (a) Foundry/Hardhat tests and relevant security/negative checks I08; (b) issuer deployment and complete lifecycle demonstrations I09; (c) architecture/API/contract/deployment/results/limitations documentation I09/D06/D07. The existing fixed-supply Solidity ERC-20 deployment and Node tests do not complete issuer-contract security acceptance.

## What does industry standard mean here?

ERC-20 standardizes token balances, transfers, delegated spending and events. It does not prescribe fiat reserves, compliance, privileged mint/burn, redemption settlement or institutional custody. OpenZeppelin supplies implementation components/extensions such as burnable and pausable tokens; role/policy/workflow composition still requires design and testing. Besu node/account permissioning is another layer and cannot substitute for token-level controls. Meeting the supplied technical brief establishes an agreed POC, not regulatory certification or production readiness.

Primary sources checked 27 November 2026: [ERC-20](https://eips.ethereum.org/EIPS/eip-20), [OpenZeppelin ERC-20 components](https://docs.openzeppelin.com/contracts/5.x/api/token/erc20), [Besu permissioning](https://docs.besu-eth.org/private-networks/concepts/permissioning).
Local evidence: [ADR-016](../adr/ADR-016-BESU-MULTI-NETWORK-ORCHESTRATION.md), [fixed-supply contract](../infrastructure/besu/contracts/BesuDemoUSD.sol), [private bindings](../infrastructure/besu/SETTLEMENT-BINDINGS.md), [activity history](36-POC-ACTIVITY-STATUS.md).

## GitHub -> Codespaces validation order

The owner will pull and execute in Codespaces. Private wallets/passwords, .env, deployment manifests and generated network state are not published. Existing original/replacement/public/private wallets must all be preserved.

1. Pull a clean checkout; confirm the published commit. If there are local changes, inspect/preserve them before pulling rather than resetting.

```bash
cd /workspaces/StableCoins
git status --short --untracked-files=normal
git pull --ff-only origin main
git log -1 --format='%H %s'
```

2. Verify prerequisites and install locked dependencies; these commands do not initialize a chain or send a transaction.

```bash
java -version
node --version
docker info >/dev/null
docker compose version
npm --prefix frontend ci
npm --prefix infrastructure ci
npm --prefix infrastructure/besu ci
npm --prefix infrastructure/besu test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend run lint
```

3. Execute database/unit/integration gates using disposable Testcontainers databases, not a destructive reset of the demonstration database.

```bash
node --env-file=.env scripts/local-workbench.cjs verify --require-database --show-failures
```

Inspect the actual Failsafe reports for every Besu persistence/claim/funding/closure/recovery suite as well as existing public suites. The generic required-suite list predates Besu; a generic PASSED alone is insufficient proof that every new Besu suite executed. Missing, skipped or failed required cases keep B01-B10 open. Contract issuer tests do not exist yet and are not covered by this command.

4. Follow [the separate Besu setup guide](../infrastructure/besu/README.md), [credential guide](../infrastructure/besu/CREDENTIAL-SETUP.md) and [staged application guide](../infrastructure/besu/APPLICATION.md). Check existing state first. Exercise B09 consensus, permissioning, persistence and RPC failure checks. New private customer instructions remain intentionally locked; this revision is for staged validation, not a claim that the full customer Besu demonstration is enabled. Complete an auditable acceptance path under B10 before normal customer activation; do not bypass the lock by modifying production records.

5. Exercise private authenticated payment/revert/restart/duplicate/uncertain outcome, per-asset ledger and reconciliation acceptance under B10. Then run the separate [public Base Sepolia walkthrough](38-COPY-PASTE-DEMO-WALKTHROUGH.md), retaining its own wallets, real public transaction receipt and DB evidence. Public test transfer/fees are an explicit operator activity; no script should silently submit them as part of dependency or database checks.

6. Record actual results and failures in activity history and close only the matching central backlog criteria. Issuer lifecycle work remains I01-I10 and is not silently included in the five settlement increments. AWS deployment follows Codespaces acceptance.
