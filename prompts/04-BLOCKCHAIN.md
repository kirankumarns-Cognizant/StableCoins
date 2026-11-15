Implement the real blockchain testnet integration.

Read:

AGENTS.md
docs/06-BLOCKCHAIN.md
docs/14-SECURITY.md
ADR-001
ADR-002

THIS INTEGRATION MUST NOT BE SIMULATED WHEN REAL_TESTNET mode is active.

Target:

Base Sepolia

Fallback:

Ethereum Sepolia

Implement:

BlockchainService
BlockchainAdapter

Capabilities:

getNativeBalance
getTokenBalance
estimateTransferGas
transferToken
submitTransaction
getTransaction
getReceipt
waitForConfirmation

Persist:

paymentId
correlationId
network
chainId
tokenContract
sourceWallet
destinationWallet
amount
txHash
blockNumber
gasUsed
transactionStatus

Never place private keys in:

source
PostgreSQL
application.yml
logs

Create AWS Secrets Manager integration/configuration.

Create documented environment variables for:

RPC endpoint
chainId
token contract
wallet secret reference

Implement REAL_TESTNET and MOCK modes explicitly.

MOCK mode must never masquerade as REAL_TESTNET.

Create instructions for:

1. configuring test wallet
2. obtaining test gas token
3. obtaining test token
4. configuring RPC
5. executing transaction
6. checking explorer
7. verifying transaction receipt

Run unit tests.

Run actual blockchain integration test ONLY when required secrets and
environment configuration are available.

Never fabricate a transaction hash.

STOP after blockchain integration.