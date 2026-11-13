# 642C observe-first — BEFORE (task 642.11)

Captured 2026-11-12 against the e2e mock-auth stack (:3001 frontend / :8081 backend), code @ e2e73ca8f (origin/main).
Read-only Playwright-library script `obs-before.cjs` (nvm node v20, `@playwright/test` from the worktree's `frontend/`;
needed a `pnpm install --frozen-lockfile` in the worktree, which had no node_modules). Theme via `localStorage["kazi-theme"]`.
No source files edited. Raw data: `obs-before.json` (compact). Selector inventory: `sel-compat-before.txt`.

## Pages used (records of lifecycle run `mwe0pu4a52ee`)

| Page | URL | Notes |
|---|---|---|
| Matter | `/org/e2e-test-org/matters/80f7d731-0a0c-4f94-bcd8-f1d2bfa10535` | "Ndlovu v RAF mwe0pu4a52ee", LITIGATION, Active; 2 Motion court dates, outstanding R 5 482,63 |
| Client | `/org/e2e-test-org/clients/cfb69742-e9fa-4c61-8438-fa42319c30b1` | "Sipho Ndlovu mwe0pu4a52ee", 2 matters, fee notes, trust, FICA done |

## Tab lists (`role=tab`, `data-testid`, dropdown = Radix `aria-haspopup="menu"` + chevron)

**Matter, Alice (owner)**: 7 groups — `tab-group-details` Details (dropdown: details, fields, team) · `tab-group-overview` Overview (single, default active) · `tab-group-work` Work (dropdown: tasks, documents, correspondence, generated) · `tab-group-finance` Fees (dropdown: time, disbursements, budget, rates, financials, statements, trust) · `tab-group-client` Client (dropdown: clients, requests, comments, adverse-parties) · `tab-group-schedule` Diary (single: court-dates) · `tab-group-activity` Activity (dropdown: activity, audit).
22 tabpanels in the DOM, one visible.

**Matter, Carol (member)**: same 7 groups, but `tab-group-activity` is a **single** tab (Audit hidden by `useAuditTabVisible`) — group shape differs by role.

**Client, Alice**: 6 groups — Details (dropdown: details, fields, tags) · Overview (single, default) · `tab-group-work` **"Matters"** (dropdown: matters, documents, generated) · `tab-group-finance` Fees (dropdown: invoices, rates, financials, trust) · `tab-group-compliance` FICA & compliance (dropdown: onboarding, requests) · `tab-group-activity` Activity (single: audit). 14 tabpanels.

Same testid, different label/content across pages: `tab-group-work` = "Work" (matter) vs "Matters" (client); `tab-group-activity` is a dropdown on the matter (owner) but single on the client and for Carol on the matter.

## Measurements

Bar: `div[role=tablist][data-testid=grouped-tab-bar]`, class `relative flex gap-6 border-b border-slate-200 dark:border-slate-800`, height 33px, **transparent background**, `position: relative`. Exactly 1 bar in the DOM on every load (no hidden duplicate seen).

| Page | Viewport | bar x / y / w | bar scrollW / clientW | doc scrollW / innerW |
|---|---|---|---|---|
| matter | 1440×900 (light & dark) | 304 / 466 / 1088 | 1088 / 1088 | 1440 / 1440 |
| matter | 1024×768 (light & dark) | 304 / 624 / 672 | 672 / 672 | 1024 / 1024 |
| client | 1440×900 (light & dark) | 280 / 467 / 1136 | 1136 / 1136 | 1440 / 1440 |
| client | 1024×768 (light & dark) | 280 / 625 / 720 | 720 / 720 | 1024 / 1024 |

- **Sticky? No.** Bar is `position: relative`; no ancestor is sticky/fixed. Page scrolls on the window (`document.scrollingElement`; `html { scroll-behavior: smooth }`). Matter `?tab=activity` scrolled 600px → bar top 466 → **−134 (off-screen)**. Default tabs are short (max scroll 340 matter / 166 client), so the bar moves up by the full scroll range and stays visible only because the page runs out (top 466→126 matter, 467→301 client; client `?tab=audit` 467→116).
- **Ancestors (bar → html)**: all `overflow: visible` on both pages — nothing blocks `position: sticky`. Positioned ancestors: only `relative` ones (the bar itself and the SidebarInset div). An `animate-in fade-in slide-in-from-bottom-[2px]` wrapper is an ancestor (transform animation on page enter).
- Bar's parent is the Radix `Tabs.Root` div (bar + all panels: 23 children matter / 15 client, height 1235 / 575) → a sticky bar would stay pinned for the whole tab-content height.
- The app shell header is `sticky top-2 z-30` (`bg-card/90`, height `--app-header-height` = 56px, rect y=8..64); the sidebar is `fixed z-10`. A sticky tab bar needs `top` ≥ ~64px (+gap) and z below 30, plus an opaque/blurred background (today it's transparent).
- **Alignment**: matter bar starts at x=304, client at x=280 — the matter page has an extra `space-y-6 p-4 lg:p-6` wrapper around the tabs, client does not.
- **Keyboard**: `focus()` on first tab then ArrowRight ×2 → focus moves Details → Overview → **Work** (`tab-group-work`) on both pages; `Tab` then moves to the next trigger (`tab-group-finance`) — every trigger has `tabIndex=0` (no roving tabindex). `:focus-visible` matches, but **no visible focus ring**: computed `outline-style: none` (width 2px, colour ring/50) and `box-shadow: none` — the `outline-none` class kills `focus-visible:outline-2` under Tailwind v4. Screenshot `alice-*-keyboard-focus-1440-light.png` (focus is on "Work", nothing drawn).
- **F7 (pre-existing horizontal overflow, light)**:

| Page | Viewport | doc scrollW / innerW | bar scrollW / clientW | bar right | last tab right | Other overflowing elements |
|---|---|---|---|---|---|---|
| matter | 390×844 | **609 / 390** | 520 / 326 | 358 | 552 | header actions row (`generate-statement-btn` →573, `overflow-actions-trigger` →609), `project-overflow-menu` →395 |
| matter | 768×1024 | **873 / 768** | 520 / 432 | 728 | 816 | header actions row (`generate-statement-btn` →837, `overflow-actions-trigger` →873) |
| client | 390×844 | **515 / 390** | 499 / 358 | 374 | 515 | tab bar only ("FICA & compliance" wraps to 2 lines) |
| client | 768×1024 | **779 / 768** | 499 / 464 | 744 | 779 | tab bar only |

  The bar has `overflow-x: visible`, so its tabs spill past its box and widen the document. On the **client** page the tab bar is the sole cause; on the **matter** page the header action row (Generate Document / Generate Statement of Account / ⋯, above the hero) overflows further than the tab bar — fixing the tab bar alone will NOT bring matter doc width to 390/768.
- **Console**: 0 errors, 0 warnings, 0 hydration warnings across all 8 Alice loads, the tab visits on both pages, the F7 loads and Carol's load.

## Tab switching (1440 light, Alice)

| Page | Action | URL after | History / network | Selected trigger text |
|---|---|---|---|---|
| matter | click `tab-group-schedule` (single) | `?tab=court-dates` | app `replaceState(null)` + Next sync `replaceState`; **0 RSC** | "Diary" |
| matter | click `tab-group-work` (inactive multi) → `tab-item-tasks` | `?tab=tasks` | as above, 0 RSC | "Work · Tasks" |
| matter | click `tab-group-finance` (inactive) → `tab-item-trust` | `?tab=trust` | 0 RSC | "Fees · Trust" |
| matter | click `tab-group-finance` (active) → `tab-item-statements` | `?tab=statements` | 0 RSC | "Fees · Statements of account" |
| client | click `tab-group-activity` (single) | `?tab=audit` | `router.replace`: 1 Next `replaceState` + **1 RSC GET `?tab=audit&_rsc=…`** | "Activity" |
| client | `tab-group-work` (inactive) → `tab-item-documents` | `?tab=documents` | 1 RSC | "Matters · Documents" |
| client | `tab-group-finance` (inactive) → `tab-item-trust` | `?tab=trust` | 1 RSC | "Fees · Trust" |
| client | `tab-group-finance` (active) → `tab-item-invoices` | `?tab=invoices` | 1 RSC (+4 `<Link>` prefetches of fee-note pages from the panel) | "Fees · Fee notes" |

The active group's accessible name gains " · <sub-tab>" (e.g. "Work · Tasks"). The open menu highlights the current sub-tab (`bg-slate-100`). Menu screenshots: `matter-dropdown-work-open-1440-light.png`, `client-dropdown-fees-open-1440-light.png`.

## Findings relevant to the restyle

- **F-a: clicking an inactive multi-tab group opens its menu; it does NOT navigate.** `grouped-tab-bar.tsx:149-156` says an inactive click "navigates to first sub-tab; keep dropdown closed", but in a real browser Radix's `DropdownMenuTrigger` opens on `pointerdown`, the modal menu sets `body { pointer-events: none }`, and the `click`/`onClick` never reaches the button. Observed every time: after clicking Work/Fees/Matters/Activity while another group is active, the menu is open (`aria-expanded=true`), the URL is unchanged, and the previous group stays `aria-selected` (screenshot `matter-dropdown-work-open-1440-light.png`: Diary is underlined while the Work menu is open). Pressing **Enter** on a focused inactive group does the same: the menu opens, focus moves to the first item, there is no navigation. So that onClick branch is dead code in browsers; it only runs in jsdom unit tests that fire a bare `click`. Consumers rely on today's behaviour: `openGroupedTab` clicks until the menu is open, so it tolerates either behaviour, but `legal-depth-ii/day-05-disbursements.spec.ts:431-432` clicks `tab-group-finance` once and expects `tab-item-disbursements` to be visible. Finance is already active there, so that case is safe either way.
- **F-b: not sticky; nothing blocks sticky.** All ancestors have visible overflow and the window scrolls. A sticky bar needs a top offset clear of the `sticky top-2 z-30` app header (bottom edge at about 64px), z < 30, and a background (today it's transparent).
- **F-c: no visible keyboard focus ring today.** `outline-none` overrides the `focus-visible:outline-*` utilities (computed `outline-style: none`).
- **F-d: F7 overflow.** The client page overflow comes from the tab bar alone. The matter page's worst overflow comes from the header action row, not the tab bar.
- **F-e: a11y wiring.** `role=tablist` has no `aria-label`/`aria-orientation`. Each panel's `aria-labelledby` points at `radix-…-trigger-<tab>`, but no element has that id (0/22 and 0/14 exist), so panels have no accessible name. 46 spec uses of unnamed `getByRole("tabpanel")` (17 files + 2 helpers) depend on exactly one visible tabpanel, and today there is exactly one. A restyle that adds a second visible tablist/tabpanel (e.g. a mobile duplicate) would break them.
- **F-f: shape differs by page and role.** `tab-group-work` is labelled "Work" on the matter page and "Matters" on the client page. `tab-group-activity` is a dropdown for the owner on the matter page and a single tab on the client page and for Carol. On the matter page the bar sits 24px further right and is 48px narrower than on the client page, because of an extra padded wrapper.
- **F-g: different URL mechanics per page.** Matter uses `history.replaceState` (no server round-trip). Client uses `router.replace`, which fires one RSC request per tab change. Six spec files assert matter/client `?tab=` URLs (7 assertions), including `project-crud:172`, which anchors with `$` (`?tab=disbursements$`).

## day-07-14 baseline

4 passed / 2 failed / 10 did not run. Both failures come from re-running with the same `runId`: a duplicate Bob comment and a duplicate `DEP/MOROKA` deposit row cause strict-mode violations. Neither is a tab-bar failure. The tab-bar steps that ran (`openGroupedTab` work/tasks ×3, finance/time ×1, plus `getByRole("tabpanel")`) passed. Details and side effects on the stack are in `e2e-day-07-14-before.txt`.

## Errata (642C review, PR #1774)

The `scrolledBy` field in every `obs-before.json` `scroll[]` entry (it reads `0`) is unreliable. `html { scroll-behavior: smooth }` animates the programmatic scroll, and the probe read `scrollTop` straight after assigning it, so it got the start position back. The bar-top measurements are the valid evidence of non-sticky behaviour: `barTopBefore` → `barTopAfter`, taken 400 ms after the scroll had settled. Examples are matter `?tab=activity` 466 → −134 (off-screen), matter default 466 → 126, client default 467 → 301 and client `?tab=audit` 467 → 116. The before-state can't be re-captured, because the stack has moved on to the restyle. `obs-before.cjs` now captures the initial `scrollTop`, waits for the scroll to settle, reports `scrolledBy` as final − initial, and samples the bar rect in the same frame, so the after-OBS can reuse it.
