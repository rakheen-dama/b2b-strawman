# 670A — exit suites, gates on `main`, verify markers (tasks 670.1–670.3)

**Head under test:** `main` @ **`c4595f9246d41988a109105aa714edbfb0a6f491`** (2026-11-27). Every Phase 91 slice is merged (#1862–#1882). Before the run, `git fetch origin` and `git rev-parse HEAD origin/main` both returned `c4595f924`, so `main` had not moved.
**Where:** worktree `worktree-epic-670A`, branch `phase91/70A-exit-gate`, created at `origin/main` with no commits before this evidence.
**Runner:** [`run-exit.sh`](run-exit.sh), committed exactly as it ran. It runs one job at a time on the machine, in this order: fresh stack B → exit-month (with the portal dev server on :3002) → fresh stack A → legal-lifecycle → legal-depth-ii → default → FE-GATE → PORTAL-GATE. Setup:
- nvm node **v20.11.1**; pnpm 10.26.1 called by absolute path `/opt/homebrew/bin/pnpm`; `SHELL=/bin/bash NODE_OPTIONS=""`.
- `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 MAILPIT_URL=http://localhost:8026`.
- No flags were passed, so the configs' default reporter applied (the same as 661B).
- Before each stack and before the gates, the runner checks that no Maven or surefire JVM is running (none was).
- A `caffeinate -dimsu -t 43200` hold was already running, and the runner itself ran under `caffeinate -dimsu`.

**Invocation (from the worktree root):** `cp qa_cycle/evidence/phase91-ui/suites/run-exit.sh /tmp/670A/run-exit.sh && nohup caffeinate -dimsu bash /tmp/670A/run-exit.sh > /tmp/670A/run1.nohup 2>&1 &`. The run was then polled in bounded loops until `/tmp/670A/run1/DONE` appeared. It ended with `end BAD=0` and `DONE`; there is no `FAILED` file.
**Raw timeline:** [`summary.txt`](summary.txt) (UTC timestamps, every exit code, pass counts as printed). Full logs stay in `/tmp/670A/run1/` and are not committed.

## Verdict

**Every suite and gate is green on the merged `main` head, and every count is at or above the 661B baseline. The pass/skip sets match the baseline exactly, except for the one expected change (665A re-enabled `roles-capabilities.spec.ts:53`, which now passes). There were no failures and no flaky or retry lines, so nothing needed triage (670.3 not triggered). PHASE REGRESSION: none.**

## Results vs the 661B baseline (`qa_cycle/evidence/phase91-ui/baseline/README.md`, `main` @ `551778100`)

| Gate | Command (from `frontend/` or `portal/`) | Run (UTC, 2026-11-27) | Baseline (661B @`551778100`) | Exit (670A @`c4595f924`) | Δ |
|---|---|---|---|---|---|
| E2E-EXIT-MONTH (stack B, own fresh stack + portal :3002) | `PORTAL_BASE_URL=http://localhost:3002 EXIT_MONTH_EVIDENCE_DIR=/tmp/670A/run1/exit-month-evidence pnpm test:e2e:exit-month` | 11:58:45 → 11:59:59 (74 s) | 16/16 passed (stack C) | **16 passed / 0 failed / 0 skipped**, rc 0 | = |
| E2E-LEGAL lifecycle (stack A) | `pnpm test:e2e:legal-lifecycle` | 12:00:44 → 12:03:37 (173 s) | 105 passed | **105 passed / 0 failed**, rc 0 | = |
| E2E-LEGAL depth-ii (stack A) | `pnpm test:e2e:legal-depth-ii` | 12:03:37 → 12:05:27 (110 s) | 40 passed + 4 skipped | **40 passed + 4 skipped / 0 failed**, rc 0 | = (same 4 skips) |
| E2E-DEFAULT (stack A, 180 tests, 5 workers) | `pnpm test:e2e` | 12:05:27 → 12:06:28 (61 s) | 166 passed + 14 skipped | **167 passed + 13 skipped / 0 failed**, rc 0 | +1 passed / −1 skipped: `settings/roles-capabilities.spec.ts:53` "Create, edit and delete a custom role" re-enabled by 665A (#1870) and now ✓ (4.6 s) |
| FE-GATE (`frontend/`, `rm -rf .next` first) | `pnpm lint`, `typecheck`, `format:check`, `test`, `build` | 12:06:28 → 12:09:22 | lint 0 errors / 76 warnings; vitest 508 files, 4980 passed + 2 skipped; typecheck, format and build rc 0 | lint rc 0, **0 errors / 75 warnings**; typecheck rc 0; format:check rc 0; **vitest 549 files passed (549); 5622 passed, 2 skipped (5624)**, rc 0; build rc 0 | +41 files, +642 tests. This matches the D-97 PR-head totals exactly. Lint warnings −1 |
| PORTAL-GATE (`portal/`) | `pnpm lint`, `typecheck`, `format:check`, `test`, `build` | 12:09:22 → 12:09:45 | lint 0 errors / 4 warnings; vitest 51 files / 293 | lint rc 0, **0 errors / 4 warnings**; typecheck, format:check rc 0; **vitest 51 files passed (51); 293 passed (293)**, rc 0; build rc 0 | = |

The test counts can be checked in the committed tails ([`B-exit-month-tail.txt`](B-exit-month-tail.txt), [`A-legal-lifecycle-tail.txt`](A-legal-lifecycle-tail.txt), [`A-legal-depth-ii-tail.txt`](A-legal-depth-ii-tail.txt), [`A-default-tail.txt`](A-default-tail.txt), [`fe-test-tail.txt`](fe-test-tail.txt), [`portal-test-tail.txt`](portal-test-tail.txt)). Every suite printed the expected "Running N tests": exit-month 16, lifecycle 105, depth-ii 44, default 180.

**Flaky / retried:** `grep -ciE "flaky|retry #"` returns 0 on all four suite logs. Retries are 0 locally in every config.

**Set comparison** against `baseline/suite-summaries.txt`, by test title with the run index stripped:
- **depth-ii skipped set:** identical (4). These are `day-30-45-soa-writeoff.spec.ts:361` and 3 × `null-prefetch-probe.spec.ts:74`, the opt-in probes.
- **default skipped set:** the baseline's 14 minus `settings/roles-capabilities.spec.ts:53`, leaving 13:
  - `auth/auth-change-password.spec.ts:35`
  - 8 × `admin-audit-30day/admin-audit-lifecycle.spec.ts` (33, 52, 70, 88, 108, 125, 143, 159)
  - `customers/customer-lifecycle.spec.ts:157`, `:178`
  - `portal/portal-data-isolation.spec.ts:36`, `:48`. These skip without `KGOSI_EMAIL`, and the gate was kept like-for-like with the baseline. A skip is not a pass.
- No test failed in any suite, so the failing sets are empty both at baseline and at exit. The baseline's stack-A exit-month precondition failure was an ordering artefact; 670A avoided it by giving exit-month its own stack (D-6).

## Fresh stacks (670.1)

The orchestrator set the order: **stack B (exit-month) first, stack A (legal + default) last.** That leaves the data-rich stack A running for Stage M. Each suite set had its own fresh stack, so the results do not depend on the order. Provenance is in [`stack-provenance.txt`](stack-provenance.txt).

| Stack | e2e-down | builder prune | e2e-up | Seed proof | Health (8081 `/actuator/health`, 3001 `/`, 8090 `/.well-known/jwks.json`, 8026 `/`) | Containers created (UTC) |
|---|---|---|---|---|---|---|
| B | rc 0 (11:57:03; tore down the stale stack built from the deleted `worktree-epic-669B`) | rc 0 (36.24 MB) | rc 0, 95 s | `docker inspect e2e-seed` ExitCode **0** | all 4 ok | 11:58:06 |
| A | rc 0 | rc 0 (2.203 GB) | rc 0, 38 s | ExitCode **0** | all 4 ok | 12:00:10 |

- **Build SHA:** `c4595f924` for both stacks, built from this worktree. The `com.docker.compose.project.working_dir` label on `e2e-frontend` and `e2e-backend` is `/Users/rakheendama/Projects/2026/worktree-epic-670A/compose`.
- **Frontend image:** `sha256:df65a3425a53…`, built 2026-11-27 11:58:05Z by stack B. Stack A reused it from the layer cache; `docker builder prune -f` only removes dangling cache.
- **Backend image:** `sha256:e7fd89e47741…`. This is the same image id the stale 669B-built stack ran. The backend build was a full layer-cache hit, because the backend inputs are byte-identical. Phase 91 changed no backend code: `git diff --name-only 551778100..c4595f924 -- backend gateway` is empty.
- **Portal for exit-month:** started by the runner with `cd portal && NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 nohup pnpm dev` (:3002, the only CORS-allowed portal origin on the e2e backend). It answered after 10 s. The runner stopped it after the suite: rc 143 is the expected SIGTERM, and :3002 had 0 listeners at 12:00:02Z.
- **Tree restore:**
  - After stack B, `git clean -n` listed 0 paths.
  - After stack A, the legal configs had written **25 untracked curated PNGs** under `documentation/screenshots/legal-vertical/`. They were archived to `/tmp/670A/run1/artefacts-A/`, then removed with `git clean -fdq -- frontend/test-results frontend/e2e/screenshots documentation/screenshots portal/test-results`. The tracked `.gitkeep` and `README.md` were untouched.
  - `git status --short` after each restore, and at the end, showed only `?? qa_cycle/evidence/phase91-ui/suites/`.
- **Stack A is left RUNNING** for Stage M. It was healthy at ≈12:14Z: frontend image `df65a3425a53`, containers created 12:00:10Z, seeded, and holding the lifecycle, depth-ii and default suite data.
  - One portal magic link for `contact@acme.example.com` was used on it at 12:11:10Z by the PORTAL-GATE observation below. Stage M should keep this in mind for the magic-link cap of 3 per contact per 5 min.
  - No portal dev server is running now. To restart it: `cd /Users/rakheendama/Projects/2026/worktree-epic-670A/portal && SHELL=/bin/bash NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 /opt/homebrew/bin/pnpm dev` (:3002).

## Portal observation (PORTAL-GATE, 670.2)

PORTAL-GATE includes "portal `/home` and `/login` observed at 1024 light". It was observed on 2026-11-27 between ≈12:10:56Z and 12:11:40Z through **Playwright MCP** (`mcp__playwright__*`). The MCP profile was free; no library-script fallback was needed. Setup:
- Portal dev server from this worktree on :3002 (`NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm dev`), against stack A after its suites had run.
- Viewport 1024×768, light. The portal is never dark (FD-2).
- `portal/` is unchanged across Phase 91: `git diff --stat 551778100..c4595f924 -- portal` is empty. The portal is affected only through `packages/` (3 files, +15/−3).

| Page | h1 / content | Screenshot | Console |
|---|---|---|---|
| `/login` | Card "Kazi · Sign in to your client portal". Email field and teal "Send Magic Link" button render correctly in light. (The tile was taken after typing the email.) | [`portal/portal-login-1024-light.png`](portal/portal-login-1024-light.png) | 2 errors: `favicon.ico` 404, and `GET :8081/portal/branding?orgId=mathebula-partners` 404 (the portal's default branding org does not exist on the e2e stack, which seeds `e2e-test-org`; `portal/` is unchanged in Phase 91) |
| `/home` (Acme Corp, `contact@acme.example.com`) | h1 "Home". Four cards: Pending info requests **1**, Upcoming deadlines **0** (Next 14 days), Recent fee notes **INV-0001 R 8 625,00**, Last trust movement "No recent activity". These are the same four cards as the Phase 90 644A observation. | [`portal/portal-home-1024-light.png`](portal/portal-home-1024-light.png) | 0 errors, 0 warnings |

Login path (browser, not REST):
1. `/login` → typed the email → "Send Magic Link". Mailpit received "Your portal access link from E2E Test Organization" at 12:11:10Z.
2. Clicked the dev-mode sign-in link. The backend logged `Exchanged magic link for portal JWT -- contact 00f339d1… customer 56b8af3c…` at 12:11:22.096Z.
3. The page then showed **"Login Failed — Link expired or invalid"**. The console shows `[Fast Refresh] rebuilding`, which was the first `next dev` compile of `/auth/exchange`, followed by a second `POST :8081/portal/auth/exchange` → **401**. The single-use token had already been consumed by the first call, which stored the JWT, because `/home` then loaded as Acme Corp without another link.

This is a `next dev` first-compile remount that re-runs a single-use exchange. It is not a Phase 91 change, because `portal/` has no diff in the range. It is **listed as a finding for Stage D's follow-up list and not fixed here**. Suggested id: F-670A-1, "portal `/auth/exchange` re-runs on dev Fast Refresh, so the second call 401s and the page shows Login Failed although the session was created". Severity is unconfirmed outside `next dev`; it has not been reproduced on a production build.

## Specs amended by phase slices

`git diff --stat 551778100..c4595f924 -- frontend/e2e` lists **8 files, 20 insertions / 26 deletions, no new spec files**. The Playwright configs and `compose/` are unchanged: `git diff --stat 551778100..c4595f924 -- compose/ frontend/e2e/playwright.config.ts frontend/e2e/playwright.legal-lifecycle.config.ts frontend/e2e/playwright.legal-depth-ii.config.ts` is empty.

| Spec | Amended by | Why |
|---|---|---|
| `auth/rbac-capabilities.spec.ts` | 663C (#1867) | K-62: members may open `/clients` |
| `settings/roles-capabilities.spec.ts` | 665A (#1870) | −4 lines: "Create, edit and delete a custom role" (`:53`) **re-enabled**. It was skipped at baseline and passes now |
| `legal-lifecycle/day-00-firm-setup.spec.ts`, `legal-lifecycle/day-90-review.spec.ts`, `settings/rate-cards.spec.ts`, `settings/tax-settings.spec.ts` | 665D (#1873) | K-124: h1 = tab label (6 heading-name lines) |
| `notifications/notification-preferences.spec.ts` | 665F (#1875) | K-115 names |
| `invoices/billing-runs.spec.ts` | 666A (#1876) | K-77 "fee run" copy (3 lines) |

**Correction to the 670.1 task text:** it says "expected: locator scoping in 666B only". That is wrong. 666B (#1877) edited no spec; five slices (663C, 665A, 665D, 665F, 666A) edited the 8 specs above. A second correction for 670.2: a portal marker is also expected for **669A (#1881)**, not only 661B, because 669A touched `packages/shared` (D-92 DA-16). See [`markers.md`](markers.md).

## Triage (670.3)

**Not triggered.** No suite or gate failed, and nothing is below baseline. Nothing was waived and nothing was re-run.

The only non-pass items are the 4 depth-ii skips and the 13 default skips. All of them are already in the baseline's skip set (see the set comparison above), and each is skipped by design: opt-in probes, Keycloak-only tests, skipped capstone specs, and `KGOSI_EMAIL`-gated tests. None is new.

The portal "Login Failed" observation (F-670A-1 above) is outside the gate counts and outside the phase diff. It is listed, not triaged as a regression.

## Manual steps (outside `run-exit.sh`)

1. **Preflight** (before the runner):
   - `pnpm install --frozen-lockfile` at the worktree root: rc 0, 7.1 s.
   - `git fetch` confirmed `HEAD == origin/main == c4595f924`.
   - `node -v` = v20.11.1.
   - No Maven/surefire JVM; :3002 free.
   - The stale e2e stack had been built from the deleted `worktree-epic-669B`; the runner's first `e2e-down` tore it down.
2. **Marker audit** (670.2), run while stack B was building. It is read-only (`gh` API), so it could not overlap any job. Output: [`markers.md`](markers.md).
3. **PORTAL-GATE observation** through Playwright MCP after the runner finished (section above). The portal dev server was stopped afterwards (:3002 has 0 listeners).
4. **Tree check:** `git status --short` shows only `qa_cycle/evidence/phase91-ui/suites/`. `.epic-brief.md` is git-ignored.

## Runner limitations

- The seed is gated on `docker inspect e2e-seed` ExitCode = 0, because `e2e-up.sh` prints "Seed completed (container exited)" from a branch that does not check the exit code. Both stacks reported ExitCode 0. The fallback (seed log "Boot-Seed Complete") was not needed.
- The FE-GATE `build` step runs after `test`, in the same order as 661B. Each step's rc is logged on its own line, and `DONE` is written only when every rc is 0.
- Portal readiness is gated on `curl -sf http://localhost:3002/` within 300 s. If the portal had not come up, exit-month would not have run and the run would have ended `FAILED`.
- `docker builder prune -f` removes only dangling build cache, so stack A reused stack B's frontend image (same SHA, same worktree). That counts as a fresh stack in the 670.1 sense: new containers, new volumes and a fresh seed. It is not a cold image build. The same was true at 661B.
- The runner does not observe the portal; that was a manual step (above).

## Optional extras not run

These were left out on the orchestrator's ruling. Neither is in 670.1:
- `test:e2e:take-on` on its own fresh stack. There is no 661B baseline for it.
- `KGOSI_EMAIL=… portal-data-isolation.spec.ts`, which would turn the 2 environment-dependent skips into a real result.

## Verify markers (670.2)

**21 / 21 exit 0.** No backend area appears in any PR, and the two portal markers are on 661B and 669A. Full table and raw output: [`markers.md`](markers.md).

## Files

| File | What |
|---|---|
| `run-exit.sh` | The runner, exactly as executed |
| `summary.txt` | Runner timeline: UTC timestamps, every rc, counts |
| `B-exit-month-tail.txt`, `A-legal-lifecycle-tail.txt`, `A-legal-depth-ii-tail.txt`, `A-default-tail.txt` | `tail -8` of each Playwright log |
| `fe-test-tail.txt`, `portal-test-tail.txt` | `tail -6` of each vitest log |
| `stack-provenance.txt` | Containers, image ids, creation times and `working_dir` labels for stacks B and A |
| `markers.md` | 670.2 marker audit |
| `portal/portal-login-1024-light.png`, `portal/portal-home-1024-light.png` | PORTAL-GATE observation (Playwright MCP, 1024×768 light) |
