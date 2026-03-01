#!/usr/bin/env bash
# dev-up.sh — Start the local development infrastructure.
# Starts Postgres, LocalStack, and Mailpit. Backend is NOT started here —
# run it locally via ./mvnw spring-boot:run for hot-reload.
#
# Usage: bash compose/scripts/dev-up.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$COMPOSE_DIR/docker-compose.yml"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose.yml not found at $COMPOSE_FILE"
  exit 1
fi

echo "=== Dev Stack ==="
echo ""

# Start infrastructure services only (exclude backend/frontend by default)
SERVICES="postgres localstack mailpit"
if [[ "${1:-}" == "--all" ]]; then
  SERVICES=""
  echo "Starting all services (including backend + frontend)..."
else
  echo "Starting infrastructure services..."
  echo "  (use --all to also start backend + frontend containers)"
fi

echo ""
echo "[1/2] Starting services..."
if [[ -n "$SERVICES" ]]; then
  docker compose -f "$COMPOSE_FILE" up -d $SERVICES
else
  docker compose -f "$COMPOSE_FILE" up -d
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

# If --all was requested, wait for backend and frontend too
if [[ "${1:-}" == "--all" ]]; then
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
if [[ "${1:-}" == "--all" ]]; then
  echo "  Backend:        http://localhost:8080"
  echo "  Frontend:       http://localhost:3000"
fi
echo ""
echo "  Start backend:  cd backend && ./mvnw spring-boot:run"
echo "  Start frontend: cd frontend && pnpm dev"
echo "  Tail logs:      docker compose -f $COMPOSE_FILE logs -f"
echo "  Stop stack:     bash compose/scripts/dev-down.sh"
echo ""
