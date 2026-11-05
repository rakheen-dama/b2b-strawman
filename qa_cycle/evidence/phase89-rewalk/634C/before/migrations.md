# 634C — Decision-row migration evidence (OQ #9) (captured 2026-11-05 13:20 SAST, scout, read-only)

## Repo (worktree `phase89/05C-notification-cluster` @ `1c6bd158a`)

```
$ ls backend/src/main/resources/db/migration/tenant | sort -V | tail -3
V8__phase88_retention_comment_attachments.sql
V9__phase88_comment_attachments.sql
V10__phase89_go_live.sql
```
Full tenant series: V1__baseline, V2__add_project_template_work_type, V3__normalise_work_types,
V4__phase88_demo_ready, V5__phase88_acceptance_decline, V6__phase88_audit_customer_index,
V7__phase88_letterhead, V8__phase88_retention_comment_attachments, V9__phase88_comment_attachments,
V10__phase89_go_live (632B, §1 only — payment receipt columns).
Global series: V1__baseline, V2__portal_contact_directory, V3__portal_backfill_markers (633A).
=> next free tenant number = **V11**.

## Keycloak dev DB (container `b2b-postgres`, db `docteams`, host b2mash.local:5432)

Command: `docker exec b2b-postgres psql -U postgres -d docteams -At -c "select version, description, installed_on, success from \"<schema>\".flyway_schema_history order by installed_rank desc limit 3;"`

```
== tenant_5039f2d497cf        (org mathebula-partners)
10|phase89 go live|2026-11-05 02:19:20.421317|t
9|phase88 comment attachments|2026-10-29 15:47:23.233025|t
8|phase88 retention comment attachments|2026-10-29 15:47:23.223701|t
== tenant_56399d06a773        (org org_phase86_squash_smoke)
10|phase89 go live|2026-11-05 02:19:20.355261|t
9|phase88 comment attachments|2026-10-29 15:42:28.683291|t
8|phase88 retention comment attachments|2026-10-28 12:23:09.308036|t
```

`notifications.reference_comment_id` column present? (`information_schema.columns`): **0** on both schemas.
Global `public.flyway_schema_history` max = 3 (`portal backfill markers`).

| Schema | Org (org_schema_mapping) | Max applied tenant version | Installed on |
|---|---|---|---|
| `tenant_5039f2d497cf` | `mathebula-partners` | **10** (`phase89 go live`) | 2026-11-05 02:19:20 |
| `tenant_56399d06a773` | `org_phase86_squash_smoke` | **10** (`phase89 go live`) | 2026-11-05 02:19:20 |

V10 IS applied on both Keycloak tenant schemas (persistent stack) → V10 must not be edited (Flyway checksum
validation would fail on the next KC boot).

## E2E stack (container `e2e-postgres`, db `app`, :5433) — wiped per gate run, not a persistent stack

`tenant_7d218705360b` max version 10 (`phase89 go live`, 2026-11-05 05:35:51).

## Decision

V10 is applied on a persistent stack (both Keycloak tenants) → 634C opens
**`backend/src/main/resources/db/migration/tenant/V11__phase89_comment_notification_id.sql`** with the §14.7.1 §2
statement (`ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_comment_id uuid;` — no FK, no index).
`V10__phase89_go_live.sql` is NOT touched. Re-check immediately before naming the file at build time (numbers are
never reserved). After 634C merges and KC-STACK restarts, both KC tenants should show V11 — record that in
`$EV/634/migrations.md` (634.21).

## Re-check at build time (builder, 2026-11-05 13:41 SAST, worktree `1c6bd158a` = origin/main)

```
$ ls backend/src/main/resources/db/migration/tenant | sort -V | tail -2
V9__phase88_comment_attachments.sql
V10__phase89_go_live.sql
$ git ls-tree --name-only origin/main backend/src/main/resources/db/migration/tenant/ | sort -V | tail -1
backend/src/main/resources/db/migration/tenant/V10__phase89_go_live.sql
== tenant_5039f2d497cf
10|phase89 go live|2026-11-05 02:19:20.421317|t
== tenant_56399d06a773
10|phase89 go live|2026-11-05 02:19:20.355261|t
```

Unchanged: V10 is the highest tenant migration and is applied on both KC tenants → **V11** taken
(`V11__phase89_comment_notification_id.sql`); V10 untouched.
