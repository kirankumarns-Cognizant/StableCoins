# ADR-006: ECS Fargate

Status: ACCEPTED

## Decision

Deploy Spring Boot application services using Amazon ECS Fargate.

## Rationale

Provides container deployment without introducing Kubernetes operational
complexity.

## Rejected for Initial POC

EKS.

## Future

EKS may be reconsidered if an actual production platform requirement
justifies Kubernetes.
