# AWS Architecture

## Edge

Route 53
CloudFront
AWS WAF

## Authentication

Amazon Cognito

## API

Amazon API Gateway

## Compute

Amazon ECS Fargate

Reason:

- container-native Spring Boot deployment
- no Kubernetes operational overhead
- simple scaling
- production-relevant deployment model

## Events

Amazon EventBridge

Purpose:

Business event routing.

Amazon SQS:

Reliable asynchronous delivery between application components.

DLQs:

Capture poison messages and exhausted retries.

## Data

Aurora PostgreSQL:

Primary operational and accounting database.

S3:

Artifacts, exports and optional audit evidence.

## Security

IAM
KMS
Secrets Manager
Security Groups
TLS

## Observability

CloudWatch Logs
CloudWatch Metrics
CloudWatch Dashboards
CloudTrail

Optional:

OpenTelemetry/X-Ray tracing.

## Infrastructure as Code

AWS CDK using TypeScript.

Infrastructure must be reproducible using:

cdk deploy

and removable for POC cost control using:

cdk destroy
## Deployment sequence update

The owner requested local validation before AWS deployment. Follow the
[local-first gates](33-LOCAL-FIRST-VALIDATION.md). AWS remains the target architecture;
current CDK messaging foundations are not a complete deployed AWS application.
