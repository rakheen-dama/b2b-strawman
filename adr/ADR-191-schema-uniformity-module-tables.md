# ADR-191: Schema Uniformity for Module Tables

**Status**: Accepted
**Date**: 2026-03-18
**Phase**: 49 (Vertical Architecture)

## Context

Future vertical modules (trust accounting, court calendar, conflict check) will require their own database tables. Trust accounting, for example, will need tables for trust ledgers, trust transactions, trust bank accounts, and trust reconciliation records. Court calendar will need tables for court dates, matter deadlines, and hearing records. These tables are domain-specific to a vertical module and have no meaning for tenants that do not use that module.

DocTeams uses schema-per-tenant isolation (ADR-064). Every tenant -- regardless of billing tier -- gets a dedicated `tenant_<hash>` schema. Flyway migrations in `db/migration/tenant/` run against every tenant schema at provisioning time and at application startup (to catch up schemas that missed migrations). The current migration set (V7 through V29+) creates the same table structure in every schema. There is no mechanism for conditional per-schema migration execution.

Phase 49 creates no module-specific tables -- the legal vertical modules are stubs with controllers only. However, the decision made now establishes the pattern that future phases will follow when they build out trust accounting (estimated 3-5 new tables), court calendar (2-3 tables), and conflict check (1-2 tables). The choice between uniform schemas (all tenants get all tables) and conditional schemas (only tenants with the module get module tables) has implications for migration complexity, debugging, and future module development.

## Options Considered

### Option 1: Uniform schemas -- all tenant schemas get all tables

Module-specific migrations are standard Flyway files in `db/migration/tenant/` (e.g., `V35__create_trust_accounting_tables.sql`). They run for every tenant schema, regardless of whether the tenant has the module enabled. Tables exist in all schemas but are only populated and queried for tenants with the module enabled. The `VerticalModuleGuard` (ADR-190) prevents access at the application layer.

- **Pros:**
  - Zero Flyway customization: the existing migration runner works unchanged -- it iterates all tenant schemas and applies all pending migrations
  - All schemas are structurally identical, making debugging trivial: `\dt` in any tenant schema shows the same tables, `pg_dump --schema` diffs are empty
  - No boot-order dependency between the migration runner and `OrgSettings` data -- migrations don't need to read tenant configuration to decide which files to run
  - Future module enablement for an existing tenant requires no schema migration -- the tables already exist, the guard just starts allowing access
  - Rolling back a module (disabling it for a tenant) leaves the tables intact, preserving data for potential re-enablement
  - Schema restore and disaster recovery are simpler: any backup can be restored to any tenant schema without worrying about which modules that tenant had enabled at backup time

- **Cons:**
  - Empty tables exist in schemas for tenants that will never use the module -- a conceptual waste, even if the storage cost is negligible (empty tables consume only catalog metadata, typically <1KB per table)
  - A tenant running `\dt` in their schema sees tables for features they don't have access to, which could cause confusion in direct database access scenarios (rare -- the application mediates all access)
  - If module tables have triggers or constraints with cross-table references, those triggers exist in all schemas even when unused (negligible runtime cost but increases `pg_trigger` catalog size)
  - Migration count grows with every new module, increasing the time for fresh tenant provisioning (mitigated by the snapshot-based provisioning pattern where a "template" schema is cloned rather than migrated from scratch)

### Option 2: Conditional migrations -- module-specific files run only for enabled schemas

Extend the Flyway migration runner to check each tenant's `enabled_modules` before applying module-specific migrations. Module migrations are tagged (e.g., with a filename convention like `V35_MOD_trust_accounting__create_tables.sql`) and the runner skips them for schemas whose tenant does not have the module enabled.

- **Pros:**
  - Schemas contain only the tables the tenant actually uses -- cleaner `\dt` output, no conceptual waste
  - Triggers and constraints for unused modules don't exist, eliminating any theoretical overhead
  - If a module has expensive migrations (large seed data, complex indexes), those only run for tenants that need them

- **Cons:**
  - Requires custom Flyway runner logic: the standard `Flyway.migrate()` API does not support conditional file inclusion per schema -- this is a custom extension that must be built and maintained
  - The runner must read `OrgSettings.enabled_modules` for each tenant before migrating, creating a dependency between the migration system and application data -- if the `org_settings` table structure changes, the migration runner must be updated
  - Flyway's checksum validation assumes all migrations are applied in sequence -- skipping a migration leaves a gap in the version history that Flyway flags as an error on subsequent runs (requires `ignoreMigrationPatterns` or a custom `MigrationResolver`)
  - When a tenant later enables a module, the skipped migrations must be retroactively applied -- this requires a "catch-up" mechanism that detects which migrations were skipped and applies them, introducing a second migration pathway
  - Schema structural divergence makes debugging harder: "tenant A has these tables but tenant B doesn't" requires knowing each tenant's module configuration to understand their schema
  - Testing doubles: integration tests must cover both "module enabled" and "module disabled" schema states, and the migration runner itself needs dedicated tests for the conditional logic
  - Disaster recovery is more complex: restoring a backup requires knowing which modules the tenant had enabled at backup time to ensure the correct schema structure

### Option 3: Separate Flyway locations per module

Module-specific migrations live in separate directories (e.g., `db/migration/module-trust-accounting/`, `db/migration/module-court-calendar/`). The migration runner maintains a list of Flyway instances or locations, selectively including module directories based on the tenant's `enabled_modules`.

- **Pros:**
  - Clean file organization: core migrations and module migrations are in separate directories, making it obvious which files belong to which module
  - No filename conventions or tagging needed -- directory structure is the discriminator
  - Potentially simpler than Option 2's single-directory conditional approach

- **Cons:**
  - Flyway manages each migration location independently, with separate version histories -- a tenant schema would have multiple `flyway_schema_history` tables (one per location), complicating version tracking
  - Alternatively, if using a single history table with multiple locations, Flyway's version ordering across locations is non-deterministic for the same version number (e.g., `V1` in `core/` vs. `V1` in `module-trust-accounting/`)
  - Requires the same conditional runner logic as Option 2 (read `enabled_modules`, include/exclude locations), plus the additional complexity of managing multiple Flyway instances
  - Cross-module foreign keys (e.g., a trust transaction referencing an invoice) would require careful migration ordering across locations, which Flyway cannot enforce across separate location sets
  - The existing migration infrastructure assumes a single `db/migration/tenant/` location -- refactoring to support multiple locations is a non-trivial infrastructure change

### Option 4: Lazy migration -- tables created on first use

Module tables are not created by Flyway at all. Instead, when a module is enabled for a tenant and its first API call arrives, a `ModuleSchemaInitializer` creates the necessary tables in the tenant's schema on-the-fly using programmatic DDL. Subsequent calls check for table existence and skip initialization.

- **Pros:**
  - Zero overhead for tenants that never use a module -- no tables, no migrations, no catalog entries
  - No Flyway customization needed for the conditional aspect
  - Module enablement is fully self-service: enable the module in settings, first API call creates the tables

- **Cons:**
  - First-request latency spike: the initial API call to a newly enabled module triggers DDL operations (CREATE TABLE, CREATE INDEX), potentially taking several seconds
  - Programmatic DDL is harder to review, version, and audit than declarative SQL migration files -- it lives in Java code rather than versioned `.sql` files
  - Schema version tracking is lost: there is no `flyway_schema_history` entry for module tables, making it impossible to know which version of the module schema a tenant has without querying `information_schema`
  - Evolving module tables (adding columns, changing indexes) requires a parallel migration system for lazy-created tables -- essentially reinventing Flyway within the module initializer
  - DDL in production request paths is dangerous: if the CREATE TABLE fails mid-way (e.g., connection timeout, disk full), the schema is left in a partially created state with no automatic recovery
  - Violates the project's convention that all schema changes go through Flyway migrations

## Decision

**Option 1 -- Uniform schemas where all tenant schemas get all tables, including module-specific ones.**

## Rationale

ADR-064 established that all tenants get dedicated schemas with identical structure, eliminating the complexity of conditional schema management. Extending this principle to module tables is the natural continuation. The alternative -- introducing conditional migration logic -- would reintroduce the exact category of complexity that ADR-064 eliminated: schemas that differ based on tenant configuration, requiring the migration system to understand tenant state, and creating divergent structures that complicate debugging and disaster recovery.

The cost of empty tables is negligible. PostgreSQL stores table metadata in the `pg_class` and `pg_attribute` system catalogs. An empty table with no rows consumes approximately 0 bytes of heap storage (no data pages allocated until the first INSERT) and a few hundred bytes of catalog metadata. Even with 10 module-specific tables across 1,000 tenant schemas, the total catalog overhead is under 10MB -- trivial compared to the data stored in active tables. There is no query performance impact: queries against empty tables return instantly, and the `VerticalModuleGuard` prevents those queries from ever executing for disabled modules.

The "enable a module for an existing tenant" scenario strongly favors uniform schemas. With conditional migrations (Options 2 and 3), enabling trust accounting for a tenant that has been running for months requires retroactively applying skipped migrations -- a "catch-up" process that must handle version gaps, potential conflicts with migrations that ran in the interim, and the possibility that the catch-up fails partway through. With uniform schemas, enabling a module is a settings change: update `enabled_modules` in `OrgSettings`, and the tables are already there, ready to use.

The lazy migration approach (Option 4) is fundamentally incompatible with the project's Flyway-based schema management. All schema changes in DocTeams are versioned SQL files reviewed in pull requests, tracked in `flyway_schema_history`, and applied deterministically. Introducing programmatic DDL in request paths would create an unversioned, unreviewable parallel schema management system -- the opposite of the controlled migration process that has worked reliably through 29+ migration versions.

## Consequences

- **Positive:**
  - The existing Flyway migration runner requires zero modification -- module migrations are standard `V{n}__*.sql` files in `db/migration/tenant/`
  - All tenant schemas remain structurally identical, preserving the simplicity established in ADR-064
  - Module enablement for an existing tenant is a pure settings change with no schema migration step
  - Disaster recovery, schema comparison, and debugging remain straightforward -- any tenant schema can be compared to any other
  - Integration tests run against a single schema structure regardless of which modules are being tested

- **Negative:**
  - Tenants that never use legal modules still have `trust_ledger`, `trust_transaction`, etc. tables in their schema (empty, zero storage cost, but conceptually present)
  - Fresh tenant provisioning time increases slightly with each new module's migration files (bounded: even 10 additional CREATE TABLE statements add <1 second to provisioning)
  - If a module is permanently removed from the platform, its tables remain in all schemas unless explicitly dropped by a cleanup migration

- **Neutral:**
  - This decision applies to future phases that build module tables -- Phase 49 creates no module-specific tables (stubs only)
  - The `VerticalModuleGuard` (ADR-190) is the access control boundary, not the schema structure -- tables exist everywhere, but access is gated per tenant
  - Module-specific seed data (if any) follows the same pattern: seeded into all schemas, but only meaningful for tenants with the module enabled
