# 664A OBS-before — Settings shell (task 664.1)

Captured 2026-11-26 (~11:54Z) on the running e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090), Playwright library, headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme through localStorage `kazi-theme`. Read-only: navigation and window scrolling only — no tab clicks, no form input, no submits.

## Stack observed

- `docker inspect e2e-frontend --format '{{.Created}}'` → `2026-11-26T11:32:44.987883253Z` (container); image `22eed7334…` created `2026-11-26T11:28:14Z`.
- Compose labels: built from `worktree-epic-663C/compose` (that worktree has since been removed). The image is 4 min younger than 663C's head `7149f9c11` (committed 11:24Z) and older than the squash-merge `5daacb930` (11:43Z). `git diff --stat 7149f9c11 5daacb930 -- frontend packages compose` is **empty**, so the frontend observed equals `main` @ `5daacb930`/`cee91f031` (this worktree's base) — assuming the 663C worktree had no uncommitted frontend changes at build time (not provable after the fact).
- Personas: **Alice** (owner; `/mock-login` UI "Sign In"), **Carol** (member; `mock-auth-token` cookie from the mock IDP, as `frontend/e2e/fixtures/auth.ts`). Carol's capabilities (`GET /api/me/capabilities`): `AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, VIEW_LEGAL, MANAGE_DISBURSEMENTS, VIEW_TRUST`; not admin.
- Org modules enabled: `court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing`.

## Scripts (run in order from `scripts/`, nvm node 20)

| Script | What |
|---|---|
| `00-discover.cjs` | GET-only: capabilities, enabled modules, a matter id and a client id per persona |
| `01-capture.cjs` | All captures + `measurements.json`, `sel-compat.json`, `console.json`; raw tiles to `/tmp/664A-before-raw/` (outside the repo) |
| `02-assemble.cjs` | Contact sheets from the raw tiles (shared `661B/walk/scripts/common.cjs` `sheet()`) |

## Sheets

| File | Contents |
|---|---|
| `sheet-firm-alice.png` · `sheet-firm-carol.png` | Firm (default tab General): rows 1024×768 / 1440×900, cols light / dark (viewport shots) |
| `sheet-matters-alice.png` · `sheet-matters-carol.png` | Matters (Templates), same grid |
| `sheet-documents-alice.png` · `sheet-documents-carol.png` | Documents (Templates), same grid |
| `sheet-fees-alice.png` · `sheet-fees-carol.png` | Fees & Trust (Rates), same grid |
| `sheet-connections-alice.png` · `sheet-connections-carol.png` | Connections (Integrations), same grid |
| `sheet-390-alice.png` · `sheet-390-carol.png` | 390×844: the five families light + Firm dark (K-02) |
| `sheet-forbidden-notice.png` | Carol 1440 `firm?tab=audit-trail` and `firm?tab=roles`, light / dark, crop x≥256 y<400 (K-12) |
| `sheet-pill-tab-strip.png` | The family pill strip (TabsList `w-max`), Alice 1440 element shots, 5 families × light / dark |
| `sheet-rail-sticky.png` | Alice light Firm › General, crop x 256..656: 1024 scrollY 0 / 600, 1440 scrollY 0 / 600 (K-03) |
| `sheet-grouped-tab-bar.png` | GroupedTabBar consumers, Alice 1440 light: matter `9a7042bc…` and client `68c6b51e…` (Acme Corp) viewport + bar element shots — the pixel baseline for 664A's `tab-underline.ts` extraction |

## Forbidden tab used for Carol

Primary: **`/settings/firm?tab=audit-trail`** — gated by `requiredCapability: "TEAM_OVERSIGHT"`, which Carol lacks. Also captured: `/settings/firm?tab=roles` (`adminOnly`), the register's K-12 route. Both render `You don't have access to {Tab} — showing General instead.` above General.

## Key facts

**P-1 tab count:** `frontend/lib/settings-nav.ts` has **32 tabs** (Firm 8, Matters 8, Documents 4, Fees & Trust 6, Connections 6). Phase 93's entry is Matters › `take-on` (label "Take-on", `requiredCapability: "MANAGE_TAKE_ON"`, line 181). It is the 8th Matters tab, shown to Alice (8 Matters tabs) and absent for Carol (2: Templates, Tags).

**K-03 (rail sticky):** the rail `nav` has computed `position: sticky; top: 56px` (`top-14`), `height: calc(100vh - 3.5rem)`. `--app-header-height` = `3.5rem` (56 px); the floating header has `top: 8px` (`top-2`), so its bottom edge while stuck is at **64 px**. At scrollY 0 the rail top is 96 (light; 88 in dark) under a header at 16–72 (light) / 8–64 (dark). After `scrollTo(0, 600)` (Firm › General; 2 444 px of scroll at 1440, 2 914 at 1024) the rail top is **56** and the header 8–64 at both 1024 and 1440: the rail's top **8 px sit under the header**. Its `py-4` padding keeps the first link ("Firm", top 72) clear of the header, so nothing is hidden, but the offset is a hard-coded 56, not header height + 0.5rem (64) like GroupedTabBar's `top-[calc(var(--app-header-height)+0.5rem)]` (= 64px measured). No scroll container between the rail and the root; the window scrolls.

**K-02 (390):** `nav[aria-label="Settings navigation"]` is in the DOM (count 1) but its `aside` is `display: none` (0 visible). In the a11y tree (`ariaSnapshot` of `body`) the only navigation landmark is `navigation "Breadcrumb"`; **no link to any settings family** ("Firm", "Matters", "Documents", "Fees & Trust", "Connections") and no visible `a[href*="/settings/"]` to another family on any of the 5 families, either persona. The only way to another family is the app Sheet. The family pill strip scrolls horizontally at 390 for Alice on Firm (723 px in 366), Matters (756), Fees (524), Connections (452); fits for Documents and for every Carol family.

**K-12 (forbidden notice):** hard-coded slate pair, the same on both forbidden tabs:
- light: text `#3a4049` on `#f8f9fb`, border `1px solid #dee1e7`, radius 8 px — contrast 9.92:1
- dark: text `#cbd0d7` on `#14171c`, border `1px solid #20242b`, radius 8 px — contrast 11.584:1

**Nav count per width:** `nav[aria-label="Settings navigation"]` = 1 at 390, 1024 and 1440 (visible at 1024/1440, hidden at 390).

**Rail active state (hard-coded classes):** family link `aria-current="page"`; current tab link `aria-current="true"`, bg `#f0fdfa` (teal-50) / text `#14171c`, `border-left 2px #00a38f` in light; bg `#20242b` (slate-800) / `#ffffff`, same teal border in dark.

**Pill strip:** active tab bg `#fdfeff` / text `#14171c` in light; `#242b32` at alpha 0.30 / `#ffffff` in dark. At 1024 (content column 456 px light / 464 dark) Alice's strip overflows on Firm (723), Matters (756) and Fees & Trust (524); never at 1440 (872/880). Carol's strips never overflow (1–2 tabs).

**Grouped-tab-bar underline (selected tab `::after`, matter and client identical):** `content: ""`, `position: absolute`, `height: 2px`, `background-color: lab(7.73948 -0.41344 -4.03315)` = `#14171c` (foreground), `bottom: 0px`, `left/right: 0px`, `opacity: 1` (unselected: `0`), `border-radius: 3.35544e+07px` (rounded-full), transition none (reduced motion). Trigger: 14 px / 500, padding `8px 8px 12px`, height 40; selected text `#14171c`, unselected `#4d545f`. Sticky bar: `top: 64px`, z 20, bg `#fdfeff`, border-bottom `1px #e0e3e8`.

**SEL-COMPAT before** (`sel-compat.json`, 1024 light `/settings/firm`, both personas): `nav[aria-label="Settings navigation"]` ×1 (`getByRole("navigation", {name})` ×1); family `aria-current="page"` = ["Firm"]; tab `aria-current="true"` = ["General"]; one tablist "Firm settings" (in `#main-content`); `role="tab"` ×8 for Alice (General `aria-selected="true"`, the rest `false`), ×2 for Carol (General, Subscription); h1 in `#main-content` = 1 ("General"); `<main id="main-content">` = 1 (and 1 `<main>` in total); "Search, Command K" exact: 2 buttons in the page (sidebar pill + header), 1 inside `<header>`. Inner Tabs baseline (Alice): custom-fields (Matters/Tasks/Clients/Fee notes), tariffs (Billing Rates/Cost Rates), email (Overview/Delivery Log), packs (Available/Installed (5)) — each an unnamed `tabs-list` beside the family strip.

**Console:** 68 page loads, **0 errors, 0 warnings, 0 hydration messages** (`console.json`).

## Findings (not fixed)

1. **Pill strip clipped with no affordance at 1024.** Alice's Firm, Matters and Fees & Trust strips overflow at 1024 (e.g. Firm 723 px in 456): the trailing tabs ("Compliance", "Features", "Subscription") are cut mid-label at the right edge with no fade or visible scrollbar (`sheet-firm-alice.png` top row). Expected to go with 664A's underline strip; recorded for the after comparison.
2. **Light vs dark shell offset at scroll 0.** In light (ADR-392 inset card) the header sits at 16 and the rail at 96; in dark at 8 and 88. After scrolling both stick at header 8 / rail 56. Intentional per the inset-card scheme; noted because rail-top numbers differ per theme.
3. **Header family crumb truncates at 390** ("Fees & Tr…", "Connectio…") — breadcrumb shrink behaviour (663C K-26 area), outside 664A.
4. Carol's Fees & Trust family has a single tab (Rates) whose panel is an in-page denial — already register row K-123 (backlog), re-observed.
5. Image provenance: the stack was built from the 663C worktree, not from `main`; equivalence rests on the empty `7149f9c11..5daacb930` frontend diff (see Stack observed).
