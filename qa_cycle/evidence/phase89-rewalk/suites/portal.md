# 636A — E2E-PORTAL (vitest + 90-day + responsive + isolation + login-bare), Phase 89 exit gate

**Status:** PASS (all five sub-suites)

## Shared facts

**Stack: G2.** Fresh (`e2e-down.sh && docker builder prune -f && e2e-up.sh`) from `$WT` @ `1034b6af8`.

- Containers created 2026-11-05T18:22:55Z; backend image `8111f5608eda`, frontend `d026d5fd096c`.
- Tenant `tenant_7d218705360b`; Flyway 11 (tenant) / 3 (global); seed `E2E Boot-Seed Complete!`.
- Pristine state before the runs: `projects` = `Website Redesign|ACTIVE`; `portal.portal_deadline_view` = 0.

**Baselines.** Captured on stack C2 with `--update-snapshots=all`, in the same sequence as G2 (90-day → wait + restart → responsive).

- 11 + 21 = 32 PNGs; not committed.
- Visual check: `portal-client-90day/day-00-home-landing.png` is an authenticated portal Home (Acme Corp, 2 pending info requests, INV-0001). `chromium/sm-trust-matter-list.png` is an authenticated trust matter page with the masked balance and transaction regions.

**:3002 holder.**

- Before the portal work: the KC dev portal (`svc.sh` PID 86758, node 86801, cwd main `portal/`). It was stopped at 20:14:46 SAST using the main checkout's `svc.sh stop portal`.
- Before each G2 sub-suite: `:3002 free` (`raw/stacks.txt`). Each Playwright `webServer` self-served the portal (dev for 90-day and responsive, production for login-bare) and stopped it at the end.
- After login-bare: `:3002 free`. The KC portal was then restarted from the main checkout at 20:42:02 and `svc.sh status` shows `portal 51238 yes yes 3002`.

**Common portal env:** `NODE_OPTIONS= NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 PORTAL_ORG_SLUG=e2e-test-org`. `PORTAL_CONTACT_EMAIL` defaults to `contact@acme.example.com` in the scripts. `CI` was not set.

### Magic-link spacing

Contact `contact@acme.example.com`. Source: `raw/stacks.txt` → "portal spacing".

| Before | Tokens in DB | Newest-token age at wait start | Age when released | Backend restarted + healthy (SAST) |
|---|---|---|---|---|
| G2-90day (fresh stack) | 1 (seed) | 10 s | — (no wait needed: seed + globalSetup + Day 0 = 3 tokens in 5 min, at the cap, as in 633C) | — |
| G2-responsive | 3 | 37 s | 330 s | 20:29:27 |
| G2-isolation | 4 | 18 s | 332 s | 20:35:22 |
| G2-login-bare | 6 | 13 s | 337 s | 20:41:21 |

The C2 capture had the same spacing before responsive: 335 s, restart at 20:21:57.

The token deltas match the brief's budget:

| Suite | Tokens spent |
|---|---|
| 90-day | 2 |
| responsive | 1 |
| isolation | 2 |

The "Exchanged magic link" counts in the backend log are cumulative, because `docker logs` persists across `docker restart`: 1 after 90-day, 2 after responsive, 4 after isolation and 5 after login-bare. That is one exchange each for 90-day, responsive and login-bare, and two for isolation. No suite ran vacuously.

---

## 1. Portal vitest

| Field | Value |
|---|---|
| Command | `cd portal && NODE_OPTIONS= pnpm test` (no stack needed; run 19:57:34 SAST while C1 built) |
| Result | `Test Files  47 passed (47)` / `Tests  273 passed (273)`, rc 0, 6.67 s |
| Plan floor / current main | 256 tests / 47 files, 273 tests (633C) |
| Status | **PASS** |

The output also contains stderr noise: 27 lines containing `http://localhost:8080/api/portal/acceptance/test-token/pdf`, inside happy-dom `DOMException [AbortError]` / `[NetworkError]` stacks. They come from `app/accept/[token]/__tests__/page.test.tsx`: the page's PDF iframe `src` fetch is aborted when the happy-dom environment tears down. No test failed or errored. Raw tail: `raw/portal-vitest.txt`.

## 2. Portal 90-day (`test:e2e:portal-client-90day`)

| Field | Value |
|---|---|
| Command | `cd portal && <common env> PLAYWRIGHT_JSON_OUTPUT_FILE=… pnpm test:e2e:portal-client-90day --reporter=list,json` |
| Start / end (SAST) | 20:23:36 / 20:23:58. Outside 00:00–02:00 ✓ |
| Result (JSON stats) | expected 13 / unexpected 0 / skipped 0 / flaky 0 |
| Result (list summary) | `13 passed (20.9s)` |
| Exit code | 0. 0 "writing actual" lines (all 11 frames compared against the C2 baselines) |
| Current main | 13 / 0 / 0 (633C) |
| Status | **PASS** |

Named specs:

| Spec | Result |
|---|---|
| `day-14-30.spec.ts:149` "Day 30 [legal-za]: Client downloads the receipt for a part payment on a fee note" (632C receipt case) | ✓ |
| `day-45-75.spec.ts:124` "Day 60 [legal-za]: Client downloads the Statement of Account from the matter page" (covers the plan's old `:166`) | ✓ |
| `day-45-75.spec.ts:181` "Day 75 [legal-za]: Client sees the court date and the prescription on Diary dates, sanitised, until the firm removes them" | ✓ |

After-checks (read-only):

- `portal.portal_deadline_view` = **0** after the run, so the Day 75 cleanup held.
- Firm-side residue: `court_dates` has 1 `CANCELLED` row and `prescription_trackers` has 1 `INTERRUPTED` row. This is the documented per-run residue.
- `information_requests`: `REQ-0002 | IN_PROGRESS` was created at 18:23:43Z by this run's Day 0–3 flow. `REQ-0001 | SENT` is the seed row.
- Day 75 captured frame: `raw/artefacts/G2-day-75-deadline-detail.png`, copied from `portal/test-results/…/day-75-deadline-detail.png`.

## 3. Portal responsive (`test:e2e:responsive`)

| Field | Value |
|---|---|
| Command | `cd portal && <common env> … pnpm test:e2e:responsive --reporter=list,json` |
| Start / end (SAST) | 20:29:31 / 20:29:51 ✓ |
| Result (JSON stats) | expected 21 / unexpected 0 / skipped 0 / flaky 0 |
| Result (list summary) | `21 passed (19.1s)` |
| Exit code | 0. 0 "writing actual" lines. The `sm/md/lg-trust-matter-list.png` size-mismatch candidate (633C) did not recur, because C2 and G2 ran identical sequences |
| Current main | 21 / 0 / 0 |
| Status | **PASS** |

## 4. portal-data-isolation (`frontend`, `KGOSI_EMAIL` set)

| Field | Value |
|---|---|
| Command | `cd frontend && NODE_OPTIONS= E2E_AUTH_MODE=mock PLAYWRIGHT_BASE_URL=http://localhost:3001 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 KGOSI_EMAIL=contact@acme.example.com pnpm exec playwright test --config e2e/playwright.config.ts e2e/tests/portal/portal-data-isolation.spec.ts --reporter=list,json` |
| Start / end (SAST) | 20:35:26 / 20:35:28 ✓ |
| Result (JSON stats) | expected 2 / unexpected 0 / skipped 0 / flaky 0 |
| Result (list summary) | `Running 2 tests using 1 worker` … `2 passed (888ms)`. Tests: `:36` "Kgosi sees only Kgosi matters via API" ✓ and `:48` "Portal JWT blocked from org API" ✓. Both **passed**, not skipped |
| Exit code | 0 |
| Current main | 2 / 0 / 0 |
| Status | **PASS** |

## 5. login-bare (opt-in, production portal)

| Field | Value |
|---|---|
| Command | `cd portal && NODE_OPTIONS= NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081 BACKEND_URL=http://localhost:8081 MAILPIT_URL=http://localhost:8026 pnpm test:e2e:login-bare --reporter=list,json`. No `PORTAL_CONTACT_EMAIL` or `PORTAL_ORG_SLUG` in the environment (checked: `env \| grep PORTAL_CONTACT` empty) |
| Start / end (SAST) | 20:41:27 / 20:41:45 ✓. The webServer ran `pnpm run build && pnpm run start` (`playwright.portal.config.ts:131`). The fresh production build finished inside this window: `portal/.next/BUILD_ID` mtime is 20:41:39. It was faster than the 2–3 min the brief expected |
| Result (JSON stats) | expected 1 / unexpected 0 / skipped 0 / flaky 0 |
| Result (list summary) | `1 passed (17.5s)`. Test `login-bare.spec.ts:70` "fresh browser: bare /login → one branded link in Mailpit → /matters" ✓ |
| Exit code | 0. The expected warning `"next start" does not work with "output: standalone"` is present, and 628G passed with it too |
| Current main | 1 / 0 / 0 (628G) |
| Status | **PASS** |

---

## Failures

None in any sub-suite or capture run.

## Capture runs (C2, unscored)

| Run | Result | "writing actual" | rc | SAST |
|---|---|---|---|---|
| C2-90day | `13 passed (24.1s)` | 11 | 0 | 20:15:56–20:16:22 |
| C2-responsive | `21 passed (22.3s)` | 21 | 0 | 20:22:03–20:22:26 |

Summaries: `raw/C2-90day.summary.txt`, `raw/C2-responsive.summary.txt`.

## Raw output

- `raw/G2-90day.{txt,tsv,json}`, `raw/G2-responsive.{txt,tsv,json}`, `raw/G2-isolation.{txt,tsv,json}`, `raw/G2-login-bare.{txt,tsv,json}`, `raw/portal-vitest.txt`.
- The `.txt` files keep Playwright's `[WebServer]` lines and ANSI colour codes verbatim; none of them is a result line.
