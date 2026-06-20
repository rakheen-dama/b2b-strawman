# Single-VPS Dev Environment for Kazi (behind Caddy)

**Date:** 2026-06-19
**Status:** Design approved, pending spec review
**Goal:** Deploy the full Kazi stack to one cheap VPS (Hostinger KVM or similar) as a
persistent **dev/demo environment** under `*-dev.heykazi.com`, secured behind Caddy, as a
low-cost alternative to the ~$155–205/mo AWS ECS staging plan until product-market fit is proven.

---

## 1. Context

Kazi is a multi-tenant B2B SaaS (schema-per-tenant). The runtime is already a Docker Compose
topology (`compose/docker-compose.yml`); this design productionises a variant of it onto a single
host. Six app/infra services plus Postgres:

| Service | Port | Needs |
|---|---|---|
| `backend` (Spring Boot 4 / Java 25) | 8080 | Postgres, S3, SMTP; hosts `/mcp` + OAuth metadata |
| `gateway` (Spring Cloud Gateway BFF) | 8443 | Postgres (session store), Keycloak |
| `frontend` (Next.js) | 3000 | gateway (BFF), backend (internal) |
| `portal` (Next.js) | 3002 | backend (direct, browser-side) |
| `keycloak` 26.5 | 8180 | own Postgres DB |
| `postgres` 16 | 5432 | — (serves app + keycloak + portal DBs) |

### Approved decisions (brainstorm 2026-06-19)
- **Reverse proxy:** Caddy (auto-HTTPS).
- **Postgres:** container on the box; nightly `pg_dump` backup.
- **Redis:** dropped — gateway uses the **jdbc** session store (sessions in Postgres).
- **Object storage:** MinIO container on the box (S3-compatible).
- **Email:** Mailpit in capture mode (no real mail leaves; UI behind basic-auth).
- **Images:** built in GitHub Actions, pushed to GHCR, pulled on the box (box never compiles).
- **Domain:** `<service>-dev.heykazi.com` (this is the standing dev environment).

---

## 2. Architecture

```
                    Internet (80/443 only; UFW blocks the rest)
                                   │
                             ┌─────▼─────┐
                             │   Caddy   │  auto-HTTPS, sole public service
                             └─────┬─────┘
   ┌──────────┬──────────┬─────────┼──────────┬───────────┬──────────┐
   ▼          ▼          ▼         ▼          ▼           ▼          ▼
frontend   gateway   keycloak   backend     minio      mailpit    (portal
:3000      :8443     :8180      :8080        :9000      :8025       :3002)
                                  │
   ───────────── internal Docker network (no host ports) ─────────────
                                  │
                            ┌─────▼─────┐
                            │ postgres  │  app + keycloak + portal DBs
                            │  :5432    │  (bound 127.0.0.1 for SSH-tunnel access)
                            └───────────┘
```

Only Caddy is internet-facing. Every other container publishes **no host port** (except Postgres
and the MinIO console bound to `127.0.0.1` for SSH-tunnel admin access). UFW allows `22/80/443`.

### 2.1 Public hostname map (Caddy vhosts)

| Hostname | → container:port | Purpose |
|---|---|---|
| `app-dev.heykazi.com` | frontend:3000 | Main Next.js app |
| `bff-dev.heykazi.com` | gateway:8443 | BFF: `/oauth2/*`, `/bff/me`, proxied `/api/*` (browser redirected here to log in) |
| `auth-dev.heykazi.com` | keycloak:8180 | Keycloak (browser redirected here to authenticate) |
| `portal-dev.heykazi.com` | portal:3002 | Customer portal Next.js app |
| `api-dev.heykazi.com` | backend:8080 | Portal direct API + **`/mcp`** + `/.well-known/oauth-protected-resource` |
| `storage-dev.heykazi.com` | minio:9000 | Public S3 endpoint for presigned upload/download URLs |
| `mail-dev.heykazi.com` | mailpit:8025 | Captured-email UI (Caddy basic-auth) |

### 2.2 Why each public host is required (traced from code)
- **`bff-dev`** — `frontend/lib/auth/middleware.ts` redirects the *browser* to
  `NEXT_PUBLIC_GATEWAY_URL/oauth2/authorization/keycloak`. The gateway must be browser-reachable.
- **`auth-dev`** — the browser is redirected to Keycloak for the login form.
- **`api-dev`** — `portal/lib/api-client.ts` calls the backend **directly** from the browser via
  `NEXT_PUBLIC_PORTAL_API_URL` (bypasses the gateway). Also serves `/mcp` and OAuth metadata.
- **`storage-dev`** — backend hands the browser **presigned S3 URLs** (data exports,
  portal document downloads via `StorageService.generateDownloadUrl`). The presigner endpoint must
  resolve publicly, so it cannot be the internal `minio:9000`.

---

## 3. Service configuration

### 3.1 New / changed Compose (`compose/docker-compose.prod.yml`)
- 4 app services use `image: ghcr.io/<owner>/kazi-<svc>:dev` (no `build:`); no host ports.
- **Drop** LocalStack (→ MinIO) and Redis (jdbc sessions).
- **Add** `minio` + a one-shot `mc` init container (creates the bucket); `caddy` (Caddyfile + cert volume).
- **Keep** postgres (container) and mailpit (capture mode).
- Postgres published as `127.0.0.1:5432:5432`; MinIO console `127.0.0.1:9001:9001` — loopback only.

### 3.2 Per-service env

**backend** — `SPRING_PROFILES_ACTIVE=prod,keycloak` (no `application-staging.yml` exists; the AWS
audit established `prod,keycloak` is the combo).
- `SPRING_DATASOURCE_*` → `postgres:5432` (app DB).
- `AWS_S3_ENDPOINT=http://minio:9000` (internal puts); `AWS_S3_PRESIGNER_ENDPOINT=https://storage-dev.heykazi.com` (browser-reachable).
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://auth-dev.heykazi.com/realms/docteams`.
- `KAZI_MCP_RESOURCE_URL=https://api-dev.heykazi.com/mcp` (RFC 9728 metadata — see §5).
- `SPRING_MAIL_HOST=mailpit`, `SPRING_MAIL_PORT=1025`.
- CORS must allow `https://portal-dev.heykazi.com` (portal → backend direct calls).

**gateway** — secure cookies + **jdbc** session store (NOT the prod profile's Redis).
- Mechanism: keep secure-cookie settings but force `SPRING_SESSION_STORE_TYPE=jdbc` and
  `SPRING_SESSION_JDBC_INITIALIZE_SCHEMA=always` via env, so we don't drag in the Redis-bound
  `production` profile wholesale. **(Verify exact override during planning — see Open Items.)**
- `KEYCLOAK_ISSUER=https://auth-dev.heykazi.com/realms/docteams`, `FRONTEND_URL=https://app-dev.heykazi.com`,
  `KEYCLOAK_ADMIN_URL=http://keycloak:8180`, DB → `postgres:5432`.

**keycloak** — behind-proxy config:
- `KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME=https://auth-dev.heykazi.com`, `KC_HTTP_ENABLED=true`.
- Realm `docteams` imported as today, **but** the `gateway-bff` client redirect URIs must add
  `https://bff-dev.heykazi.com/login/oauth2/code/keycloak` + post-logout URLs.
- Keep `start-dev` for simplicity (dev environment; not full prod-hardened — documented tradeoff).

**frontend image** (built in CI with these baked in):
- `NEXT_PUBLIC_AUTH_MODE=keycloak`, `NEXT_PUBLIC_GATEWAY_URL=https://bff-dev.heykazi.com`.
- Server-side runtime: `BACKEND_URL=http://backend:8080`, `INTERNAL_API_KEY`.

**portal image** (built in CI): `NEXT_PUBLIC_PORTAL_API_URL=https://api-dev.heykazi.com`.

### 3.3 Caddy
- One block per hostname, `reverse_proxy` to the internal container; auto-HTTPS via Let's Encrypt.
- `mail-dev` wrapped in `basicauth`.
- Pass `X-Forwarded-Proto/Host` (Caddy default) for Keycloak's `xforwarded` handling.

---

## 4. Laptop access (admin)

- **Mailpit UI:** browse `https://mail-dev.heykazi.com` (TLS + basic-auth) — no tunnel needed.
- **Postgres:** Postgres binds `127.0.0.1:5432` on the host (UFW blocks it externally). From the
  laptop: `ssh -L 15432:localhost:5432 you@box`, then connect a client to `localhost:15432`.
- **MinIO console:** same pattern, `ssh -L 19001:localhost:9001 you@box` → `localhost:19001`.

Caddy is not used for Postgres (not HTTP); SSH tunnels are the mechanism. No extra firewall holes.

---

## 5. Claude Code → MCP server

The MCP server (`backend/.../mcp/`, `spring-ai-starter-mcp-server-webmvc`) is **Streamable HTTP**
(`spring.ai.mcp.server.protocol: STREAMABLE`) at `/mcp`, OAuth-protected by design:
`SecurityConfig` keeps `/.well-known/oauth-protected-resource` public (RFC 9728) and `/mcp`
`.authenticated()` through the tenant/member/capability filter chain. This is exactly what Claude
Code's remote-MCP OAuth flow consumes.

**Connect:** `claude mcp add --transport http kazi https://api-dev.heykazi.com/mcp`
Claude fetches the public metadata → discovers Keycloak → runs authorization-code/PKCE in the
browser → connects with a tenant-scoped token; the capability guard enforces read-only tool access.

**Requirements (config, not new infra):**
1. `KAZI_MCP_RESOURCE_URL=https://api-dev.heykazi.com/mcp` so the metadata advertises the public
   resource (`McpProtectedResourceMetadataController` reads this; default is `localhost:8080/mcp`).
   Issuer already = `https://auth-dev.heykazi.com/realms/docteams`.
2. Backend `/mcp` + `/.well-known/...` publicly reachable — satisfied by the `api-dev` vhost (no 8th subdomain).
3. **Keycloak must allow Claude as an OAuth client** — enable Dynamic Client Registration on the
   realm OR pre-register a public PKCE client. **(Verify during planning.)**
4. Token **audience/resource** (RFC 8707) must match what the resource server expects. **(Verify.)**

---

## 6. Image pipeline

GitHub Actions builds all 4 images **with dev-env build args** (the `NEXT_PUBLIC_*` values bake into
the Next bundles), pushes to GHCR (`ghcr.io/<owner>/kazi-*:dev`). A deploy job SSHes to the box and
runs `docker compose pull && docker compose up -d`. The box only pulls — protects its RAM from the
Maven build.

---

## 7. Sizing & cost

- **Hostinger KVM 4 (16 GB / 4 vCPU / 200 GB NVMe)**, ~$10–15/mo. Working set ≈ 7–8 GB (two app
  JVMs + Keycloak + two Node apps + Postgres + MinIO); 16 GB covers Keycloak's boot spike + pulls.
- 8 GB / 4 vCPU is the floor (add 4 GB swap); 16 GB recommended.
- Everything else (Caddy TLS, MinIO, Postgres, Mailpit) on-box and free.
- **All-in ≈ the VPS price** vs. $155–205/mo for ECS staging.

---

## 8. Security checklist

- UFW: allow only `22/80/443`.
- No host port mappings except Caddy (public) and Postgres/MinIO-console (loopback-only).
- SSH key-only, password auth disabled, fail2ban.
- Secrets in `/opt/kazi/.env` (chmod 600, not in git) — or Docker secrets.
- Keycloak `/admin` IP-allowlisted in Caddy (or not publicly routed).
- Nightly `pg_dump` → a MinIO bucket (or off-box) — DR for the dev environment.

---

## 9. Deliverables (implementation)

1. `compose/docker-compose.prod.yml`
2. `compose/caddy/Caddyfile`
3. `compose/.env.prod.example` (full env matrix, secrets templated)
4. GitHub Actions deploy workflow (build → GHCR → SSH pull)
5. `DEPLOY-VPS.md` runbook: provision box → UFW/SSH hardening → DNS records → first boot →
   Keycloak realm redirect-URI + MCP-client patch → MinIO bucket → smoke test → MCP connect.

---

## 10. Open items (resolve during planning — not blockers)

1. **Gateway jdbc-session override** — confirm the exact profile/env mechanism that yields secure
   cookies + jdbc store without the Redis-bound `production` profile, and that the `SPRING_SESSION`
   schema auto-initialises on the gateway's datasource.
2. **`frontend/lib/portal-api.ts`** `NEXT_PUBLIC_BACKEND_URL` — confirm browser- vs server-side; if
   browser-side it needs a public route (likely `api-dev`).
3. **Keycloak OAuth client for Claude** — DCR vs pre-registered public PKCE client; realm export change.
4. **MCP token audience/resource** (RFC 8707) alignment between Keycloak and the resource server.
5. **Docs site** (`docs.heykazi.com` referenced by the frontend) — out of scope; links point at the
   existing prod docs, or add a vhost later.
6. **Keycloak `start-dev` vs `start --optimized`** — keeping `start-dev`; revisit if this dev env
   starts taking real traffic.
