#!/usr/bin/env bash
# deploy.sh — box-side deploy for the Kazi VPS stack.
#
# The CI workflow (deploy-vps.yml) only BUILDS and PUSHES images to GHCR — it no
# longer SSHes into the box (GitHub runner IPs get dropped at the provider edge,
# and inbound CI SSH is fragile / burns Actions minutes on a private repo). The
# box pulls for itself: run this on the VPS after CI has pushed new images, or
# after any config change you `git pull`.
#
# Usage (on the box):
#   bash /opt/kazi/compose/scripts/deploy.sh
# Or remotely (you can SSH in even though GitHub can't):
#   ssh USER@HOST 'bash /opt/kazi/compose/scripts/deploy.sh'
#
# GHCR auth: the images are private, so the box must be logged in to ghcr.io.
# `docker login` persists creds in ~/.docker/config.json, so a one-time login is
# usually enough. For unattended re-auth, drop a read:packages PAT in
# ~/.config/kazi/ghcr_pat (chmod 600) and this script logs in with it.
set -euo pipefail

KAZI_DIR="${KAZI_DIR:-/opt/kazi}"
COMPOSE_DIR="${KAZI_DIR}/compose"
COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env.prod"
GHCR_USER="${GHCR_USER:-rakheen-dama}"
GHCR_PAT_FILE="${GHCR_PAT_FILE:-${HOME}/.config/kazi/ghcr_pat}"

dc() { docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"; }

echo "=== Kazi box deploy ==="

# 1. Config changes (compose, Caddyfile, realm, scripts) come from git.
echo "[1/5] git pull (config)…"
git -C "${KAZI_DIR}" pull --ff-only

cd "${COMPOSE_DIR}"

# 2. Ensure GHCR auth. Prefer the PAT file if present; otherwise rely on the
#    persisted docker login (and surface a clear hint if a pull later fails).
echo "[2/5] GHCR auth…"
if [[ -r "${GHCR_PAT_FILE}" ]]; then
  tr -d '\n' < "${GHCR_PAT_FILE}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin
else
  echo "  (no PAT file at ${GHCR_PAT_FILE}; using existing docker credentials)"
fi

# 3. Pull the freshly-built images.
echo "[3/5] pull images…"
dc pull

# 4. Apply: recreate any changed containers.
echo "[4/5] up -d…"
dc up -d

# 5. Caddy reads its Caddyfile as a bind mount — `up -d` does NOT reload it when
#    only the file content changed. Hot-reload so vhost/cert changes take effect
#    with no downtime. (Falls back to a container recreate if reload is unavailable.)
echo "[5/5] reload Caddy config…"
if ! dc exec -T caddy caddy reload --config /etc/caddy/Caddyfile 2>/dev/null; then
  echo "  caddy reload failed — recreating the caddy container instead"
  dc up -d --force-recreate caddy
fi

docker image prune -f >/dev/null 2>&1 || true

echo ""
echo "=== Deploy complete ==="
dc ps
echo ""
echo "NOTE: Keycloak realm/theme changes (realm-export.json, email theme) need a"
echo "      manual: docker compose -f ${COMPOSE_FILE} up -d --force-recreate keycloak"
