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