# 632B — Decision-row migration evidence (captured 2026-11-04 23:29 SAST, scout, read-only)

## Repo (worktree `phase89/03B-receipt-backend` @ `ec2906de4`)

```
$ ls backend/src/main/resources/db/migration/tenant | sort -V | tail -1
V9__phase88_comment_attachments.sql
```
Full tenant series: V1__baseline, V2__add_project_template_work_type, V3__normalise_work_types,
V4__phase88_demo_ready, V5__phase88_acceptance_decline, V6__phase88_audit_customer_index,
V7__phase88_letterhead, V8__phase88_retention_comment_attachments, V9__phase88_comment_attachments.
Global series: V1__baseline, V2__portal_contact_directory.
=> next free tenant number = **V10** → create `V10__phase89_go_live.sql` (§14.7.1 §1).

## Keycloak dev DB (container `b2b-postgres`, db `docteams`, host b2mash.local:5432)

Command: `docker exec b2b-postgres psql -U postgres -d docteams -At -c "select version, description, installed_on, success from \"<schema>\".flyway_schema_history order by installed_rank desc limit 3;"`

| Schema | Org (org_schema_mapping) | Max applied tenant version | Installed on | legal-za pack_install.pack_version |
|---|---|---|---|---|
| `tenant_5039f2d497cf` | `mathebula-partners` | **9** (`phase88 comment attachments`) | 2026-10-29 15:47:23 | 11 |
| `tenant_56399d06a773` | `org_phase86_squash_smoke` | **9** (`phase88 comment attachments`) | 2026-10-29 15:42:28 | 11 |

No V10 row on either Keycloak tenant schema → V10 is unapplied on every persistent stack observed.

## E2E stack (container `e2e-postgres`, db `app`, :5433) — wiped per gate run, not a persistent stack

`tenant_7d218705360b` max version 9 (`phase88 comment attachments`, 2026-11-04 18:42:46).

## Decision

632B creates `backend/src/main/resources/db/migration/tenant/V10__phase89_go_live.sql` with the §14.7.1 §1
block verbatim (header comment included; §2 is NOT included — 634C owns it and, per OQ #9, expects V11 once
the Keycloak dev stack restarts on a head containing V10). Re-check immediately before naming the file at
build time (numbers are never reserved). After 632B merges and KC-STACK restarts, both KC tenants will
show V10 — record that in `$EV/632/migrations.md` (632.17) as the OQ #9 input for 634C.

## Re-check at build time (builder, 2026-11-05 SAST, before naming the file)

```
$ ls backend/src/main/resources/db/migration/tenant | sort -V | tail -2
V8__phase88_retention_comment_attachments.sql
V9__phase88_comment_attachments.sql

$ for s in <tenant schemas in b2b-postgres/docteams>; do select version, description, installed_on, success from "$s".flyway_schema_history order by installed_rank desc limit 1; done
== tenant_5039f2d497cf
9|phase88 comment attachments|2026-10-29 15:47:23.233025|t
== tenant_56399d06a773
9|phase88 comment attachments|2026-10-29 15:42:28.683291|t
```

Unchanged: no V10 row on either Keycloak tenant schema → 632B created `V10__phase89_go_live.sql` (§14.7.1 header + §1 only).
