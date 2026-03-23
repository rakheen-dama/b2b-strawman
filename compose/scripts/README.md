# Compose Scripts

Helper scripts for managing the dev and E2E Docker Compose stacks. Both stacks can run simultaneously without interference.

## Dev Stack

Infrastructure for local development (Postgres, LocalStack, Mailpit). Backend and frontend are typically run outside Docker for hot-reload.

```bash
bash compose/scripts/dev-up.sh              # Start infrastructure
bash compose/scripts/dev-up.sh --all        # Start infrastructure + backend container
bash compose/scripts/dev-down.sh            # Stop (preserve data)
bash compose/scripts/dev-down.sh --clean    # Stop + wipe all volumes (fresh Postgres & S3)
bash compose/scripts/dev-rebuild.sh         # Rebuild backend (default)
bash compose/scripts/dev-rebuild.sh backend mailpit  # Rebuild specific services
```

| Service | Port |
|---------|------|
| Postgres | 5432 |
| LocalStack (S3) | 4566 |
| Mailpit SMTP | 1025 |
| Mailpit UI | [http://localhost:8025](http://localhost:8025) |
| Backend (with `--all`) | 8080 |

## Keycloak E2E Stack (Primary — Phase 54+)

Full Keycloak dev stack for Playwright E2E tests. This is the primary test target.
Builds all services from source.

```bash
bash compose/scripts/dev-e2e-up.sh              # Build + start all services + Keycloak bootstrap
bash compose/scripts/dev-e2e-up.sh --clean      # Wipe volumes first, then start
bash compose/scripts/dev-e2e-down.sh            # Tear down + wipe volumes
bash compose/scripts/dev-seed-tenant.sh         # Seed acme-corp tenant for migrated tests
```

| Service | Port |
|---------|------|
| Frontend | 3000 |
| Gateway (BFF) | 8443 |
| Backend | 8080 |
| Keycloak | 8180 (admin/admin) |
| Mailpit SMTP | 1025 |
| Mailpit UI | [http://localhost:8025](http://localhost:8025) |

**Seed users (after `dev-seed-tenant.sh`):**
- `padmin@docteams.local` / `password` (platform admin)
- `alice@example.com` / `password` (owner — acme-corp)
- `bob@example.com` / `password` (admin — acme-corp)
- `carol@example.com` / `password` (member — acme-corp)

**Run tests:**
```bash
cd frontend && npx playwright test e2e/tests/keycloak/
```

## E2E Stack (Mock Auth — Deprecated)

> **Deprecated** (Phase 54, 2026-03): Use the Keycloak E2E stack above for new tests.
> Mock auth stack retained for reference. Do not write new tests targeting port 3001.

Full mock-auth stack for Playwright testing and agent UI navigation. Builds from current source.

```bash
bash compose/scripts/e2e-up.sh              # Build + start all services (~3-5 min first time)
bash compose/scripts/e2e-down.sh            # Tear down + wipe volumes
bash compose/scripts/e2e-rebuild.sh backend # Rebuild specific service(s)
bash compose/scripts/e2e-reseed.sh          # Re-run seed without rebuild
```

| Service | Port |
|---------|------|
| Postgres | 5433 |
| LocalStack (S3) | 4567 |
| Mailpit SMTP | 1026 |
| Mailpit UI | [http://localhost:8026](http://localhost:8026) |
| Mock IDP | 8090 |
| Backend | 8081 |
| Frontend | 3001 |

**Mock login:** [http://localhost:3001/mock-login](http://localhost:3001/mock-login) (Alice=owner, Bob=admin, Carol=member)

## Common Workflows

**Fresh start (wipe everything):**
```bash
bash compose/scripts/dev-down.sh --clean && bash compose/scripts/dev-up.sh
```

**Rebuild backend after code changes (dev):**
```bash
bash compose/scripts/dev-rebuild.sh
```

**Rebuild frontend + backend for E2E:**
```bash
bash compose/scripts/e2e-rebuild.sh backend frontend
```

**Seed acme-corp tenant for Keycloak E2E tests:**
```bash
bash compose/scripts/dev-seed-tenant.sh
```

**View captured emails:**
- Dev: [http://localhost:8025](http://localhost:8025)
- E2E: [http://localhost:8026](http://localhost:8026)
