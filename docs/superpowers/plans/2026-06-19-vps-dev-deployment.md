# VPS Dev-Environment Deployment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy the full Kazi stack to a single VPS as a persistent dev environment under `*-dev.heykazi.com`, secured behind Caddy, with Postgres + MinIO + Mailpit on-box and images pulled from GHCR.

**Architecture:** One Docker Compose stack (`compose/docker-compose.prod.yml`) where only Caddy is internet-facing (auto-HTTPS, 7 vhosts). Backend runs `prod,keycloak`; gateway runs the default profile (jdbc session store, no Redis) with secure cookies forced via env; MinIO replaces LocalStack; Mailpit runs in capture mode. CI builds the 4 app images with dev-env `NEXT_PUBLIC_*` build args and pushes to GHCR; the box only pulls.

**Tech Stack:** Docker Compose, Caddy 2, MinIO, Postgres 16, Mailpit, Keycloak 26.5, GitHub Actions, GHCR.

**Spec:** `docs/superpowers/specs/2026-06-19-vps-dev-deployment-design.md`

## Global Constraints

- All public hostnames are `<service>-dev.heykazi.com`. The 7 hosts and their targets are fixed (see Task 3 table). `NEXT_PUBLIC_*` values bake into Next bundles at build time — they MUST match these hosts.
- Only Caddy publishes host ports (80/443). Postgres and the MinIO console publish to `127.0.0.1` only. Everything else is internal-network-only. UFW allows 22/80/443.
- Backend profile is exactly `prod,keycloak` (no `application-staging.yml` exists). `keycloak` must come second so its JWT issuer overrides `prod`'s Clerk issuer.
- Backend datasource comes from `DATABASE_URL` / `DATABASE_MIGRATION_URL` (prod profile), **not** `SPRING_DATASOURCE_APP_JDBC_URL`. Credentials are embedded in the URL.
- Backend S3 bucket env var is `AWS_S3_BUCKET` (prod profile), not `AWS_S3_BUCKET_NAME`.
- Gateway must NOT run the `production` profile (it forces Redis). Default profile = jdbc sessions; secure cookies via `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`.
- MCP resource URL: `KAZI_MCP_RESOURCE_URL=https://api-dev.heykazi.com/mcp`.
- No secrets committed. `compose/.env.prod` is git-ignored; only `.env.prod.example` is tracked.

---

## File Structure

| File | Responsibility |
|---|---|
| `compose/.env.prod.example` | Templated env matrix for all services (tracked; real `.env.prod` is git-ignored) |
| `compose/docker-compose.prod.yml` | Production stack: app services (GHCR images), Postgres, MinIO(+init), Mailpit, Caddy |
| `compose/caddy/Caddyfile` | 7 vhosts, auto-HTTPS, basic-auth on mail |
| `compose/data/postgres/02-create-keycloak-db.sql` | (exists) creates the `keycloak` DB on first init — reused as-is |
| `.github/workflows/deploy-vps.yml` | Build 4 images with dev-env build args → push GHCR → SSH `compose pull && up -d` |
| `DEPLOY-VPS.md` | One-time provisioning + ongoing-ops runbook |
| `.gitignore` | add `compose/.env.prod` |

---

## Task 1: Reproduce the `prod,keycloak` boot locally against Postgres + MinIO

Reproduce-before-deploy (CLAUDE.md gate #4). This de-risks the four binding unknowns — S3 endpoint/credentials, CORS env, datasource URL, MCP resource — before any deploy artifact is written. Run the backend image locally with the exact prod env against a throwaway Postgres + MinIO, and confirm it boots and serves.

**Files:**
- Scratch only (a temporary `compose/docker-compose.prodcheck.yml` you delete at the end). No tracked changes unless a config gap is found.

**Interfaces:**
- Produces: a **confirmed env matrix** (the values Task 2 templates) and a list of any micro-gaps (e.g. an S3 endpoint property the prod profile doesn't bind).

- [ ] **Step 1: Build the backend image locally**

```bash
cd backend && ./mvnw -q -DskipTests package && \
docker build -t kazi-backend:prodcheck -f Dockerfile .
```
Expected: image builds.

- [ ] **Step 2: Start throwaway Postgres + MinIO**

```bash
docker network create kazicheck 2>/dev/null
docker run -d --name pg-check --network kazicheck -e POSTGRES_USER=app -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=app postgres:16-alpine
docker run -d --name minio-check --network kazicheck -e MINIO_ROOT_USER=kazi -e MINIO_ROOT_PASSWORD=kazi-secret minio/minio server /data --console-address :9001
docker run --rm --network kazicheck --entrypoint sh minio/mc -c \
  "mc alias set k http://minio-check:9000 kazi kazi-secret && mc mb -p k/kazi-dev"
```
Expected: bucket `kazi-dev` created.

- [ ] **Step 3: Boot the backend with the candidate prod env**

```bash
docker run --rm --name be-check --network kazicheck -p 18080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod,keycloak \
  -e DATABASE_URL='jdbc:postgresql://pg-check:5432/app?user=app&password=changeme' \
  -e DATABASE_MIGRATION_URL='jdbc:postgresql://pg-check:5432/app?user=app&password=changeme' \
  -e SPRING_DATASOURCE_PORTAL_JDBC_URL='jdbc:postgresql://pg-check:5432/app' \
  -e SPRING_DATASOURCE_PORTAL_USERNAME=app -e SPRING_DATASOURCE_PORTAL_PASSWORD=changeme \
  -e JWT_ISSUER_URI='http://localhost:8180/realms/docteams' \
  -e JWT_JWK_SET_URI='http://localhost:8180/realms/docteams/protocol/openid-connect/certs' \
  -e AWS_REGION=us-east-1 -e AWS_S3_BUCKET=kazi-dev \
  -e AWS_S3_ENDPOINT='http://minio-check:9000' \
  -e AWS_S3_PRESIGNER_ENDPOINT='http://localhost:9000' \
  -e AWS_CREDENTIALS_ACCESS_KEY_ID=kazi -e AWS_CREDENTIALS_SECRET_ACCESS_KEY=kazi-secret \
  -e SMTP_HOST='' -e SMTP_AUTH=false -e SMTP_STARTTLS=false \
  -e CORS_ALLOWED_ORIGINS_0='https://app-dev.heykazi.com' \
  -e CORS_ALLOWED_ORIGINS_1='https://portal-dev.heykazi.com' \
  -e INTERNAL_API_KEY=check -e PORTAL_JWT_SECRET=check-secret-32-bytes-min-aaaaaaaa \
  -e PORTAL_MAGIC_LINK_SECRET=check-secret-32-bytes-min-bbbbbbbb \
  -e INTEGRATION_ENCRYPTION_KEY=0123456789abcdef0123456789abcdef \
  -e KAZI_MCP_RESOURCE_URL='http://localhost:8080/mcp' \
  kazi-backend:prodcheck
```
Expected: Flyway runs global + tenant migrations; app reaches "Started BackendApplication". JWKS errors at boot are OK (no Keycloak running) — we only need the context to start and datasource/S3/CORS beans to bind.

- [ ] **Step 4: Verify the bindings that the prod profile leaves unset**

```bash
curl -s localhost:18080/actuator/health | grep -o '"status":"[A-Z]*"' | head -1   # UP (db/mail health on)
curl -s localhost:18080/.well-known/oauth-protected-resource                       # resource + authorization_servers JSON
```
Expected: health `UP`; the metadata JSON shows `"resource":"http://localhost:8080/mcp"` (confirms `KAZI_MCP_RESOURCE_URL` binds). If the container logs show an S3 client init failure or "endpoint" not applied, record it — the prod profile may need an explicit `aws.s3.endpoint` env key name (check `config/S3*Config.java` for the `@Value`/property it reads) and note the exact var for Task 2.

- [ ] **Step 5: Record findings and tear down**

Write the confirmed env var names (and any gap found in Step 4) into a scratch note you'll fold into Task 2. Then:
```bash
docker rm -f be-check pg-check minio-check; docker network rm kazicheck
rm -f compose/docker-compose.prodcheck.yml 2>/dev/null
```
Expected: clean. **No commit** (scratch only) unless Step 4 required a tracked backend config fix — in which case commit that fix alone:
```bash
git add backend/src/main/resources/application-prod.yml   # only if a gap was fixed
git commit -m "fix(backend): bind S3 endpoint/credentials in prod profile for self-hosted MinIO"
```

---

## Task 2: Write `compose/.env.prod.example`

**Files:**
- Create: `compose/.env.prod.example`
- Modify: `.gitignore` (add `compose/.env.prod`)

**Interfaces:**
- Produces: every env var consumed by Tasks 3–4 (the compose file references these names).
- Consumes: the confirmed matrix from Task 1.

- [ ] **Step 1: Write the env template**

```dotenv
# compose/.env.prod.example — copy to .env.prod on the box and fill real values.
# .env.prod is git-ignored. NEVER commit real secrets.

# ── Postgres (container) ──────────────────────────────────────────────
POSTGRES_USER=app
POSTGRES_PASSWORD=CHANGE_ME_strong_db_password
POSTGRES_DB=app

# ── MinIO (object storage) ────────────────────────────────────────────
MINIO_ROOT_USER=kazi
MINIO_ROOT_PASSWORD=CHANGE_ME_strong_minio_password
S3_BUCKET=kazi-dev

# ── Keycloak ──────────────────────────────────────────────────────────
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=CHANGE_ME_strong_kc_admin_password
KEYCLOAK_CLIENT_SECRET=CHANGE_ME_gateway_bff_client_secret

# ── Backend secrets ───────────────────────────────────────────────────
INTERNAL_API_KEY=CHANGE_ME_internal_api_key
PORTAL_JWT_SECRET=CHANGE_ME_min_32_byte_secret
PORTAL_MAGIC_LINK_SECRET=CHANGE_ME_min_32_byte_secret
INTEGRATION_ENCRYPTION_KEY=CHANGE_ME_32_byte_hex_key
EMAIL_UNSUBSCRIBE_SECRET=CHANGE_ME_secret
EMAIL_SENDER_ADDRESS=noreply@heykazi.com

# ── Caddy basic-auth for the Mailpit UI ───────────────────────────────
# Generate hash: docker run --rm caddy caddy hash-password --plaintext 'yourpass'
MAIL_BASIC_AUTH_USER=admin
MAIL_BASIC_AUTH_HASH=CHANGE_ME_caddy_bcrypt_hash

# ── GHCR image coordinates ────────────────────────────────────────────
IMAGE_OWNER=rakheen-dama
IMAGE_TAG=dev
```

- [ ] **Step 2: Git-ignore the real env file**

Add to `.gitignore`:
```
compose/.env.prod
```

- [ ] **Step 3: Commit**

```bash
git add compose/.env.prod.example .gitignore
git commit -m "chore(deploy): add VPS env template and git-ignore .env.prod"
```

---

## Task 3: Write `compose/caddy/Caddyfile`

**Files:**
- Create: `compose/caddy/Caddyfile`

**Interfaces:**
- Consumes: `MAIL_BASIC_AUTH_USER`, `MAIL_BASIC_AUTH_HASH` (Caddy reads env via `{$VAR}`).
- Produces: the public→internal routing the whole stack depends on.

Hostname → target (fixed):

| Hostname | Target |
|---|---|
| `app-dev.heykazi.com` | `frontend:3000` |
| `bff-dev.heykazi.com` | `gateway:8443` |
| `auth-dev.heykazi.com` | `keycloak:8180` |
| `portal-dev.heykazi.com` | `portal:3002` |
| `api-dev.heykazi.com` | `backend:8080` |
| `storage-dev.heykazi.com` | `minio:9000` |
| `mail-dev.heykazi.com` | `mailpit:8025` |

- [ ] **Step 1: Write the Caddyfile**

```caddyfile
{
	email admin@heykazi.com
}

app-dev.heykazi.com {
	reverse_proxy frontend:3000
}

bff-dev.heykazi.com {
	reverse_proxy gateway:8443
}

auth-dev.heykazi.com {
	reverse_proxy keycloak:8180
}

portal-dev.heykazi.com {
	reverse_proxy portal:3002
}

api-dev.heykazi.com {
	reverse_proxy backend:8080
}

storage-dev.heykazi.com {
	reverse_proxy minio:9000
}

mail-dev.heykazi.com {
	basic_auth {
		{$MAIL_BASIC_AUTH_USER} {$MAIL_BASIC_AUTH_HASH}
	}
	reverse_proxy mailpit:8025
}
```

- [ ] **Step 2: Validate the Caddyfile**

```bash
docker run --rm -e MAIL_BASIC_AUTH_USER=x -e MAIL_BASIC_AUTH_HASH='$2a$14$x' \
  -v "$PWD/compose/caddy/Caddyfile:/etc/caddy/Caddyfile" caddy caddy validate --config /etc/caddy/Caddyfile
```
Expected: `Valid configuration`.

- [ ] **Step 3: Commit**

```bash
git add compose/caddy/Caddyfile
git commit -m "feat(deploy): add Caddy reverse-proxy config for 7 *-dev hosts"
```

---

## Task 4: Write `compose/docker-compose.prod.yml`

**Files:**
- Create: `compose/docker-compose.prod.yml`

**Interfaces:**
- Consumes: all vars from Task 2; the Caddyfile from Task 3; `compose/data/postgres/02-create-keycloak-db.sql` (exists).
- Produces: the runnable stack the workflow (Task 6) and runbook (Task 7) drive.

- [ ] **Step 1: Write the compose file**

```yaml
name: kazi-prod

services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    ports:
      - "127.0.0.1:5432:5432"   # loopback only — reach via SSH tunnel
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./data/postgres:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks: [internal]

  minio:
    image: minio/minio:latest
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    ports:
      - "127.0.0.1:9001:9001"   # console: loopback only
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD-SHELL", "mc ready local || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [internal]

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set k http://minio:9000 $${MINIO_ROOT_USER} $${MINIO_ROOT_PASSWORD} &&
      mc mb -p k/$${S3_BUCKET} || true"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      S3_BUCKET: ${S3_BUCKET}
    networks: [internal]

  mailpit:
    image: axllent/mailpit:latest
    restart: unless-stopped
    networks: [internal]

  keycloak:
    image: quay.io/keycloak/keycloak:26.5.0
    restart: unless-stopped
    command: start-dev --import-realm --features=scripts
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: ${POSTGRES_USER}
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
      KC_HTTP_ENABLED: "true"
      KC_HTTP_PORT: 8180
      KC_HOSTNAME: https://auth-dev.heykazi.com
      KC_PROXY_HEADERS: xforwarded
      KC_BOOTSTRAP_ADMIN_USERNAME: ${KEYCLOAK_ADMIN_USERNAME}
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET}
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
      - ./keycloak/providers:/opt/keycloak/providers
      - ./keycloak/themes:/opt/keycloak/themes
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/localhost/8180 && echo done"]
      interval: 10s
      timeout: 5s
      retries: 20
      start_period: 40s
    networks: [internal]

  backend:
    image: ghcr.io/${IMAGE_OWNER}/kazi-backend:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod,keycloak
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}?user=${POSTGRES_USER}&password=${POSTGRES_PASSWORD}
      DATABASE_MIGRATION_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}?user=${POSTGRES_USER}&password=${POSTGRES_PASSWORD}
      SPRING_DATASOURCE_PORTAL_JDBC_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_PORTAL_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PORTAL_PASSWORD: ${POSTGRES_PASSWORD}
      JWT_ISSUER_URI: https://auth-dev.heykazi.com/realms/docteams
      JWT_JWK_SET_URI: https://auth-dev.heykazi.com/realms/docteams/protocol/openid-connect/certs
      KEYCLOAK_AUTH_SERVER_URL: http://keycloak:8180
      KEYCLOAK_REALM: docteams
      KEYCLOAK_ADMIN_USERNAME: ${KEYCLOAK_ADMIN_USERNAME}
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      AWS_REGION: us-east-1
      AWS_S3_BUCKET: ${S3_BUCKET}
      AWS_S3_ENDPOINT: http://minio:9000
      AWS_S3_PRESIGNER_ENDPOINT: https://storage-dev.heykazi.com
      AWS_CREDENTIALS_ACCESS_KEY_ID: ${MINIO_ROOT_USER}
      AWS_CREDENTIALS_SECRET_ACCESS_KEY: ${MINIO_ROOT_PASSWORD}
      SMTP_HOST: mailpit
      SMTP_PORT: 1025
      SMTP_AUTH: "false"
      SMTP_STARTTLS: "false"
      CORS_ALLOWED_ORIGINS_0: https://app-dev.heykazi.com
      CORS_ALLOWED_ORIGINS_1: https://portal-dev.heykazi.com
      APP_BASE_URL: https://app-dev.heykazi.com
      PORTAL_BASE_URL: https://portal-dev.heykazi.com
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
      PORTAL_JWT_SECRET: ${PORTAL_JWT_SECRET}
      PORTAL_MAGIC_LINK_SECRET: ${PORTAL_MAGIC_LINK_SECRET}
      INTEGRATION_ENCRYPTION_KEY: ${INTEGRATION_ENCRYPTION_KEY}
      EMAIL_SENDER_ADDRESS: ${EMAIL_SENDER_ADDRESS}
      EMAIL_UNSUBSCRIBE_SECRET: ${EMAIL_UNSUBSCRIBE_SECRET}
      KAZI_MCP_RESOURCE_URL: https://api-dev.heykazi.com/mcp
    depends_on:
      postgres:
        condition: service_healthy
      minio:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 60s
    networks: [internal]

  gateway:
    image: ghcr.io/${IMAGE_OWNER}/kazi-gateway:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      # NO SPRING_PROFILES_ACTIVE → default profile → jdbc session store (no Redis)
      SERVER_SERVLET_SESSION_COOKIE_SECURE: "true"
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB}
      DB_USER: ${POSTGRES_USER}
      DB_PASSWORD: ${POSTGRES_PASSWORD}
      KEYCLOAK_ISSUER: https://auth-dev.heykazi.com/realms/docteams
      KEYCLOAK_CLIENT_ID: gateway-bff
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET}
      BACKEND_URL: http://backend:8080
      FRONTEND_URL: https://app-dev.heykazi.com
    depends_on:
      keycloak:
        condition: service_healthy
      backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8443/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 40s
    networks: [internal]

  frontend:
    image: ghcr.io/${IMAGE_OWNER}/kazi-frontend:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      BACKEND_URL: http://backend:8080
      INTERNAL_API_KEY: ${INTERNAL_API_KEY}
    depends_on:
      backend:
        condition: service_healthy
    networks: [internal]

  portal:
    image: ghcr.io/${IMAGE_OWNER}/kazi-portal:${IMAGE_TAG}
    restart: unless-stopped
    environment:
      PORT: 3002
    depends_on:
      backend:
        condition: service_healthy
    networks: [internal]

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      MAIL_BASIC_AUTH_USER: ${MAIL_BASIC_AUTH_USER}
      MAIL_BASIC_AUTH_HASH: ${MAIL_BASIC_AUTH_HASH}
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - frontend
      - gateway
      - backend
      - portal
      - keycloak
      - minio
      - mailpit
    networks: [internal]

volumes:
  postgres_data:
  minio_data:
  caddy_data:
  caddy_config:

networks:
  internal:
    driver: bridge
```

- [ ] **Step 2: Validate compose syntax against the example env**

```bash
cd compose && cp .env.prod.example .env.prod && \
docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null && \
echo "COMPOSE OK" && rm .env.prod
```
Expected: `COMPOSE OK` (no interpolation/syntax errors).

- [ ] **Step 3: Commit**

```bash
git add compose/docker-compose.prod.yml
git commit -m "feat(deploy): add single-VPS production compose stack"
```

---

## Task 5: Keycloak realm — production redirect URIs + MCP OAuth client

The imported realm's `gateway-bff` client only allows localhost redirect URIs, and there is no client for Claude's MCP OAuth. Add both. Keep the localhost entries so local dev still works.

**Files:**
- Modify: `compose/keycloak/realm-export.json` (gateway-bff `redirectUris`, `webOrigins`, `post.logout.redirect.uris`; add an `mcp-claude` public client)

**Interfaces:**
- Consumes: hostnames `bff-dev`, `app-dev`, `api-dev`.
- Produces: a realm Claude Code and the gateway can authenticate against on the VPS.

- [ ] **Step 1: Extend gateway-bff for the dev hosts**

In `gateway-bff`'s `redirectUris` add:
```json
"https://bff-dev.heykazi.com/login/oauth2/code/keycloak",
"https://app-dev.heykazi.com/*"
```
In `webOrigins` add `"https://app-dev.heykazi.com"`. Set `post.logout.redirect.uris` to:
```json
"http://localhost:3000##http://localhost:3000/*##https://app-dev.heykazi.com##https://app-dev.heykazi.com/*"
```

- [ ] **Step 2: Add a public PKCE client for Claude's MCP OAuth**

Add to the realm `clients` array:
```json
{
  "clientId": "mcp-claude",
  "name": "Claude MCP Client",
  "enabled": true,
  "publicClient": true,
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": false,
  "redirectUris": [
    "https://claude.ai/api/mcp/auth_callback",
    "http://localhost:*/callback",
    "https://api-dev.heykazi.com/*"
  ],
  "webOrigins": ["+"],
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "+"
  },
  "protocol": "openid-connect",
  "fullScopeAllowed": true
}
```
> Decision recorded: pre-registered public PKCE client (vs enabling anonymous Dynamic Client Registration). Simpler and tighter for a controlled dev env. Revisit DCR only if Claude's client refuses the fixed `client_id`. The exact `redirectUris` Claude uses must be confirmed in Task 7 Step (MCP connect) — adjust here if the callback differs.

- [ ] **Step 3: Validate the JSON**

```bash
python3 -m json.tool compose/keycloak/realm-export.json >/dev/null && echo "REALM JSON OK"
```
Expected: `REALM JSON OK`.

- [ ] **Step 4: Commit**

```bash
git add compose/keycloak/realm-export.json
git commit -m "feat(deploy): add dev-host redirect URIs + mcp-claude PKCE client to realm"
```

---

## Task 6: GitHub Actions `deploy-vps.yml`

Build the 4 images with dev-env build args, push to GHCR, then SSH to the box and pull. Mirror the existing `seed-images.yml` patterns.

**Files:**
- Create: `.github/workflows/deploy-vps.yml`
- Reference (read for patterns, do not modify): `.github/workflows/seed-images.yml`, `.github/workflows/deploy-staging.yml`

**Interfaces:**
- Consumes repo secrets: `VPS_SSH_HOST`, `VPS_SSH_USER`, `VPS_SSH_KEY`. Uses the built-in `GITHUB_TOKEN` for GHCR push.
- Produces: `ghcr.io/<owner>/kazi-{backend,gateway,frontend,portal}:dev` and a deployed box.

- [ ] **Step 1: Write the workflow**

```yaml
name: deploy-vps
on:
  workflow_dispatch:
    inputs:
      tag:
        description: Image tag
        default: dev

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  OWNER: ${{ github.repository_owner }}
  TAG: ${{ inputs.tag || 'dev' }}

jobs:
  build-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build & push backend
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/Dockerfile
          push: true
          tags: ghcr.io/${{ env.OWNER }}/kazi-backend:${{ env.TAG }}

      - name: Build & push gateway
        uses: docker/build-push-action@v6
        with:
          context: ./gateway
          file: ./gateway/Dockerfile
          push: true
          tags: ghcr.io/${{ env.OWNER }}/kazi-gateway:${{ env.TAG }}

      - name: Build & push frontend
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./frontend/Dockerfile
          push: true
          build-args: |
            NEXT_PUBLIC_AUTH_MODE=keycloak
            NEXT_PUBLIC_GATEWAY_URL=https://bff-dev.heykazi.com
            NEXT_PUBLIC_BACKEND_URL=https://api-dev.heykazi.com
          tags: ghcr.io/${{ env.OWNER }}/kazi-frontend:${{ env.TAG }}

      - name: Build & push portal
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./portal/Dockerfile
          push: true
          build-args: |
            NEXT_PUBLIC_PORTAL_API_URL=https://api-dev.heykazi.com
          tags: ghcr.io/${{ env.OWNER }}/kazi-portal:${{ env.TAG }}

  deploy:
    needs: build-push
    runs-on: ubuntu-latest
    steps:
      - name: SSH pull & up
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.VPS_SSH_HOST }}
          username: ${{ secrets.VPS_SSH_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/kazi/compose
            echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
            docker compose --env-file .env.prod -f docker-compose.prod.yml pull
            docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
            docker image prune -f
```

- [ ] **Step 2: Lint the workflow YAML**

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/deploy-vps.yml')); print('WORKFLOW YAML OK')"
```
Expected: `WORKFLOW YAML OK`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-vps.yml
git commit -m "ci(deploy): add build->GHCR->SSH-pull workflow for the VPS dev env"
```

---

## Task 7: Write `DEPLOY-VPS.md` runbook

**Files:**
- Create: `DEPLOY-VPS.md`

**Interfaces:**
- Consumes: all prior artifacts. The human/agent operator follows this end-to-end.

- [ ] **Step 1: Write the runbook**

Content sections (write each as concrete, copy-pasteable commands):

1. **Provision** — Hostinger KVM 4 (16 GB), Ubuntu 24.04, Docker template.
2. **Harden** —
   ```bash
   ufw default deny incoming; ufw allow 22,80,443/tcp; ufw enable
   sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config && systemctl reload ssh
   apt-get update && apt-get install -y fail2ban
   ```
3. **DNS** — 7 `A` records (`app-dev`, `bff-dev`, `auth-dev`, `portal-dev`, `api-dev`, `storage-dev`, `mail-dev`) → VPS IP.
4. **Place files** — clone repo (or copy `compose/`) to `/opt/kazi`; `cp compose/.env.prod.example compose/.env.prod` and fill secrets; generate the Mailpit hash with `docker run --rm caddy caddy hash-password --plaintext '...'`.
5. **Repo secrets** — set `VPS_SSH_HOST/USER/KEY` in GitHub.
6. **First boot** — run the `deploy-vps` workflow (builds + pushes + pulls). On the box: `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d`. Watch `docker compose ... logs -f caddy` for cert issuance.
7. **Verify (observed, per CLAUDE.md gate #3)** —
   ```bash
   curl -sI https://app-dev.heykazi.com | head -1            # 200/307
   curl -s  https://api-dev.heykazi.com/actuator/health      # {"status":"UP"}
   curl -s  https://api-dev.heykazi.com/.well-known/oauth-protected-resource   # public metadata
   curl -sI https://auth-dev.heykazi.com/realms/docteams | head -1             # 200
   ```
   Then browser: log in via `app-dev` (redirects through `bff-dev`→`auth-dev`), confirm a portal magic-link email lands in `https://mail-dev.heykazi.com`.
8. **Laptop admin access** —
   ```bash
   ssh -L 15432:localhost:5432 USER@HOST     # Postgres → localhost:15432
   ssh -L 19001:localhost:9001 USER@HOST     # MinIO console → localhost:19001
   ```
9. **Connect Claude Code to MCP** —
   ```bash
   claude mcp add --transport http kazi https://api-dev.heykazi.com/mcp
   ```
   Complete the browser PKCE login (Keycloak). Confirm tools list. If the OAuth callback URL Claude uses isn't in the `mcp-claude` client, add it (Task 5 Step 2) and re-import the realm: `docker compose ... exec keycloak /opt/keycloak/bin/kc.sh import --file ...` or re-create the keycloak container.
10. **Backups** — cron `pg_dump` to a MinIO bucket:
    ```bash
    0 3 * * * docker exec kazi-prod-postgres-1 pg_dump -U app app | gzip > /opt/kazi/backups/app-$(date +\%F).sql.gz
    ```
11. **Ongoing ops** — redeploy = re-run the `deploy-vps` workflow. Restart one service: `docker compose ... up -d --no-deps <svc>`. Logs: `docker compose ... logs -f <svc>`.

- [ ] **Step 2: Commit**

```bash
git add DEPLOY-VPS.md
git commit -m "docs(deploy): add VPS provisioning + ops runbook"
```

---

## Self-Review

**Spec coverage:** §2 topology → Tasks 3,4. §2.1 hostnames → Task 3. §3 service config → Tasks 2,4. §3.2 gateway jdbc/cookies → Task 4 (env, no `production` profile). §3.2 Keycloak proxy → Task 4; redirect URIs → Task 5. §4 laptop access → Task 7 Step 8. §5 MCP → Task 5 (client) + Task 1 Step 4 (metadata) + Task 7 Step 9. §6 image pipeline → Task 6. §7 sizing → Task 7 Step 1. §8 security → Task 7 Step 2. §9 deliverables → Tasks 2–7. §10 open items: #1 gateway override → Task 4; #2 NEXT_PUBLIC_BACKEND_URL (resolved: browser-side, routed to api-dev) → Tasks 4,6; #3 Keycloak client → Task 5; #4 MCP audience → Task 1 Step 4 + Task 7 Step 9; #5 docs site → out of scope (noted); #6 start-dev → Task 4.

**Placeholder scan:** `CHANGE_ME_*` are intentional secret placeholders in a template (`.env.prod.example`). `<owner>`/`IMAGE_OWNER` is a real templated value. No TODO/TBD in steps.

**Type/name consistency:** Env var names match across Tasks 1, 2, 4 (`S3_BUCKET`→`AWS_S3_BUCKET`, `MINIO_ROOT_USER`→`AWS_CREDENTIALS_ACCESS_KEY_ID`). Hostnames identical across Tasks 3, 4, 5, 6, 7. Image names `kazi-{backend,gateway,frontend,portal}` identical in Tasks 4 and 6.

**Residual risk (verify during execution, not blockers):** Task 1 Step 4 is the gate for the one genuine unknown — whether the `prod` profile's S3 config binds `AWS_S3_ENDPOINT`/credentials for MinIO. If it doesn't, the fix is a small `application-prod.yml` addition committed in Task 1 Step 5 before the deploy artifacts assume it.
