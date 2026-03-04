# Keycloak Gateway Operational Runbook

This runbook covers setup, configuration, and troubleshooting for all three authentication modes supported by DocTeams.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Mode 1: Clerk (Default)](#2-mode-1-clerk-default)
3. [Mode 2: Keycloak BFF](#3-mode-2-keycloak-bff)
4. [Mode 3: Mock E2E](#4-mode-3-mock-e2e)
5. [Switching Between Modes](#5-switching-between-modes)
6. [Keycloak Administration](#6-keycloak-administration)
7. [Troubleshooting](#7-troubleshooting)
8. [Health Checks](#8-health-checks)

---

## 1. Architecture Overview

DocTeams supports three authentication modes, selected entirely by environment variables. No code changes are required to switch between modes.

| Mode | Frontend Env | Backend Profile | Identity Provider | Token Flow |
|---|---|---|---|---|
| **Clerk** | `NEXT_PUBLIC_AUTH_MODE=clerk` | `SPRING_PROFILES_ACTIVE=local` | Clerk SaaS | Browser -> Clerk JWT -> Next.js -> Bearer JWT -> Backend |
| **Keycloak** | `NEXT_PUBLIC_AUTH_MODE=keycloak` | `SPRING_PROFILES_ACTIVE=keycloak` | Keycloak 26.5 (self-hosted) | Browser -> SESSION cookie -> Next.js -> Gateway -> Bearer JWT -> Backend |
| **Mock** | `NEXT_PUBLIC_AUTH_MODE=mock` | `SPRING_PROFILES_ACTIVE=e2e` | Mock IDP (Node.js container) | Browser -> mock-auth-token cookie -> Next.js -> Bearer JWT -> Backend |

**Key design principle**: The `NEXT_PUBLIC_AUTH_MODE` variable is a **build-time** variable in Next.js (prefixed with `NEXT_PUBLIC_`). It must be set before building the frontend Docker image or running `pnpm dev`. It is tree-shakeable -- unused auth provider code is eliminated at build time.

---

## 2. Mode 1: Clerk (Default)

### Environment Variables

#### Frontend

| Variable | Required | Example |
|---|---|---|
| `NEXT_PUBLIC_AUTH_MODE` | No (defaults to `clerk`) | `clerk` |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Yes | `pk_test_...` |
| `CLERK_SECRET_KEY` | Yes (server-side) | `sk_test_...` |
| `CLERK_WEBHOOK_SIGNING_SECRET` | Yes (server-side) | `whsec_...` |
| `BACKEND_URL` | Yes (server-side) | `http://localhost:8080` |
| `INTERNAL_API_KEY` | Yes (server-side) | `local-dev-api-key-change-in-production` |

#### Backend

| Variable | Required | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `local` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Yes | `https://crucial-sturgeon-38.clerk.accounts.dev` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Yes | `https://crucial-sturgeon-38.clerk.accounts.dev/.well-known/jwks.json` |
| `INTERNAL_API_KEY` | Yes | `local-dev-api-key-change-in-production` |

### Startup Order

1. **Start infrastructure**:
   ```bash
   bash compose/scripts/dev-up.sh
   ```
   Services started: PostgreSQL, LocalStack, Mailpit

2. **Start backend**:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   Runs on port 8080. Uses `application-local.yml` (Clerk JWKS endpoints).

3. **Start frontend**:
   ```bash
   cd frontend
   NODE_OPTIONS="" pnpm dev
   ```
   Runs on port 3000. Reads `.env.local` for Clerk keys.

### Tenant Provisioning

In Clerk mode, tenants are provisioned via **Clerk webhooks**:
- `organization.created` webhook -> calls `POST /internal/orgs/provision`
- `organizationMembership.created` webhook -> syncs member

JIT provisioning is **disabled** (`app.jit-provisioning.enabled=false` in the `local` profile). If an org is not yet provisioned, API requests return 403.

### Testing

Backend tests use `@ActiveProfiles("test")` with Testcontainers -- they do NOT connect to Clerk or any external service. Frontend Vitest tests mock the Clerk SDK.

```bash
# Backend
cd backend && ./mvnw clean verify -q

# Frontend
cd frontend && NODE_OPTIONS="" pnpm test
```

---

## 3. Mode 2: Keycloak BFF

### Environment Variables

#### Frontend

| Variable | Required | Example |
|---|---|---|
| `NEXT_PUBLIC_AUTH_MODE` | Yes | `keycloak` |
| `NEXT_PUBLIC_GATEWAY_URL` | Yes | `http://localhost:8443` |
| `GATEWAY_URL` | Yes (server-side) | `http://localhost:8443` |

Note: Clerk variables (`NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, etc.) are not needed and are ignored when `AUTH_MODE=keycloak`.

#### Backend

| Variable | Required | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `keycloak` |
| `KEYCLOAK_ISSUER` | Yes | `http://localhost:8180/realms/docteams` |
| `KEYCLOAK_JWKS_URI` | Yes | `http://localhost:8180/realms/docteams/protocol/openid-connect/certs` |

#### Gateway

| Variable | Required | Example |
|---|---|---|
| `KEYCLOAK_ISSUER` | Yes | `http://localhost:8180/realms/docteams` |
| `KEYCLOAK_CLIENT_ID` | Yes | `gateway-bff` |
| `KEYCLOAK_CLIENT_SECRET` | Yes | (generated, see below) |
| `KEYCLOAK_ADMIN_URL` | Yes | `http://localhost:8180` |
| `DB_HOST` | Yes | `localhost` or `postgres` (Docker) |
| `DB_PORT` | Yes | `5432` |
| `DB_NAME` | Yes | `app` |
| `DB_USER` | Yes | `postgres` |
| `DB_PASSWORD` | Yes | `changeme` |

### Keycloak Client Secret Generation

1. Start the dev stack: `bash compose/scripts/dev-up.sh`
2. Open Keycloak Admin Console: `http://localhost:8180/admin/master/console/`
3. Login with `admin` / `admin` (dev defaults)
4. Navigate to: `docteams` realm -> Clients -> `gateway-bff` -> Credentials tab
5. Copy the Client Secret
6. Set the environment variable:
   ```bash
   export KEYCLOAK_CLIENT_SECRET=<copied-value>
   ```
7. Alternatively, add to a `.env` file loaded by Docker Compose

### Keycloak Realm Import

The realm is auto-imported on first Keycloak start via volume mount:

```yaml
command: start-dev --import-realm --features=scripts
volumes:
  - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
```

Import is idempotent -- if the `docteams` realm already exists, import is skipped silently.

#### Realm Contents

- Realm name: `docteams`
- Organizations: enabled
- Registration: allowed
- Email verification: enabled (disabled for dev)
- Client: `gateway-bff` (confidential, standard flow)
- Redirect URIs: `http://localhost:8443/login/oauth2/code/keycloak`
- Custom organization role mapper (Script Mapper: adds `roles` array to `organization` JWT claim)

### Startup Order

Services must start in this order (health checks enforce this in Docker Compose):

1. **PostgreSQL** -- healthcheck: `pg_isready`
2. **Keycloak** (depends on PostgreSQL) -- wait for: `http://localhost:8180/realms/docteams` (up to 120s)
3. **Gateway** (depends on Keycloak) -- wait for: `http://localhost:8443/actuator/health` (up to 120s)
4. **Backend** (depends on PostgreSQL) -- `http://localhost:8080/actuator/health`
5. **Frontend** -- `http://localhost:3000`

```bash
# Start everything
bash compose/scripts/dev-up.sh

# Seed dev users
bash compose/scripts/keycloak-seed.sh
```

### Dev Data Seeding

```bash
bash compose/scripts/keycloak-seed.sh
```

Creates:
- Organization: `acme-corp`
- Users: `alice` (owner), `bob` (admin), `carol` (member)
- All passwords: `password`

### Tenant Provisioning

In Keycloak mode, JIT (Just-in-Time) provisioning is **enabled** (`app.jit-provisioning.enabled=true` in `application-keycloak.yml`). On first API request from an unknown org:

1. `TenantFilter` detects unknown `orgId` in the JWT
2. Automatically provisions a new tenant schema
3. Creates `org_schema_mapping` entry
4. Request proceeds as normal

For member sync: `MemberFilter` extracts email and name from the JWT and creates/updates the member record on each request.

---

## 4. Mode 3: Mock E2E

### Environment Variables

#### Frontend (build-time)

| Variable | Required | Example |
|---|---|---|
| `NEXT_PUBLIC_AUTH_MODE` | Yes | `mock` |
| `NEXT_PUBLIC_MOCK_IDP_URL` | Yes | `http://localhost:8090` |

#### Frontend (server-side)

| Variable | Required | Example |
|---|---|---|
| `BACKEND_URL` | Yes | `http://backend:8080` (Docker) or `http://localhost:8081` |
| `INTERNAL_API_KEY` | Yes | `e2e-test-api-key` |

#### Backend

| Variable | Required | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `e2e` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | Yes | `http://mock-idp:8090/.well-known/jwks.json` |
| `INTERNAL_API_KEY` | Yes | `e2e-test-api-key` |

### Startup / Teardown

```bash
# Start the full E2E stack (builds from source, ~3-5 min first time)
bash compose/scripts/e2e-up.sh

# Reset data without rebuilding
bash compose/scripts/e2e-reseed.sh

# Rebuild specific services
bash compose/scripts/e2e-rebuild.sh backend frontend

# Tear down and wipe all data
bash compose/scripts/e2e-down.sh
```

### Service Ports (E2E Stack)

| Service | Port | URL |
|---|---|---|
| Frontend | 3001 | `http://localhost:3001` |
| Backend | 8081 | `http://localhost:8081` |
| Mock IDP | 8090 | `http://localhost:8090` |
| PostgreSQL | 5433 | `localhost:5433` (user: postgres, pass: changeme, db: app) |
| Mailpit | 8026 | `http://localhost:8026` |

### Authentication Flow

The Mock IDP is a lightweight Node.js Express server that:
- Generates RSA keys at startup
- Exposes `/.well-known/jwks.json` for JWT signature verification
- Exposes `POST /token` to issue JWTs with configurable claims
- Exposes `GET /userinfo` for user info

Playwright fixtures authenticate by:
1. Calling `POST http://localhost:8090/token` with user claims
2. Setting the returned JWT as a `mock-auth-token` cookie
3. Next.js reads the cookie and forwards it as `Authorization: Bearer` to the backend

### Available Test Users

| User | userId | orgRole | orgSlug |
|---|---|---|---|
| Alice | `user_e2e_alice` | owner | `e2e-test-org` |
| Bob | `user_e2e_bob` | admin | `e2e-test-org` |
| Carol | `user_e2e_carol` | member | `e2e-test-org` |

### Running Playwright Tests

```bash
# After e2e-up.sh completes:
cd frontend
NODE_OPTIONS="" pnpm test:e2e
```

Smoke tests verify:
1. Mock login -> dashboard redirect
2. Owner can create a project
3. Member cannot access admin settings

---

## 5. Switching Between Modes

### Clerk -> Keycloak

1. Set frontend env vars:
   ```bash
   export NEXT_PUBLIC_AUTH_MODE=keycloak
   export NEXT_PUBLIC_GATEWAY_URL=http://localhost:8443
   export GATEWAY_URL=http://localhost:8443
   ```
2. Set backend profile:
   ```bash
   export SPRING_PROFILES_ACTIVE=keycloak
   ```
3. Ensure Keycloak and Gateway are running (via `dev-up.sh`)
4. Generate and set `KEYCLOAK_CLIENT_SECRET`
5. Rebuild frontend (required -- `NEXT_PUBLIC_*` is build-time):
   ```bash
   cd frontend && NODE_OPTIONS="" pnpm build
   ```
6. Restart backend and frontend

### Keycloak -> Clerk

1. Set frontend env vars back to Clerk defaults:
   ```bash
   export NEXT_PUBLIC_AUTH_MODE=clerk
   # Ensure NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY and CLERK_SECRET_KEY are set
   ```
2. Set backend profile:
   ```bash
   export SPRING_PROFILES_ACTIVE=local
   ```
3. Rebuild frontend and restart all services
4. Keycloak and Gateway can remain running (they are simply unused) or be stopped

### Any Mode -> Mock E2E

Use the dedicated E2E stack scripts. The E2E stack runs on separate ports (3001, 8081, 8090, 5433) and does not conflict with the dev stack.

```bash
bash compose/scripts/e2e-up.sh    # Start
bash compose/scripts/e2e-down.sh  # Stop
```

### Important Notes

- `NEXT_PUBLIC_AUTH_MODE` is a **build-time** variable. Changing it requires rebuilding the frontend (`pnpm build` or Docker rebuild). Simply restarting the dev server is NOT sufficient for production builds.
- In `pnpm dev` mode, Next.js re-reads env vars on restart, so a restart suffices for local development.
- No database migrations are mode-specific. The same schema works for all three modes.
- No code changes are required to switch modes.

---

## 6. Keycloak Administration

### Admin Console

- URL: `http://localhost:8180/admin/master/console/`
- Credentials: `admin` / `admin` (dev defaults)

### Common Admin Tasks

#### Create a New Organization

1. Navigate to `docteams` realm -> Organizations
2. Click "Create organization"
3. Set name and alias (slug)
4. Add members via the Members tab

#### Assign Organization Roles

1. Navigate to Organizations -> select org -> Members
2. Select a member
3. Assign roles: `owner`, `admin`, or `member`

#### Regenerate Client Secret

1. Navigate to Clients -> `gateway-bff` -> Credentials
2. Click "Regenerate" on the Client Secret
3. Update the `KEYCLOAK_CLIENT_SECRET` env var and restart Gateway

#### View Active Sessions

1. Navigate to Sessions (realm-level) to see all active sessions
2. Or navigate to Users -> select user -> Sessions tab

#### Export Realm Configuration

```bash
# From the Keycloak container
docker exec -it keycloak /opt/keycloak/bin/kc.sh export \
  --realm docteams \
  --file /tmp/realm-export.json \
  --users realm_file
```

---

## 7. Troubleshooting

### 7.1 Clerk Mode Issues

#### "No auth token available -- user may not be authenticated"

- **Cause**: User not logged in via Clerk, or Clerk dev instance is misconfigured
- **Fix**: Verify `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` matches the Clerk dev instance
- **Check**: The configured issuer in `application-local.yml` should match `https://crucial-sturgeon-38.clerk.accounts.dev`

#### "Organization not provisioned" (403 on API calls)

- **Cause**: Org exists in Clerk but `org_schema_mapping` table has no matching entry
- **Fix**: Trigger provisioning via Clerk webhook, or manually call `POST /internal/orgs/provision` with the internal API key
- **Note**: JIT provisioning is disabled by default in Clerk mode

#### Backend JWT validation fails in tests

- **Cause**: Testcontainers may cache a stale `docker.host` in `~/.testcontainers.properties`
- **Fix**: Remove stale entries from `~/.testcontainers.properties`; unset `DOCKER_HOST` env var
- **Note**: Tests use `@ActiveProfiles("test")` with a fake issuer URI -- never use `@ActiveProfiles("local")`

### 7.2 Keycloak Mode Issues

#### Gateway cannot connect to Keycloak

- **Symptom**: Gateway fails to start or returns 502
- **Fix**: Ensure Keycloak health check passes before Gateway starts
- **Check**: `curl http://localhost:8180/realms/docteams` should return realm metadata JSON
- **Docker**: Verify the `depends_on` + `healthcheck` configuration in `docker-compose.yml`

#### SESSION cookie redirect loop

- **Symptom**: Browser keeps redirecting to Keycloak login, then back, in a loop
- **Cause**: Gateway is not setting the SESSION cookie after OAuth2 login
- **Fix 1**: Verify `KC_HOSTNAME=localhost` in Keycloak env (redirect URIs must match the hostname)
- **Fix 2**: Verify `KEYCLOAK_CLIENT_SECRET` is correct and matches the Keycloak admin console
- **Fix 3**: Check browser DevTools -> Application -> Cookies for a `SESSION` cookie

#### CSRF token mismatch on mutations (POST/PUT/DELETE)

- **Symptom**: 403 Forbidden on mutation requests, with "CSRF token mismatch" in Gateway logs
- **Cause**: `XSRF-TOKEN` cookie not present, or not forwarded by Next.js server component
- **Fix 1**: Verify `CookieCsrfTokenRepository.withHttpOnlyFalse()` is configured in Gateway's `SecurityConfig`
- **Fix 2**: Verify `api.ts` forwards the `X-XSRF-TOKEN` header for non-GET requests
- **Fix 3**: Check that the frontend does a GET request first (which sets the CSRF cookie) before any mutations

#### JIT provisioning not triggering

- **Symptom**: 403 "Organization not provisioned" even in Keycloak mode
- **Cause**: `app.jit-provisioning.enabled` is `false`
- **Fix**: Verify `application-keycloak.yml` has `app.jit-provisioning.enabled: true`
- **Check**: `SPRING_PROFILES_ACTIVE` includes `keycloak`

#### Keycloak JWT claims not recognized by backend

- **Symptom**: `ClerkJwtUtils.extractOrgId()` returns null
- **Cause**: Keycloak JWT uses `organization` claim structure, not the Clerk `o` claim
- **Fix**: Ensure `JwtClaimExtractor` interface is implemented and `@Profile("keycloak")` is on `KeycloakJwtClaimExtractor`
- **Check**: Decode a Keycloak JWT at jwt.io and verify the `organization` claim structure

### 7.3 Mock E2E Mode Issues

#### E2E stack will not start

- **Check**: Docker is running (`docker ps`)
- **Check**: Port conflicts (`lsof -i :3001 -i :8081 -i :8090 -i :5433`)
- **Check**: Logs (`docker compose -f compose/docker-compose.e2e.yml logs -f backend`)
- **Fix**: Run `bash compose/scripts/e2e-down.sh` first, then retry

#### Playwright smoke tests fail on login

- **Cause**: Mock IDP is not returning a valid JWT, or cookie is not set
- **Check**: `curl http://localhost:8090/.well-known/jwks.json` returns a JWKS response
- **Check**: `curl -X POST http://localhost:8090/token -H 'Content-Type: application/json' -d '{"userId":"test"}'` returns a JWT
- **Fix**: Restart Mock IDP container: `docker compose -f compose/docker-compose.e2e.yml restart mock-idp`

#### "mock-auth-token cookie not found" in backend logs

- **Cause**: Backend is running with `local` profile instead of `e2e` profile
- **Fix**: Check `SPRING_PROFILES_ACTIVE=e2e` is set on the e2e-backend container
- **Check**: E2E backend uses `jwk-set-uri: http://mock-idp:8090/.well-known/jwks.json` (not Clerk)

#### Mock JWT rejected by backend

- **Cause**: JWT issuer (`iss`) does not match what backend expects
- **Note**: The `application-e2e.yml` only sets `jwk-set-uri`, not `issuer-uri` -- Spring validates signature only
- **Fix**: Ensure Mock IDP is using RSA keys that match the JWKS endpoint response

### 7.4 General Issues

#### Port conflicts

```bash
# Check which process is using a port
lsof -i :3000  # Frontend (dev)
lsof -i :3001  # Frontend (E2E)
lsof -i :8080  # Backend (dev)
lsof -i :8081  # Backend (E2E)
lsof -i :8090  # Mock IDP
lsof -i :8180  # Keycloak
lsof -i :8443  # Gateway
lsof -i :5432  # PostgreSQL (dev)
lsof -i :5433  # PostgreSQL (E2E)
```

#### Database access

```bash
# Dev stack
psql -h localhost -p 5432 -U postgres -d app

# E2E stack
docker exec -it e2e-postgres psql -U postgres -d app

# List tenant schemas
SELECT schema_name FROM org_schema_mapping;

# Check a tenant schema
SET search_path TO 'tenant_<schema>';
\dt
```

#### Log tailing

```bash
# Dev stack
docker compose -f compose/docker-compose.yml logs -f backend
docker compose -f compose/docker-compose.yml logs -f keycloak
docker compose -f compose/docker-compose.yml logs -f gateway

# E2E stack
docker compose -f compose/docker-compose.e2e.yml logs -f backend
docker compose -f compose/docker-compose.e2e.yml logs -f frontend
```

---

## 8. Health Checks

### Dev Stack Services

| Service | Health Endpoint | Expected Response |
|---|---|---|
| Backend | `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| PostgreSQL | `pg_isready -h localhost -p 5432` | exit code 0 |
| Keycloak | `http://localhost:8180/realms/docteams` | JSON realm metadata |
| Gateway | `http://localhost:8443/actuator/health` | `{"status":"UP"}` |
| LocalStack | `http://localhost:4566/_localstack/health` | `{"services":{"s3":"running"}}` |
| Mailpit | `http://localhost:8025` | Web UI |

### E2E Stack Services

| Service | Health Endpoint | Expected Response |
|---|---|---|
| Frontend | `http://localhost:3001` | HTML page |
| Backend | `http://localhost:8081/actuator/health` | `{"status":"UP"}` |
| Mock IDP | `http://localhost:8090/.well-known/jwks.json` | JWKS JSON |
| PostgreSQL | `pg_isready -h localhost -p 5433` | exit code 0 |
