#!/bin/sh
set -eu

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
TIMEOUT="${TIMEOUT:-120}"
INTERVAL="${INTERVAL:-2}"

echo "==> Waiting for backend at ${BACKEND_URL}/actuator/health (timeout: ${TIMEOUT}s)"

elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
  if curl -sf "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
    echo "==> Backend is healthy (${elapsed}s elapsed)"
    exit 0
  fi
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done

echo "==> ERROR: Backend not healthy after ${TIMEOUT}s"
exit 1
