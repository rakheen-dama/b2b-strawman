# 627H BEFORE: migration-number evidence (OQ #9)

Recorded 2026-10-28, read-only `SELECT`s. Worktree `$WT` (branch `phase88/10H-comment-attachments-backend`, HEAD `69a4d4e16`).

## Repo: tenant migrations in the worktree

```bash
ls backend/src/main/resources/db/migration/tenant | sort -V | tail -3
```

```
V6__phase88_audit_customer_index.sql
V7__phase88_letterhead.sql
V8__phase88_retention_comment_attachments.sql
```

V8 is named `..._retention_comment_attachments` and its header says "627H appends §7 (comment attachments) to this file
while it is unapplied to any persistent stack". Its body holds only §5 U17 (retention): no comment-attachment DDL.
That condition no longer holds (see below), so 627H must not append to V8.

## Keycloak dev DB (`b2b-postgres`, database `docteams`)

`public.org_schema_mapping`:

| external_org_id | schema_name | created_at |
|---|---|---|
| org_phase86_squash_smoke | tenant_56399d06a773 | 2026-10-18 02:21:30+00 |
| mathebula-partners | tenant_5039f2d497cf | 2026-10-24 07:13:25+00 |

`SELECT version, description, installed_on, success FROM <schema>.flyway_schema_history ORDER BY installed_rank DESC LIMIT 3`:

### tenant_56399d06a773
| version | description | installed_on | success |
|---|---|---|---|
| 8 | phase88 retention comment attachments | 2026-10-28 12:23:09.308036 | t |
| 7 | phase88 letterhead | 2026-10-28 12:23:09.28625 | t |
| 6 | phase88 audit customer index | 2026-10-28 12:23:09.248465 | t |

### tenant_5039f2d497cf
| version | description | installed_on | success |
|---|---|---|---|
| 8 | phase88 retention comment attachments | 2026-10-28 12:23:09.387785 | t |
| 7 | phase88 letterhead | 2026-10-28 12:23:09.376617 | t |
| 6 | phase88 audit customer index | 2026-10-28 12:23:09.365183 | t |

V8 checksum on both KC tenants: `163234837`, the same as the e2e tenant, so it matches the current file.
Changing the V8 file would fail Flyway validation on the KC dev DB at the next backend start.
`to_regclass('<schema>.comment_attachments')` is NULL on both KC tenants.
Public schema: max V2 (`portal contact directory`).

Timezone note: `installed_on` is a `timestamp` without time zone. Flyway fills it with `now()` in the session time zone,
and the host JVM's pgjdbc session uses the JVM default time zone (SAST). So `12:23:09` is most likely SAST (10:23 UTC).
The value read is what the table holds.

The e2e tenant `tenant_7d218705360b` is also at V8 (applied at stack start 2026-10-28 12:09 UTC). The e2e stack is
ephemeral, so it is not the deciding factor.

**Conclusion: the max tenant version on KC is V8 (applied on both KC tenants). 627H takes V9 as a new file (`V9__...sql`).
It must not append to V8.**

## Re-read immediately before opening the PR (2026-10-28T12:51Z, builder)

```
== tenant_56399d06a773  (version|description|installed_on|success|checksum, top 2)
8|phase88 retention comment attachments|2026-10-28 12:23:09.308036|t|163234837
7|phase88 letterhead|2026-10-28 12:23:09.28625|t|-1173317990
== tenant_5039f2d497cf
8|phase88 retention comment attachments|2026-10-28 12:23:09.387785|t|163234837
7|phase88 letterhead|2026-10-28 12:23:09.376617|t|-1173317990
select count(*) from information_schema.tables where table_name='comment_attachments' → 0
```

Unchanged: KC max tenant = V8, no `comment_attachments` table. The PR ships `V9__phase88_comment_attachments.sql`; V8 untouched.
