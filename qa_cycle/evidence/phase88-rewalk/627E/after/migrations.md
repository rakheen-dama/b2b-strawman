# 627E — KC dev DB migration record (K0/K1)

**Statement:** On 2026-10-28 at 10:23:09Z (12:23 SAST) the KC dev backend was restarted from `$MAIN` (`$MAIN`, HEAD `e1df4c43c` = 627D; `git diff e1df4c43c f3a098bba -- backend` is empty, so the backend is byte-identical to the 627E PR head) after `./mvnw -q clean compile -DskipTests` (EXIT 0; `target/classes/db/migration/tenant/` = V1..V8). This was the **first application of tenant V6, V7 and V8 to the persistent KC dev DB** (`b2b-postgres` / `docteams`), applied to both KC tenants `tenant_5039f2d497cf` (mathebula-partners) and `tenant_56399d06a773` (smoke). Authorised by the orchestrator (OD-12 option a). Consequence: tenant V8 is now applied on the KC DB, so 627H cannot append to V8 and takes the next free version (**V9**). Public schema unchanged (V2).

## Before (K0, read-only SELECT, prior backend PID 39387, etime 3d11h, pre-627D build)

```
== tenant_5039f2d497cf
5|phase88 acceptance decline|2026-10-25 00:19:35.385466|t
4|phase88 demo ready|2026-10-24 09:13:25.411561|t
3|normalise work types|2026-10-24 09:13:25.398636|t
2|add project template work type|2026-10-24 09:13:25.381194|t
== tenant_56399d06a773
5|phase88 acceptance decline|2026-10-25 00:19:35.320498|t
4|phase88 demo ready|2026-10-24 09:01:53.871463|t
3|normalise work types|2026-10-22 17:12:49.031763|t
2|add project template work type|2026-10-22 17:12:49.002347|t
== public
2|portal contact directory|2026-10-24 19:13:06.756576
1|baseline|2026-10-18 04:20:53.007533
```

## Backend log (`$MAIN/.svc/logs/backend.log`, Flyway lines, message field)

```
2026-10-28T10:22:59.264597Z Current version of schema "public": 2
2026-10-28T10:23:09.251142Z Current version of schema "tenant_56399d06a773": 5
2026-10-28T10:23:09.262120Z Migrating schema "tenant_56399d06a773" to version "6 - phase88 audit customer index"
2026-10-28T10:23:09.294824Z Migrating schema "tenant_56399d06a773" to version "7 - phase88 letterhead"
2026-10-28T10:23:09.314466Z Migrating schema "tenant_56399d06a773" to version "8 - phase88 retention comment attachments"
2026-10-28T10:23:09.334520Z Successfully applied 3 migrations to schema "tenant_56399d06a773", now at version v8 (execution time 00:00.046s)
2026-10-28T10:23:09.367122Z Current version of schema "tenant_5039f2d497cf": 5
2026-10-28T10:23:09.369224Z Migrating schema "tenant_5039f2d497cf" to version "6 - phase88 audit customer index"
2026-10-28T10:23:09.381065Z Migrating schema "tenant_5039f2d497cf" to version "7 - phase88 letterhead"
2026-10-28T10:23:09.392414Z Migrating schema "tenant_5039f2d497cf" to version "8 - phase88 retention comment attachments"
2026-10-28T10:23:09.406224Z Successfully applied 3 migrations to schema "tenant_5039f2d497cf", now at version v8 (execution time 00:00.016s)
```

## After (read-only SELECT)

```
== tenant_5039f2d497cf
8|phase88 retention comment attachments|2026-10-28 12:23:09.387785|t
7|phase88 letterhead|2026-10-28 12:23:09.376617|t
6|phase88 audit customer index|2026-10-28 12:23:09.365183|t
5|phase88 acceptance decline|2026-10-25 00:19:35.385466|t
== tenant_56399d06a773
8|phase88 retention comment attachments|2026-10-28 12:23:09.308036|t
7|phase88 letterhead|2026-10-28 12:23:09.28625|t
6|phase88 audit customer index|2026-10-28 12:23:09.248465|t
5|phase88 acceptance decline|2026-10-25 00:19:35.320498|t
```

All three rows `success = t` in both tenants.
