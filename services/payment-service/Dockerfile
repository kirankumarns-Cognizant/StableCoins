# payment-service — multi-stage Docker build
# Build context: services/
#   docker build -f payment-service/Dockerfile -t stablecoins/payment-service:latest .
#
# Java 21 — approved architecture (AGENTS.md, CLAUDE.md).
# Do NOT change to Java 17 or Java 25.

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper first so dependency layer is cached independently of source
COPY mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Parent POM then module POM — allows Docker layer caching of dependency downloads
COPY pom.xml ./
# Maven scans all reactor module descriptors, including the shared library.
COPY platform-flow/pom.xml ./platform-flow/pom.xml
COPY platform-security/pom.xml ./platform-security/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY compliance-quote-service/pom.xml ./compliance-quote-service/pom.xml
COPY blockchain-service/pom.xml ./blockchain-service/pom.xml
COPY settlement-ledger-service/pom.xml ./settlement-ledger-service/pom.xml
COPY reconciliation-service/pom.xml ./reconciliation-service/pom.xml
COPY visa-simulator/pom.xml ./visa-simulator/pom.xml

RUN ./mvnw dependency:go-offline -pl payment-service -am \
        --no-transfer-progress -q || true

# Copy shared connection configuration, then source and build
COPY config/connections.properties ./config/connections.properties
COPY platform-security/src ./platform-security/src
COPY platform-flow/src ./platform-flow/src
COPY payment-service/src ./payment-service/src
RUN ./mvnw clean package -pl payment-service -am \
        -DskipTests --no-transfer-progress

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user — containers must not run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/payment-service/target/*.jar app.jar

EXPOSE 8080

# Container-aware JVM flags:
#   UseContainerSupport  — respect cgroup CPU/memory limits
#   MaxRAMPercentage     — cap heap at 75 % of container memory
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
