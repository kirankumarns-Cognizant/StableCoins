# visa-simulator — multi-stage Docker build
# Build context: services/
#   docker build -f visa-simulator/Dockerfile -t stablecoins/visa-simulator:latest .
#
# Java 21 — approved architecture (AGENTS.md, CLAUDE.md).
# visa-simulator is INTERNAL VPC only — not exposed through API Gateway (ADR-009).

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

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

RUN ./mvnw dependency:go-offline -pl visa-simulator -am \
        --no-transfer-progress -q || true

COPY config/connections.properties ./config/connections.properties
COPY platform-security/src ./platform-security/src
COPY platform-flow/src ./platform-flow/src
COPY visa-simulator/src ./visa-simulator/src
RUN ./mvnw clean package -pl visa-simulator -am \
        -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/visa-simulator/target/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8086/health || exit 1
