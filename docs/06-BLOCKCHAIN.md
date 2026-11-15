# Blockchain Architecture

## Objective

Execute a REAL token transfer on a public blockchain testnet.

## Preferred Network

Base Sepolia.

## Fallback

Ethereum Sepolia.

Network selection must remain configurable.

## Blockchain Service

Responsibilities:

- wallet interaction
- balance query
- gas estimation
- token contract interaction
- transaction signing
- transaction submission
- transaction receipt retrieval
- confirmation tracking

## Required Interface

getNativeBalance(address)

getTokenBalance(address, token)

estimateTransferGas(request)

transferToken(request)

getTransaction(txHash)

getReceipt(txHash)

waitForConfirmation(txHash)

## Persist

paymentId
correlationId
network
chainId
tokenContract
senderAddress
recipientAddress
amount
tokenDecimals
transactionHash
blockNumber
gasUsed
status
submittedAt
confirmedAt

## Keys

Never store private keys:

- in Git
- in PostgreSQL
- in application.yml
- in logs

Use Secrets Manager/KMS for POC configuration.

Production architecture should evaluate institutional custody and MPC/HSM.

## Smart Contracts

No custom smart contract is required for Version 1.

Interact directly with the ERC-20-compatible token contract.

Future programmable payment features may introduce Solidity contracts.

## Java Implementation

Use web3j for all EVM interactions in the blockchain-service.

web3j provides:

- ERC-20 contract interaction
- transaction signing
- RPC provider abstraction (HTTP, WebSocket)
- receipt and event log retrieval

Version: use the latest stable release compatible with Java 21 and
Spring Boot 3.x. Verify compatibility at time of implementation.
Do not pin a version without verification.

The blockchain adapter must be implemented behind an interface so that the
real web3j implementation can be replaced with a mock for unit and
integration tests (Spring profile: test, local).

See ADR-010 for the test profile strategy.

## Blockchain Explorer

UI must provide a network-specific explorer link using the transaction hash.

Base Sepolia explorer: https://sepolia.basescan.org/tx/{txHash}

Ethereum Sepolia explorer: https://sepolia.etherscan.io/tx/{txHash}

The explorer base URL must be configurable per network.

Never fabricate transaction hashes.

## Iteration 5 Implementation

### Technology

- web3j 6.1.0 (groupId: org.web3j) with Java 21
- Spring Boot 3.3.5, port 8082
- Aurora PostgreSQL via Flyway (wallet, transaction, outbox and treasury execution-readiness migrations)

### MOCK vs REAL_TESTNET Mode

Controlled by `BLOCKCHAIN_MODE` env var (default: `MOCK`) combined with `TESTNET_ENABLED` (default: `false`).

- `MOCK`: uses `MockBlockchainAdapter` — no network calls, deterministic fake responses
- `REAL_TESTNET` + `TESTNET_ENABLED=true`: uses `RealTestnetBlockchainAdapter` with real web3j RPC calls to Base Sepolia

The `BlockchainAdapter` interface abstracts all blockchain I/O so both modes are testable without code changes.

### Chain ID Startup Validation

On every `prepareTransfer` call the service calls `adapter.getChainId()` and compares against `blockchain.chain-id` (84532 for Base Sepolia). A mismatch throws `BlockchainConfigurationException` immediately, preventing accidental mainnet transactions.

### Treasury reservation, nonce and recovery (step 4)

The current implementation is documented in
[25-TREASURY-AND-EXECUTION-READINESS.md](25-TREASURY-AND-EXECUTION-READINESS.md).
It extends the existing adapter, signer, wallet/transaction records and outbox.
Wallet row locks serialize USDC/ETH holds; available balances exclude active
reservations. Gas price and pending nonce are read from the configured adapter.

An unsigned HELD reservation may expire/release. Submission commits SUBMITTING /
PINNED, expected hash and claimed nonce before its single broadcast attempt.
Unknown outcomes, confirmation timeout and process restart retain the reservation;
receipt inspection can recover a committed claim. No automatic resend, nonce
rollback or fallback is permitted. A matching receipt with sufficient confirmations
sets CONFIRMED or REVERTED and settles the hold. Inclusion counts as confirmation
one. Outbox identity matches its event envelope; payloads identify MOCK/LIVE and
chain/contract context.

The PostgreSQL concurrency and commit-boundary tests require Docker. Do not
interpret unit test success as verified database locking or a real testnet transfer.
The existing execution HTTP controller is default-off pending protected internal
access; BLOCKCHAIN_EXECUTION_API_ENABLED is not authentication.

### Security

- Current local testnet signer loads `BLOCKCHAIN_WALLET_PRIVATE_KEY` from the environment; Secrets Manager/KMS integration remains required for AWS execution
- Never stored in PostgreSQL, never logged, never in any event JSON payload
- Only `txHash` and wallet `address` appear in outbound data
- `scripts/testnet-preflight.ps1` checks key presence without printing its value

### Outbox Pattern

`BlockchainOutboxDispatcher` polls `blockchain_outbox_event` every 5 seconds (SKIP LOCKED for concurrent safety) and publishes two event types:

- `BlockchainTransactionSubmitted` — after successful `ethSendRawTransaction`
- `BlockchainTransactionConfirmed` — after receipt with sufficient confirmations

### settlement-ledger-service Consumer

`BlockchainConfirmedConsumer` listens on `stablecoin-blockchain-confirmed-queue` and posts a balanced `BLOCKCHAIN_SETTLEMENT` journal:

- DR 120002 USDC In Transit
- CR 120003 Beneficiary USDC Settlement

Duplicate events are silently dropped via `DataIntegrityViolationException` on the `UNIQUE(payment_id, journal_type)` constraint.

## Step 8 actual fee evidence

Raw public receipts preserve supported L2 fee components, gasUsed,
effectiveGasPrice, completeness/reason and confirmation depth. Actual wei totals
are stored exactly; estimates cannot close settlement. Receipt-evidence refresh
is internal, execution-gated and restricted to the same already-mined transaction.
A reverted receipt can post an actual fee without successful USDC settlement.
See [supported fee policy and independent proof](31-SETTLEMENT-PAYOUT-RECONCILIATION.md#exact-receipt-fee-evidence).
