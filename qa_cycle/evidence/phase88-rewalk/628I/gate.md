# 628I — Epic 628 gate (task 628.19)

Orchestrator run, 2026-10-29 12:31–13:12Z (14:31–15:12 SAST), on **main `cb10c5d63`** (= origin/main, contains 628A–628H). Two worktrees at that head: `worktree-628I-be` (detached, BE-VERIFY only) and `worktree-epic-628I` (branch `phase88/11I-gate`: FE/PORTAL gates, e2e stack, Playwright). Keeping them separate avoids the 628C `next build` vs `backend/target` clash. No product code changes in this slice, so there is no PR. This follows the 625G precedent: a gate-only docs commit.

## Verdicts

| Gate | Result | Baseline | Verdict | Evidence |
|---|---|---|---|---|
| **BE-VERIFY** (`./mvnw spotless:apply -q`, then `bash scripts/verify.sh -q`; fresh worktree, no `target/`) | exit 0, 16:52; 745 reports / **6290 testcases** / 0 failures / 0 errors / 26 skipped; spotless produced no diff | 628D 6290 (628E–628H touched no backend) | **PASS** | `logs/be-head.txt`, `.claude/markers/verify-backend.json` |
| **FE-GATE** (`rm -rf .next`, lint, format:check, vitest, build) | lint 0 errors / 79 warnings; format clean; **434 files / 3551 passed / 2 skipped**; build OK | 628F 434/3551/2 | **PASS** | `logs/fe-*-tail.txt`, `.claude/markers/verify-frontend.json` |
| **PORTAL-GATE** (`rm -rf .next`, lint, format:check, vitest, build) | lint 0 errors / 4 warnings; format clean; **45 files / 256 passed**; build OK | 628G 45/256 | **PASS** | `logs/portal-*-tail.txt`, `.claude/markers/verify-portal.json` |
| Guards, run explicitly (vitest reporter hides passing files) | FE `no-terminology-remnants` + `eslint-rules/no-isvisible-catch`: 2 files / 70 passed; portal `no-terminology-remnants`: 1 file / 8 passed; backend `TerminologyRemnantScanTest` inside BE-VERIFY (0 failures) | — | **PASS** | `logs/guards.txt` |
| **E2E-LEGAL** lifecycle (fresh stack: `e2e-down.sh` + `e2e-up.sh` from the worktree; run after BE-VERIFY finished) | **101 passed / 0 failed / 3 skipped** | 628F 101/0/3 | **PASS (= baseline)** | `logs/lifecycle-tail.txt` |
| **E2E-LEGAL** depth-ii (same stack) | **39 passed / 0 failed / 4 skipped** | 628F 39/0/4 | **PASS (= baseline)** | `logs/depth-tail.txt` |
| **E2E-DEFAULT** `pnpm test:e2e` (same stack) | **159 passed / 0 failed / 18 skipped / 0 did not run** | 628F 159/0/18/0 | **PASS (= baseline)** | `logs/default-tail.txt` |
| **E2E-PORTAL** isolation (`KGOSI_EMAIL=contact@acme.example.com`, `e2e/tests/portal/`) | **2 passed / 0 failed** | 628G 2/0 | **PASS** | `logs/e2e-isolation-tail.txt` |
| **E2E-PORTAL** `test:e2e:portal` (chromium/responsive + portal-client-90day; same stack) | **32 passed / 0 failed / 1 skipped**; 0 soft-skips | 628G 32/0/1 | **PASS (= baseline)**, rerun (see the harness note) | `logs/e2e-portal-tail.txt` |
| **E2E-PORTAL** login-bare | **1 passed** | 628G 1/0 | **PASS**, rerun | `logs/e2e-login-bare-tail.txt` |
| Shell overflow, Settings › Documents (Playwright MCP on :3001, Alice) | `scrollWidth` 1440 @ 1440, 1024 @ 1024 (628B *before*: 1471 @ 1024) | — | **PASS (observed)** | this file |
| **Repo-wide grep** `\b(Projects?\|Customers?\|Invoices?)\b` | FE + portal: **0 user-visible** (all 6 production hits are comments). Backend: 0 user-visible inside the R8 census scope (guard green). **4 residual user-visible sites outside the R8 census** are routed as register N-9…N-12. | R8 ≈589 | **PASS on R8 scope; 4 residuals routed, not fixed here** (CLAUDE.md §7) | `grep.md` |
| **MARKERS** | backend + frontend + portal written on `cb10c5d63` | — | **DONE** | `.claude/markers/verify-{backend,frontend,portal}.json` |

## Skipped tests (the baseline set; no soft-skips)
- lifecycle, 3: the three `628E-BLOCKER` fixmes. These are the conflict-check null-relationship crash, the second-user interest-run approval, and the missing Section 35 pack (`TrustReportPackSeeder` is never wired).
- depth-ii, 4: one fixme (`day-30-45-soa-writeoff.spec.ts:292` Statements-tab Download) and three opt-in null-prefetch probes.
- default, 18:
  - the seven `628F-BLOCKER` fixmes (data export, anonymize preview ×2, DORMANT→OFFBOARDING, OFFBOARDING→OFFBOARDED, client-page Generate Document, Create Role dialog);
  - the eight `admin-audit-30day/admin-audit-lifecycle.spec.ts` scaffolds, unconditionally `test.skip(true, "510A scaffold — full assertions deferred to slice 510B")` (pre-Phase-88, part of the 628F baseline);
  - one Keycloak-only account test;
  - the 2 env-gated portal isolation tests, which ran separately with `KGOSI_EMAIL` and passed 2/0.
- portal, 1: the Day 75 `628G-BLOCKER` fixme (portal deadline sync is dormant).

## Harness note: portal leg run 1 discarded (gate-script bug, not product)
In the first chain, the frontend legs ran under `export PLAYWRIGHT_BASE_URL=http://localhost:3001`, and the chain unset only `E2E_AUTH_MODE` afterwards. `portal/playwright.portal.config.ts:119` and `portal/e2e/helpers/auth.ts:31` read `PLAYWRIGHT_BASE_URL` before falling back to `:3002`. So run 1 drove the **firm app's** mock-login page: every `/home` h1 check failed (5 failed / 28 did not run), and login-bare failed. The screenshot is `logs/run1-leaked-baseurl-firm-mock-login.png`, and the summary is in `logs/e2e-portal-run1-LEAKED-BASEURL-summary.txt`. The rerun `logs/portal-rerun.sh` unsets the variable and keeps the 628G magic-link budget (token age ≥ 310 s plus an `e2e-backend` restart before each portal invocation). It ran on the same stack after the legal and default suites and was green, as above. 628G had already shown that two portal runs on one stack are supported.

## Run notes
- BE-VERIFY ran concurrently with FE-GATE, PORTAL-GATE and the e2e image build. Playwright started only after BE-VERIFY finished, so there was no JVM contention.
- The KC portal on :3002 was stopped for the portal legs and restarted afterwards. `portal/next-env.d.ts` in the main checkout was restored.
- Both worktrees were clean after the runs (`git checkout`/`git clean` on `frontend/test-results`, `documentation/screenshots`, `frontend/e2e/screenshots` and `portal/e2e/screenshots`).
- e2e stack state after the gate: fresh from `cb10c5d63`, plus the data from the lifecycle, depth-ii, default, isolation, one portal run (plus the run-1 globalSetup) and login-bare. KC stack untouched.

**Epic 628 Done**: 628A #1703, 628B #1704, 628C #1705, 628D #1706, 628E #1707, 628F #1708, 628G #1709, 628H #1710, and the 628I gate.
