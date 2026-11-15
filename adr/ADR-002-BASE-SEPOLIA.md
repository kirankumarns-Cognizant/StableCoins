# ADR-002: Preferred Test Network

Status: ACCEPTED WITH FALLBACK

## Decision

Base Sepolia is the preferred network.

Ethereum Sepolia is the fallback.

## Rationale

An EVM-compatible network simplifies:

- ERC-20 integration
- Java/web3 libraries
- tooling
- smart-contract evolution

## Constraint

Network and contract addresses must be configurable.

Never hardcode chain-specific assumptions throughout business logic.