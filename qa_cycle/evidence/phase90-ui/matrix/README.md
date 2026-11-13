# 644B: exit observation matrix (task 644.4), KC dev stack

Observed 2026-11-13 between 21:30 and 21:50 UTC (23:30–23:50 SAST) on the **Keycloak dev stack**, running from the shared main checkout at `main` @ `21be784473fd96b038b6765b6f2f26b55e5ef7c0`. The product code is identical to the 644A gate head `2773ec9b6`; `21be78447` only adds docs.

## Provenance: stack restart (ruling R3)

| Step | Observed |
|---|---|
| Found | `svc.sh status`: backend 88751 / gateway 88930 (ext), frontend 88937 (healthy **no**: `:3000` → 500, `next-themes` missing from main `node_modules`), portal 93130. All four started Wed Nov 11 21:52–22:14, before 643A/643B merged. The cwd of each port holder was confirmed as `…/b2b-strawman/{backend,gateway,frontend,portal}` before stopping. |
| Stop | `bash compose/scripts/svc.sh stop all`. Ports 8080/8443/3000/3002 were then free (`lsof`). The user's Playwright-MCP Chrome and the e2e Docker stack were not touched. |
| Install | `NODE_OPTIONS="" /opt/homebrew/bin/pnpm install` in the main checkout (lockfile up to date, +3 packages; `git status` unchanged). `next-themes` present afterwards. Removed `frontend/.next`. |
| Backend | `./mvnw -q clean compile -DskipTests` exit 0. The new classes exist in `target/classes`: `dashboard/dto/TrustBalanceTrend{Point,Response}`, `report/BillingTrendService`, `ReportController$BillingTrend{Point,Response}`. |
| Start | `docker builder prune -f`, then `svc.sh start all`. backend ready in 33 s, gateway 6 s, frontend 3 s, portal 3 s. |
| Running | `svc.sh status`: all `yes yes`. Port holders: backend java **45318** (started Fri Nov 13 23:27:28 SAST), gateway java **45473** (23:27:46), frontend next-server **45528** (23:27:51), portal next-server **45581** (23:27:56). svc PIDs 45161/45348/45481/45538. `curl :3000/` → 200, `:8080/actuator/health` → `UP`. The stack was left running. |
| 643A/643B served | `scripts/stack-proof.cjs` → `stack-proof.json`, `stack-proof-trust-1440-light.png`, `stack-proof-profitability-1440-light.png`. As the owner in the browser, `[data-testid=trust-balance-trend]` (643A) and `[data-testid=billing-trend]` (643B) both render a recharts SVG with 12 months (Dec 2025 → Nov 2026). Neither shows the "not available" state. |

## Method (and deviations)

- **Deviation: library script instead of Playwright MCP.** One `mcp__plugin_playwright_playwright__browser_navigate` call returned "Browser is already in use for …/mcp-chrome-5d273ba, use --isolated". That profile belongs to the user's live session. Per ruling R4, the OBS uses a Node `@playwright/test` 1.59.1 headless Chromium script, resolved from this worktree's `frontend/`.
- Scripts are in `scripts/` and committed byte-for-byte as last executed: `common-kc.cjs` (Keycloak two-step login, theme/width switch, in-page audit), `stack-proof.cjs`, `matrix.cjs`, `personas.cjs`, `portal.cjs`, `marketing-control.cjs`.
- Script edits between runs (each script's committed version is the one that produced the committed artefacts):
  - `common-kc.cjs` gained `mouse.move(1,1)` after the first stack-proof dry run. That run's tooltip was pinned open by the pointer left on the Keycloak submit button. `stack-proof.cjs` was re-run afterwards, and its PNGs are from the second run.
  - `portal.cjs` ran three times:
    1. Relative dev-mode link → `Cannot navigate to invalid URL`.
    2. Fixed with `new URL(link, PORTAL)`. Stopped at the exchange page (see portal finding P-1).
    3. Records the exchange state, continues to `/home`, and redacts tokens.
- Contexts use `reducedMotion: "reduce"`, `timezoneId: "Africa/Johannesburg"`, and `waitUntil: "load"` plus the h1 (never `networkidle`). Then a 3 s settle (portal 4 s) and the pointer parked at (1,1).
- **Theme:**
  - `localStorage["kazi-theme"]` is set to `light`/`dark` plus `emulateMedia({colorScheme})`. Every shot records `document.documentElement.classList.contains("dark")` and the stored value.
  - All 14 dark matrix shots: `dark=true`, stored `dark`. All 14 light shots: `dark=false`, stored `light` (`obs-report.json`).
- **Console:** `console` messages of type error/warning (plus any `/hydrat/i`) and `pageerror` are captured per page.
  - The dark-walk run starts with a `console.warn` probe through the same `common-kc.cjs` sink, and the probe was captured (`../darkmode/walk.json` → `probeCaptured: true`). So the empty lists below are real zeros, not a dead listener.
- **Audit:** `common-kc.cjs` `pageAudit()` runs per shot. It computes the WCAG ratio of every visible text owner against its alpha-blended ancestor background (Tailwind v4 oklch/lab is normalised via a 1×1 canvas). It also records light surfaces on a dark page, form fields whose border and fill both vanish, and horizontal scroll containers. Results are in `obs-report.json` under `audit`.
- Every PNG listed below was opened and read before its observation was written. The exception is the four viewport frames marked "(= top of -full)": they come from the same page load as the `-full` shot that was read.
- The round "N" button bottom-left is the Next.js dev indicator (dev-mode chrome, not UI).

## Index: 7 surfaces × {1024, 1440} × {light, dark} (owner Thandi), 28 shots

Base URL `http://localhost:3000/org/mathebula-partners`. Matter = Estate Late Peter Moroka `9181f514-1742-43c5-b446-99e1d41f220e`; client = Moroka Family Trust `713a1f9a-9cd4-406e-920e-507ac449c98a`. HTTP status 200 everywhere. Console: **0 errors / 0 warnings / 0 hydration warnings on every matrix page** (`console.json` = `[]`).

| File | Surface (URL) | Persona | Width | Theme | Observation | Console |
|---|---|---|---|---|---|---|
| `home-1024-light.png` | Home `/dashboard` | owner | 1024 | light | OK. Owner audience. KPI row as a 2×2 grid, diary, WIP, debtors. No overflow. "Nothing in the diary today" is `text-slate-400` at 2.64:1 (known 640B `todays-agenda.tsx` slate-400 finding). No KPI deltas: all 8 time entries are on 2026-11-11, so a delta cannot be observed on this tenant. | 0 |
| `home-1024-dark.png` | Home | owner | 1024 | dark | OK. Cards, sparkline and KPI figures are legible. Diary hint is readable in dark. | 0 |
| `home-1440-light.png` / `home-1440-light-full.png` | Home | owner | 1440 | light | OK (= top of -full). 4-up KPI row (Active matters 3, Hours 18,0h + sparkline, Billable 100,0%, Overdue 0); WIP empty state; Debtors (Lerato R 10 925,00); Trust position "Balanced"; FICA; Matter Health; Sensitive events; Recent activity. No clipping. | 0 |
| `home-1440-dark.png` / `home-1440-dark-full.png` | Home | owner | 1440 | dark | OK (= top of -full). Every card is legible. In dark, the matter-health progress tracks for 0 % rows are very dark red (readable via the % column). | 0 |
| `matters-1024-light.png` | Matters `/matters` | owner | 1024 | light | Renders. **Table scrolls horizontally inside its container**: 993 px in 718 px, so Fee earner / Trust / Next in diary are off-screen and reachable by scroll. This is the known 641A F1 density item, and it is a **cosmetic density regression**: 844 px before the phase, 904 px at 641A (the kit's inactive sort chevrons), 993 px at exit. Not illegible or broken (R8 not triggered); listed in backlog B-12. | 0 |
| `matters-1024-dark.png` | Matters | owner | 1024 | dark | As light; dark tokens correct. The "Active" chip is inverted (light chip) by design. | 0 |
| `matters-1440-light.png` | Matters | owner | 1440 | light | OK. All 9 columns. The table container is still 23 px too narrow (1157/1134), so the row "…" action column is half-clipped at the right edge (cosmetic; adds to 641A F1). | 0 |
| `matters-1440-dark.png` | Matters | owner | 1440 | dark | As light. | 0 |
| `matter-detail-1024-light.png` | Matter detail, Overview | owner | 1024 | light | OK. Hero 2×2 (Unbilled WIP, Trust R 50 100,00, Outstanding, Next court date) pushes the tab bar to y≈614 (known 641B F2). | 0 |
| `matter-detail-1024-dark.png` | Matter detail, Overview | owner | 1024 | dark | OK. | 0 |
| `matter-detail-1440-light.png` | Matter detail, Overview | owner | 1440 | light | OK. 4-up hero. The Trust balance appears in both the hero and the Overview strip (known 641B F3). | 0 |
| `matter-detail-1440-dark.png` | Matter detail, Overview | owner | 1440 | dark | OK. | 0 |
| `clients-1024-light.png` | Clients `/clients` | owner | 1024 | light | OK. Filter chips wrap to 2 rows; no table overflow. | 0 |
| `clients-1024-dark.png` | Clients | owner | 1024 | dark | OK. | 0 |
| `clients-1440-light.png` | Clients | owner | 1440 | light | OK. All columns incl. Completeness/Created. | 0 |
| `clients-1440-dark.png` | Clients | owner | 1440 | dark | OK. | 0 |
| `client-detail-1024-light.png` | Client detail `/clients/713a…` | owner | 1024 | light | OK. Hero 2×2 (Open matters 1, Trust R 50 100,00, Outstanding R 0,00, FICA Done). Grouped tab bar fits. | 0 |
| `client-detail-1024-dark.png` | Client detail | owner | 1024 | dark | OK. (The audit's only low-contrast item is the disabled "Verify with AI" button, which is intentional.) | 0 |
| `client-detail-1440-light.png` | Client detail | owner | 1440 | light | OK. | 0 |
| `client-detail-1440-dark.png` | Client detail | owner | 1440 | dark | OK. | 0 |
| `trust-1024-light.png` | Trust dashboard `/trust-accounting` | owner | 1024 | light | OK. 4 tiles (Trust R 61 123,02, Active clients 2, Pending 0, "Not yet reconciled"). The balance-trend chart is cut by the fold. The PageHeader description wraps to 2 lines. | 0 |
| `trust-1024-dark.png` | Trust dashboard | owner | 1024 | dark | OK. Chart axes legible. | 0 |
| `trust-1440-light.png` | Trust dashboard | owner | 1440 | light | OK. Full trend card; the current month is dashed (partial). | 0 |
| `trust-1440-dark.png` | Trust dashboard | owner | 1440 | dark | OK. | 0 |
| `profitability-1024-light.png` | Profitability `/reports/profitability` | owner | 1024 | light | OK. Billing-trend chart, utilisation bars. The Matter profitability table scrolls inside its card (1059/670 px; known 643C "Matter table still scrolls at 1024"). | 0 |
| `profitability-1024-dark.png` | Profitability | owner | 1024 | dark | OK. The date-input calendar icons are visible in dark. | 0 |
| `profitability-1440-light.png` / `profitability-1440-light-full.png` | Profitability | owner | 1440 | light | OK (= top of -full). Billing trend, Team utilisation (3 × 100 %), Matter profitability (3 rows, margin 59–60 %), Client profitability. Truncated y labels "Estate Late Pe…" (known 643B N5). | 0 |
| `profitability-1440-dark.png` / `profitability-1440-dark-full.png` | Profitability | owner | 1440 | dark | OK (= top of -full). Green margin figures and bars are legible. | 0 |

### Matter detail: ≥ 3 tabs visited (1440, both themes)

The tab names were recorded from `[role=tab][aria-selected=true]` per shot (`obs-report.json` → `tabs`). Visited: **Overview** (above), **Fees · Time recording**, **Fees · Trust**, **Activity · Activity**.

| File | Tab (URL) | Theme | Observation | Console |
|---|---|---|---|---|
| `matter-detail-tab-time-1440-light.png` | `?tab=time` | light | OK. 5 summary tiles; By Task table. Billable hours use `text-green-600` at 2.97:1 on the table row (`components/projects/time-summary-panel.tsx:158`; light only; LOW). | 0 |
| `matter-detail-tab-time-1440-dark.png` | `?tab=time` | dark | OK. | 0 |
| `matter-detail-tab-trust-1440-light.png` | `?tab=trust` | light | OK. Trust balance card R 50 100,00 "Funds Held", 3 actions. | 0 |
| `matter-detail-tab-trust-1440-dark.png` | `?tab=trust` | dark | OK. | 0 |
| `matter-detail-tab-activity-1440-light.png` | `?tab=activity` | light | OK. The tab label reads "Activity · Activity" (known 642C finding). | 0 |
| `matter-detail-tab-activity-1440-dark.png` | `?tab=activity` | dark | OK. | 0 |

## Persona, portal and marketing shots (`personas.cjs`, `portal.cjs`, `matrix.cjs`, `marketing-control.cjs`)

| File | Surface | Persona | Width | Theme | Observation | Console |
|---|---|---|---|---|---|---|
| `home-member-carol-1440-light.png` | Home | Carol, system **Member** | 1440 | light | **As specified.** `data-audience="feeEarner"`. Cells: diary, tasks, unbilledWip, matterHealth, activity. **No KPI row.** The only StatCard is "My unbilled time 0m". | 0 |
| `home-fv-role-carol-1440-light.png` | Home | Carol on custom role **"OBS 644B Financial Viewer"** (Financial Visibility only) | 1440 | light | **As specified (OQ #1).** `data-audience="firm"`. KPI row present. **Billable % = "—" + hint "Visible to admins and owners"**, no delta element (`stat-card-delta` absent). The other three KPIs have no delta either, because of the single-day time entries (see Home note). | 0 |
| `matter-detail-member-carol-1440-light.png` | Matter detail (Moroka) | Carol, Member | 1440 | light | **As specified.** Hero tiles = **Trust balance R 50 100,00 + Next court date only**: Unbilled WIP and Outstanding are omitted. No Close/Complete actions; only "Generate Statement of Account". | 0 |
| `portal-home-1024-light.png` | Portal `/home` (`:3002`) | Moroka Family Trust contact `moroka.trust@email.co.za` | 1024 | light | OK, populated. Pending info requests 0, Upcoming deadlines 0, Recent fee notes INV-0002 R 12 224,50, Last trust movement R 100,00. The top-left brand box is an empty outlined rectangle: the tenant's uploaded logo is a white image (the same white block shows in firm Settings › General › Logo; tenant data). | 0 |
| `portal-trust-1024-light.png` | Portal `/trust` → redirects to `/trust/9181f514…` (single matter) | Moroka contact | 1024 | light | **Populated.** Trust balance R 50 100,00 "As of 11 Nov 2026"; 2 transactions (Deposit R 50 000,00, Transfer in R 100,00) with running balance; "No statement documents yet". | 1 warning: Next.js dev advisory "Detected `scroll-behavior: smooth` on the `<html>` element…" |
| `portal-login-link-sent-1024-light.png` | Portal `/login?orgId=mathebula-partners` after "Send Magic Link" | — | 1024 | light | Dev-mode panel shows the magic link. The link was taken from the page (`portal-report.json` → `linkSource`); Mailpit was not needed. **The token visible in this image is dead:** it was consumed at 21:46:17 UTC (exchange, backend log), tokens are single-use with a 15-min TTL (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java:28`, `TOKEN_TTL_MINUTES = 15`), and it was issued by a localhost dev stack. The PNG is kept as executed. | 0 |
| `portal-exchange-after-link-1024-light.png` | Portal `/auth/exchange?token=…` | — | 1024 | light | **Finding P-1 (LOW, dev-only, pre-existing, not Phase 90).** The page shows "Login Failed — Link expired or invalid" although the backend logged "Exchanged magic link for portal JWT -- contact f6350605… customer 713a1f9a…" and `localStorage.portal_jwt` is set (`hasJwt: true`). Cause: `portal/app/auth/exchange/page.tsx` runs the exchange in a `useEffect`. Under `next dev` React StrictMode runs it twice: the first call stores the session but its `router.push` is skipped (`cancelled`), and the second call 401s on the consumed token. Production builds do not double-invoke effects (not observed here). Navigating to `/home` works. | 1 error: "Failed to load resource: 401" (the second exchange call) |
| `marketing-stored-dark-1440.png` | Marketing `/` | anonymous, `kazi-theme=dark` stored + OS dark | 1440 | stored dark | **Still light (ADR-371).** `<html class="light">`, `style.colorScheme=light`, body background `lab(96.7 …)` (the light `--background`). The hero is the page's own dark-designed section in both themes; the light body shows below it (see -full shots). | 0 |
| `marketing-control-no-pref-1440.png` / `-full.png` | Marketing `/` | anonymous, no stored preference, OS light | 1440 | default | Control. | 1 warning: Next.js dev "Image … /screenshots/invoices.png was detected as the LCP … add loading=eager" |
| `marketing-stored-dark-rerun-1440.png` / `-full.png` | Marketing `/` | anonymous, `kazi-theme=dark` + OS dark | 1440 | stored dark | **Byte-identical to the control**: `viewportIdentical: true`, `fullPageIdentical: true` (`marketing-report.json`). The stored dark preference changes nothing outside `/org/`. | same LCP advisory as control |

### FINANCIAL_VISIBILITY custom-role flow (ruling R6): every step observed (`role-flow/`, `personas-report.json` → `flow`)

| # | Step | Evidence | Result |
|---|---|---|---|
| 1 | Roles before | `role-flow/01-roles-before.png` | "OBS 644B Financial Viewer" absent (`roleExistedBefore: false`); "No custom roles yet". |
| 2 | Carol original | `role-flow/team-02-carol-original.png`, `-sheet.png` | Badge **Member**, sheet role **Member**, caps **[View Trust, AI Execute]**, override count none, no override marks. The no-override guard passed. |
| 3 | Create role | `role-flow/03-role-create-dialog.png`, `04-roles-after-create.png` | Created with Financial Visibility only; visible in Custom Roles. |
| 4 | Assign to Carol | `role-flow/team-05-…`, `team-06-carol-assign-selected.png`, `team-07-carol-on-fv-role{,-sheet}.png` | Badge/role "OBS 644B Financial Viewer", caps [Financial Visibility]. |
| 5 | FV Home (fresh Carol login) | `home-fv-role-carol-1440-light.png` | See table above. |
| 6 | Restore Carol | `role-flow/team-08-…`, `team-09-carol-restore-selected.png`, `team-10-carol-restored{,-sheet}.png`, `11-team-carol-restored-after-reload.png` | Badge **Member** (also after reload), role **Member**, caps **[View Trust, AI Execute]** = original, no overrides. **`restoreVerified: true`**. |
| 7 | Delete role | `role-flow/12-role-delete-confirm.png`, `13-roles-after-delete.png` | Deleted via "Delete OBS 644B Financial Viewer" → AlertDialog "Delete". `roleVisibleAfterDelete: false`. |
| 8 | Carol Home after restore | `role-flow/14-home-carol-after-restore-1440-light.png` | Back to `feeEarner`, the same cells as `home-member-carol-1440-light.png`, no KPI row. |
| DB (read-only SELECT, after) | `org_roles ⋈ members` in `tenant_5039f2d497cf` | — | `Admin bob@ · Member carol@ · Owner thandi@`; no other role rows. |

### Known limitation of `personas.cjs` (CodeRabbit on PR #1781)

`scripts/personas.cjs` sets its cleanup flags **after** the write's UI wait: `created = true` only after the create dialog detaches (`:113`), and `assigned = true` only after `assignRole()` returns (`:119`). If the server accepted a write but the following wait threw, the `finally` block would skip the matching cleanup (restore Carol / delete the role), leaving tenant state behind.

- **This run was unaffected.** `personas-report.json`: `error`, `restoreError` and `afterRestoreError` are all absent; `flow.roleVisibleAfterCreate: true`, `flow.restoreVerified: true`, `flow.roleVisibleAfterDelete: false`. Screenshots: `role-flow/team-10-carol-restored-sheet.png` (Carol back on Member with her original caps) and `role-flow/13-roles-after-delete.png` (the role is gone). The read-only SELECT after the run (table above) shows no leftover role row.
- The script is kept byte-for-byte as executed (evidence rule, as in 644A), so it is not edited here.
- **For any future reuse:** set each cleanup flag immediately **before** its write (e.g. `created = true` before clicking Create, `assigned = true` before `assignRole()`), and make the cleanup tolerate "nothing to undo".

The only other tenant writes were magic-link tokens and emails for the Moroka portal contact (portal runs 1–3: backend log: tokens generated 21:44:31, 21:44:42, 21:46:14 UTC and exchanged 21:44:45, 21:46:17 UTC. The run-1 token was never exchanged and expires unused. That is 3 tokens across ~2 min, within the 3 / 5 min cap). No SQL writes.

## Verdict per surface (owner matrix)

| Surface | Light 1024/1440 | Dark 1024/1440 | Defects observed (cosmetic; already listed by a slice unless marked **new**) |
|---|---|---|---|
| Home | OK | OK | diary hint slate-400 2.64:1 light (640B) |
| Matters list | OK | OK | 1024 table h-scroll (641A F1; density regression 904 → 993 px, B-12); **new:** 1440 "…" column half-clipped (+23 px, O-1, B-12) |
| Matter detail (+3 tabs) | OK | OK | 1024 hero pushes tabs (641B F2); Trust duplicated (641B F3); "Activity · Activity" (642C); **new:** time-tab green 2.97:1 (LOW, O-2) |
| Clients list | OK | OK | — |
| Client detail | OK | OK | — |
| Trust dashboard | OK | OK | — |
| Profitability | OK | OK | 1024 matter table scroll (643C); truncated y labels (643B N5) |

**No illegible or broken regression on any of the 7 in-scope surfaces, so ruling R8 is not triggered.** Cosmetic density regressions are listed in backlog B-12 (`tasks/phase90-ui-modernization.md` § Closure report C.6 (b)). All 28 + 6 tab + 3 persona + 2 portal + marketing shots were taken.

Not observable on this tenant (stated, not claimed): KPI deltas (time entries on one day only); StatCard error state (OQ #27, vitest-only); a completed reconciliation (0 reconciliations); multi-currency profitability (643C, vitest-only).

## Files

- `obs-report.json`: per-shot facts (url, h1, `dark`, stored theme, body background, error overlay, audit). `console.json`: `[]`.
- `personas-report.json`, `personas-console.json` (`[]`), `portal-report.json` (tokens redacted), `marketing-report.json`, `stack-proof.json`.
- All JSON is compact (one line) per ruling R9.
