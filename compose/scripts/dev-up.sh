#!/usr/bin/env bash
# dev-up.sh — Start the local development stack.
#
# Auth mode is controlled by AUTH_MODE in compose/.env:
#   AUTH_MODE=clerk    → Clerk cloud auth (default)
#   AUTH_MODE=keycloak → Self-hosted Keycloak (auto-started)
#
# You can also pass --keycloak to override AUTH_MODE for this run.
#
# Usage: bash compose/scripts/dev-up.sh [--all] [--keycloak]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"
ENV_FILE="$COMPOSE_DIR/.env"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.yml not found at $COMPOSE_FILE"
  exit 1
fi

echo "=== Dev Stack ==="
echo ""

# Read AUTH_MODE from .env (default: clerk)
AUTH_MODE="clerk"
if [[ -f "$ENV_FILE" ]]; then
  PARSED_MODE=$(grep -E "^AUTH_MODE=" "$ENV_FILE" 2>/dev/null | cut -d= -f2 | tr -d '[:space:]' || true)
  if [[ -n "$PARSED_MODE" ]]; then
    AUTH_MODE="$PARSED_MODE"
  fi
fi

# Start infrastructure services only (exclude backend/frontend by default)
SERVICES="postgres localstack mailpit"
KEYCLOAK=false
ALL=false
COMPOSE_PROFILES=""

for arg in "$@"; do
  case "$arg" in
    --all)
      ALL=true
      SERVICES=""
      echo "Starting all services (including backend + frontend)..."
      ;;
    --keycloak)
      AUTH_MODE="keycloak"
      ;;
  esac
done

# Export AUTH_MODE so docker-compose picks it up for build args
# (NEXT_PUBLIC_AUTH_MODE: ${AUTH_MODE:-clerk} in docker-compose.yml)
export AUTH_MODE

# Enable Keycloak profile when AUTH_MODE=keycloak
if [[ "$AUTH_MODE" == "keycloak" ]]; then
  KEYCLOAK=true
  COMPOSE_PROFILES="--profile keycloak"

  # Set Spring profiles so backend and gateway pick up keycloak config
  export BACKEND_PROFILES="local,keycloak"
  export GATEWAY_PROFILES="default,keycloak"

  if [[ "$ALL" == "false" ]]; then
    SERVICES="$SERVICES keycloak keycloak-init"
  fi
  echo "Auth mode: Keycloak (self-hosted)"
  echo "  Keycloak will be available at http://localhost:9090"
else
  echo "Auth mode: Clerk (cloud)"
fi

if [[ "$ALL" == "false" ]]; then
  echo "Starting infrastructure services..."
  echo "  (use --all to also start backend + frontend + gateway containers)"
fi

echo ""
echo "[1/2] Starting services..."
if [[ -n "$SERVICES" ]]; then
  docker compose -f "$COMPOSE_FILE" $COMPOSE_PROFILES up -d $SERVICES
else
  # Always --build when starting all services: NEXT_PUBLIC_AUTH_MODE is inlined
  # at Next.js build time, so the frontend image must be rebuilt when switching
  # between clerk/keycloak. Docker layer caching keeps this fast when unchanged.
  docker compose -f "$COMPOSE_FILE" $COMPOSE_PROFILES up -d --build
fi

echo ""
echo "[2/2] Waiting for services to become healthy..."

MAX_WAIT=60
INTERVAL=3
ELAPSED=0

# Wait for Postgres
printf "  Postgres (localhost:5432)... "
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if docker exec b2b-postgres pg_isready -U postgres > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "TIMEOUT (${MAX_WAIT}s)"
  echo "Check logs: docker compose -f $COMPOSE_FILE logs postgres"
  exit 1
fi

# Wait for LocalStack
ELAPSED=0
printf "  LocalStack (localhost:4566)... "
while [[ $ELAPSED -lt $MAX_WAIT ]]; do
  if curl -sf http://localhost:4566/_localstack/health > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep $INTERVAL
  ELAPSED=$((ELAPSED + INTERVAL))
done
if [[ $ELAPSED -ge $MAX_WAIT ]]; then
  echo "TIMEOUT (${MAX_WAIT}s)"
  echo "Check logs: docker compose -f $COMPOSE_FILE logs localstack"
  exit 1
fi

# Mailpit starts instantly, but check anyway
printf "  Mailpit (localhost:8025)... "
ELAPSED=0
while [[ $ELAPSED -lt 15 ]]; do
  if curl -sf http://localhost:8025/ > /dev/null 2>&1; then
    echo "ready"
    break
  fi
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done
if [[ $ELAPSED -ge 15 ]]; then
  echo "TIMEOUT (15s)"
fi

# Wait for Keycloak (if started)
if [[ "$KEYCLOAK" == "true" ]]; then
  ELAPSED=0
  printf "  Keycloak (localhost:9090)... "
  while [[ $ELAPSED -lt 120 ]]; do
    if curl -sf http://localhost:9090/realms/master > /dev/null 2>&1; then
      echo "ready"
      break
    fi
    sleep $INTERVAL
    ELAPSED=$((ELAPSED + INTERVAL))
  done
  if [[ $ELAPSED -ge 120 ]]; then
    echo "TIMEOUT (120s)"
    echo "Check logs: docker compose -f $COMPOSE_FILE logs keycloak"
    exit 1
  fi
fi

# If --all was requested, wait for backend, gateway, and frontend too
if [[ "$ALL" == "true" ]]; then
  ELAPSED=0
  printf "  Backend (localhost:8080)... "
  while [[ $ELAPSED -lt 300 ]]; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
      echo "ready"
      break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
  if [[ $ELAPSED -ge 300 ]]; then
    echo "TIMEOUT (300s)"
    echo "Check logs: docker compose -f $COMPOSE_FILE logs backend"
    exit 1
  fi

  ELAPSED=0
  printf "  Gateway (localhost:8090)... "
  while [[ $ELAPSED -lt 120 ]]; do
    if curl -sf http://localhost:8090/actuator/health > /dev/null 2>&1; then
      echo "ready"
      break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
  if [[ $ELAPSED -ge 120 ]]; then
    echo "TIMEOUT (120s)"
    echo "Check logs: docker compose -f $COMPOSE_FILE logs gateway"
    exit 1
  fi

  ELAPSED=0
  printf "  Frontend (localhost:3000)... "
  while [[ $ELAPSED -lt 300 ]]; do
    if curl -sf http://localhost:3000/ > /dev/null 2>&1; then
      echo "ready"
      break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
  if [[ $ELAPSED -ge 300 ]]; then
    echo "TIMEOUT (300s)"
    echo "Check logs: docker compose -f $COMPOSE_FILE logs frontend"
    exit 1
  fi
fi

echo ""
echo "=== Dev Stack Ready ==="
echo ""
echo "  Postgres:       localhost:5432"
echo "  LocalStack S3:  localhost:4566"
echo "  Mailpit SMTP:   localhost:1025"
echo "  Mailpit UI:     http://localhost:8025"
if [[ "$KEYCLOAK" == "true" ]]; then
  echo "  Keycloak Admin: http://localhost:9090/admin (admin/admin)"
fi
if [[ "$ALL" == "true" ]]; then
  echo "  Backend:        http://localhost:8080"
  echo "  Gateway:        http://localhost:8090"
  echo "  Frontend:       http://localhost:3000"
fi
echo ""
if [[ "$ALL" == "false" ]]; then
  echo "  Start backend:  cd backend && ./mvnw spring-boot:run"
  echo "  Start frontend: cd frontend && pnpm dev"
fi
echo "  Tail logs:      docker compose -f $COMPOSE_FILE logs -f"
echo "  Stop stack:     bash compose/scripts/dev-down.sh"
echo ""
