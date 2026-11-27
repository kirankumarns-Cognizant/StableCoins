# ADR-003: Visa Simulator

Status: ACCEPTED

## Context

The POC architecture should demonstrate Visa integration without making
commercial Visa connectivity a prerequisite.

## Decision

Create VisaPaymentAdapter.

Version 1 implementation:

VisaSimulatorAdapter.

## Future

Authorized Visa implementation can be added behind the same abstraction.

## Constraint

Simulator responses must always be identifiable as simulated.
## Additive wallet provider simulation - 2026-11-26

The owner explicitly requested wallet payout simulation in addition to fiat
payouts. The existing VisaPaymentAdapter and visa-simulator now implement a
separate token-wallet provider capability under the same simulator-only boundary.
No new microservice, real Visa connection or financial posting policy is added.
All provider evidence remains SIMULATED and is not a blockchain receipt.

The internal test harness remains separate from approved customer payment
execution. Customer routing/execution integration must bind one exact delivery
path and reconcile provider evidence before activation. Existing wallet outcome
accounting and fiat payout behavior remain as accepted in ADR-015.
See [implementation and acceptance](../docs/39-VISA-WALLET-SIMULATOR.md).