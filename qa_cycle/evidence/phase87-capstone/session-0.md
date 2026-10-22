# Session 0 — Stack start-up + prep (legal-za 90-day, Keycloak) — 617C capstone — 2026-10-22 — HEAD dac048f12

Walker A. Brief Part A.2 start-up procedure plus scenario lines 43–51.

## Stack identity

- Main checkout (services run here) HEAD `dac048f12c2441475c5494c1dc0ceac72fbd80a8` = worktree `epic-617C/capstone-closure` HEAD `dac048f12c2441475c5494c1dc0ceac72fbd80a8`. `git status --short -- backend frontend portal gateway packages` was empty before start-up.
- After start-up, the portal dev server rewrote tracked `portal/next-env.d.ts` in the **main checkout** (`./.next/types/routes.d.ts` → `./.next/dev/types/routes.d.ts`). This is a Next dev artefact, not a code change. Nothing was committed. Restore it with `git -C <main> checkout portal/next-env.d.ts` after the services stop.
- Caffeinate hold PID 41495 alive (`caffeinate -dimsu -t 43200`); on AC power.

## Start-up log (2026-10-22, 15:10–15:13Z)

| # | Command | Result |
|---|---|---|
| 0 | `bash compose/scripts/e2e-down.sh` | e2e containers + volumes removed ("Stack stopped. All containers and volumes removed.") |
| 1 | `bash compose/scripts/dev-up.sh` | EXIT 0; b2b-postgres / b2b-keycloak / b2b-mailpit / b2b-localstack already up 4 days, healthy |
| 2 | Session 0 read-only checks | see 0.A / 0.B rows; schemas = `tenant_56399d06a773` only; org mapping = `org_phase86_squash_smoke` only; KC `docteams` users = `padmin@docteams.local` only; KC `org` table empty; `access_requests` empty; Mailpit total 0 → **no reset needed** |
| 3a | `cd backend && ./mvnw -q clean compile -DskipTests` | EXIT 0, 17 s wall. After: `target/classes/.../diary/DiaryController.class` present; `target/classes/db/migration/tenant` = V1__baseline, V2__add_project_template_work_type, V3__normalise_work_types (stale pre-Phase-87 classes gone) |
| 3b | `pnpm install --frozen-lockfile` | Done (no-op, 10.7 s) |
| 3c | `bash compose/scripts/svc.sh start all` | backend ready 27 s, gateway 6 s, frontend 3 s, portal 3 s; EXIT 0 |
| 3d | `svc.sh status` | verbatim below |
| 3e | `curl … http://localhost:3002/login` | 200 |
| 3f | `touch /tmp/.e2e-keycloak-mode` | done (Playwright port-3000 hook) |

Port holders right after start (fresh PIDs, not stale external holders): 8080 pid 60426 etime 00:46 · 8443 pid 60574 00:29 · 3000 pid 60624 00:24 · 3002 pid 60683 00:11.

### `svc.sh status` (verbatim, 15:13Z)

```
=== svc.sh status backend gateway frontend portal ===


  SERVICE      PID      RUNNING  HEALTHY  PORT
  -------      ---      -------  -------  ----
  backend      60278    yes      yes      8080
  gateway      60449    yes      yes      8443
  frontend     60581    yes      yes      3000
  portal       60640    yes      yes      3002


Done.
```

### Backend serves Phase 87 code

- `logs/walkA-backend-startup.txt`: "Started BackendApplication in 10.669 seconds"; on boot the TenantMigrationRunner applied **2 migrations** to the old smoke tenant `tenant_56399d06a773` ("now at version v3"). V2/V3 exist only in the Phase 87 tree, so the freshly compiled classes are being served.
- The new Mathebula tenant was provisioned with V1–V3 (`logs/walkA-backend-provisioning.txt`, "Ran tenant migrations for schema tenant_5039f2d497cf").
- In the browser, the Phase 87 shell renders: six-zone sidebar; the **Diary** item opens `/org/mathebula-partners/diary` (15:25Z: "Diary — Court dates, prescription dates, matter due dates and task due dates", Day/Week/Month/List, "New prescription tracker", "New court date", "Nothing in the diary for this week.", no error state, frontend log `GET …/diary 200`); settings `?tab=` families (see `day-00.md`). The backend request behind the Diary page is server-side, so the browser cannot see it, and the backend writes no access log. A successful `/api/diary` call is therefore **not directly observed**. The page did render without its error state.
- `curl http://localhost:8080/api/diary` unauthenticated → 401, and so did `/api/nonexistent-xyz`. This health-level probe cannot tell the two apart, so it is **not** cited as proof. The class-file and migration evidence above is the proof.

## Scenario steps

| Step | Result | Evidence (what was observed) | Mailpit ID | Screenshot | Gap |
|---|---|---|---|---|---|
| 0.A | PASS | Read-only `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'` → only `tenant_56399d06a773` (Phase 86 Squash Smoke). No `tenant_mathebula*`; nothing to drop | — | — | — |
| 0.B | PASS | Read-only KC DB: `docteams` realm users = `padmin@docteams.local` only. No `@mathebula-test.local` users; nothing to delete. KC `org` table empty | — | — | — |

**Counts (Session 0):** PASS 2 · FAIL 0 · DEFERRED 0.

Master doc M.1–M.9 are the shared startup/teardown steps. They are covered by the start-up log above (infra up, services healthy, Mailpit empty at 0 messages, no stale tenant/users). Per the brief, **no wipe was run**, and the master doc's M.6 `DROP SCHEMA` was not used (SQL writes are forbidden).

Note: the Mathebula schema is named `tenant_5039f2d497cf`, the same name as in the 2026-07-06 cycle. The schema name is a deterministic hash of the slug. The schema did not exist before this walk (0.A).
