# Session 0 — Prep & Reset (legal-za 90-day, Keycloak) — 629A full re-walk — 2026-10-29 — HEAD 138119b8116a0ee3e32e86293815cd6479a1211f

Walker 629A-W1a. Scenario lines 43–48 + master lines 115–152 (M.1–M.9). Reset recipe: brief §4.4 (= 623A §R + the 629A R.2 directory addition), copied byte for byte from the brief to `/tmp/629A/session0-reset.sh` (`diff` against the scout copy `/tmp/scout629a/reset.sh` empty; `bash -n` ok). Dry run first (`logs/session0-reset-dryrun.txt`, census identical to brief §3.1, guard 0|0|0|0), then the real run. Verbatim output: `logs/session0-reset.txt`; as-written checks: `logs/session0-checks.txt`.

## Reset log (629.2 §R — slice-authorised infra writes)
| Step | Command (summary) | Result |
|---|---|---|
| census BEFORE | mapping, schemas, access_requests, directory, portal org-keyed counts (13:43:42Z) | mapping `mathebula-partners→tenant_5039f2d497cf`, `org_phase86_squash_smoke→tenant_56399d06a773`; access_requests thandi@ APPROVED (2026-10-24 07:08:41Z) + thandi@ **PENDING (2026-10-29 03:00:30Z)**; directory 4 rows (moroka.trust@, multifirm.624i@, nomvula.khoza@, sipho.dlamini@email.co.za); portal invoices 4 / requests 0 / tasks 9 / documents 0 / projects 4 / summaries 0 / comments 0 / directory 4 — matches brief §3.1 |
| R.0 | `svc.sh stop backend` | "backend: stopped"; `lsof :8080` empty |
| R.1 | KC org DELETE + per-user loop + read-back | users before bob@/carol@/padmin@/thandi@; org `mathebula-partners` `a21f5935-c745-4454-8dde-aa6b758af449` DELETE → **204**; thandi/bob/carol "already gone" (org delete removed the org-managed members, as 623A/624I); users after **padmin@docteams.local** only; orgs after none |
| R.2 guard | 4 NOT-IN counts | **0 / 0 / 0 / 0** |
| R.2 | portal deletes (13 statements incl. the 629A directory addition) | in order: acceptance 1, notification pref 4, trust tx 4, trust balance 2, deadline 0, invoices 4, requests 0, tasks 9, documents 0, projects 4, summaries 0, comments 0, **contact_directory 4** — COMMIT (matches census) |
| R.3 | `DROP SCHEMA tenant_5039f2d497cf CASCADE` | "drop cascades to 103 other objects" → DROP SCHEMA |
| R.4 | public rows | subscriptions 1 / org_schema_mapping 1 / organizations 1 / access_requests **2** (APPROVED + the unexplained 03:00:30Z PENDING thandi@ row — ruling C-15: removed here) — COMMIT |
| R.5 | Mailpit DELETE | 200, before 0 / after 0 |
| R.6 | verify + `svc.sh start backend` | schemas `tenant_56399d06a773` only; mapping `org_phase86_squash_smoke` only; Mathebula access_requests 0; organizations 1; subscriptions 1; every Mathebula org-keyed portal table 0 (incl. `portal_contact_directory`); backend "ready (15s)", 8080 PID 1420 etime 00:13; log "Running tenant migrations for 1 schemas" → `tenant_56399d06a773`: 9 "up to date … 0 migrations applied"; directory backfill "0 tenants scanned, 1 skipped (already marked)"; health `{"status":"UP"}` |

Known residue left on purpose (brief §4.4): `public.job_queue` COMPLETED rows; `portal.portal_contact_directory_backfill` marker for `tenant_5039f2d497cf`.

## Scenario steps
| Step | Result | Evidence (what was observed) | Mailpit ID | Screenshot | Gap |
|---|---|---|---|---|---|
| 0.Aa | FAIL | As written (`tenant_mathebula*`): `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_mathebula%'` → 0 rows, both before the reset (census lists only `tenant_5039f2d497cf`, `tenant_56399d06a773`) and after. The literal check finds nothing to drop, so it would have left the real Mathebula schema `tenant_5039f2d497cf` in place. Class: scenario-mismatch — §5a row 1 pending founder authorisation (ruling C-9) — `logs/session0-checks.txt`, `logs/session0-reset.txt` census | — | — | G88-M1 (§5a row 1) |
| 0.Ab | PASS | Product path: the org's schema from `public.org_schema_mapping` (`mathebula-partners → tenant_5039f2d497cf`) dropped by R.3 ("drop cascades to 103 other objects"); R.6 read-back: only `tenant_56399d06a773` remains — `logs/session0-reset.txt:88-91` (R.3), `:211-215` (R.6) | — | — | — |
| 0.B | PASS | R.1: KC `docteams` users before bob@/carol@/thandi@mathebula-test.local + padmin@; after the org DELETE (204) only `padmin@docteams.local`; orgs none — `logs/session0-reset.txt` | — | — | — |
| M.1 | PASS | `docker ps`: b2b-keycloak "Up 6 days (healthy)", b2b-localstack / b2b-postgres / b2b-mailpit "Up 11 days (healthy)"; `dev-up.sh` not needed (infra already up) — `logs/session0-checks.txt` | — | — | — |
| M.2 | N/A | Not run: realm `docteams` and `padmin@docteams.local` already exist; the bootstrap would reset passwords (623A precedent) | — | — | — |
| M.3 | PASS | 629.1 `svc.sh start all` (backend/gateway/frontend/portal all `yes yes`, fresh PIDs from `$MAIN`) + R.6 `svc.sh start backend` ("ready (15s)") — README § KC-STACK, `logs/kc-stack-start.txt` | — | — | — |
| M.4 | PASS | After R.6 (13:44:15Z): 8080/actuator/health 200 `{"status":"UP"}`, 8443 200 `{"status":"UP"}`, :3000/ 200, :3002/login 200, :8180/realms/docteams 200 `{"realm":"docteams"}` — `logs/session0-checks.txt` | — | — | — |
| M.5 | PASS | Mailpit before the reset: `{"Messages":0}` (R.5 "count before: 0") — `logs/session0-reset.txt:206-207` | — | — | — |
| M.6a | FAIL | As written `docker exec b2b-postgres psql -U postgres -d app -c "SELECT schema_name … LIKE 'tenant_%';"` → `FATAL:  database "app" does not exist` (exit 2). Class: scenario-mismatch — §5a row 1 pending founder authorisation — `logs/session0-checks.txt` | — | — | G88-M1 (§5a row 1) |
| M.6b | PASS | Product path `-d docteams`: before the reset `tenant_5039f2d497cf` + `tenant_56399d06a773`; the Mathebula schema dropped (R.3); after: `tenant_56399d06a773` only (1 row), mapping `org_phase86_squash_smoke` only — `logs/session0-reset.txt:88-91`, `:211-215`; `logs/session0-checks.txt:13-20` | — | — | — |
| M.7 | PASS | KC `docteams` realm users (read-only on the `keycloak` DB, brief §4.3 — console UI not required): `padmin@docteams.local` only; org count 0 — `logs/session0-checks.txt` | — | — | — |
| M.8 | PASS | R.5 `DELETE /api/v1/messages` → 200; `{"Messages":0,"Unread":0}` after — `logs/session0-checks.txt:27` | — | — | — |
| M.9 | PARTIAL | Playwright MCP context: 1440×900, light; cookies cleared (1 cookie before); localStorage cleared on :3000 (0), :3002 (3 keys → 0), :8180 (0); read-back `[]`/`[]` on :3000 and :3002; the probe navigation left 4 KC/gateway cookies (AUTH_SESSION_ID, KC_AUTH_SESSION_HASH, KC_RESTART, SESSION), cleared again → **0 cookies** at 13:44:5xZ before Phase A. Observed in-session through `browser_evaluate`; **no artefact captured** (review pass 2, same rule as the 629A fixer ruling 9c) | — | — | — |
| CP-1 | PASS | All four services healthy (M.4) — `logs/session0-checks.txt` | — | — | — |
| CP-2 | PASS | `padmin@docteams.local` is the only `docteams` user (M.7) — `logs/session0-checks.txt:23-` | — | — | — |
| CP-3 | PASS | No leftover Mathebula tenant schema (0.Ab / M.6b) — `logs/session0-reset.txt:211-215` | — | — | — |
| CP-4 | PASS | Mailpit 0 (M.8) — `logs/session0-checks.txt:27` | — | — | — |
| CP-5 | PARTIAL | Clean browser session, 0 cookies (M.9): observed in-session, no artefact captured | — | — | — |

**Counts (Session 0, 18 rows = 13 doc rows [0.A and M.6 split] + 5 checkpoints):** PASS 13 · FAIL 2 (0.Aa, M.6a — both G88-M1 / §5a row 1, not new) · PARTIAL 2 (M.9, CP-5 — observed, no artefact; review pass 2) · DEFERRED 0 · N/A 1 (M.2).
