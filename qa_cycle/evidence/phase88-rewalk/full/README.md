# Phase 88 end-of-phase re-walk (629) — Keycloak — evidence index

Scenario `qa/testplan/demos/legal-za-90day-keycloak.md` (as amended by 628H). Stack: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080, Keycloak :8180, Mailpit :8025, portal :3002 — prod build for portal steps) served from the main checkout at `138119b8116a0ee3e32e86293815cd6479a1211f` (= origin/main) after `./mvnw -q clean compile -DskipTests`. Walk date 2026-10-29 (UTC). Walkers: 629A runs W1a…W4 (branch `phase88/12A-full-rewalk-days-00-35`); 629B (Days 36–90 + portal 90-day + E.1–E.11).

## KC-STACK (629.1)

Start time 2026-10-29T13:41:04Z. Host clock is SAST (UTC+2); Postgres `installed_on` values are local time.

| # | Command | Result |
|---|---|---|
| 1 | `pmset -g log` (last 5 sleep/wake) + `pmset -g batt` + `nohup caffeinate -dimsu -t 43200 &` | last sleep 2026-10-27 13:10:47 (Maintenance Sleep), wake 13:13:32; "Now drawing from 'AC Power'", battery 100 % charged; caffeinate hold PID 99323 (phase runner PID 6817 `caffeinate -is ./scripts/run-phase.sh 88` left alone) — `logs/kc-stack-pmset.txt`, `logs/kc-stack-identity.txt` |
| 2 | `git rev-parse HEAD origin/main`; `git status --short -- backend frontend portal gateway packages compose` | both `138119b8116a0ee3e32e86293815cd6479a1211f`; status empty; worktree HEAD identical — `logs/kc-stack-identity.txt` |
| 3 | zombie check `pgrep -fl 'surefire\|ForkedBooter\|embedded-pg'` | none |
| 4 | `svc.sh stop backend gateway frontend portal`; ports free | before: backend `ext` 5527, gateway `ext` 68482, frontend 45819, portal 88764; all stopped; :8080/:8443/:3000/:3002 all empty — `logs/kc-stack-stop.txt` |
| 5 | flyway BEFORE (stack down) | `logs/kc-stack-flyway-before.txt` — both tenants V8 (`phase88 retention comment attachments`, 2026-10-28 12:23:09), public V2 |
| 6a | `docker builder prune -f` | "Total: 2.177GB" reclaimed (`logs/kc-stack-builder-prune.txt`) |
| 6b | `./mvnw -q clean compile -DskipTests` | exit 0, wall 16 s; `target/classes/db/migration/tenant/` = V1…V9 (`V9__phase88_comment_attachments.sql`) — `logs/kc-stack-compile.txt` |
| 6c | `pnpm install --frozen-lockfile` | "Lockfile is up to date … Already up to date" — `logs/kc-stack-pnpm-install.txt` |
| 7 | `svc.sh start all` | backend ready 27 s, gateway 6 s, frontend 3 s, portal 3 s (wall 41 s) — `logs/kc-stack-start.txt` |
| 7b | health | 8080 200 `{"status":"UP"}`, 8443 200 `{"status":"UP"}`, 3000 200, 3002/login 200, :8180 realm 200 — `logs/kc-stack-svc-status.txt` |
| 8 | backend ERROR lines this start | 0 |

Result: **629.1 PASS** (fresh processes from HEAD, V9 applied to both tenant schemas, global stays V2, all healthy).

Spec drift note for 629D (ruling C-2, no gap): 629.1's "Flyway lines showing V4–V7 + global V2", the 629A row's "KC is at tenant V5 / global V2" and 629.12's "tenant V7" are stale. Observed: KC tenants V8 before → **V9** after this restart; global **V2**.

## `svc.sh status` (verbatim)

```
  SERVICE      PID      RUNNING  HEALTHY  PORT
  -------      ---      -------  -------  ----
  backend      99912    yes      yes      8080
  gateway      207      yes      yes      8443
  frontend     344      yes      yes      3000
  portal       441      yes      yes      3002
```

Port holders (pid / etime at 13:42:5xZ / cwd):

```
:8080 pid 175 etime 00:33 cwd /Users/rakheendama/Projects/2026/b2b-strawman/backend
:8443 pid 334 etime 00:16 cwd /Users/rakheendama/Projects/2026/b2b-strawman/gateway
:3000 pid 421 etime 00:12 cwd /Users/rakheendama/Projects/2026/b2b-strawman/frontend
:3002 pid 494 etime 00:08 cwd /Users/rakheendama/Projects/2026/b2b-strawman/portal
```

## Flyway — V9 application (verbatim log lines, `logs/kc-stack-backend-startup.txt`)

```
2026-10-29T13:42:20.268760Z DbMigrate Current version of schema "public": 2
2026-10-29T13:42:20.271063Z DbMigrate Schema "public" is up to date. No migration necessary.
2026-10-29T13:42:28.593328Z BackendApplication Started BackendApplication in 10.625 seconds (process running for 10.815)
2026-10-29T13:42:28.658302Z TenantMigrationRunner Running tenant migrations for 2 schemas
2026-10-29T13:42:28.685747Z DbMigrate Current version of schema "tenant_56399d06a773": 8
2026-10-29T13:42:28.694933Z DbMigrate Migrating schema "tenant_56399d06a773" to version "9 - phase88 comment attachments"
2026-10-29T13:42:28.724151Z DbMigrate Successfully applied 1 migration to schema "tenant_56399d06a773", now at version v9 (execution time 00:00.020s)
2026-10-29T13:42:28.748715Z DbMigrate Current version of schema "tenant_5039f2d497cf": 8
2026-10-29T13:42:28.751055Z DbMigrate Migrating schema "tenant_5039f2d497cf" to version "9 - phase88 comment attachments"
2026-10-29T13:42:28.762294Z DbMigrate Successfully applied 1 migration to schema "tenant_5039f2d497cf", now at version v9 (execution time 00:00.006s)
2026-10-29T13:42:28.789289Z PortalContactDirectoryBackfillRunner Portal contact directory backfill: 0 tenants scanned, 2 skipped (already marked), 0 failed, 0 rows upserted
2026-10-29T13:42:28.792862Z PackReconciliationRunner Running pack reconciliation for 2 tenants
2026-10-29T13:42:29.467190Z PackReconciliationRunner Pack reconciliation: checked 2 tenants, 2 succeeded, 0 failed
```

The same start reconciled the 628C pack texts into both old tenants (common-project v2→v3, conveyancing-za-project v1→v2, legal-za-project v2→v3, template pack common → v4, legal-za-clauses v1→v2, standard-clauses v1→v2, standard-reports v1→v2, automation-common v3→v4) — full list in `logs/kc-stack-backend-startup.txt`.

| Schema | Before | After | V9 row (installed_on SAST) |
|---|---|---|---|
| `tenant_5039f2d497cf` (mathebula-partners, 623/624I data — dropped by Session 0) | 8 | 9 | 2026-10-29 15:42:28.74666 |
| `tenant_56399d06a773` (org_phase86_squash_smoke — durable record) | 8 | 9 | 2026-10-29 15:42:28.683291 |
| `public` (global) | 2 | 2 | — |

## Sections
| Section | Doc lines | File | Rows | P / F / PARTIAL / D / N/A |
|---|---|---|---|---|
| KC-STACK (629.1) | — | README.md | 1 | PASS |
| Session 0 (629.2) | 43–48 + master 115–152 | session-0.md | 18 | 15 / 2 / 0 / 0 / 1 |
| Day 0 A–D (629.2, W1a) | 52–125 | day-00.md | 44 | 40 / 4 / 0 / 0 / 0 |

## Wow-moment candidates shot in 629A
| Wow | Step | File | Shows |
|---|---|---|---|

## Candidate gaps (629A) — index
| ID | Severity | Step | Class | Owner | File |
|---|---|---|---|---|---|
| G88-M1 (existing) | LOW | 0.Aa, M.6a | scenario mismatch — §5a row 1 pending founder authorisation | 628H / founder | session-0.md |
| G88-F1 | LOW | 0.5a, 0.15a, 0.28a, 0.29a (later runs append) | scenario mismatch needing authorisation (wording drift outside §5/§5a, ruling C-5) | new — founder §6 | day-00.md |
| G88-F2 | LOW | Team page (0.27 / CP-AD-2) | product bug — "Joined" column hard-coded "—" (`member-list.tsx:75,124`) | new | day-00.md |

## Stack state left for 629B
<filled by W4>
