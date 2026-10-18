# Flyway baseline (Phase 86 squash)

## 1. What happened

On 2026-10-18 (Epic 605, Phase 86, ADR-331) both Flyway series were squashed into one baseline file each:

| series | squashed | replaced by |
|---|---|---|
| tenant | V1..V144 = 142 files: 133 pre-Phase-86 migrations (V113/V114 never existed) + 9 Phase 86 drop migrations V136..V144 | `src/main/resources/db/migration/tenant/V1__baseline.sql` |
| global | V1..V29 = 29 files: 25 pre-Phase-86 migrations + 4 Phase 86 drop migrations V26..V29 | `src/main/resources/db/migration/global/V1__baseline.sql` |

13 Phase 86 drop migrations in total. Source tree: commit `a99b11f50` (main, post-Epic-604); the parity proof was recorded at the Epic 605A commit, whose migration files are identical to `a99b11f50`.

Tooling: PostgreSQL 16.14 / pg_dump 16.14 (`postgres:16-alpine`), Flyway 11.14.1 (`flyway/flyway:11.14.1`, the version Spring Boot 4.0.2 pins). Tests run on zonky embedded PostgreSQL 16.

There was no schema change, with one caveat. The old tenant series was not deterministic across tenants. V7, V8, V10, V16, V27, V29 and V127 guarded eight constraints and one column with unscoped catalog lookups (`pg_constraint WHERE conname = …`, `information_schema.columns WHERE table_name = …`). Only the first tenant in a database got them:

- `uq_customers_email`, `chk_customers_customer_type`, `uq_customer_projects_customer_project`
- `chk_documents_scope_fks`, `chk_documents_provenance`, `uq_portal_contacts_email_customer`
- `uq_magic_link_tokens_hash`, `chk_brand_color_format`, and the column `tasks.customer_visible`

The baseline reproduces the empty-database (first-tenant) shape. That shape is what the migration authors intended, and it is what the e2e seed org and the parity snapshot have. Every tenant now gets all nine objects.

## 2. Where the history is

- Git tag `horizontal-platform-final` (annotated tag object `22b528628`, pointing at commit `7f47f9629`).
- Mirror: https://github.com/rakheen-dama/kazi-horizontal-archive
- Example:

  ```bash
  git show horizontal-platform-final:backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql
  ```

The Phase 86 drop migrations (tenant V136..V144, global V26..V29) are on `main` history before the squash commit.

## 3. How it was generated and verified

Generation used a throwaway container, so no shared stack was touched:

1. Started an empty `postgres:16-alpine`. Applied the pre-squash global series to `public` and the tenant series to an empty `tenant_000000000000` schema with the Flyway 11.14.1 CLI (`baselineOnMigrate=false`). This matches production: global first, then `CREATE SCHEMA` + tenant migrate.
2. Checked the high-water marks (tenant 142 rows / v144, global 29 rows / v29). Checked that no Phase 86-removed table, column or capability exists.
3. Counted the migration-seeded rows. Every table with rows after a Flyway-only migrate:
   - tenant: `org_roles` 3, `org_role_capabilities` 47 (owner 22 / admin 20 / member 5), `tax_rates` 3, `accounting_tax_code_mapping` 4, `request_counters` 1
   - global: `shedlock` 1, `shard_config` 1

   `invoice_counters` is **not** seeded by any migration.
4. Ran `pg_dump --schema-only --no-owner --no-privileges --no-comments` for the tenant schema and for `public` + `portal`, then cleaned the output with a script:
   - Removed `SET`, `set_config`, `\restrict`/`\unrestrict` and comment lines, and unqualified all names.
   - Moved every PK/UNIQUE/CHECK/FK constraint inline, keeping its name and definition.
   - Wrote `CREATE TABLE IF NOT EXISTS` in FK-dependency order. Two FK cycles (`bank_statement_lines`↔`trust_transactions`, `invoice_lines`↔`legal_disbursements`) close with guarded trailing `ALTER TABLE … ADD CONSTRAINT`.
   - Wrote `CREATE [UNIQUE] INDEX IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION` for the audit guards, and the two `audit_events` triggers inside `current_schema()`-scoped `DO` guards.
   - Converted pg_dump's `(col)::text = ANY ((ARRAY['A'::character varying, …])::text[])` back to the source form `col IN ('A', …)`. The dumped form re-parses to a different stored expression.
   - Re-expressed the seed rows in the original migrations' style (`gen_random_uuid()`, `NOW()`, `INSERT … SELECT … WHERE slug = …`, original `ON CONFLICT` guards). There are no literal UUIDs or timestamps.
   - Global V1 starts with `CREATE EXTENSION IF NOT EXISTS pg_trgm;` and `CREATE SCHEMA IF NOT EXISTS portal;`.
5. Round-trip proof. Applied only the two new `V1__baseline.sql` files to a second empty database. The pg_dump output of old series vs new baseline is byte-identical for both the tenant schema and `public` + `portal`. The seeded rows are equal, and each history table has exactly one row (version 1, success).
6. `BaselineSchemaParityTest` (`src/test/java/.../provisioning/`) provisions a tenant through the real `TenantProvisioningService` path. It compares a normalised dump of the tenant schema and of `public`/`portal` with the snapshots under `src/test/resources/schema/`. The dump covers tables, columns, constraints, key columns, `pg_constraint` definitions including FK actions, indexes, triggers, non-extension functions, extensions and the seeded rows. The snapshots were recorded from the pre-squash tree in Epic 605A, before any series change, and pass unchanged after the squash.

## 4. The rule

**The baseline is immutable.** Once the first production tenant exists (Phase 84 take-on):

- never squash again;
- every schema change is a new `V<n>__` file;
- do not edit either `V1__baseline.sql`.

Flyway checksums would reject an edited baseline on every existing schema.

## 5. How to add a migration

- Add `V2__<description>.sql` (then the next free number) to `src/main/resources/db/migration/tenant/` or `global/`. Double underscore.
- Make it idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`). Scope every catalog guard to the current schema (`… AND n.nspname = current_schema()`, `table_schema = current_schema()`). An unscoped guard silently skips the object in every tenant after the first (see §1).
- `baselineOnMigrate(false)` stays everywhere: in `FlywayConfig`, `TenantProvisioningService`, `TenantMigrationRunner` and the two shard tests. A non-empty schema without history must fail fast, not be baselined.
- `BaselineSchemaParityTest` is the permanent proof of the V1 file. Feature migrations do not re-record its snapshots. The first PR that adds a schema-changing V2 must convert the test to migrate a scratch schema with Flyway `.target(MigrationVersion.fromVersion("1"))`. The snapshots then stay frozen as the proof of `V1__baseline.sql`, and new migrations get their own targeted tests.
- `OrgSettingsSchemaSnapshotTest` still pins `org_settings`. A change to that table needs an authorised migration plus a deliberate pin update.

## 6. Volume wipe (one time, after the squash)

Any database migrated by the old series has history rows (V2..V144 / V2..V29) that no longer exist on the classpath. On boot, `globalFlyway` validation fails with `Detected applied migration not resolved locally`, and tenant migration fails the same way. There is no reconciliation path (ADR-331). Wipe instead:

```bash
bash compose/scripts/dev-down.sh --clean   # Keycloak dev stack (also wipes the Keycloak DB → re-run keycloak-bootstrap.sh)
bash compose/scripts/e2e-down.sh           # E2E mock-auth stack
```

- **VPS dev environment** (`DEPLOY-VPS.md`): remove the Postgres data volume before deploying an image built from the squash commit.
- **Staging**: the same applies if staging is ever revived.
- `spring-boot:test-run` and the test suite use a fresh embedded Postgres per JVM, so there is nothing to do there.
