# 638A (task 638.1) — "before" observation of the app shell (sidebar + header)

- **Head:** `68cf12103` (main, after 637B / PR #1760)
- **Captured:** 2026-11-12, headless Chromium, Playwright 1.59.1 library
- **Stack provenance:** fresh `e2e-down` → Docker builder prune → `e2e-up` from a detached worktree at origin/main (`/Users/rakheendama/Projects/2026/worktree-638A-before`), 2026-11-12. Mock-auth stack: frontend :3001, backend :8081, mock IDP :8090, org `e2e-test-org`, user Alice (owner).

## Method (deviation: library script, not Playwright MCP)
The Playwright MCP browser profile is held by the user's live interactive session (shared Chrome profile, must not be killed), so captures were taken with a standalone Playwright **library** script: `before.cjs` (in this folder), run with `node`. Login via `/mock-login` → "Sign In" → wait for `/org/e2e-test-org/dashboard` + `h1`; storage state reused across fresh contexts (one context per viewport×theme). Waits: `load` + visible `h1` + ~1.2 s settle (never `networkidle`). Theme set via init script `localStorage['kazi-theme'] = 'light'|'dark'` before navigation (confirmed by `<html class="light|dark">` in each capture). Viewport screenshots only (not full page). Read-only: only UI state touched (zone toggles, mobile menu). `probe-active.cjs` was a follow-up read-only probe (active-link classes, nav overflow).

## Captures
| File | URL | Viewport | Theme | Notes |
|---|---|---|---|---|
| shell-1024-light.png | /org/e2e-test-org/dashboard | 1024×768 | light | default zones |
| shell-1024-dark.png | /org/e2e-test-org/dashboard | 1024×768 | dark | default zones |
| shell-1440-light.png | /org/e2e-test-org/dashboard | 1440×900 | light | default zones |
| shell-1440-dark.png | /org/e2e-test-org/dashboard | 1440×900 | dark | default zones |
| shell-matters-1440-light.png | /org/e2e-test-org/matters | 1440×900 | light | "Matters" link active |
| shell-1440-light-all-zones.png | /org/e2e-test-org/dashboard | 1440×900 | light | Fees, Trust, Firm expanded (nav auto-scrolled to Fees/Trust; Firm items below the fold) |
| mobile-390-closed.png | /org/e2e-test-org/dashboard | 390×844 | light | sidebar hidden |
| mobile-390-sheet-open.png | /org/e2e-test-org/dashboard | 390×844 | light | after "Toggle menu" |
| mobile-390-sheet-open-dark.png | /org/e2e-test-org/dashboard | 390×844 | dark | after "Toggle menu" |

Full per-capture data: `before-report.json`.

## Key facts
**Widths**
- Desktop sidebar = `<aside>` **240 px** wide, full viewport height (768 / 900), at x=0 — identical at 1024 and 1440, light and dark.
- `#main-content` is a `<main>`: x=240, y=56 (header height **56 px**), width **784 px** @1024, **1200 px** @1440, **390 px** @390 (x=0).
- `nav[aria-label="Main navigation"]` box: y=137; height 464 @1024×768, 596 @1440×900. At 1440×900 with default zones the nav **overflows** (scrollHeight 628 > clientHeight 596, `overflow-y: auto`) → the **Firm** zone header is below the fold and needs a scroll; nothing below "Trust" is visible in shell-1440-light.png.
- Mobile 390: desktop `<aside>` has 0×0 box (hidden); with the sheet open a second `nav[aria-label="Main navigation"]` appears inside a `div` container **240 px** wide × 844 (one `role=dialog`).

**Zones (ordered, default aria-expanded)** — same at every viewport/theme:
`Today=true | Matters=true | Clients=true | Fees=false | Trust=false | Firm=false`
(all-zones capture clicked Fees, Trust, Firm → all six `true`). No zone state found in localStorage after load (`localStorage` empty for the probe context).

**Links inside the nav (DOM order)**
- Default (11): Home | My work | Diary | Matters | New matter | Conflict check | Adverse parties | AI reviews | Clients | FICA & compliance | Data requests
- All zones open (28): the 11 above + Unbilled work | Fee notes | Fee runs | Debtors | Disbursements | Tariffs | Overview | Transactions | Client ledgers | Reconciliation | Interest | Investments | Trust reports | Team | Reports | Audit trail | Settings
- Collapsed zones render **no** links in the DOM (11 vs 28).
- Outside the nav (visible in shots, below it in the aside): Platform Admin, Notifications, user card (Alice Owner). Above the nav: "Kazi" wordmark, org name, Search (⌘K).

**Active state**
- **No link carries `aria-current="page"`** in any capture (dashboard or matters). Active state is class-only: active link has `bg-white/5 text-white`; inactive `text-white/60 hover:bg-slate-800 hover:text-white` (Home active on dashboard, Matters active on /matters, per probe).
- Sidebar is dark (slate/black) in **both** light and dark themes; only the content area/header switch.

**Other counts**
- `main` elements: **1** in every capture.
- "Toggle menu": 1 in the DOM in every capture; accessible-by-role count 1 only at 390 closed (0 on desktop = hidden, 0 with the sheet open = aria-hidden behind modal).
- `h1`: "Home" (dashboard), "Matters" (/matters).

**Console**
- **0** console errors / warnings / page errors collected across all 9 captures; **no hydration messages**.

## Observe-first E2E (run by the orchestrator on this stack, before this capture)
- `pnpm test:e2e:navigation` → **16 passed** (exit 0) — `/tmp/638A-before/navigation.log`
- `day-00-firm-setup.spec.ts` → **16 passed** (exit 0) — `/tmp/638A-before/day-00.log`

```
--- navigation.log (tail -5)
  ✓  14 [chromium] › e2e/tests/navigation/sidebar-navigation.spec.ts:33:9 › NAV-01: Sidebar Navigation › Notifications page loads (245ms)
  ✓  15 [chromium] › e2e/tests/navigation/sidebar-navigation.spec.ts:33:9 › NAV-01: Sidebar Navigation › Settings page loads (327ms)
  ✓  16 [chromium] › e2e/tests/navigation/sidebar-navigation.spec.ts:75:7 › NAV-02: Sidebar zones › six zones in order with 28 items in order (740ms)

  16 passed (7.1s)
--- day-00.log (tail -5)
  ✓  14 [chromium] › e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:262:7 › Day 0 — Firm Setup › Alice: LPFF rate 6.5% — added on Trust › Interest, listed on the trust settings tab (1.3s)
  ✓  15 [chromium] › e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:303:7 › Day 0 — Firm Setup › Alice: Legal modules enabled (trust, court, conflict, tariff) (622ms)
  ✓  16 [chromium] › e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:324:7 › Day 0 — Firm Setup › Alice: Capture dashboard with legal navigation (356ms)

  16 passed (12.4s)
```
