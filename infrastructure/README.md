# AWS infrastructure foundations

The current CDK stack defines the payment EventBridge bus, 14 routing rules and
32 SQS queues including dead-letter queues. It does not yet provision the full
ECS Fargate, Aurora, API Gateway, private ALB, frontend, identity or monitoring
architecture. Template tests are not AWS deployment evidence.

The owner requested local application validation before AWS deployment. Follow
[the local-first guide](../docs/33-LOCAL-FIRST-VALIDATION.md), then finish the
remaining accepted AWS architecture as part of step 10. Do not deploy this partial
stack and describe it as the complete application.

From the repository root:

```bash
npm --prefix infrastructure ci
npm --prefix infrastructure run build
npm --prefix infrastructure test -- --runInBand
```

Connection and resource names reuse
[the central configuration](../services/config/connections.properties). The
connection-wiring tests also verify six-service Compose ports, local schema
bootstrap and secret scoping. `local-workbench.cjs verify` runs these checks with
the frontend and Java suites. No command in that helper calls CDK deploy.

Review the intended AWS account, region, cost, secrets, identity, migration,
recovery and deployment configuration after local acceptance. The accepted
[architecture decisions](../adr/ADR-009-API-GATEWAY-ALB-FARGATE.md) remain authoritative.
