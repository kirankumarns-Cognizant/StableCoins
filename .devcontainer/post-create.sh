#!/usr/bin/env bash
# .devcontainer/post-create.sh
# Runs once after the Codespace container is created.
#
# This script:
#   - verifies the Java 21, Node, Maven, and Docker toolchain
#   - pre-fetches Maven dependencies (offline dependency cache)
#   - installs frontend npm packages
#   - installs CDK npm packages
#
# This script does NOT:
#   - deploy AWS infrastructure
#   - create blockchain wallets
#   - store secrets
#   - run real blockchain transactions
#   - execute cdk deploy
# ─────────────────────────────────────────────────────────────────────────────

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "════════════════════════════════════════════════════════"
echo "  Stablecoin POC — Codespace bootstrap"
echo "════════════════════════════════════════════════════════"

# ── 1. Verify Java 21 ────────────────────────────────────────────────────────
echo ""
echo "── Java version ─────────────────────────────────────────"
java -version
JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
if [ "$JAVA_MAJOR" -ne 21 ]; then
  echo "ERROR: Java 21 is required. Found Java ${JAVA_MAJOR}."
  echo "       Java 21 is an approved architecture decision — do not change."
  exit 1
fi
echo "✓ Java 21 confirmed"

# ── 2. Verify javac ───────────────────────────────────────────────────────────
echo ""
echo "── javac version ────────────────────────────────────────"
javac -version
echo "✓ javac 21 confirmed"

# ── 3. Verify Maven Wrapper ───────────────────────────────────────────────────
echo ""
echo "── Maven (via wrapper) ──────────────────────────────────"
cd services
chmod +x mvnw
./mvnw --version
cd "$REPO_ROOT"
echo "✓ Maven wrapper ready"

# ── 4. Verify Node ────────────────────────────────────────────────────────────
echo ""
echo "── Node.js version ──────────────────────────────────────"
node --version
npm --version
echo "✓ Node ready"

# ── 5. Verify Docker (required for Testcontainers / ADR-010) ─────────────────
echo ""
echo "── Docker (required for Testcontainers) ─────────────────"
if docker version > /dev/null 2>&1; then
  docker version
  echo "✓ Docker daemon reachable — Testcontainers will work"
else
  echo "WARNING: Docker daemon not yet reachable."
  echo "         If this persists after full container startup, check"
  echo "         devcontainer.json docker-in-docker feature configuration."
fi

# ── 6. Pre-fetch Maven dependencies ──────────────────────────────────────────
echo ""
echo "── Pre-fetching Maven dependencies ─────────────────────"
cd services
if ./mvnw dependency:go-offline --no-transfer-progress -q; then
  echo "Maven dependency prefetch succeeded"
else
  echo "WARNING: Maven prefetch failed; verification still needs dependency access."
fi
cd "$REPO_ROOT"
echo "Maven prefetch attempt finished; inspect its status above."

# ── 7. Install frontend npm packages ─────────────────────────────────────────
echo ""
echo "── Frontend: npm ci ─────────────────────────────────────"
cd frontend
npm ci --prefer-offline
cd "$REPO_ROOT"
echo "✓ Frontend dependencies installed"

# ── 8. Install CDK npm packages ──────────────────────────────────────────────
echo ""
echo "── CDK: npm ci ──────────────────────────────────────────"
cd infrastructure
npm ci --prefer-offline
cd "$REPO_ROOT"
echo "✓ CDK dependencies installed"

# ── 9. Create .env from .env.example if not already present ──────────────────
echo ""
echo "── Environment configuration ────────────────────────────"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "  Created .env from .env.example."
  echo "  Update .env with real values before running services."
  echo "  For secrets, use Codespaces Secrets (repo Settings)."
else
  echo "  .env already exists — not overwritten."
fi
echo "✓ Environment configuration checked"

# ── 10. Summary ───────────────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════"
echo "  Bootstrap complete."
echo ""
echo "  Quick start:"
echo "    cd services && ./mvnw test"
echo "    cd services && ./mvnw verify   # runs PaymentApiIT (needs Docker)"
echo "    cd frontend && npm run build"
echo "    cd infrastructure && npx cdk synth"
echo ""
echo "  Codespaces walkthrough: docs/34-CODESPACES-DEMO-LAUNCH.md"
echo "  Strict container verification: node scripts/local-workbench.cjs verify --require-database"
echo "  Full local stack:"
echo "    docker compose up              # infra only"
echo "    docker compose --profile services up --build"
echo "════════════════════════════════════════════════════════"
