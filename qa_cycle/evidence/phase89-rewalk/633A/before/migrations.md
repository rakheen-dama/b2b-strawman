# 633A — Decision-row migration evidence (captured 2026-11-05 02:45 SAST, builder, read-only)

## Repo (worktree `phase89/04A-portal-tasks-global-v3` @ `c1c9172d9`)

```
$ ls backend/src/main/resources/db/migration/global | sort -V
V1__baseline.sql
V2__portal_contact_directory.sql

$ ls backend/src/main/resources/db/migration/global | sort -V | tail -1
V2__portal_contact_directory.sql

$ ls backend/src/main/resources/db/migration/tenant | sort -V | tail -1     # for completeness, not touched
V10__phase89_go_live.sql
```
=> next free global number = **V3**.

## Keycloak dev DB (container `b2b-postgres`, db `docteams`)

```
$ docker exec b2b-postgres psql -U postgres -d docteams -At \
    -c "select version, description, installed_on, success from public.flyway_schema_history order by installed_rank desc limit 3;"
2|portal contact directory|2026-10-24 19:13:06.756576|t
1|baseline|2026-10-18 04:20:53.007533|t

$ docker exec b2b-postgres psql -U postgres -d docteams -At \
    -c "select coalesce(to_regclass('portal.portal_backfill_markers')::text,'NULL');"
NULL

$ docker exec b2b-postgres psql -U postgres -d docteams -At -c "select external_org_id, schema_name from public.org_schema_mapping;"
org_phase86_squash_smoke|tenant_56399d06a773
mathebula-partners|tenant_5039f2d497cf
```

Global history max = **2** on the only persistent stack; no `portal.portal_backfill_markers` relation.
(The E2E stack `e2e-postgres`/`app` is wiped per gate run — not a persistent stack, not counted.)

## Decision

633A creates `backend/src/main/resources/db/migration/global/V3__portal_backfill_markers.sql` with the
§14.7.2 body verbatim (one word corrected in the header comment: "633C depends on it" → "633B depends on
it", per the §8 re-cut). After merge + KC-STACK restart (633.5) the KC dev DB global history will show V3
and one `portal_task_backfill` marker row per tenant (2 tenants above).

## Re-check at build time

Same commands re-run immediately before naming the file (2026-11-05 02:45 SAST): unchanged — repo tail
`V2__portal_contact_directory.sql`, KC global history max 2, `portal_backfill_markers` NULL.
