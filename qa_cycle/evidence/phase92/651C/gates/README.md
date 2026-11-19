# 651C gates — evidence (PR #1810)

Slice: frontend-only removal of the calculated interest-run screens and the LPFF-rate settings section (Phase 92 E7).
Under the 2026-11-18 gate-cadence ruling 651C is **not** an E2E-LEGAL checkpoint (651E is), so it runs the spec
files it edits plus OBS. No backend verify (frontend-only; task file "No backend verify: 651C").

## Stack

- Head under test: `ebb1b0eb861bf4e4bb15e1f5421e22ba8211d8e3` (frontend code head of PR #1810; later commits on the
  branch are `qa_cycle/` evidence only).
- Fresh stack: `e2e-down.sh` → `e2e-up.sh` from the worktree (`stack1-down.log`, `stack1-up.log`, rc `0`).
  `stack1-up.log`: `Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-651C`.
- Build proof: `docker exec e2e-frontend ls "/app/frontend/.next/server/app/(app)/org/[slug]/trust-accounting"` →
  `client-ledgers investments other-creditors page… reconciliation reports transactions` — **no `interest`**.

## Edited specs (observed)

| Suite | Result | Log |
|---|---|---|
| `test:e2e:legal-lifecycle` (whole config — `day-60-investments` needs Days 0–45 run state) | **105 passed / 0 failed** (3.1 m; 110 at 650C − 4 day-60 run tests − 1 day-00 LPFF test) | `lifecycle.log` (names `day-60-investments.spec.ts` ×10), `lifecycle-last-run.json` |
| `test:e2e:navigation` | **17 passed / 0 failed**, incl. "six zones in order with 28 items in order" | `navigation.log` |

Tree restored after the runs: the curated lifecycle PNGs were copied to `lifecycle-artifacts/` and
`documentation/screenshots/legal-vertical` cleaned. `git status` afterwards is the same as `lifecycle-tree-pre.txt`.

## OBS — `obs.cjs` (final run after the delta-review fix: item a reads visible links with the zone open; item d's `hasInterestRunDue` check was swallowed by a comment in run 1 and is now enforced) (headless Chromium, Alice/owner via `/mock-login`, 1440 px), result `obs/obs-result.json`

| Item | Result | Observed | Screenshot |
|---|---|---|---|
| a. Sidebar Trust zone | **PASS** | The Trust header was clicked until `aria-expanded="true"` and "Trust reports" became visible. The **visible** Trust links are exactly Overview, Transactions, Client ledgers, Other trust creditors, Reconciliation, Investments and Trust reports (7), read with `isVisible()` per anchor. `getByRole("link", {name: "Interest", exact: true})` count is 0 with the zone open. The screenshot is an element capture of the nav at a 1 600 px viewport. The rail scrolls at 1 000 px, and a fullPage capture re-renders the zones collapsed (run 1's screenshot showed Trust collapsed; delta review A, PR #1810) | `obs/a-sidebar.png` |
| b. Command palette | **PASS** | "Interest" offers only fuzzy matches (Diary, Settings › …), no Interest page. "Other trust" → "Other trust creditors"; "Investments" → "Investments" | `obs/b-palette-interest.png` |
| c. Removed route | **PASS** | `GET /org/e2e-test-org/trust-accounting/interest` → **404**, Next default "This page could not be found" | `obs/c-interest-route.png` |
| d. Settings → Fees & Trust → Trust accounting | **PASS** | `[data-testid=lpff-rates-section]` count 0. No "LPFF rate", "Add rate", "Interest run" or "Interest Run Due" text. The description reads "Manage trust accounts, approval settings, and reminder configuration." The Trust Accounts, Approval Settings and Reminder Settings cards render (Low Balance Alert, Reconciliation Due, Investment Maturity). The Section 86(6) advisory "Contact the LPFF to verify" (648C `Section866Advisory`) is an expected KEEP (D-3) | `obs/d-settings-trust-tab.png` |
| e. Settings-nav description | **N/A** | `lib/settings-nav.ts` `description` is not rendered on any screen. `components/command-palette-dialog.tsx:46` ("descriptions stay out of the search index"), and the settings rail and family pages render labels only. The changed string is covered by vitest only | `obs/e-settings-index.png` |
| f. Kept surfaces | **PASS** | `/trust-accounting/investments` 200, h1 "Investments". `/trust-accounting/other-creditors` 200, h1 "Other trust creditors", with the "Legal Practitioners' Fidelity Fund — interest and charges" card, ledger and memo section. `/trust-accounting/reconciliation` 200, h1 "Reconciliation". No error boundary | `obs/f-*.png` |
| g. Backend log | **PASS (weak)** | `docker compose … logs backend` (516 lines, JSON): 0 lines match `/interest-runs\|/lpff-rates`; 0 ERROR-level lines. Limitation: the e2e backend log does not record request URIs, so the zero match is not on its own proof that nothing called the endpoints. Item h and the acceptance grep (no frontend code references the endpoints) carry that | — |
| h. Read-only DB | **PASS** | `tenant_7d218705360b`, after the full lifecycle: `interest_runs` 0 rows (at 650C the day-60 spec created draft runs), `lpff_rates` 0 rows. The tables still exist and are dropped in 651A / 651E. Read-only `select`; QA wrote no SQL | — |

Console errors: 2, both expected:
- the known post-login `MockAuthContextProvider: failed to fetch userinfo` noise
- the resource 404 for the removed route itself (item c)

Page errors: 0.

## Frontend gate

- Builder FE-GATE @ebb1b0eb8: lint 0 errors, typecheck, format:check, vitest 482 files / 4 563 passed / 2 skipped,
  build.
- CI Frontend: **pass** @ebb1b0eb8.
- Orchestrator re-run @ebb1b0eb8 (node v20.11.1, `fe-gate.log`, rc 0): lint 0 errors / 77 warnings, typecheck ok, format:check ok, vitest 482 files / 4 563 passed / 2 skipped, build ok.

## Reviews

- Superpowers-lens reviews (two `feature-dev:code-reviewer`), both **APPROVE @ebb1b0eb8**: 0 Critical, 0 High.
- Review A flagged one Medium, the portal spec comment. It is outside the frontend-only scope, so it went to `found.md` F-651C-3.
- CodeRabbit: REVIEWED, no actionable comments.
