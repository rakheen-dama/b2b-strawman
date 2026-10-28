# 627B (G-D3, matter retention): observation AFTER the fix

- Date: 2026-10-28, 06:38–06:46 UTC
- PR #1694, head `c8371067c` (branch `phase88/10B-retention-model`, worktree `/Users/rakheendama/Projects/2026/worktree-epic-627B`).
- Stack: E2E mock-auth (:3001 / :8081 / `e2e-postgres` db `app`). **Only the backend was rebuilt**, from the 627B worktree:
  `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend`. This is what `e2e-rebuild.sh backend` runs, plus `--no-deps`. I added `--no-deps` because the running stack had been started from the (since removed) 627A worktree. Without it, compose would have recreated localstack and the other dependencies, since their bind-mount paths differ. No data was wiped: no reseed, no `down`, and the named volumes are untouched. The frontend container (built from 627A/main) is valid for this slice because the PR has no frontend/portal/packages changes (`git diff --stat main...HEAD -- frontend portal packages` is empty).
- New code confirmed live:
  - tenant `flyway_schema_history` rank 8 = `8 phase88 retention comment attachments`, installed 06:40:26.
  - `running-jar-javap.txt`: javap of the jar inside the running container. `RetentionPolicyRepository` no longer has `insertIfAbsent`, `MatterClosureService` has no retention-policy member, and `Project.closeMatter(Instant, int)` plus the `retentionEndsOn` field are present.
- Tenant `tenant_7d218705360b`, user Alice (owner) via `/mock-login`. Every state change was made through the UI (Playwright). The DB was only read, with SELECTs (`sql.txt`). No code changes, nothing committed.

## Matters

| Matter | id | Use |
|---|---|---|
| 627B retention repro (from `before/`) | `7c28d1cf-6fc5-428a-9690-054f49ae7370` | read-only: V8 backfill + card |
| Apex Holdings — Shareholders' Agreement Review mvrovhe0b72d (depth-ii) | `68b5e1e8-ece3-479e-88e9-ffdd7b38b364` | read-only: V8 backfill + card |
| Ndlovu v RAF — Depth II mvrovhe0b72d (depth-ii, also CLOSED) | `077a4ed6-91b6-4475-97b4-c84b440ad1c2` | read-only: V8 backfill |
| **627B observer O3-O6** (new; client Sipho Ndlovu mvrovhe0b72d `3c1891ee-…`, no work type, so 0 tasks) | `7d0aaa19-fad5-41d2-89a5-c34bd5639767` | Created 06:41:24 → Complete 06:41:42 → Close 06:42:50 (override FINAL_BILL) → Reopen 06:43:46 → Close 06:44:25 (override) → *extra:* Reopen 06:45:28 → Complete 06:45:35 (left COMPLETED) |

## O-table

| # | Observation | Verdict | Evidence |
|---|---|---|---|
| O1 | V8 migration backfill | **PASS** | `org_settings.legal_matter_retention_years` = **5** (was NULL in `before/`). All 3 CLOSED matters have `retention_ends_on = 2031-10-28`, which equals `COALESCE(closed_at, clock)::date + 5y`: `68b5e1e8` closed 05:45:57, `7c28d1cf` closed 06:08:47 (legacy clock 06:06:30 kept, anchor = closed_at as specified), `077a4ed6` closed 05:46:02. By status: ACTIVE 9/0 with `retention_ends_on`, CLOSED 3/3, no COMPLETED rows at that point. Index exists: `idx_projects_retention_ends_on … btree (retention_ends_on) WHERE (retention_ends_on IS NOT NULL)`. Column `retention_ends_on date NULL`. |
| O2 | Card after backfill | **PASS** | `7c28d1cf`: `[data-testid=retention-card][data-state=active]`, text "Clock started on 28 Oct 2026. This closed matter will be permanently deleted on 28 Oct 2031 (1826 days remaining). View data-protection settings" (`01-*.png`). `68b5e1e8` shows the identical state and text (`02-*.png`). In `before/` both cards were `unconfigured` / "isn't configured". |
| O3 | Completion does not start the clock | **PASS** | After UI Complete: `COMPLETED, completed_at 06:41:42.542, closed_at NULL, retention_clock_started_at NULL, retention_ends_on NULL`. Card `data-state=pre-clock`: "Retention clock not yet stamped. Close this matter from the actions menu to start the retention period (5 years by default; your firm's setting overrides)." (`03-*.png`) |
| O4 | Close stamps the clock + end date | **PASS** | Closure action result (`responses.json`, raw `network-115-*.txt`): `closedAt 2026-10-28T06:42:50.690328345Z`, **`retentionEndsAt "2031-10-28"`**. DB: `closed_at 06:42:50.690328+00`, `retention_clock_started_at 06:42:50.690328` (equal to closed_at, `t`), **`retention_ends_on 2031-10-28`** = closed_at UTC date + 5y = response value. The refreshed MatterResponse in the same payload has `retentionEndsOn "2031-10-28"`. Card `active` "…permanently deleted on 28 Oct 2031 (1826 days remaining)" (`04-*.png`). `retention_policies`: 3 rows before and after, MATTER row `8334a16d` `updated_at` unchanged (05:45:57). See the caveat below. |
| O5 | Reopen clears the end date | **PASS** | After UI Reopen: `ACTIVE, closed_at NULL, retention_ends_on NULL, retention_clock_started_at 06:42:50.690328` (unchanged). The closure log row `b65e805c` now has `reopened_at 06:43:46`. ACTIVE renders no retention card (`05-*.png`). The reopen dialog showed only the "Reopen notes (≥ 10 characters)" field, with no guard text. |
| O6 | Re-close re-stamps | **PASS** | Re-close ran ACTIVE→CLOSED, 95 s after O4. Action result `closedAt 2026-10-28T06:44:25.034385166Z`, `retentionEndsAt "2031-10-28"` (raw `network-196-*.txt`). DB: `closed_at 06:44:25.034385`, clock `06:44:25.034385`, which equals closed_at and is strictly later than O4's 06:42:50 (`clock_later_than_o4 = t`). `retention_ends_on 2031-10-28`: re-stamped from NULL (O5) and equal to the response. New closure log `d2781c01`. `retention_policies` still 3 rows, MATTER `updated_at` unchanged. Card `active`, 28 Oct 2031 (`06-*.png`). |
| O7 | Setting readable via GET /api/settings | **PASS** (read) | The frontend fetches settings server-side, so the request is not visible in browser network. I minted a mock-idp token instead: stateless JWT from `POST :8090/token {userId:user_e2e_alice, orgSlug:e2e-test-org}`, with no app mutation. Then a read-only `GET :8081/api/settings` returned HTTP 200 with **`legalMatterRetentionYears: 5`** (`settings-get.json`). **Years PATCH: covered by integration tests only; the UI arrives in 627C.** No PATCH was sent. |
| O8 | Reopen guard on legacy data | **N/A** | Not forced. Both reopens were well inside the window, and no guard or retention text appeared in the reopen dialog. |

**Caveat on "closing no longer inserts a MATTER `retention_policies` row":** the tenant already had the MATTER row (from the first depth-ii closure on the old code), and UNIQUE `(record_type, trigger_event)` would have absorbed a re-insert anyway. "Count unchanged" therefore does not discriminate here. `pg_stat_user_tables.n_tup_ins` stayed 3, but the old `ON CONFLICT DO NOTHING` path did not bump it either. The behavioural proof is the running-jar javap (`insertIfAbsent` and the `RetentionPolicyRepository` dependency are gone from the deployed classes). Observing "no row on an empty table" would need a fresh tenant, and I did not do that.

## Extra observation (not in the brief): stale "Anchor preview" after reopen → complete

I reopened `7d0aaa19` again (06:45:28) and marked it COMPLETED (06:45:35) through the UI. DB: `COMPLETED, closed_at NULL, retention_ends_on NULL, retention_clock_started_at 06:44:25.034385`. The clock is kept from the previous close, as specified. The card then renders `data-state=completed-pending-close`: "Retention will begin when this matter moves to Closed. **Anchor preview: 28 Oct 2026**. Default retention period is 5 years; your firm's setting overrides." (`07-*.png`).

After this slice, the only way to reach `completed-pending-close` is a matter that was closed, reopened and then completed. The "Anchor preview" in that state is the *previous* close instant, which the next close overwrites, so it is not a real preview. A first-time COMPLETED matter lands in `pre-clock` instead (O3). This is frontend card copy and state logic, so it belongs to 627C's card rework, not a backend defect. It is reported here so 627C can take it into account.

## E2E spec grep

`grep -rn "retention\|isn't configured\|Scheduled deletion" frontend/e2e/tests` gives:
- `settings/data-protection-settings.spec.ts:5,134,141–173,180–211`: retention-*policies* table on Settings → Data protection. Unaffected because this slice does not touch the frontend or the policies table.
- `legal-depth-ii/day-75-85-closure.spec.ts:292` (**not fixme**): `expect(retention-card).toContainText(/clock started on/i)`. Before, the unconfigured text was "Retention clock started on…". Now the active text is "Clock started on…". The case-insensitive regex matches both, so it **still passes**.
- `legal-depth-ii/day-75-85-closure.spec.ts:303–322` (**`test.fixme`**, `FIXME(depth-ii:retention-period-unconfigurable)`) asserts `data-state=active`, `/permanently deleted on/` and `String(currentYear + 5)`. The observed card now satisfies all three ("…permanently deleted on 28 Oct 2031…", state `active`). This fixme is now an **un-fixme candidate** (627C or the epic gate). Its comment text describing the bug is stale after this slice.
- No other spec under `frontend/e2e` references `retention-card`, `isn't configured`, "Anchor preview", `pre-clock` or `completed-pending-close`.

**Conclusion:** the visible behaviour change (the card shows a date for closed matters, and COMPLETED shows `pre-clock`) does not break any non-fixme e2e assertion.

## Files
- `sql.txt`: every SELECT with verbatim output, with a timestamped header per phase
- `responses.json`: both closure action results, plus the refreshed MatterResponse retention fields
- `network-115-close1-action-rsc-payload.txt`, `network-196-close2-action-rsc-payload.txt`: raw RSC payloads of the two close actions
- `settings-get.json`: GET /api/settings response (token not stored)
- `running-jar-javap.txt`: proof that the deployed classes are the 627B code
- `01`–`07` screenshots
