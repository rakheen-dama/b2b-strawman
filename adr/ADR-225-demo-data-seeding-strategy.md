# ADR-225: Demo Data Seeding Strategy

**Status**: Accepted

**Context**:

After a demo tenant is provisioned (schema created, packs seeded), it contains only configuration data — field definitions, document templates, compliance packs, rate structures, report definitions. The dashboards, project lists, time tracking views, invoicing pages, and profitability reports are all empty. An empty product does not demo well: prospects need to see realistic data to evaluate whether the platform fits their workflow.

The platform needs a mechanism to populate demo tenants with realistic transactional data: customers, projects, tasks, time entries, invoices, proposals, and related records. This data must be internally consistent (invoices must match time entries, budgets must reflect actual hours), profile-aware (an accounting firm demo should have SARS deadlines, not web design projects), and use realistic South African business context.

The question is how to produce this data: through Java service code that uses the entity layer, through raw SQL scripts, or through pre-built database snapshots.

**Options Considered**:

1. **SQL scripts per vertical profile** — Static SQL files (`seed-generic.sql`, `seed-accounting.sql`, `seed-legal.sql`) that INSERT demo records directly into the tenant schema.
   - Pros:
     - Fast execution — raw SQL is faster than service-layer inserts.
     - No dependency on Java entity classes — SQL can be tested independently.
     - Easy to review: the exact data is visible in the SQL files.
   - Cons:
     - Schema drift: when an entity gains a new NOT NULL column (e.g., `billing_rate_snapshot` added to `time_entries` in Phase 8), every SQL script must be updated manually. The Java entity's constructor or service layer would enforce the new field automatically; SQL scripts silently insert incomplete rows.
     - No validation: SQL bypasses all entity-level validation (status transitions, FK consistency checks, business rules). An invoice SQL INSERT does not verify that referenced time entries exist or that totals match line items.
     - No audit events: the existing audit infrastructure (`AuditEventBuilder`) is not triggered by raw SQL inserts. Demo tenants would have transactional records with no corresponding audit trail, which is inconsistent with how the product works.
     - Date management: SQL scripts with hardcoded dates become stale. Demo data created "3 months ago" with fixed dates eventually becomes "18 months ago." Relative date computation in SQL is verbose and error-prone.
     - UUID generation: SQL must generate UUIDs for all entities and maintain cross-references manually (e.g., invoice line items referencing time entry UUIDs). This is fragile and hard to maintain.

2. **Database snapshot per vertical profile** — Pre-built `.dump` files (pg_dump output) that are restored into the tenant schema.
   - Pros:
     - Instant — `pg_restore` is the fastest possible seeding mechanism.
     - Exact reproducibility: the same dump always produces the same data.
     - No code execution — no risk of runtime errors during seeding.
   - Cons:
     - Snapshot compatibility: every schema migration invalidates all snapshots. When Phase N adds a column, all three snapshots must be regenerated. With 84+ tenant migrations, snapshots would need regeneration frequently.
     - Snapshot generation requires a running, fully-migrated database with demo data already seeded — circular dependency on the seeding mechanism itself.
     - Binary format: `.dump` files are opaque, not reviewable in PRs, and not diffable.
     - Static dates: snapshots contain fixed timestamps. Demo data looks increasingly stale over time.
     - Schema name: pg_dump captures the schema name. Restoring into a different schema name (`tenant_abc123` vs. `tenant_def456`) requires sed-like text replacement on the dump, which is fragile.
     - No profile customization without maintaining separate dumps for every vertical.

3. **Service-based seeding through the entity layer (chosen)** — Java classes (`BaseDemoDataSeeder`, `GenericDemoDataSeeder`, etc.) that create demo records using existing repositories and services, executing within a tenant transaction context.
   - Pros:
     - Schema-resilient: when a new NOT NULL column is added to an entity, the seeder's entity construction fails at compile time (or at test time if using the entity constructor). No silent data corruption.
     - Validation-inclusive: entity constructors, service-level validations, and business rules all apply. If a time entry requires a valid task ID, the seeder's time entry references a real task — not because the developer remembered, but because the entity layer enforces it.
     - Audit-compatible: if seeding goes through service methods (optional), audit events are generated. Alternatively, seeders can use repositories directly and skip audit for performance, with a documented trade-off.
     - Relative dates: Java `Instant.now().minus(Duration.ofDays(N))` naturally produces dates relative to today. Demo data always looks fresh.
     - Profile-aware: the seeder class hierarchy (`BaseDemoDataSeeder` → profile-specific implementations) uses standard Java polymorphism. Each profile's seeder knows what data to create.
     - Testable: integration tests can run the seeder and verify data consistency (invoices match time entries, budgets match hours).
   - Cons:
     - Slower than SQL or pg_restore: service-layer inserts go through JPA, Hibernate, and connection pooling. For 150-250 time entries, this adds a few seconds.
     - More code: each seeder is a substantial Java class (200-400 lines). Three profile-specific seeders plus a base class is ~1,000 lines of seeding code.
     - Seeding order is the developer's responsibility: the class must create parents before children. A mistake causes an FK violation at runtime, not compile time.

**Decision**: Option 3 — Service-based seeding through the entity layer.

**Rationale**:

The decisive factor is schema resilience. This platform has 84+ tenant migrations across 57 phases, and the migration count will continue to grow. Every new phase that adds or modifies entities would require updating SQL scripts (Option 1) or regenerating snapshots (Option 2). Service-based seeding (Option 3) absorbs schema changes automatically: the Java compiler catches missing fields, the entity layer enforces new constraints, and the test suite verifies consistency.

The performance trade-off is acceptable. Seeding 150-250 time entries, 40-60 tasks, and 8-12 invoices through JPA takes seconds, not minutes. This is a one-time operation during demo provisioning — not a hot path. The admin clicks "Create Demo Tenant," sees a loading spinner for 10-30 seconds, and gets a fully populated demo. The absolute execution time is irrelevant as long as it fits within a single HTTP request timeout (the provisioning endpoint can use async processing with status polling if needed, but synchronous execution is expected to be sufficient).

The code volume concern (Option 3 disadvantage) is offset by the shared base class pattern. `BaseDemoDataSeeder` provides date utilities, name generation, member creation, and invoice generation helpers. Profile-specific seeders override data content (customer names, project types, rate schedules) but share the structural logic. This reduces per-seeder code to ~200-300 lines of profile-specific data definitions, not 400 lines of plumbing.

Option 1 (SQL scripts) is eliminated by the schema drift risk. A single forgotten `NOT NULL` column in a SQL INSERT silently creates an incomplete record that causes runtime errors when the entity layer tries to read it. In a platform with 92 entity classes and active development, this risk is unacceptable.

Option 2 (snapshots) is eliminated by the maintenance burden. Snapshots are binary, non-diffable, schema-version-locked, and date-fixed. They are appropriate for backup/restore scenarios, not for generating fresh demo data on demand.

**Consequences**:

- New entity fields automatically surface in seeder compilation or test failures. No silent data corruption from schema drift.
- Demo data always uses today-relative dates. Demo tenants created in January 2026 and April 2026 both show "3 months of recent activity."
- Profile-specific seeders can check ``VerticalModuleRegistry.getModule("court_calendar").isPresent()`` to conditionally seed legal entities, gracefully handling missing Phase 55 modules.
- The seeder code is a maintenance responsibility: when new entities are added in future phases, the seeder should be extended to include them for richer demos. This is explicitly best-effort — a demo with slightly fewer entity types is better than no demo at all.
- Integration tests for the seeder verify data consistency invariants: invoice totals match line items, budget utilization matches time entries, all FK references are valid.
- The reseed operation (delete transactional data, re-run seeder) is straightforward because the seeder is a repeatable Java method, not a one-time SQL script or snapshot restore.
- Related: [ADR-224](ADR-224-demo-provisioning-bypass.md) (demo provisioning triggers seeding after schema creation).
