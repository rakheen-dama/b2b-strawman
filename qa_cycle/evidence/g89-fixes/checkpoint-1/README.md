# G89 fix initiative — Checkpoint 1 (e2e regression gate after #1745, #1746, #1747)

**Verdict: PASS. No e2e regressions on main @ `d6742789f`.**

Every scored run finished with 0 failed, 0 flaky and 0 did-not-run. Each suite's passed and skipped counts equal the 636A gate. The per-test outcomes are also identical to 636A: diffing file + title + status + last-result against `qa_cycle/evidence/phase89-rewalk/suites/raw/*.tsv` gives an empty diff for all seven scored runs.

No failure was classified, so no spec changes are requested. There was one infrastructure event: the first G1 stack build failed on a transient Google Fonts fetch, and a retry on the same context succeeded. See "Infrastructure events". It did not affect any scored run.

- Source: detached worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp1` @ `d6742789f023869116457889f7b65ba3288e59c3` (origin/main). That is 29 commits after 636A's `1034b6af8` and contains #1745, #1746 and #1747.
- Recipe: 636A (`qa_cycle/evidence/phase89-rewalk/suites/README.md`). Four fresh stacks: C1 capture legal → G1 scored legal + default → C2 capture portal → G2 scored portal. Every stack was built with `bash compose/scripts/e2e-down.sh && SHELL=/bin/bash bash compose/scripts/e2e-up.sh`, run nohup-detached from the worktree. `e2e-reseed.sh` was never used. Unlike 636A, no `docker builder prune -f` was run, per the CP1 brief.
- Run window: 2026-11-07, 02:53–03:41 SAST. This is outside 00:00–02:00. `caffeinate -dims` was held for the whole run, on AC power.

## Verdict table

The counts come from the Playwright JSON reporter `.stats` (`raw/<label>.json`), cross-checked against the list-reporter summary (`raw/<label>.txt`). The two agree for every suite. "Did not run" counts the `notrun` last-results in `raw/<label>.tsv`.

| Suite | Stack | Passed / failed / skipped / did not run | 636A baseline | Per-test diff vs 636A | Status |
|---|---|---|---|---|---|
| legal-lifecycle | G1 | **105 / 0 / 0 / 0** (3.0 m) | 105 / 0 / 0 / 0 | identical | PASS |
| legal-depth-ii | G1 | **39 / 0 / 4 / 0** (1.9 m) | 39 / 0 / 4 / 0 | identical (same 4 skips) | PASS |
| default (`test:e2e`) | G1 | **165 / 0 / 14 / 0** (54 s) | 165 / 0 / 14 / 0 | identical (same 14 skips) | PASS |
| portal vitest | — | **49 files / 278 tests passed** | 47 files / 273 tests | +2 files / +5 tests, added by the PRs (below) | PASS |
| portal 90-day | G2 | **13 / 0 / 0 / 0** (19.6 s) | 13 / 0 / 0 / 0 | identical | PASS |
| portal responsive | G2 | **21 / 0 / 0 / 0** (24.4 s) | 21 / 0 / 0 / 0 | identical | PASS |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | **2 / 0 / 0 / 0** | 2 / 0 / 0 / 0 | identical (both passed, not skipped) | PASS |
| login-bare (optional, production portal) | G2 | **1 / 0 / 0 / 0** (17.8 s) | 1 / 0 / 0 / 0 | identical | PASS |

**Portal vitest delta.** The extra 2 files are new in the PRs, per `git diff --diff-filter=A 1034b6af8 HEAD`: `portal/app/(authenticated)/home/__tests__/trust-card.test.tsx` and `portal/lib/__tests__/labels.test.ts`. Existing files gained tests too (for example `deadline-list.test.tsx`, `transaction-list.test.tsx`).

The vitest output contains the same happy-dom `AbortError` noise on `localhost:8080/api/portal/acceptance/test-token/pdf` that 636A recorded (21 lines). No test failed.

**Snapshot writes on scored runs: 0.** Every scored run printed no "writing actual" lines, so every frame was compared against its capture baseline.

**Capture runs** (unscored, `--update-snapshots=all`):

| Run | Result | Frames written |
|---|---|---|
| C1-lifecycle | 105 / 0 / 0 | 26 |
| C1-depth-ii | 39 / 0 / 4 | 23 |
| C2-90day | 13 / 0 / 0 | 11 |
| C2-responsive | 21 / 0 / 0 | 21 |

The frame counts match 636A exactly (26 + 23, 11 + 21). See `raw/C*.summary.txt`.

### Named specs (all `expected` / `passed`)

| Spec | Run | Result |
|---|---|---|
| `day-60-interest.spec.ts:97` create + calculate. Now asserts the "Client" column, the client name (never the UUID) and "LPFF arrangement rate 50.00%", as amended in #1746 for G89-F10 | G1-lifecycle | ✓ |
| `day-60-interest.spec.ts:173` Bob approves / `:194` Alice posts (were `:168` / `:189` in 636A; shifted +5 by the #1746 spec edit) | G1-lifecycle | ✓ ✓ |
| `day-90-review.spec.ts:118` Section 35 Data Pack | G1-lifecycle | ✓ |
| `customer-data-protection.spec.ts:47` / `:77` / `:108` / `:150` / `:165` | G1-default | ✓ ✓ ✓ ✓ ✓ |
| `project-tasks.spec.ts:277` case 8 / `:322` case 9 | G1-default | ✓ ✓ |
| `day-14-30.spec.ts:149` receipt for a part payment | G2-90day | ✓ |
| `day-45-75.spec.ts:124` Day 60 SoA / `:181` Day 75 Diary dates | G2-90day | ✓ ✓ |

### Skips (all on the known-skip list, all with last-result `skipped`, none `notrun`)

- **depth-ii (4):**
  - `day-30-45-soa-writeoff.spec.ts:292` (`test.fixme`)
  - `null-prefetch-probe.spec.ts:74` ×3 (opt-in, `NULL_PREFETCH_PROBE` unset)
- **default (14):**
  - `customer-lifecycle.spec.ts:157`, `:178` (fixme)
  - `roles-capabilities.spec.ts:53` (fixme)
  - `portal-data-isolation.spec.ts:36`, `:48` (no `KGOSI_EMAIL` in the default run; scored separately on G2: 2 passed)
  - `auth-change-password.spec.ts:35` (KC-only)
  - `admin-audit-lifecycle.spec.ts:33, 52, 70, 88, 108, 125, 143, 159` (`test.skip(true, …)`)

## Stack table

Full checks are in `raw/stacks.txt` and the timeline in `raw/timeline.txt`. Every stack passed its health checks: backend `UP`, frontend 200, mock-idp 200, mailpit 200. Every stack logged seed `E2E Boot-Seed Complete!` and was built from compose working_dir `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp1/compose`.

| Stack | Purpose | Build start → end (SAST) | rc | Backend / frontend image | Tenant | Tenant / global Flyway | Pristine state |
|---|---|---|---|---|---|---|---|
| C1 | capture legal | 02:53:24 → 03:00:33 (cold backend build: Maven downloads, new code) | 0 | `f59cb6e9cdc1` / `c780864d8d90` | `tenant_7d218705360b` | **12** (g89 interest matter allocation) / **4** (portal trust transaction date) | `projects` = `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |
| G1 (attempt 1) | scored legal + default | 03:06:01 → 03:06:21 | **1** | — (frontend `next build` failed, no containers created) | — | — | — |
| G1 | **scored** legal + default | 03:06:48 → 03:08:11 | 0 | `f59cb6e9cdc1` / `9a3d2eaf43b1` | same | **12 / 4** | `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |
| C2 | capture portal | 03:14:44 → 03:15:24 | 0 | `f59cb6e9cdc1` / `9a3d2eaf43b1` | same | **12 / 4** | `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |
| G2 | **scored** portal | 03:22:18 → 03:22:57 | 0 | `f59cb6e9cdc1` / `9a3d2eaf43b1` | same | **12 / 4** | `Website Redesign\|ACTIVE`; `portal_deadline_view` = 0 |

**Image notes**

- The backend image `f59cb6e9cdc1` is the same on all stacks. It was built from `d6742789f` on C1, and later stacks reused the cached layers. BuildKit's content-addressed `COPY src` makes them byte-identical to the source.
- The frontend changed from C1 to G1 (`c780864d8d90` → `9a3d2eaf43b1`). This is the known 636A-c1 effect: the untracked C1 legal baselines under `frontend/e2e/screenshots/` enter the build context. C2 and G2 reused `9a3d2eaf43b1`. The standalone output does not ship `e2e/`, so there is no runtime effect.

**Freshness proof**

- C1: no `e2e-*` container or stack was up beforehand. `docker ps` at start showed only the `b2b-*` KC-infra containers.
- G1 attempt 1, C2 and G2: each ran `docker compose down -v`. Their logs show `Volume docteams-e2e_e2e_postgres_data Removed` and `Volume docteams-e2e_e2e_localstack_data Removed` (`raw/stack-g1-attempt1-font-fetch.txt`).
- G1 retry: attempt 1 had already removed the C1 volumes and failed at build time, before any container was created. The retry therefore started from no containers and no volumes, and its Postgres container was created at 01:07:36Z.
- Every stack's `projects` table held only the seed row. `portal_deadline_view` was 0 on every stack.

## Portal magic-link spacing (≥ 330 s + `docker restart e2e-backend` between suites)

| Before | Tokens / newest-token age at wait start | Age when released | Backend restarted + healthy (SAST) | :3002 |
|---|---|---|---|---|
| C2-responsive (capture) | 3 / 19 s | 330 s | 03:21:32 | free |
| G2-90day (fresh stack) | 1 / 3 s (seed) | — | — | free |
| G2-responsive | 3 / 15 s | 332 s | 03:28:51 | free |
| G2-isolation | 4 / 16 s | 332 s | 03:34:50 | free |
| G2-login-bare | 6 / 0 s | 333 s | 03:40:42 | free |

**Non-vacuity.** The "Exchanged magic link" counts in the backend log are cumulative across restarts: 1 after 90-day, 2 after responsive, 4 after isolation, 5 after login-bare. This is identical to 636A.

**G2-90day after-checks.** `portal_deadline_view` = 0, so the Day 75 cleanup held. `REQ-0002 | IN_PROGRESS` was created by this run; `REQ-0001 | SENT` is the seed row.

The KC dev stack's app services were not touched. :3002 was free throughout, and each portal Playwright `webServer` self-served and stopped the portal.

## Failure classification

**No scored-run failures. No capture-run failures. Nothing to classify as (a), (b) or (c).**

The changes the brief listed as expected did not break any spec:

- The only spec touched by the PRs is `day-60-interest.spec.ts`, already updated in #1746. It passed in both C1 and G1.
- The lifecycle trust checks also passed:
  - `day-07-14-work-trust.spec.ts:221` "Verify Moroka trust ledger balance"
  - `day-30-billing.spec.ts:223` "Moroka trust balance R241,500"
  - `day-60-interest.spec.ts:194` "Post the approved interest run to the client ledgers"
  - `day-90-review.spec.ts:148` "Client Trust Balances"

  These ran against the new Ledger-totals card, the matter-balance Trust tab, interest credits that carry a matter, and the matter-debit guard.
- The portal 90-day and responsive frames passed with the transaction-date trust rows and the humanised deadline badges.

## Infrastructure events (not test failures)

**E1 — G1 attempt 1: frontend image build failed (transient external fetch). Class (c), proven by retry.**

- **Error** (`raw/stack-g1-attempt1-font-fetch.txt`): `[frontend builder 11/11] RUN pnpm --filter frontend build` → `Error: Turbopack build failed with 24 errors: [next]/internal/font/google/ibm_plex_sans_63208076.module.css:8:8 Error: Module not found: Can't resolve '@vercel/turbopack-next/internal/font/google/font'`, then `ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL`. Elapsed 14 s.
- **Cause:** this is Turbopack's `next/font/google` failure mode when the build cannot download the font files from `fonts.gstatic.com`.
- **Proof it was transient:**
  - Immediately afterwards, `fonts.googleapis.com` and `fonts.gstatic.com` both returned 200 from the host.
  - The retry, 27 s later from the identical build context with no file changes, completed the same step: `#74 [frontend builder 11/11] … DONE 44.9s`, stack rc 0.
  - C1 had built the same `next build` successfully 7 minutes earlier.
- **Impact:** no scored run ran on the failed attempt.
- **Observation, not filed as a defect:** the e2e frontend image build has a hard network dependency on Google Fonts at build time.

## Observations

1. **Screenshot scope.** As in 636A, baselines are captured on the same sha (C1/C2) and then scored (G1/G2). The screenshot assertions therefore prove that the frames are deterministic between two fresh stacks at `d6742789f`. They do not prove visual equality with 636A. The intended visual changes are not regressions in this design and could not show up as diffs: the Trust tab primary figure, the Ledger-totals card and the portal trust dates.
2. **`projects` after G1-default holds 25 rows**, where 636A recorded 2. The rows are the seed `Website Redesign|ACTIVE` (still ACTIVE, so the #1739 guard holds), `E2E closure override lxjd|CLOSED`, the lifecycle and depth-ii matters (`… mw5pfllz7767`, `… mw5pjhp2c149`) and the default-suite matters (`raw/stacks.txt` → "G1 projects after default"). This is the expected footprint of the three suites. 636A's statement that "the legal suites' matters are evidently not written to this table" does not reproduce here, and that 2-row observation looks like it was a 636A recording artefact. No test depends on it.
3. **Worktree state.** The Playwright legal and portal configs left 107 untracked PNGs (`documentation/screenshots/**`, `frontend/e2e/screenshots/**`, `portal/e2e/screenshots/**`), the same count as 636A. The throwaway worktree is left in place and nothing was committed.

## Raw files (`raw/`)

- Scored runs: `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.{json,tsv,txt}`.
  - JSON is the Playwright JSON reporter output; `.stats` is the source for every count.
  - TSV columns: file, line, title, status, last-result.
  - TXT is the list reporter output.
- Capture runs: `C1-{lifecycle,depth-ii}.summary.txt`, `C2-{90day,responsive}.summary.txt`.
- `portal-vitest.txt`
- `stacks.txt`: per-stack checks, image ids, Flyway versions, spacing, non-vacuity, G1 post-default projects.
- `timeline.txt`
- `stack-g1-attempt1-font-fetch.txt`: E1 evidence.

**Reproduce the per-test diff:**

```
diff <(cut -f1,3,4,5 ../../phase89-rewalk/suites/raw/<L>.tsv | sort) <(cut -f1,3,4,5 raw/<L>.tsv | sort)
```

Run it from this directory. It prints nothing for all seven scored labels; this was re-checked from the docs worktree.

## Environment left behind

- `bash compose/scripts/e2e-down.sh` was run at the end. 0 `e2e-*` containers remain and the volumes were removed.
- `caffeinate` was stopped.
- The worktree `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp1` was left in place. It has `node_modules` installed by `pnpm install --frozen-lockfile` and the 107 untracked PNGs. Nothing was committed.
- The KC dev stack app services, the main checkout and the builder's worktree were not touched.
