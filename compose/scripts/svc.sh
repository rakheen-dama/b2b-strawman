#!/usr/bin/env bash
# svc.sh — Start, stop, restart, or check status of local Keycloak-mode services.
#
# Designed for agent use: services run in the background with PID tracking
# and log output redirected to files. Agents can start/stop/restart individual
# services or all at once, and check health via the status command.
#
# Usage:
#   bash compose/scripts/svc.sh start   [backend|gateway|frontend|portal|all]
#   bash compose/scripts/svc.sh stop    [backend|gateway|frontend|portal|all]
#   bash compose/scripts/svc.sh restart [backend|gateway|frontend|portal|all]
#   bash compose/scripts/svc.sh status
#   bash compose/scripts/svc.sh logs    [backend|gateway|frontend|portal]  # tail -50
#
# Examples:
#   bash compose/scripts/svc.sh start all          # start everything
#   bash compose/scripts/svc.sh restart backend     # restart just backend
#   bash compose/scripts/svc.sh stop frontend portal # stop frontend + portal
#   bash compose/scripts/svc.sh status              # health check all
#   bash compose/scripts/svc.sh logs backend        # last 50 lines of backend log

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PID_DIR="$REPO_ROOT/.svc"
LOG_DIR="$REPO_ROOT/.svc/logs"
mkdir -p "$PID_DIR" "$LOG_DIR"

# --- Service definitions ---

declare -A SVC_DIR SVC_CMD SVC_HEALTH SVC_PORT SVC_WAIT

SVC_DIR[backend]="$REPO_ROOT/backend"
SVC_CMD[backend]="SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run"
SVC_HEALTH[backend]="http://localhost:8080/actuator/health"
SVC_PORT[backend]=8080
SVC_WAIT[backend]=120

SVC_DIR[gateway]="$REPO_ROOT/gateway"
SVC_CMD[gateway]="./mvnw spring-boot:run"
SVC_HEALTH[gateway]="http://localhost:8443/actuator/health"
SVC_PORT[gateway]=8443
SVC_WAIT[gateway]=90

SVC_DIR[frontend]="$REPO_ROOT/frontend"
SVC_CMD[frontend]="NODE_OPTIONS= NEXT_PUBLIC_AUTH_MODE=keycloak /opt/homebrew/bin/pnpm dev"
SVC_HEALTH[frontend]="http://localhost:3000/"
SVC_PORT[frontend]=3000
SVC_WAIT[frontend]=30

SVC_DIR[portal]="$REPO_ROOT/portal"
SVC_CMD[portal]="NODE_OPTIONS= /opt/homebrew/bin/pnpm dev"
SVC_HEALTH[portal]="http://localhost:3002/"
SVC_PORT[portal]=3002
SVC_WAIT[portal]=30

ALL_SERVICES=(backend gateway frontend portal)

# --- Helpers ---

pid_file() { echo "$PID_DIR/$1.pid"; }
log_file() { echo "$LOG_DIR/$1.log"; }

is_running() {
  local svc="$1"
  local pf
  pf="$(pid_file "$svc")"
  if [[ -f "$pf" ]]; then
    local pid
    pid="$(cat "$pf")"
    if kill -0 "$pid" 2>/dev/null; then
      return 0
    fi
    # Stale PID file
    rm -f "$pf"
  fi
  return 1
}

is_healthy() {
  local svc="$1"
  curl -sf "${SVC_HEALTH[$svc]}" > /dev/null 2>&1
}

wait_healthy() {
  local svc="$1"
  local max_wait="${SVC_WAIT[$svc]}"
  local elapsed=0
  local interval=3
  printf "  Waiting for %s (port %s)... " "$svc" "${SVC_PORT[$svc]}"
  while [[ $elapsed -lt $max_wait ]]; do
    if is_healthy "$svc"; then
      echo "ready (${elapsed}s)"
      return 0
    fi
    sleep $interval
    elapsed=$((elapsed + interval))
  done
  echo "TIMEOUT (${max_wait}s)"
  echo "  Check log: $(log_file "$svc")"
  return 1
}

# --- Commands ---

do_start() {
  local svc="$1"

  if is_running "$svc"; then
    if is_healthy "$svc"; then
      echo "  $svc: already running (PID $(cat "$(pid_file "$svc")")) and healthy"
      return 0
    else
      echo "  $svc: PID exists but not healthy — restarting..."
      do_stop "$svc"
    fi
  fi

  echo "  $svc: starting..."
  local log
  log="$(log_file "$svc")"

  # Truncate log on fresh start
  : > "$log"

  # Start in background with nohup
  (
    cd "${SVC_DIR[$svc]}"
    # Use SHELL=bash to avoid zoxide alias issues with cd
    SHELL=/bin/bash nohup bash -c "${SVC_CMD[$svc]}" >> "$log" 2>&1 &
    echo $! > "$(pid_file "$svc")"
  )

  # Wait for health
  wait_healthy "$svc"
}

do_stop() {
  local svc="$1"
  local pf
  pf="$(pid_file "$svc")"

  if [[ ! -f "$pf" ]]; then
    # No PID file — try to find by port
    local port_pid
    port_pid="$(lsof -ti tcp:"${SVC_PORT[$svc]}" 2>/dev/null | head -1 || true)"
    if [[ -n "$port_pid" ]]; then
      echo "  $svc: no PID file but port ${SVC_PORT[$svc]} in use (PID $port_pid) — killing..."
      kill "$port_pid" 2>/dev/null || true
      sleep 2
      kill -9 "$port_pid" 2>/dev/null || true
      echo "  $svc: stopped"
      return 0
    fi
    echo "  $svc: not running"
    return 0
  fi

  local pid
  pid="$(cat "$pf")"
  echo "  $svc: stopping (PID $pid)..."

  # Kill the process tree (Maven/pnpm spawn children)
  # First try graceful, then force
  if kill -0 "$pid" 2>/dev/null; then
    # Kill the process group if possible
    kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    local waited=0
    while kill -0 "$pid" 2>/dev/null && [[ $waited -lt 10 ]]; do
      sleep 1
      waited=$((waited + 1))
    done
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 -- "-$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
    fi
  fi

  rm -f "$pf"

  # Also kill anything still on the port (child processes)
  local port_pid
  port_pid="$(lsof -ti tcp:"${SVC_PORT[$svc]}" 2>/dev/null | head -1 || true)"
  if [[ -n "$port_pid" ]]; then
    kill "$port_pid" 2>/dev/null || true
    sleep 1
    kill -9 "$port_pid" 2>/dev/null || true
  fi

  echo "  $svc: stopped"
}

do_restart() {
  local svc="$1"
  do_stop "$svc"
  sleep 1
  do_start "$svc"
}

do_status() {
  echo ""
  printf "  %-12s %-8s %-8s %-8s %s\n" "SERVICE" "PID" "RUNNING" "HEALTHY" "PORT"
  printf "  %-12s %-8s %-8s %-8s %s\n" "-------" "---" "-------" "-------" "----"
  for svc in "${ALL_SERVICES[@]}"; do
    local pid="—"
    local running="no"
    local healthy="no"
    local pf
    pf="$(pid_file "$svc")"
    if [[ -f "$pf" ]]; then
      pid="$(cat "$pf")"
      if kill -0 "$pid" 2>/dev/null; then
        running="yes"
      else
        pid="stale"
      fi
    fi
    if is_healthy "$svc"; then
      healthy="yes"
      # If healthy but no PID tracked, find it
      if [[ "$running" == "no" ]]; then
        running="ext"
        pid="$(lsof -ti tcp:"${SVC_PORT[$svc]}" 2>/dev/null | head -1 || echo '?')"
      fi
    fi
    printf "  %-12s %-8s %-8s %-8s %s\n" "$svc" "$pid" "$running" "$healthy" "${SVC_PORT[$svc]}"
  done
  echo ""
}

do_logs() {
  local svc="$1"
  local log
  log="$(log_file "$svc")"
  if [[ -f "$log" ]]; then
    echo "=== Last 50 lines of $svc log ==="
    tail -50 "$log"
  else
    echo "  $svc: no log file found at $log"
  fi
}

# --- Main ---

ACTION="${1:-status}"
shift || true

# Collect target services
TARGETS=()
if [[ $# -eq 0 ]]; then
  if [[ "$ACTION" == "status" ]]; then
    TARGETS=("${ALL_SERVICES[@]}")
  else
    echo "Usage: svc.sh {start|stop|restart|status|logs} [backend|gateway|frontend|portal|all]"
    exit 1
  fi
else
  for arg in "$@"; do
    if [[ "$arg" == "all" ]]; then
      TARGETS=("${ALL_SERVICES[@]}")
      break
    elif [[ -n "${SVC_DIR[$arg]+x}" ]]; then
      TARGETS+=("$arg")
    else
      echo "ERROR: Unknown service '$arg'. Valid: backend, gateway, frontend, portal, all"
      exit 1
    fi
  done
fi

echo ""
echo "=== svc.sh $ACTION ${TARGETS[*]} ==="
echo ""

case "$ACTION" in
  start)
    for svc in "${TARGETS[@]}"; do do_start "$svc"; done
    ;;
  stop)
    # Stop in reverse order (frontend/portal first, then gateway, then backend)
    for (( i=${#TARGETS[@]}-1; i>=0; i-- )); do
      do_stop "${TARGETS[$i]}"
    done
    ;;
  restart)
    for svc in "${TARGETS[@]}"; do do_restart "$svc"; done
    ;;
  status)
    do_status
    ;;
  logs)
    if [[ ${#TARGETS[@]} -eq 0 ]]; then
      echo "Usage: svc.sh logs [backend|gateway|frontend|portal]"
      exit 1
    fi
    for svc in "${TARGETS[@]}"; do do_logs "$svc"; done
    ;;
  *)
    echo "Usage: svc.sh {start|stop|restart|status|logs} [backend|gateway|frontend|portal|all]"
    exit 1
    ;;
esac

echo ""
echo "Done."
