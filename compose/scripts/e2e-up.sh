#!/usr/bin/env bash
# e2e-up.sh — Start the E2E mock-auth stack for agent UI navigation.
# Builds from current source (always latest code), starts all services,
# waits for health checks, and prints a connection summary.
#
# Usage: bash compose/scripts/e2e-up.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.e2e.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.e2e.yml not found at $COMPOSE_FILE"
  exit 1
fi

echo "=== E2E Mock-Auth Stack ==="
echo ""
echo "Building from source at: $(cd "$COMPOSE_DIR/.." && pwd)"
echo ""

# Build and start all services
echo "[1/3] Building and starting services..."
docker compose -f "$COMPOSE_FILE" up -d --build

# Wait for services to become healthy
echo ""
echo "[2/3] Waiting for services to become healthy..."

MAX_WAIT=300
INTERVAL=5
ELAPSED=0

# Wait for backend
printf "  Backend (localhost:8081)... "
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "TIMEOUT (${MAX_WAIT}s)"
  echo "Check logs: docker compose -f $COMPOSE_FILE logs backend"
  exit 1
fi

# Wait for frontend
ELAPSED=0
printf "  Frontend (localhost:3001)... "
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf http://localhost:3001/ > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "TIMEOUT (${MAX_WAIT}s)"
  echo "Check logs: docker compose -f $COMPOSE_FILE logs frontend"
  exit 1
fi

# Wait for mock IDP
ELAPSED=0
printf "  Mock IDP (localhost:8090)... "
while [[ $ELAPSED -lt 60 ]]; do
  if curl -sf http://localhost:8090/.well-known/jwks.json > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
done
if [[ $ELAPSED -ge 60 ]]; then
  echo "TIMEOUT (60s)"
  exit 1
fi

# Mailpit starts fast
printf "  Mailpit (localhost:8026)... "
ELAPSED=0
while [[ $ELAPSED -lt 15 ]]; do
  if curl -sf http://localhost:8026/ > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done
if [[ $ELAPSED -ge 15 ]]; then
  echo "TIMEOUT (15s)"
fi

# Wait for seed to complete (it exits after provisioning)
echo ""
echo "[3/3] Waiting for seed to complete..."
SEED_TIMEOUT=120
ELAPSED=0
while [[ $ELAPSED -lt $SEED_TIMEOUT ]]; do
  SEED_JSON=$(docker compose -f "$COMPOSE_FILE" ps seed --format json 2>/dev/null || true)
  # Empty output means container exited and was removed — treat as success
  if [[ -z "$SEED_JSON" ]]; then
    echo "  Seed completed (container exited)"
    break
  fi
  SEED_STATUS=$(echo "$SEED_JSON" | jq -r '.State // .state // "unknown"' 2>/dev/null || echo "unknown")
  if [[ "$SEED_STATUS" == "exited" ]]; then
    SEED_EXIT=$(echo "$SEED_JSON" | jq -r '.ExitCode // .exit_code // "0"' 2>/dev/null || echo "0")
    if [[ "$SEED_EXIT" == "0" ]]; then
      echo "  Seed completed successfully"
    else
      echo "  WARNING: Seed exited with code $SEED_EXIT"
      echo "  Check logs: docker compose -f $COMPOSE_FILE logs seed"
    fi
    break
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
done
if [[ $ELAPSED -ge $SEED_TIMEOUT ]]; then
  echo "  WARNING: Seed did not complete within ${SEED_TIMEOUT}s (may still be running)"
fi

# Print summary
echo ""
echo "=== E2E Stack Ready ==="
echo ""
echo "  Frontend (mock auth):  http://localhost:3001"
echo "  Mock login page:       http://localhost:3001/mock-login"
echo "  Backend (e2e profile): http://localhost:8081"
echo "  Mock IDP:              http://localhost:8090"
echo "  Mailpit SMTP:          localhost:1026"
echo "  Mailpit UI:            http://localhost:8026"
echo "  Postgres:              localhost:5433 (user: postgres, pass: changeme, db: app)"
echo ""
echo "  Users: Alice (owner), Bob (admin), Carol (member)"
echo "  Org:   e2e-test-org"
echo ""
echo "  Tail logs:   docker compose -f $COMPOSE_FILE logs -f backend frontend"
echo "  Access DB:   docker exec -it e2e-postgres psql -U postgres -d app"
echo "  Stop stack:  bash compose/scripts/e2e-down.sh"
echo "  Reseed data: bash compose/scripts/e2e-reseed.sh"
echo ""
