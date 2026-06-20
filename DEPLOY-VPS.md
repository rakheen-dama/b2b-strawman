# Kazi VPS Deployment Runbook

End-to-end guide for provisioning and operating the Kazi dev environment on a Hostinger VPS.
All commands are copy-pasteable. Placeholder values are in `ALL_CAPS`.

---

## Table of Contents

1. [Provision the VPS](#1-provision-the-vps)
2. [Harden the Server](#2-harden-the-server)
3. [DNS — 7 A Records](#3-dns--7-a-records)
4. [Place Files and Fill `.env.prod`](#4-place-files-and-fill-envprod)
5. [Repo Secrets (GitHub)](#5-repo-secrets-github)
6. [First Boot — Trigger the Workflow](#6-first-boot--trigger-the-workflow)
7. [Verify (Observed, per CLAUDE.md gate 3)](#7-verify-observed-per-claudemd-gate-3)
8. [Laptop Admin Access (SSH Tunnels)](#8-laptop-admin-access-ssh-tunnels)
9. [Connect Claude Code to MCP](#9-connect-claude-code-to-mcp)
10. [Backups](#10-backups)
11. [Ongoing Ops](#11-ongoing-ops)
12. [Troubleshooting — Split-Subdomain Gotchas](#12-troubleshooting--split-subdomain-gotchas)
13. [Security Hardening — Known Deferrals](#13-security-hardening--known-deferrals)

---

## 1. Provision the VPS

**Provider:** Hostinger  
**Plan:** KVM 4 (16 GB RAM, 8 vCPU)  
**OS:** Ubuntu 24.04 LTS with Docker pre-installed template

In the Hostinger console:
1. Select the KVM 4 plan and the Ubuntu 24.04 + Docker template.
2. Set an SSH key at provision time (or add one immediately via the console).
3. Note the assigned IPv4 address — you will use it for all DNS A records.

Verify Docker is running after first login:

```bash
ssh root@VPS_IP
docker version
docker compose version
```

---

## 2. Harden the Server

Run these commands as `root` immediately after first login.

### Firewall

```bash
ufw default deny incoming
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
ufw status verbose
```

### Disable password SSH login

```bash
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl reload ssh
```

### Install fail2ban

```bash
apt-get update && apt-get install -y fail2ban
systemctl enable --now fail2ban
```

### Create a non-root operator user (recommended)

```bash
useradd -m -s /bin/bash kazi
usermod -aG docker,sudo kazi
mkdir -p /home/kazi/.ssh
cp /root/.authorized_keys /home/kazi/.ssh/authorized_keys   # or paste your pubkey
chown -R kazi:kazi /home/kazi/.ssh
chmod 700 /home/kazi/.ssh && chmod 600 /home/kazi/.ssh/authorized_keys
```

From this point the runbook uses `USER@HOST` to mean your operator account and VPS IP.

---

## 3. DNS — 7 A Records

All records are managed in **AWS Route 53**, hosted zone `heykazi.com`.

Create the following 7 A records pointing to the VPS IP (TTL 300 is fine for a dev env):

| Hostname | Type | Value |
|---|---|---|
| `app-dev.heykazi.com` | A | VPS_IP |
| `bff-dev.heykazi.com` | A | VPS_IP |
| `auth-dev.heykazi.com` | A | VPS_IP |
| `portal-dev.heykazi.com` | A | VPS_IP |
| `api-dev.heykazi.com` | A | VPS_IP |
| `storage-dev.heykazi.com` | A | VPS_IP |
| `mail-dev.heykazi.com` | A | VPS_IP |

Caddy uses HTTP-01 ACME for TLS. Once these records resolve and ports 80/443 are open, certificates issue automatically on first request. You do not need to do anything extra for TLS — watch Caddy logs during first boot to confirm.

Confirm propagation before booting the stack:

```bash
dig +short app-dev.heykazi.com     # must return VPS_IP
dig +short api-dev.heykazi.com     # must return VPS_IP
```

---

## 4. Place Files and Fill `.env.prod`

### Clone (or copy) the compose directory to the box

```bash
ssh USER@HOST
sudo mkdir -p /opt/kazi
sudo chown kazi:kazi /opt/kazi

# Option A: clone the repo (requires a GitHub deploy key or HTTPS token)
git clone https://github.com/rakheen-dama/b2b-strawman.git /opt/kazi

# Option B: rsync just the compose/ directory from your laptop
# rsync -av compose/ USER@HOST:/opt/kazi/compose/
```

All subsequent commands on the box assume the working directory `/opt/kazi/compose`.

### Copy the env template

```bash
cd /opt/kazi/compose
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

### Generate secrets and fill `.env.prod`

Open `.env.prod` in an editor (`nano .env.prod`) and replace every `CHANGE_ME_*` value.

Use these generators — run each on your **laptop**, then paste the output into `.env.prod`:

```bash
# Strong random hex passwords — use for:
#   POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD,
#   KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_CLIENT_SECRET,
#   INTERNAL_API_KEY, PORTAL_JWT_SECRET, PORTAL_MAGIC_LINK_SECRET,
#   EMAIL_UNSUBSCRIBE_SECRET
openssl rand -hex 32

# INTEGRATION_ENCRYPTION_KEY — MUST be Base64 of 32 raw bytes.
# A 32-char hex string decodes to only 24 bytes and will crash backend startup.
openssl rand -base64 32

# Mailpit Caddy basic-auth hash (bcrypt) — for MAIL_BASIC_AUTH_HASH
# Replace YOURPASS with the password you want to protect the Mailpit UI
docker run --rm caddy caddy hash-password --plaintext 'YOURPASS'
```

> **⚠️ Escape the Mailpit hash.** The bcrypt hash contains `$` characters, and
> docker-compose interpolates `$`. You **must double every `$` to `$$`** when you
> paste the hash into `.env.prod`, or compose mangles it and Mailpit login always
> fails (`$2a$14$abc…` → `$$2a$$14$$abc…`). Verify with
> `docker compose --env-file .env.prod -f docker-compose.prod.yml config | grep MAIL_BASIC_AUTH_HASH`
> — the printed hash must keep its full `$2a$14$…` form. Log in with the **plaintext**
> password (the `--plaintext` value), not the hash.

**Key notes:**
- `IMAGE_OWNER` must be set to `rakheen-dama` (the GitHub owner) — the workflow pushes images to `ghcr.io/rakheen-dama/kazi-*`.
- `IMAGE_TAG` defaults to `dev` — leave as is unless you are deploying a tagged release.
- `MAIL_BASIC_AUTH_USER` is the username shown on the Mailpit login prompt (e.g. `admin`).
- `MAIL_BASIC_AUTH_HASH` is the bcrypt hash produced by the `caddy hash-password` command above.
- `CLERK_ISSUER` and `CLERK_JWKS_URI` should point to the Keycloak issuer (see the example file) — these satisfy Spring's placeholder resolution even though Keycloak overrides the actual token validation at runtime.

The final `.env.prod` should have no remaining `CHANGE_ME_*` values:

```bash
grep CHANGE_ME .env.prod   # must return nothing
```

---

## 5. Repo Secrets (GitHub)

Go to **GitHub → Settings → Secrets and variables → Actions** for the `rakheen-dama/b2b-strawman` repository and add these three secrets:

| Secret name | Value |
|---|---|
| `VPS_SSH_HOST` | VPS IPv4 address |
| `VPS_SSH_USER` | `kazi` (or `root` if you skipped creating a non-root user) |
| `VPS_SSH_KEY` | Contents of the **private** SSH key whose public half is on the box |

The deploy workflow (`.github/workflows/deploy-vps.yml`) uses these to SSH into the box and run `docker compose pull && up`.

---

## 6. First Boot — Trigger the Workflow

### Step 1 — Authenticate the box to GHCR (one-time manual step)

The GHCR images (`ghcr.io/rakheen-dama/kazi-*`) are **private**. The deploy workflow logs in automatically, but for any manual `docker compose pull` or `up` you run on the box, you must authenticate first using a GitHub Personal Access Token with `read:packages` scope:

```bash
# On the VPS
echo "GHCR_PAT" | docker login ghcr.io -u rakheen-dama --password-stdin
```

Generate the PAT at: GitHub → Settings → Developer settings → Personal access tokens → Fine-grained (or classic with `read:packages`).

### Step 2 — Trigger the workflow

1. Go to **GitHub → Actions → deploy-vps**.
2. Click **Run workflow** → leave `tag` as `dev` → **Run workflow**.
3. The workflow builds and pushes all four images, then SSHes into the box and runs `pull` + `up`.

### Step 3 — Watch Caddy for TLS cert issuance

```bash
ssh USER@HOST
cd /opt/kazi/compose
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f caddy
```

Look for lines like `certificate obtained successfully` for each of the 7 hostnames. This happens on the first inbound request to each hostname, so trigger it by curling them from your laptop (see Section 7).

### Manual start (if the workflow SSH step fails)

If you need to start the stack by hand on the box:

```bash
cd /opt/kazi/compose

# Pull images (requires GHCR login from Step 1)
docker compose --env-file .env.prod -f docker-compose.prod.yml pull

# Start in detached mode
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d

# Check all containers are healthy
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

---

## 7. Verify (Observed, per CLAUDE.md gate 3)

Run all checks from your **laptop**. "PASS" means you observed the output — inferred green is not green.

### HTTP smoke tests

```bash
# Frontend loads (expect 200 or 307 redirect)
curl -sI https://app-dev.heykazi.com | head -1

# Backend health (expect {"status":"UP"})
curl -s https://api-dev.heykazi.com/actuator/health

# MCP OAuth metadata (expect JSON with issuer/resource fields)
curl -s https://api-dev.heykazi.com/.well-known/oauth-protected-resource

# Keycloak realm endpoint (expect 200)
curl -sI https://auth-dev.heykazi.com/realms/docteams | head -1

# Portal (expect 200)
curl -sI https://portal-dev.heykazi.com | head -1

# MinIO S3 API (expect 403 or XML error — means MinIO is up)
curl -sI https://storage-dev.heykazi.com | head -1

# Mailpit (expect 401 — Caddy basic auth is active)
curl -sI https://mail-dev.heykazi.com | head -1
```

### Browser end-to-end

1. Open `https://app-dev.heykazi.com` in a browser.
2. You are redirected through `bff-dev.heykazi.com` → `auth-dev.heykazi.com` for login.
3. Log in with the Keycloak admin credentials (or a test user created in the `docteams` realm).
4. Confirm the Kazi dashboard loads.
5. Trigger a portal magic-link email (e.g. invite a client contact).
6. Open `https://mail-dev.heykazi.com` (log in with `MAIL_BASIC_AUTH_USER` / the plaintext password you hashed).
7. Confirm the magic-link email is visible in Mailpit.

### Container health check

```bash
ssh USER@HOST "cd /opt/kazi/compose && docker compose --env-file .env.prod -f docker-compose.prod.yml ps"
```

All services should show `(healthy)` or `Up`.

---

## 8. Laptop Admin Access (SSH Tunnels)

These services are bound to loopback only on the VPS (not exposed via Caddy). Access them by forwarding the port over SSH.

### PostgreSQL

```bash
ssh -L 15432:localhost:5432 USER@HOST -N
```

Then connect from your laptop:

```
Host:     localhost
Port:     15432
User:     app    (value of POSTGRES_USER)
Password: (value of POSTGRES_PASSWORD)
Database: app    (value of POSTGRES_DB)
```

### MinIO console

```bash
ssh -L 19001:localhost:9001 USER@HOST -N
```

Open `http://localhost:19001` in a browser. Log in with `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`.

### Persistent tunnel (optional)

Add to your laptop's `~/.ssh/config` for convenience:

```
Host kazi-vps
    HostName VPS_IP
    User     kazi
    LocalForward 15432 localhost:5432
    LocalForward 19001 localhost:9001
```

Then `ssh kazi-vps -N` forwards both tunnels simultaneously.

---

## 9. Connect Claude Code to MCP

### Dependency — backend MCP metadata fix (MERGED #1465)

Claude MCP OAuth auto-discovery relies on `/.well-known/oauth-protected-resource` advertising the Keycloak issuer. Under Spring Security 7 a built-in endpoint shadowed the custom one (no `authorization_servers`). This is fixed and merged to `main` (PR **#1465**), so any `:dev` image built after that merge includes it — no action needed beyond deploying a current image. If you ever see Claude fail PKCE discovery, confirm the backend image post-dates the #1465 merge and rebuild via the `deploy-vps` workflow.

### Prerequisite — enable MCP dynamic client registration (run `keycloak-bootstrap.sh`)

Claude authenticates to the MCP server via OAuth **Dynamic Client Registration** (DCR): it registers
itself with Keycloak on first connect. Keycloak's *default* anonymous client-registration policies
block this — empty Trusted Hosts (→ "Host not trusted") and the built-in `service_account` scope,
which is invalid at the authorization endpoint (→ "invalid_scope … service_account").

`keycloak-bootstrap.sh` configures a **constrained** DCR posture: trusts only `localhost` + `claude.ai`
redirect hosts, allows the OIDC optional scopes Claude needs (incl. `offline_access`), and removes the
`service_account` scope. It **must be run after every fresh Keycloak realm import** — this config
cannot live in `realm-export.json` because Keycloak re-adds the defaults on import. The script is
idempotent and also sets up the gateway-bff mappers and the `org_role` backfill.

**Prerequisites on the box:** the script uses `jq` and `curl` (curl is usually present, `jq` is not on a base Ubuntu image):

```bash
sudo apt-get update && sudo apt-get install -y jq curl
```

**Run it (VPS-safe invocation):**

```bash
ssh USER@HOST
cd /opt/kazi/compose
# Find the Keycloak container name for the kazi-prod project (usually kazi-prod-keycloak-1):
docker compose -p kazi-prod ps --format '{{.Name}}' | grep keycloak

# SEED_DEV_CREDENTIALS=false is REQUIRED on the VPS — it skips the dev-only steps
# that create a padmin/password backdoor and reset every existing user's password
# to "password". KCADM_SERVER stays in-container; KEYCLOAK_URL is the public host
# (Keycloak has no published host port on the VPS).
SEED_DEV_CREDENTIALS=false \
KEYCLOAK_URL=https://auth-dev.heykazi.com \
KCADM_SERVER=http://localhost:8180 \
KEYCLOAK_ADMIN=<admin> KEYCLOAK_ADMIN_PASSWORD=<pwd> \
KC_CONTAINER=kazi-prod-keycloak-1 \
  bash scripts/keycloak-bootstrap.sh
```

This applies only the prod-relevant steps: `org_role` user-profile registration, the `gateway-bff`
mappers, the `org_role` backfill on existing members, and the constrained MCP DCR policy. It does **not**
seed dev passwords — create your platform-admin via the Keycloak admin console (one-time; the realm
imports with no users).

Without the DCR step, `claude mcp add` will fail the PKCE flow with "Host not trusted" or "invalid_scope".
(The static `mcp-claude` client in `realm-export.json` stays as a fallback; with DCR enabled, Claude
self-registers and the Trusted Hosts policy constrains it to the same redirect hosts.)

> ⚠️ Re-running the script later (e.g. after a fresh realm re-import) — always keep
> `SEED_DEV_CREDENTIALS=false` on the VPS, or step [7/8] will reset every then-existing
> user's password to `"password"`.

### Add the MCP server to Claude Code

```bash
claude mcp add --transport http kazi https://api-dev.heykazi.com/mcp
```

Claude Code will open a browser PKCE flow to Keycloak (`auth-dev.heykazi.com/realms/docteams`). Complete the login. After authentication, run:

```bash
claude mcp list
# Should show: kazi  https://api-dev.heykazi.com/mcp  (connected)
```

Verify tools are available:

```bash
claude mcp tools kazi
```

### If the OAuth callback URI is rejected by Keycloak

Claude Code's callback URI may differ from the placeholder URIs registered in the `mcp-claude` Keycloak client. If the PKCE flow fails with "invalid redirect URI":

1. Note the exact callback URI from the browser error or Claude's output.
2. Add it to `compose/keycloak/realm-export.json` under the `mcp-claude` client's `redirectUris` array.
3. Re-import the realm on the box:

```bash
ssh USER@HOST
cd /opt/kazi/compose
docker compose --env-file .env.prod -f docker-compose.prod.yml exec keycloak \
  /opt/keycloak/bin/kc.sh import --file /opt/keycloak/data/import/realm-export.json \
  --override true
```

Or restart the Keycloak container (it auto-imports on start with `--import-realm`):

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps keycloak
```

---

## 10. Backups

### PostgreSQL — nightly cron dump to local disk

Add to the operator user's crontab on the VPS (`crontab -e`):

```
0 3 * * * docker exec kazi-prod-postgres-1 pg_dump -U app app | gzip > /opt/kazi/backups/app-$(date +\%F).sql.gz
```

Create the backup directory first:

```bash
mkdir -p /opt/kazi/backups
```

### Offload to MinIO (optional)

If you want backups in the `kazi-dev` bucket:

```bash
# Install MinIO client on the VPS
curl -O https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc && mv mc /usr/local/bin/

# Configure alias
mc alias set kazi-prod http://localhost:9000 MINIO_ROOT_USER MINIO_ROOT_PASSWORD

# Manual backup upload
mc cp /opt/kazi/backups/app-$(date +%F).sql.gz kazi-prod/kazi-dev/backups/
```

Or extend the crontab entry to pipe directly:

```
0 3 * * * docker exec kazi-prod-postgres-1 pg_dump -U app app | gzip | \
  mc pipe kazi-prod/kazi-dev/backups/app-$(date +\%F).sql.gz
```

### Retention

Delete backups older than 30 days:

```bash
find /opt/kazi/backups -name "*.sql.gz" -mtime +30 -delete
```

Add to crontab after the dump line:

```
30 3 * * * find /opt/kazi/backups -name "*.sql.gz" -mtime +30 -delete
```

---

## 11. Ongoing Ops

### Redeploy (standard — after any code push to main)

1. Go to **GitHub → Actions → deploy-vps → Run workflow**.
2. The workflow builds fresh `:dev` images, pushes to GHCR, and SSHes to the box to pull and restart containers.
3. No manual box access is required for a normal redeploy.

### Manual pull and restart on the box

If you need to redeploy manually (e.g. after manually editing `.env.prod`):

```bash
ssh USER@HOST
cd /opt/kazi/compose

# Authenticate to GHCR first (if not already logged in)
echo "GHCR_PAT" | docker login ghcr.io -u rakheen-dama --password-stdin

docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
docker image prune -f
```

### Restart a single service

```bash
cd /opt/kazi/compose
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend
```

Replace `backend` with: `gateway`, `frontend`, `portal`, `keycloak`, `postgres`, `minio`, `mailpit`, or `caddy`.

### View logs

```bash
cd /opt/kazi/compose

# Tail a specific service
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend

# Tail all services
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f

# Last N lines
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend
```

### Stop the stack

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml down
```

Add `--volumes` to wipe all data (destructive — do not do in production).

### Update `.env.prod` secrets

Edit `/opt/kazi/compose/.env.prod` on the box, then restart affected services:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --no-deps backend gateway
```

---

## 12. Troubleshooting — Split-Subdomain Gotchas

The stack puts each service on its own `*-dev.heykazi.com` subdomain. The app and
gateway were designed same-origin (one host, or `/bff` path), so several config
values that "just work" on `localhost` need explicit per-host wiring. All of the
below are **already fixed in the committed config** — this table is for diagnosis
if a symptom recurs, or when you re-point the deploy at a new domain.

| Symptom | Cause | Where it's fixed |
|---|---|---|
| Mailpit login always fails | bcrypt hash's `$` mangled by compose interpolation | `.env.prod` — escape `$`→`$$` (see §4) |
| `/request-access` "Unable to reach the server" | frontend server actions had no `GATEWAY_URL` (fell back to `localhost:8443`) | `docker-compose.prod.yml` frontend: `GATEWAY_URL: http://gateway:8443` (PR #1467) |
| Login loops — "redirected you too many times" | gateway `SESSION` cookie was host-only on `bff-dev`; middleware on `app-dev` never saw it | `docker-compose.prod.yml` gateway: `SERVER_SERVLET_SESSION_COOKIE_DOMAIN: heykazi.com` (PR #1468) |
| Accept-invite page "invalid link" | frontend image lacked `NEXT_PUBLIC_KEYCLOAK_URL` → kcUrl validator defaulted to `localhost:8180` | `frontend/Dockerfile` + `deploy-vps.yml` build args (PR #1469) — **rebuild required** |
| Invite email link points at `localhost:3000` | Keycloak `org-invite.ftl` email theme hard-coded the bounce base | env-driven via `theme.properties` + `KC_INVITE_BOUNCE_BASE_URL` (PR #1469) — **KC restart required** |
| OAuth `redirect_uri` built as `http://…:8443` | gateway didn't honour `X-Forwarded-*` behind Caddy | `docker-compose.prod.yml` gateway: `SERVER_FORWARD_HEADERS_STRATEGY: framework` |

Two of these need more than a container restart when triggered:
`NEXT_PUBLIC_*` values are **baked into the frontend image at build time** — changing
them requires re-running the `deploy-vps` workflow (no runtime override). The Keycloak
email theme is cached in production mode — changing `KC_INVITE_BOUNCE_BASE_URL` (or the
`.ftl`) requires `docker compose ... up -d --force-recreate keycloak`.

### Re-pointing at a different base domain

If you change `heykazi.com` (or the `-dev` prefix), update every place the host is
pinned — these are not all driven by a single variable:

- **DNS** — 7 A records (§3).
- **`compose/caddy/Caddyfile`** — the 7 vhost blocks.
- **`compose/docker-compose.prod.yml`** — `KC_HOSTNAME`, `JWT_ISSUER_URI`/`JWT_JWK_SET_URI`, `AWS_S3_PRESIGNER_ENDPOINT`, `KEYCLOAK_ISSUER`, `FRONTEND_URL`, `APP_BASE_URL`, `PORTAL_BASE_URL`, `HEYKAZI_BASE_URL`/`HEYKAZI_FRONTEND_URL`, `CLERK_ISSUER`/`CLERK_JWKS_URI`, `SERVER_SERVLET_SESSION_COOKIE_DOMAIN`, `KC_INVITE_BOUNCE_BASE_URL`, `KAZI_MCP_RESOURCE_URL`.
- **`.github/workflows/deploy-vps.yml`** — the frontend `NEXT_PUBLIC_*` build args (then rebuild).
- **`compose/keycloak/realm-export.json`** — `gateway-bff` redirect URIs / web origins / post-logout, and `mcp-claude` redirect URIs (re-import or edit in the admin console).

---

## 13. Security Hardening — Known Deferrals

These are tracked decisions made explicitly for the dev environment — not oversights. Each should be revisited before promoting to a production tenant-facing deployment.

**(a) Keycloak — RESOLVED: runs in production mode**

`docker-compose.prod.yml` uses `command: start --import-realm` (Keycloak production mode). This was boot-verified: the server logs `Profile prod activated`, imports the `docteams` realm, and — behind Caddy's `X-Forwarded-*` headers with `KC_HOSTNAME=https://auth-dev.heykazi.com` + `KC_PROXY_HEADERS=xforwarded` — resolves its OIDC issuer to `https://auth-dev.heykazi.com/realms/docteams`. No `KC_HOSTNAME_STRICT` or other extra config was needed. (The local dev stack `docker-compose.yml` still uses `start-dev` and is unaffected.)

**(b) Shared Postgres superuser across all services**

`backend`, `gateway`, `portal`, and `keycloak` all connect as the `POSTGRES_USER` (`app`) which is the same superuser used to create the database. Least-privilege hardening would give each service its own role with only the permissions it needs. Tracked: create per-service DB roles before production.

**(c) DB credentials embedded in JDBC URL query string**

The `DATABASE_URL` and `DATABASE_MIGRATION_URL` env vars embed `user=` and `password=` in the query string (e.g. `jdbc:postgresql://postgres:5432/app?user=app&password=SECRET`). These values can appear in application logs if the connection URL is logged at startup. Tracked: migrate to separate `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` env vars when promoting to production.

---

*Runbook version: 2026-06-20 (adds §12 split-subdomain troubleshooting after a live deploy surfaced five host-config gotchas — PRs #1467–#1469). Artifacts: `compose/docker-compose.prod.yml`, `compose/.env.prod.example`, `compose/caddy/Caddyfile`, `.github/workflows/deploy-vps.yml`.*
