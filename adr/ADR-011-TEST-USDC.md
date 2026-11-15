# ADR-011: Use Circle's Official Test USDC on Base Sepolia

Status: ACCEPTED

## Context

The Blockchain Service must transfer a stablecoin token on a public testnet.
The POC demonstrates a BFSI-relevant payment scenario. The token choice should
reinforce the USDC narrative for the audience.

Options considered:

Option A: Use Circle's official test USDC deployed on Base Sepolia.
Option B: Deploy a custom minimal ERC-20 token for the POC.

## Decision

Use Circle's official test USDC on Base Sepolia (Option A).

AGENTS.md §15 prohibits introducing a custom smart contract in the initial
implementation without explicit instruction. Option B is therefore not
permitted without a new ADR.

The token contract address, token symbol and token decimals are configurable
environment variables. They must never be hardcoded in business logic.
See BLOCKCHAIN_TOKEN_CONTRACT, BLOCKCHAIN_TOKEN_SYMBOL,
BLOCKCHAIN_TOKEN_DECIMALS in .env.example.

Ethereum Sepolia remains the fallback network. The fallback also uses Circle's
official test USDC on Ethereum Sepolia (different contract address, same
configuration mechanism).

## Blocking Condition

If Circle's testnet USDC contract address on Base Sepolia cannot be obtained
at time of implementation, the blockchain-service implementation must be
reported as BLOCKED and a new ADR proposed before any substitute token is used.

Silent substitution of an unofficial token is not permitted.

## Rationale

- Circle's USDC is the reference stablecoin for BFSI use cases; using it
  maximises demo credibility.
- ERC-20 standard interface is the same regardless of which contract is used;
  the implementation cost of Option A equals Option B.
- Keeping the token address configurable satisfies ADR-002 (network
  configurability) and future-proofs the demo for other networks.

## Consequences

- Token acquisition depends on Circle's testnet faucet availability and
  daily drip limits. Pre-fund wallets well before any demo.
- The faucet may require a Coinbase Wallet account. Document this in
  scripts/wallet-setup.md.
