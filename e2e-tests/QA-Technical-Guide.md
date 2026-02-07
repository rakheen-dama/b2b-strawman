# DocTeams — QA Technical Guide

Technical reference for test infrastructure, environment setup, data management, and tooling for the DocTeams E2E test suite.

Companion to [TestScenarios.md](./TestScenarios.md) which documents *what* to test. This document covers *how* to test it.

---

## Table of Contents

- [Environment Setup](#environment-setup)
- [Authentication Strategy](#authentication-strategy)
- [Test Data Management](#test-data-management)
- [Org Cleanup Script Specification](#org-cleanup-script-specification)
- [Playwright Project Structure](#playwright-project-structure)
- [CI Integration](#ci-integration)

---

## Environment Setup

### Required Services

| Service | Local Address | Purpose |
|---------|--------------|---------|
| PostgreSQL 16 | `b2mash.local:5432` | Database (db: `app`, user: `postgres`, password: `changeme`) |
| LocalStack S3 | `b2mash.local:4566` | S3 emulation (bucket: `docteams-dev`, region: `us-east-1`) |
| Next.js Frontend | `http://localhost:3000` | UI under test |
| Spring Boot Backend | `http://localhost:8080` | API under test |
| Clerk Dev Instance | Clerk dashboard | Auth provider (external) |

Start local infra:
```bash
# From compose/
docker compose up -d

# From frontend/
pnpm dev

# From backend/
./mvnw spring-boot:run
# or with Testcontainers (no Docker Compose needed):
./mvnw spring-boot:test-run
```

### E2E Environment Variables

The `e2e-tests/` module needs its own `.env` file with connection details for Postgres, S3, Clerk, and the backend API. These are used by cleanup scripts and test helpers — not by the application itself.

```bash
# e2e-tests/.env

# Postgres (direct connection for cleanup scripts)
POSTGRES_HOST=b2mash.local
POSTGRES_PORT=5432
POSTGRES_DB=app
POSTGRES_USER=postgres
POSTGRES_PASSWORD=changeme

# LocalStack S3 (for cleanup scripts)
AWS_ENDPOINT=http://b2mash.local:4566
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
S3_BUCKET_NAME=docteams-dev

# Clerk (optional — only needed for --include-clerk flag or test user setup)
CLERK_SECRET_KEY=sk_test_...

# Backend API
BACKEND_URL=http://localhost:8080
INTERNAL_API_KEY=local-dev-api-key-change-in-production
```

---

## Authentication Strategy

### The Clerk CAPTCHA Problem

Clerk sign-up/sign-in pages use **Cloudflare Turnstile CAPTCHA**, which headless browsers cannot complete. This is the single biggest constraint on E2E test design.

### Bypass Strategies

| Strategy | How It Works | Best For |
|----------|-------------|----------|
| **Clerk Testing Tokens** | Clerk provides a `__clerk_testing_token` mechanism that bypasses CAPTCHA in development instances | UI tests that need authenticated sessions |
| **Pre-seeded Users + Saved Auth State** | Create test users via Clerk Backend API in `globalSetup`, authenticate via Clerk Frontend API, persist `storageState` to disk | Reusing auth across many UI test files |
| **Direct JWT from Clerk Backend API** | Obtain a valid JWT via `clerkClient.sessions.getToken()` and pass as `Authorization: Bearer` header | API-only tests (no browser needed) |
| **`storageState` Reuse** | Authenticate once, save cookies/localStorage, load in subsequent tests via Playwright `storageState` option | All UI tests after initial auth |

### Test User Personas

Pre-create these users in the Clerk dev instance (manually or via Backend API in `globalSetup`):

| Persona | Email | Clerk Role in Org A | Clerk Role in Org B | Purpose |
|---------|-------|--------------------|--------------------|---------|
| Owner | `owner@test.docteams.io` | `org:owner` | — | Full control, delete projects |
| Admin | `admin@test.docteams.io` | `org:admin` | — | Create/edit projects, invite members |
| Member | `member@test.docteams.io` | `org:member` | — | Read-only, upload docs |
| Outsider | `outsider@test.docteams.io` | — | `org:member` | Cross-tenant isolation tests |

### Auth State File Convention

```
e2e-tests/
└── .auth/                     # gitignored
    ├── owner.json             # storageState for owner persona
    ├── admin.json             # storageState for admin persona
    └── member.json            # storageState for member persona
```

---

## Test Data Management

### Constraints

| Constraint | Impact | Mitigation |
|------------|--------|------------|
| No `DELETE org` API | Orgs and tenant schemas accumulate | Cleanup script (see below) |
| Clerk dev org limits | Cannot create unlimited orgs per test run | Fixture orgs (2–3, never deleted) |
| No S3 cleanup API | Uploaded test files persist in LocalStack | S3 wipe in global teardown |
| `processed_webhooks` dedup | Blocks re-running same webhook events | Truncate in global teardown |

### Fixture Organizations

Instead of creating Clerk orgs per test, pre-provision **2–3 long-lived orgs** that persist permanently:

| Fixture | Clerk Org Slug | Purpose | Users |
|---------|---------------|---------|-------|
| **Org A** (primary) | `test-org-alpha` | All CRUD, RBAC, dashboard, upload tests | owner, admin, member |
| **Org B** (isolation) | `test-org-beta` | Tenant isolation tests (Section 12 of TestScenarios) | outsider |
| **Org C** (switching) | `test-org-gamma` | Org switching tests (optional) | owner |

These are created once (manually or in a bootstrap script) and never deleted during normal test runs. Only 2–3 Clerk org slots are ever consumed.

### Per-Test Cleanup Pattern

Most tests create projects and documents within fixture orgs. These are cleaned up via the existing API:

```
DELETE /api/projects/{id}  →  cascades to documents (DB ON DELETE CASCADE)
```

Tests should clean up in `afterEach` or `afterAll`. S3 objects from deleted documents become orphans — cleaned separately in global teardown.

### Cleanup Hierarchy

| Layer | Created By | Cleaned By | When |
|-------|-----------|------------|------|
| Fixture orgs | Bootstrap script | Never (permanent) | — |
| Projects + documents | Individual tests | `afterEach` via `DELETE /api/projects/{id}` | Per-test |
| Orphaned S3 objects | Upload tests | Global teardown (S3 wipe) | Per-suite |
| Webhook dedup entries | Webhook tests | Global teardown (truncate) | Per-suite |
| Provisioning test orgs | Provisioning tests only | Cleanup script (see below) | Per-test |

---

## Org Cleanup Script Specification

### Purpose

A standalone script to fully delete an organization and all its associated data (S3 objects, tenant schema, global DB rows, optionally the Clerk org itself). Usable from CLI for manual testing and importable as a module for E2E test teardown.

### Dependencies

| Package | Purpose |
|---------|---------|
| `pg` + `@types/pg` | Direct Postgres access (schema drop, row deletion) |
| `@aws-sdk/client-s3` | S3 list and batch delete |
| `@clerk/backend` | Clerk org deletion API |
| `dotenv` | Load `.env` file |
| `tsx` (dev) | Run TypeScript scripts without compilation |

### CLI Interface

```bash
# Delete single org (DB + S3 only, Clerk org preserved)
pnpm delete-org org_2abc123

# Delete single org including Clerk org
pnpm delete-org org_2abc123 --include-clerk

# Delete all orgs (prompts for confirmation)
pnpm delete-org --all

# Dry run — show what would be deleted without executing
pnpm delete-org org_2abc123 --dry-run
```

### Module Interface

```typescript
import { deleteOrg } from '../scripts/lib/cleanup';

// In Playwright test teardown
await deleteOrg('org_2abc123', { includeClerk: false });

// Delete all orgs
await deleteAllOrgs({ includeClerk: true });
```

### Cleanup Steps (Executed in Order)

For a given Clerk org ID (e.g., `org_2abc123`):

```
Step 1: Look up schema name
   └─ SELECT schema_name FROM public.org_schema_mapping WHERE clerk_org_id = $1
      (Do NOT re-implement the Java UUID hash — query the DB as source of truth)

Step 2: Delete S3 objects
   └─ List all objects with prefix: org/{clerkOrgId}/
   └─ Batch delete (S3 DeleteObjects, handles pagination for >1000 objects)
   └─ Bucket: docteams-dev (from env)

Step 3: Drop tenant schema
   └─ DROP SCHEMA IF EXISTS "tenant_<hash>" CASCADE
      (CASCADE drops projects + documents tables and all data)

Step 4: Delete global DB rows (in a transaction)
   └─ DELETE FROM public.org_schema_mapping WHERE clerk_org_id = $1
   └─ DELETE FROM public.organizations WHERE clerk_org_id = $1

Step 5: Delete Clerk org (only if --include-clerk)
   └─ clerkClient.organizations.deleteOrganization(orgId)
   └─ Treat 404 (already deleted) as success
```

### Error Handling

Each step is independent — wrapped in its own try/catch. If S3 is down, DB cleanup still runs. Each step returns a result:

```typescript
interface CleanupResult {
  step: string;       // e.g., "s3", "schema", "db-rows", "clerk"
  success: boolean;
  detail?: string;    // e.g., "Deleted 12 S3 objects", "Dropped schema tenant_abc123def456"
  error?: string;     // Error message if failed
}
```

The CLI prints a summary table after execution:

```
Cleanup results for org_2abc123:
  ✓ S3 objects     Deleted 12 objects from org/org_2abc123/
  ✓ Tenant schema  Dropped tenant_a1b2c3d4e5f6
  ✓ DB rows        Removed 1 row from org_schema_mapping, 1 from organizations
  ⊘ Clerk org      Skipped (use --include-clerk to delete)
```

### Safety Features

| Feature | Description |
|---------|-------------|
| Org ID validation | Must match `org_` prefix pattern |
| `--all` confirmation | Prompts "Delete ALL N orgs? (y/N)" via stdin |
| `--dry-run` mode | Queries and prints what would be deleted without executing |
| Idempotent | Safe to run multiple times — `IF EXISTS`, `ON CONFLICT` patterns |
| Independent steps | Partial failure doesn't block remaining cleanup |

### What the Script Does NOT Clean

| Resource | Reason |
|----------|--------|
| `processed_webhooks` | No FK to orgs — just a dedup cache. Truncate separately if needed. |
| Clerk user accounts | Users can belong to multiple orgs. Never auto-delete users. |
| Other orgs' data | Strict equality match on `clerk_org_id`, no LIKE queries. |

### File Structure

```
e2e-tests/
├── scripts/
│   ├── delete-org.ts          # CLI entry point (arg parsing, formatting, stdin prompts)
│   └── lib/
│       └── cleanup.ts         # Core logic: deleteOrg(), deleteAllOrgs() exports
├── .env.example               # Template with all required vars
├── tsconfig.json              # TypeScript config for scripts + tests
└── package.json               # Updated with deps + "delete-org" script
```

Two logic files. No over-abstraction — S3/DB/Clerk logic lives together in `cleanup.ts` since they share the same lifecycle.

---

## Playwright Project Structure

Organize tests into Playwright projects with explicit dependency ordering:

```
projects: [
  { name: 'setup',        testMatch: /global\.setup\.ts/     }
  { name: 'api',          testDir: './tests/api',          dependencies: ['setup'] }
  { name: 'ui-chromium',  testDir: './tests/ui',           dependencies: ['setup'] }
  { name: 'provisioning', testDir: './tests/provisioning', dependencies: ['setup'] }
  { name: 'teardown',     testMatch: /global\.teardown\.ts/, dependencies: ['api', 'ui-chromium', 'provisioning'] }
]
```

| Project | Purpose | Runs When |
|---------|---------|-----------|
| **setup** | Bootstrap fixture orgs (idempotent), authenticate test users, save `storageState` | First, always |
| **api** | API-level tests (direct HTTP, no browser) | After setup |
| **ui-chromium** | UI tests in Chrome | After setup |
| **provisioning** | Org creation/deletion tests (isolated, uses cleanup script) | After setup |
| **teardown** | Truncate tenant tables, wipe S3 orphans, reset webhook dedup | Last, always |

### Test Directory Layout

```
e2e-tests/
├── tests/
│   ├── api/
│   │   ├── projects.spec.ts         # PROJ-API-* scenarios
│   │   ├── documents.spec.ts        # DOC-API-* scenarios
│   │   ├── rbac.spec.ts             # RBAC-API-* scenarios
│   │   └── tenant-isolation.spec.ts # ISO-API-* scenarios
│   ├── ui/
│   │   ├── auth.spec.ts             # AUTH-UI-* scenarios
│   │   ├── dashboard.spec.ts        # DASH-UI-* scenarios
│   │   ├── projects.spec.ts         # PROJ-UI-* scenarios
│   │   ├── documents.spec.ts        # DOC-UI-* scenarios
│   │   ├── team.spec.ts             # TEAM-UI-* scenarios
│   │   ├── navigation.spec.ts       # NAV-UI-* scenarios
│   │   └── responsive.spec.ts       # RESP-* scenarios
│   ├── provisioning/
│   │   └── provisioning.spec.ts     # PROV-* scenarios
│   ├── global.setup.ts
│   └── global.teardown.ts
├── helpers/
│   ├── api-client.ts                # Typed HTTP client with auth
│   └── factories.ts                 # Test data factory (createProject, uploadDocument)
└── scripts/
    ├── delete-org.ts
    └── lib/
        └── cleanup.ts
```

### Test Data Factories

Lightweight helpers to reduce boilerplate in test files:

```
createProject(overrides?)     → POST /api/projects, returns { id, cleanup() }
uploadDocument(projectId)     → Full 3-phase upload, returns { id, cleanup() }
getAuthToken(persona)         → Cached JWT for owner/admin/member
```

The `cleanup()` handle ensures resources are deleted even if the test fails mid-execution (called in `finally` block or Playwright fixture teardown).

---

## CI Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/e2e.yml
name: E2E Tests

on:
  pull_request:
  push:
    branches: [main]

jobs:
  e2e:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: app
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: changeme
        ports: ['5432:5432']
      localstack:
        image: localstack/localstack
        env:
          SERVICES: s3
        ports: ['4566:4566']

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
      - uses: actions/setup-java@v4

      # Start backend (Testcontainers not needed — Postgres service above)
      - run: ./backend/mvnw -f backend/pom.xml spring-boot:start

      # Start frontend
      - run: pnpm --dir frontend build && pnpm --dir frontend start &

      # Install browsers + run tests
      - run: pnpm --dir e2e-tests install
      - run: npx --prefix e2e-tests playwright install --with-deps chromium
      - run: pnpm --dir e2e-tests test

      # Upload report on failure
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: playwright-report
          path: e2e-tests/playwright-report/
```

### CI-Specific Considerations

| Concern | CI Approach |
|---------|-------------|
| Clerk auth | Use Clerk Testing Tokens (no real user accounts needed) |
| Org fixtures | Pre-provision in `globalSetup` against CI Postgres (ephemeral, no limits) |
| Cleanup | Not needed — CI database is ephemeral (destroyed after job) |
| Parallelism | `workers: 1` in CI (Playwright config already does this via `process.env.CI`) |
| Browser | Chromium only in CI (skip Firefox/WebKit to save time) |
| Flakiness | 2 retries on CI (Playwright config already does this via `process.env.CI`) |
