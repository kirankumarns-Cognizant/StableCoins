# Claude Code Instructions

You are the implementation agent for the Stablecoin Intelligent Payment
Orchestration POC.

## Before working

Always read:

1. AGENTS.md
2. relevant files under /docs
3. applicable ADRs under /adr

AGENTS.md contains mandatory architecture rules.

## Working Method

For non-trivial tasks:

1. understand the requirement
2. inspect existing code
3. identify files requiring modification
4. implement the smallest complete increment
5. build
6. run relevant tests
7. fix implementation problems
8. report results

Never redesign the system silently.

## Important Rules

Do not fabricate:

- AWS APIs
- blockchain RPC responses
- Visa APIs
- test results
- transaction hashes
- credentials
- contract addresses

Never expose secrets.

Never put blockchain private keys in source control or PostgreSQL.

## Architecture

Primary stack:

React + TypeScript
Java 21
Spring Boot 3.x
AWS ECS Fargate
API Gateway
EventBridge
SQS
Aurora PostgreSQL
AWS CDK TypeScript
Base Sepolia
Visa Simulator

## Most Important Requirement

The blockchain testnet integration is REAL.

USD funding, FX, compliance and Visa are SIMULATED.

Always preserve this distinction.

## Scope Discipline

Implement only the requested build iteration.

Do not proceed automatically to subsequent iterations.

When implementation is complete report:

- files created
- files modified
- commands executed
- tests executed
- test results
- unresolved problems
- assumptions
