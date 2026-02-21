# ADR-087: E2E Seed Data Strategy

**Status**: Proposed

**Context**:

The E2E testing stack needs pre-populated data to exercise the full application: at minimum, a provisioned tenant with members, and ideally a customer, project, and tasks for meaningful UI testing. The data must be consistent with the current schema (35 tenant migrations as of Phase 19) and respect all validation rules, lifecycle guards, and pack seeding logic.

The backend's existing provisioning flow — triggered by Clerk webhooks in production — creates the tenant schema, runs Flyway migrations, seeds packs (fields, templates, compliance, reports), and syncs members. Integration tests already exercise this flow via `/internal/*` REST endpoints with API key authentication. The question is whether to replicate this flow for E2E seeding or use a different approach.

**Options Considered**:

1. **Boot-seed via REST endpoints (chosen)** — A one-shot container that waits for the backend to be healthy, then calls `/internal/orgs/provision`, `/internal/plans/sync`, `/internal/members/sync`, and authenticated `/api/*` endpoints to create sample data.
   - Pros: Uses the same code path as production; always consistent with current schema and validation rules; pack seeding (fields, templates, compliance, reports) happens automatically during provisioning; seed data respects lifecycle guards (e.g., customer must be ACTIVE before creating projects); exercises the real API surface.
   - Cons: Slower than SQL dump (~5-10 seconds for provisioning + Flyway + pack seeding); requires backend to be running and healthy; seed container needs to obtain mock JWT tokens for authenticated endpoints.

2. **SQL dump loaded at container start** — A `pg_dump` snapshot loaded via Docker entrypoint script.
   - Pros: Instant data availability — no waiting for backend; deterministic (exact byte-for-byte data).
   - Cons: Breaks every time a Flyway migration changes (must regenerate dump); doesn't exercise pack seeding logic (packs might be stale); doesn't respect validation rules (dump could contain invalid data if rules changed); must be manually regenerated and committed; large file in repository.

3. **Flyway afterMigrate callback** — A SQL script that runs automatically after Flyway migrations in the `e2e` profile.
   - Pros: Runs during normal backend startup; no separate container needed; data is always schema-compatible.
   - Cons: SQL inserts bypass Java validation (lifecycle guards, audit events, service logic); doesn't trigger pack seeding (packs are seeded by `TenantProvisioningService`, not Flyway); must manually construct UUIDs and foreign keys; brittle when columns are added or renamed.

4. **TestContainers-based seed** — Use the same `@BeforeAll` pattern as integration tests but in a standalone seed runner.
   - Pros: Reuses existing test utilities (`TestCustomerFactory`, `TestChecklistHelper`); Java-based, type-safe.
   - Cons: Requires a JVM to run the seed (adds ~200MB to the stack); couples seed runner to backend test classpath; integration tests already test this path — E2E should test from outside the JVM.

**Decision**: Option 1 — boot-seed via REST endpoints.

**Rationale**:

The E2E stack's purpose is to test the application as users and agents experience it — from the outside, via HTTP. Boot-seeding via REST endpoints exercises the same provisioning and data creation paths that production uses. When a new migration adds a column with `NOT NULL DEFAULT`, the boot-seed flow handles it automatically (the API applies defaults). When pack seeding logic changes (new templates, new field packs), the boot-seed gets them for free because it triggers `TenantProvisioningService.provisionTenant()`.

The 5-10 second startup overhead is acceptable — E2E stacks are brought up infrequently (once per test run or once per development session), not per-test. The seed container is a simple shell script (~50 lines of `curl` calls) with no custom logic beyond endpoint URLs and JSON payloads. It obtains mock JWT tokens from the mock IDP for authenticated endpoints (`/api/customers`, `/api/projects`), which validates the full auth flow end-to-end even during seeding.

**Consequences**:

- Positive:
  - Seed data is always consistent with current schema and validation rules
  - Pack seeding (fields, templates, compliance, reports) happens automatically
  - Lifecycle guards are respected (customers transition through proper states)
  - Exercises the real provisioning flow — catches regressions in provisioning logic
  - Simple implementation (shell script with `curl` calls)

- Negative:
  - ~5-10 second startup overhead for provisioning + Flyway + seeding
  - Seed container must wait for backend health check before starting
  - Requires mock IDP to be available for authenticated API calls

- Neutral:
  - Seed container exits after completion — no ongoing resource usage
  - Seed data persists in Postgres volume — survives frontend/backend restarts
  - Adding more seed data (invoices, time entries, documents) is a matter of adding more `curl` calls
