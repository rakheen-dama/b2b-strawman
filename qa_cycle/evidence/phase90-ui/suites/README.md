# 644A — Exit suites and gates on `main` (tasks 644.1–644.3)

**Head under test:** `main` @ **`2773ec9b6f00fa03d564d503f4df267d11fd6206`** (2026-11-13) — every Phase 90 code slice (P1 #1758, 637A–643C #1759–#1777) merged, plus the two trust-reconciliation wizard fixes #1778/#1779 that landed after 643C.
**Where:** detached worktree `worktree-epic-644A` (branch `phase90/44A-exit-suites` created at `origin/main`, no commits before this evidence).
**Runner:** [`run-gates.sh`](run-gates.sh) — one gate at a time on the machine (BE-VERIFY first, then the fresh e2e stack, then FE-GATE, then PORTAL-GATE; never E2E alongside a Maven verify), under `caffeinate -is`, node **v20.11.1** (nvm; CI pin), pnpm by absolute path `/opt/homebrew/bin/pnpm`, `NODE_OPTIONS=""`.
**Invocation (from the worktree root):** `mkdir -p /tmp/644A/run1 && cp qa_cycle/evidence/phase90-ui/suites/run-gates.sh /tmp/644A/run-gates.sh && nohup caffeinate -is bash /tmp/644A/run-gates.sh "$PWD" /tmp/644A/run1 > /tmp/644A/run1.nohup 2>&1 &` (detached; completion = `/tmp/644A/run1/DONE`). Steps outside the script are listed under *Manual steps* below.
**Raw timeline:** [`summary.txt`](summary.txt) (UTC timestamps, exit codes, pass counts as printed by each tool).

## Verdict

**All five gates green on the merged `main` head; every count ≥ the 637.1 baseline; no failures, no flaky retries, nothing to triage (644.3 not triggered).** 644B may start.

## Results vs the 637.1 baseline (`qa_cycle/evidence/phase90-ui/baseline/README.md`, `main` @ `f7b7dbdf1`)

| Gate | Command (from the worktree) | Run (UTC, 2026-11-13) | Baseline (637.1) | Exit (644A) | Δ |
|---|---|---|---|---|---|
| **BE-VERIFY** | `cd backend && ./mvnw spotless:apply && bash scripts/verify.sh` | 19:55 → 20:15 (runner window 20:09 incl. spotless + preflight; Maven total time 19:54 min) | not part of 637.1 (FE-only baseline) | **exit 0 — BUILD SUCCESS; 6871 tests, 0 failures, 0 errors, 26 skipped** (`<testcase>` count over surefire+failsafe XML = 6871, `<skipped>` = 26; no report with failures/errors) | spotless made no change (`git status` clean) |
| **E2E-LEGAL** legal-lifecycle | `E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle` | 20:17 → 20:20 (3.2m) | 105 passed | **105 passed** | = |
| **E2E-LEGAL** legal-depth-ii | `… pnpm test:e2e:legal-depth-ii` | 20:20 → 20:22 (2.0m) | 40 passed, 4 skipped | **40 passed, 4 skipped** | = |
| **E2E-DEFAULT** | `… pnpm test:e2e` (same stack) | 20:22 → 20:24 (1.7m) | 157 passed, 1 failed, 7 did not run, 14 skipped | **165 passed, 14 skipped, 0 failed** | +8 passed: INV-02 *DRAFT → APPROVED* passed ([`default-inv02.txt`](default-inv02.txt)), so its serial describe's 7 "did not run" ran — 157 + 1 + 7 = 165 |
| **FE-GATE** | `cd frontend && rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | 20:24 → 20:28 | 457 files, 3763 passed / 2 skipped | **exit 0 — 477 files, 4347 passed / 2 skipped (4349)** | +20 files (29 added − 9 deleted, all Phase 90), +584 tests: 579 from Phase 90 suites + 5 from #1778/#1779 (`frontend/__tests__/trust-reconciliation.test.tsx` 5 → 10 `it(` blocks between `54096a4fa` and `2773ec9b6`) |
| **PORTAL-GATE** | `cd portal && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build` | 20:28 → 20:28 (+ OBS 20:36) | 51 files, 284 passed | **exit 0 — 51 files, 284 passed**; **OBS `/home`, `/trust`, `/invoices` @1024 light: all render, console clean** (see *Portal observation*) | = (portal only received the token retune) |

Flaky / retried tests across the three Playwright runs: **0** (`grep -ciE "flaky|retry #"` = 0 on each log).

The baseline E2E-DEFAULT failure (INV-02 *DRAFT → APPROVED*, `e2e/tests/invoices/invoice-lifecycle.spec.ts:88`) was recorded in 637.1 as intermittent, cause unconfirmed, and passing on the next fresh stack. It passed here too (2.5 s). This exit run shows it is not a Phase 90 regression. It does not show the intermittency is gone, so it stays an open baseline observation, not a fix.

## Fresh stack (644.1)

- Sequence (in the worktree, any time of day): `bash compose/scripts/e2e-down.sh` (exit 0, volumes wiped) → `SHELL=/bin/bash docker builder prune -f` (exit 0) → `bash compose/scripts/e2e-up.sh` (whole block — down, prune, up — 20:15:37 → 20:17:27Z; e2e-up exit 0, "Building from source at: …/worktree-epic-644A").
- Containers: all six service containers plus `e2e-seed` (seven `e2e-*`) created 22:16:53–54 SAST (= 20:16:53–54Z) by that `e2e-up` (`stack-provenance.txt`; the run-time `e2e-containers.txt` was captured without `-a`, so it lists only the six running services).
- Images (stack build provenance):
  - `docteams-e2e-frontend` `sha256:0d4e47a1bf40…` — built in this run (created 2026-11-13T20:16:53Z) from the worktree @ `2773ec9b6`.
  - `docteams-e2e-backend` `sha256:0f540ef6f0f1…` — every step resolved from layer cache, including `COPY src src` (image created 2026-11-13T04:39:09Z). BuildKit's COPY cache key is a content checksum, so a CACHED `COPY src src` means this run's backend build context was byte-identical to the one that produced that image. That fits the history: the image was built during 643B, after its last backend commit `20955954f` (04:02Z), and `git diff 5864da54d 2773ec9b6 -- backend/` is empty (`5864da54d` = 643B merge #1776, the last backend change on `main`). Backend correctness of this exact source is independently covered by BE-VERIFY above (compiled and tested from the worktree).
- After the suites: `git checkout -- frontend/test-results documentation/screenshots`. The run also left 26 untracked `documentation/screenshots/legal-vertical/*.png`, removed with `git clean -f documentation/screenshots`. The tree is clean apart from this evidence folder (`git status --short` → `?? qa_cycle/evidence/phase90-ui/suites/`).

**Specs amended by a phase slice:** none. `git diff --name-only f7b7dbdf1..2773ec9b6 -- frontend/e2e` is empty, so no spec, helper or Playwright config changed between the 637.1 baseline and the exit head (matches 644.1's "expected: none").

## Portal observation (PORTAL-GATE, 644.2)

PORTAL-GATE includes "`/home`, `/trust` and `/invoices` observed at 1024 in light (§15.6.3; portal never dark)". Observed 2026-11-13 ≈20:36Z with a Playwright **library** script ([`portal-obs.cjs`](portal-obs.cjs), a copy of `639A/portal-obs.cjs` with only paths, the page list and `fullPage` changed; the Playwright MCP Chrome profile is held by the user's live session, the same deviation as 637A–643C). Setup:
- Portal from this worktree: `cd portal && NODE_OPTIONS="" NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 pnpm exec next dev --port 3012`, against the fresh e2e backend :8081, after the suites had run on it (so the lifecycle data is present).
- Contact `contact@acme.example.com`, magic link exchanged through the backend API.
- :3002, the only CORS-allowed portal origin, is held by the Keycloak dev portal, so a Playwright route rewrote `Origin` and added CORS headers inside the test browser only. No app change.

| Page | h1 | Screenshot | Observed |
|---|---|---|---|
| `/home` | Home | [`portal/home-1024-light.png`](portal/home-1024-light.png) | four cards render (pending info requests 1, upcoming deadlines 0, recent fee notes INV-0001 R 8 625,00, last trust movement "No recent activity") |
| `/trust` | Trust statement | [`portal/trust-1024-light.png`](portal/trust-1024-light.png) | empty state "No trust activity on your matters" for Acme Corp. The lifecycle suite's trust deposits belong to a different client, so this is the seeded data, not a regression |
| `/invoices` | Fee notes | [`portal/invoices-1024-light.png`](portal/invoices-1024-light.png) | INV-0001 Sent, 13 Nov 2026 / 13 Dec 2026, R 8 625,00, View + PDF actions |

[`portal/obs-report.json`](portal/obs-report.json):
- **console: [] (no errors/warnings, no hydration warnings) on all three pages.**
- Body background `lab(96.7396 …)` and `--ring` `lab(52.8717% …)` are identical to 639A's `/home` capture, so the portal tokens are unchanged since 639A.
- The pages render in light only.
- The round "N" bubble at the bottom left of each shot is the Next.js dev-mode indicator (`next dev`), not portal UI.

## Manual steps (outside `run-gates.sh`)

- Marker audit: `bash -c 'for pr in $(seq 1758 1777); do …; bash scripts/check-verify-markers.sh --committed-only $pr; echo "exit $?"; done' > /tmp/644A/markers.txt`; `markers.md` is generated from it.
- Spec-amendment check: `git diff --name-only f7b7dbdf1..2773ec9b6 -- frontend/e2e`.
- Cleanup: `git clean -f documentation/screenshots` (26 untracked PNGs that `git checkout --` cannot remove).
- Evidence extraction:
  - `tail -8 /tmp/644A/run1/<suite>.log > <suite>-tail.txt` for legal-lifecycle, legal-depth-ii and default.
  - `tail -6 /tmp/644A/run1/fe-test.log > fe-test-tail.txt` and `tail -6 /tmp/644A/run1/portal-test.log > portal-test-tail.txt` (two separate commands).
  - `grep -E 'Tests run:.*Fail|BUILD|Total time' be-verify.log | tail -3 > be-verify-tail.txt`.
  - `grep -E INV-02 default.log > default-inv02.txt`.
  - `cp summary.txt`.
- Portal OBS: start the portal on :3012 as above, run `node portal-obs.cjs`, then stop the dev server.

## Runner limitations (CodeRabbit on `1817ddf6d`) and why the recorded run is unaffected

`run-gates.sh` is committed **verbatim as executed**, so it is not edited after the fact. CodeRabbit found two robustness gaps in it.

1. **It does not normalise `<worktree>`/`<outdir>` to absolute paths.** A relative argument would misplace logs after the `cd`s. The recorded run passed absolute paths (`"$PWD"` and `/tmp/644A/run1`; see *Invocation*).
2. **It records but does not gate on the `e2e-down`/`e2e-up` exit codes.** `e2e-up.sh` also reports success even if the seed or Mailpit wait fails. So a partial or stale stack would not stop the suites. In the recorded run every setup step succeeded, as `summary.txt` and the run-host logs show:
   - `e2e-down exit 0`, which removed all containers and both volumes (`docteams-e2e_e2e_postgres_data`, `…_localstack_data`);
   - `e2e-up exit 0`;
   - backend, frontend, mock IDP and Mailpit all "ready";
   - `e2e-up.sh` printed "Seed completed (container exited)". That message comes from a branch that is not exit-code-aware (`compose/scripts/e2e-up.sh:107-109`), so it is not proof on its own;
   - `e2e-seed` container `ExitCode 0` (20:17:21 → 20:17:26Z), and the seed log reports "Boot-Seed Complete". This is the actual seed proof, captured in [`stack-provenance.txt`](stack-provenance.txt) from the still-running stack after the run;
   - all six service containers plus `e2e-seed` created at 20:16:53–54Z by this `e2e-up` (`stack-provenance.txt`).

   The suites ran on the freshly provisioned stack.

A future exit-gate runner should resolve both paths up front and skip the suites unless teardown, startup and seed all succeed. That hardening is a tooling change for its own PR, not part of this evidence.

## Verify markers (644.2)

See [`markers.md`](markers.md): `scripts/check-verify-markers.sh --committed-only` over #1758–#1777 → **20 / 20 PASS**. Backend markers are present for P1, 643A and 643B. Every UI slice has a frontend marker, and portal markers exist for 637A and 639A (plus 640A and 640C).

## Files

| File | What |
|---|---|
| `run-gates.sh` | the runner, verbatim |
| `summary.txt` | runner timeline and counts |
| `be-verify-tail.txt` | Maven totals and `BUILD SUCCESS` line |
| `legal-lifecycle-tail.txt`, `legal-depth-ii-tail.txt`, `default-tail.txt` | Playwright result tails |
| `default-inv02.txt` | every INV-02 line from the E2E-DEFAULT log (all ✓) |
| `fe-test-tail.txt`, `portal-test-tail.txt` | vitest totals |
| `markers.md` | marker audit (644.2) |
| `stack-provenance.txt` | post-run `docker inspect`/`ps -a`/seed-log capture of the e2e stack (seed exit code, container creation times) |
| `portal-obs.cjs`, `portal/*.png`, `portal/obs-report.json` | PORTAL-GATE observation |

Full logs (not committed; ~MBs) were in `/tmp/644A/run1/` on the run host.
