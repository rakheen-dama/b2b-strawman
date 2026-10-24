# Migration-numbering decision record — Phase 88 mid-phase re-walk (623.8, 623C)

Closes the 623 half of OQ #9 ("Migration numbering after 623", task file line 29). Source of truth: read-only `SELECT`s on the Keycloak dev database (container `b2b-postgres`, database `docteams`), captured fresh by 623C at **2026-10-24T10:53:02Z** (`logs/623c-flyway-history.txt`). No writes, no service restart.

## 1. Decision

**V4 is applied to a persistent stack as of 2026-10-24 09:01:53 SAST (07:01:53Z); every later tenant migration takes a new file.**

Durable record: `tenant_56399d06a773` (`org_phase86_squash_smoke`, still present) `flyway_schema_history` row `4 / phase88 demo ready / 2026-10-24 09:01:53.871463 / t`, on top of V1 (2026-10-18), V2 and V3 (2026-10-22). The same backend start upgraded `tenant_5039f2d497cf` (617C Mathebula tenant) in place at 09:01:53.948575. That schema was then dropped by the slice-authorised Session 0 reset and re-provisioned from empty (V1–V4 at 09:13:24–09:13:25 SAST), so its history rows are history only and must not be cited as the durable record. Global: `public.flyway_schema_history` max V1 (baseline 2026-10-18); global V2 is free for 624A.

Next free numbers: **tenant V5**, **global V2**.

Why the timestamps read SAST: `installed_on` is `timestamp without time zone DEFAULT now()`, so Postgres writes it in the **JDBC session zone**, not the server zone (the `b2b-postgres` server `timezone` is UTC). The Keycloak dev backend runs on the host JVM (SAST, UTC+2), so the KC rows read SAST. The backend startup log is in UTC and agrees: `2026-10-24T07:01:53.887484Z … Migrating schema "tenant_56399d06a773" to version "4 - phase88 demo ready"` = 09:01:53 SAST (`logs/backend-startup-v4.txt`). The e2e stack's backend runs in Docker (UTC), so e2e rows read UTC.

## 2. Evidence — fresh read-only SELECTs (623C, 2026-10-24T10:53:02Z)

Raw output, verbatim from `logs/623c-flyway-history.txt`. Two Keycloak tenant schemas exist; both are at max V4; no schema has a V5 or later row.

Durable tenant (`org_phase86_squash_smoke`):

```text
$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT version, description, installed_on, success FROM tenant_56399d06a773.flyway_schema_history ORDER BY installed_rank;"
 version |          description           |        installed_on        | success 
---------+--------------------------------+----------------------------+---------
 1       | baseline                       | 2026-10-18 04:21:29.676908 | t
 2       | add project template work type | 2026-10-22 17:12:49.002347 | t
 3       | normalise work types           | 2026-10-22 17:12:49.031763 | t
 4       | phase88 demo ready             | 2026-10-24 09:01:53.871463 | t
(4 rows)
```

Mathebula tenant (re-provisioned in Session 0; history only):

```text
$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT version, description, installed_on, success FROM tenant_5039f2d497cf.flyway_schema_history ORDER BY installed_rank;"
 version |          description           |        installed_on        | success 
---------+--------------------------------+----------------------------+---------
 1       | baseline                       | 2026-10-24 09:13:24.320391 | t
 2       | add project template work type | 2026-10-24 09:13:25.381194 | t
 3       | normalise work types           | 2026-10-24 09:13:25.398636 | t
 4       | phase88 demo ready             | 2026-10-24 09:13:25.411561 | t
(4 rows)
```

Global history (`FlywayConfig`: `locations("classpath:db/migration/global").schemas("public")`; the `portal` schema has no history table of its own):

```text
$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT version, description, installed_on, success FROM public.flyway_schema_history ORDER BY installed_rank;"
 version | description |        installed_on        | success 
---------+-------------+----------------------------+---------
 1       | baseline    | 2026-10-18 04:20:53.007533 | t
(1 row)
```

Tenant mapping and tenant schemas present:

```text
$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT external_org_id, schema_name FROM public.org_schema_mapping ORDER BY 1;"
     external_org_id      |     schema_name     
--------------------------+---------------------
 mathebula-partners       | tenant_5039f2d497cf
 org_phase86_squash_smoke | tenant_56399d06a773
(2 rows)

$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%' ORDER BY 1;"
     schema_name     
---------------------
 tenant_5039f2d497cf
 tenant_56399d06a773
(2 rows)
```

Per-schema max version (successful rows only):

```text
$ docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT 'tenant_5039f2d497cf' AS tenant_schema, max(version::int) AS max_version, count(*) AS rows FROM tenant_5039f2d497cf.flyway_schema_history WHERE success UNION ALL SELECT 'tenant_56399d06a773', max(version::int), count(*) FROM tenant_56399d06a773.flyway_schema_history WHERE success UNION ALL SELECT 'public (global)', max(version::int), count(*) FROM public.flyway_schema_history WHERE success ORDER BY 1;"
    tenant_schema    | max_version | rows 
---------------------+-------------+------
 public (global)     |           1 |    1
 tenant_5039f2d497cf |           4 |    4
 tenant_56399d06a773 |           4 |    4
(3 rows)
```

The fresh output is identical to 623A's durable-record capture (`logs/v4-durable-record.txt`, 2026-10-24T07:49:57Z) for `tenant_56399d06a773`, and to the 623C scout's read at 10:40:58Z. Nothing has been applied since.

Informational only, not a numbering basis: the e2e stack (`e2e-postgres` :5433, db `app`) is wipeable (OQ #9), so it never counts. Per the 623C scout's earlier read, its tenant was also at V4 and its global history at V1 — **not re-verified by 623C** (the fresh read in `logs/623c-flyway-history.txt` covers the Keycloak dev database only).

## 3. First application (623A, KC-STACK)

Verbatim startup lines (`logs/backend-startup-v4.txt`, copied from `README.md` § V4):

```text
2026-10-24T07:01:45.646619Z  DbMigrate  Schema "public" is up to date. No migration necessary.
2026-10-24T07:01:53.737581Z  BackendApplication  Started BackendApplication in 10.553 seconds (process running for 10.751)
2026-10-24T07:01:53.849673Z  TenantMigrationRunner  Running tenant migrations for 2 schemas
2026-10-24T07:01:53.874149Z  DbMigrate  Current version of schema "tenant_56399d06a773": 3
2026-10-24T07:01:53.887484Z  DbMigrate  Migrating schema "tenant_56399d06a773" to version "4 - phase88 demo ready"
2026-10-24T07:01:53.928979Z  DbMigrate  Successfully applied 1 migration to schema "tenant_56399d06a773", now at version v4 (execution time 00:00.035s)
2026-10-24T07:01:53.930849Z  TenantMigrationRunner  Migrated schema tenant_56399d06a773 — 1 migrations applied
2026-10-24T07:01:53.951186Z  DbMigrate  Current version of schema "tenant_5039f2d497cf": 3
2026-10-24T07:01:53.954677Z  DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "4 - phase88 demo ready"
2026-10-24T07:01:53.978351Z  DbMigrate  Successfully applied 1 migration to schema "tenant_5039f2d497cf", now at version v4 (execution time 00:00.018s)
2026-10-24T07:01:53.980204Z  TenantMigrationRunner  Migrated schema tenant_5039f2d497cf — 1 migrations applied
```

Other existing evidence (all in this folder):

- `README.md` § V4 — first application to a persistent stack (OQ #9), incl. § Statement.
- `logs/v4-before.txt` — both tenant histories ended at V3 before the start.
- `logs/v4-after.txt` — V4 rows on both tenants after the start.
- `logs/v4-durable-record.txt` — 623A's read of the durable tenant (07:49:57Z).
- `logs/day0-provisioning.txt` — re-provisioning of `tenant_5039f2d497cf` from empty (V1 → V4) after the Session 0 reset.
- `logs/623c-flyway-history.txt` — this record's fresh read (10:53:02Z).

## 4. Repo migration files and next free numbers (branch base `d954bd0e4`)

```text
$ ls backend/src/main/resources/db/migration/tenant | sort -V
V1__baseline.sql
V2__add_project_template_work_type.sql
V3__normalise_work_types.sql
V4__phase88_demo_ready.sql

$ ls backend/src/main/resources/db/migration/global | sort -V
V1__baseline.sql
```

| Series | Highest file in repo | Highest applied on KC (`docteams`) | Next free |
|---|---|---|---|
| Tenant (`db/migration/tenant`) | `V4__phase88_demo_ready.sql` | V4 (both tenant schemas) | **V5** |
| Global (`db/migration/global`) | `V1__baseline.sql` | V1 (`public`) | **V2** |

`V4__phase88_demo_ready.sql` is now an **applied** file on a persistent stack: it must not be edited or appended to. The two V1 baselines are immutable (task file line 7).

## 5. Planned allocation and the per-slice rule

Plan only. Each migration-bearing slice re-checks before naming its file; if another slice has merged or applied a number first, take the next free one (memory `feedback_migration_numbers_next_free`).

| Slice | Task | File named in the task file | Planned number | Basis |
|---|---|---|---|---|
| 624A | 624.2 | `$M/global/V2__portal_contact_directory.sql` | global V2 | global max V1 (above) |
| 624F | 624.14 | `$M/tenant/V5__phase88_acceptance_decline.sql` (§6) | tenant V5 | tenant max V4 applied (above) |
| 626A | 626.2 | `$M/tenant/V6__phase88_letterhead.sql` (§4) | tenant V6 | V5 applied by 624's KC observation |
| 627B | 627.5 | `$M/tenant/V7__phase88_retention_comment_attachments.sql` (§5) | tenant V7 | V6 applied by 626's KC observation; shared with 627H while unapplied |
| 627H | 627.17 | V7 += §7 | tenant V7 (else V8) | only if `flyway_schema_history` on Keycloak still shows no V7 row |

Rules (task file, verbatim):

- Line 7: "every later migration-bearing slice (624F §6 decline, 626A §4 letterhead, 627B §5 retention, 627H §7 comment attachments) **expects** to open a new file (V5, V6, V7…) — the builder runs `ls $M/tenant | sort -V | tail -1` and reads `flyway_schema_history` on one Keycloak tenant schema before naming the file (memory `feedback_migration_numbers_next_free`)."
- Line 202 (hotspot table): "append-only while unapplied; a **Decision** row per slice records the number taken and the `flyway_schema_history` evidence; never edit an applied file".

Per-slice check (copy into each PR body with its output):

```bash
ls backend/src/main/resources/db/migration/tenant | sort -V | tail -1
ls backend/src/main/resources/db/migration/global | sort -V | tail -1
docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT version, description, installed_on, success FROM tenant_56399d06a773.flyway_schema_history ORDER BY installed_rank;"
docker exec b2b-postgres psql -U postgres -d docteams -c "SELECT version, description, installed_on, success FROM public.flyway_schema_history ORDER BY installed_rank;"
```

## 6. Linked from

- Task file **Decision** notes: 624.14 (624F, already linked), 626.2 (626A), 627.5 (627B), 627.17 (627H) — `tasks/phase88-demo-ready-root-cause-closure.md`.
- OQ #9 (task file line 29). OQ #9 stays open until the last migration slice (627H) records its number; closing it is the orchestrator's call.
