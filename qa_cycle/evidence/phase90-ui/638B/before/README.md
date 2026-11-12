# 638B "before" — header / palette / user menu on `main`

- **Head:** `9d7d2b62e` (`main`) — "docs(phase90): 638A Done (PR #1761)".
- **Stack provenance:** E2E mock-auth stack (frontend :3001, backend :8081, mock IDP :8090, org `e2e-test-org`), brought up fresh on 2026-11-12 by the orchestrator: `e2e-down` → docker builder prune → `e2e-up` from the main checkout. This task did not touch docker.
- **Captured:** 2026-11-12, as Alice (owner) via `/mock-login`. The day-00 SEL-COMPAT run happened first, so tenant state includes what day-00 creates (brand colour #1B3A4B, billing/cost rates, LPFF rate). That state does not change the header.
- **Method:** `before.cjs` is a Playwright **library** script (headless chromium from `worktree-epic-638B/frontend/node_modules/@playwright/test`). It logs in once and reuses `storageState` in a fresh context for each capture. Theme is set with an init script, `localStorage['kazi-theme']`. Each capture waits for `load`, then a visible `h1`, then 1.2 s (never `networkidle`). Screenshots are viewport-only.
- **Deviation:** this used a library script, not Playwright MCP. The MCP Chrome profile is held by the user's live session, so it was not used and no Chrome process was killed.
- **Data:** `before-report.json` has, for each capture: header tag/role/box/position/z-index/background/backdrop, header controls, `main` count, Search/Toggle-menu counts (with locations), dialog count, console and hydration messages, and tab order for the 1440 captures.

## Captures

| File | Route | Viewport | Theme | Notes |
|---|---|---|---|---|
| header-1024-light.png | dashboard | 1024×768 | light | scroll 0 |
| header-1024-dark.png | dashboard | 1024×768 | dark | scroll 0 |
| header-1440-light.png | dashboard | 1440×900 | light | scroll 0; tab order recorded |
| header-1440-dark.png | dashboard | 1440×900 | dark | scroll 0; tab order recorded |
| header-scrolled-1440-light.png | dashboard | 1440×900 | light | `scrollTo(0,600)` clamped to scrollY 386 |
| header-scrolled-1440-dark.png | dashboard | 1440×900 | dark | same |
| palette-1440-light.png | dashboard | 1440×900 | light | `Meta+k` opens the palette dialog |
| palette-1440-dark.png | dashboard | 1440×900 | dark | same |
| mobile-390-light.png | dashboard | 390×844 | light | sidebar collapsed into Sheet trigger |
| mobile-390-dark.png | dashboard | 390×844 | dark | same |
| user-menu-1440-light.png | dashboard | 1440×900 | light | user menu open: Theme Light/Dark/System + Sign out |

## Key facts (main @ 9d7d2b62e)

- **Header:** a single `<header>` (implicit banner) inside `SidebarInset`, with `position: sticky; top: 0; z-index: 30` and height **56 px** (`h-14`).
  - Box at 1024 and 1440: x=256 (after the 256 px sidebar), y=0, width = viewport − 256.
  - Box at 390: x=0, width=390.
  - Background is translucent: `bg-slate-100/80`, or `slate-950/90` in dark. Backdrop is `blur(12px)`. Border-bottom is `slate-200/60`.
- **Header contents:**
  - 1440: `Toggle menu` (SidebarTrigger), breadcrumb (`E2E Test Organization` link › `Home`), mock org chip `e2e-test-org`, avatar button `AO`, `Notifications` bell.
  - 390: `Toggle menu`, `Home` crumb, org chip, `AO`, `Notifications`.
- **Scroll:** page heights at 1440 are dashboard 1286 px and projects 900 px (projects does not scroll), so the dashboard was used. `scrollTo(0,600)` clamps to scrollY **386**. After scrolling, the header box stays at y=0, height 56, so it stays visible (sticky works).
- **`main` count:** 1 in every capture (`main#main-content`).
- **Search buttons:**
  - `getByRole('button', { name: 'Search', exact: true })` matches **0** in every capture.
  - `/search/i` matches **1** at 1024 and 1440: the sidebar button with accessible name **"Search, Command K"** at box (8,106,239×32). It is in the sidebar, **not** in the header.
  - 0 at 390 (sidebar hidden in the Sheet) and 0 while the palette is open (rest of the page is `aria-hidden`).
- **Toggle menu:** `getByRole('button', { name: 'Toggle menu' })` matches **1** at every viewport. It matches 0 while the palette dialog is open, because the page is aria-hidden behind the modal.
- **Command palette:** `Meta+k` opens 1 dialog at 512×350, centred at (464,275). Placeholder is "Search pages, settings...", with a "Pages" group (Home, My work, Diary, Matters, …).
- **User menu:**
  - Trigger: in mock auth it is the header avatar button with text `AO` and **no aria-label**. The `MockUserButton` in `components/auth-header-controls.tsx` is a hand-rolled div popover, not a Radix menu.
  - Opened panel: Alice Owner / alice@e2e-test.local, then "Theme" with a Light/Dark/System segmented radio group, then Sign out.
- **Tab order (1440, light and dark, starting at Toggle menu):**
  - Toggle menu → `E2E Test Organization` (breadcrumb link) → `AO` (user menu) → `Notifications`.
  - It then leaves the header and goes into page content: Open diary → Open unbilled work → Open collections → Open reconciliation → View all.
  - The org chip is not focusable.
- **Console:** 0 errors/warnings, 0 page errors, 0 hydration messages across all 11 captures.

## SEL-COMPAT before-run

`day-00-firm-setup.spec.ts` under `e2e/playwright.legal-lifecycle.config.ts`, run from `worktree-epic-638B/frontend` (no `e2e/` diff vs main): **16 passed, 0 failed, 0 skipped (14.0 s), exit 0**. Full log: `day-00-firm-setup.txt`. Run standalone (day-00 is the first file of the serial chain). The worktree was restored afterwards: `git checkout -- frontend/test-results documentation/screenshots`, then `git status` clean except this folder.
