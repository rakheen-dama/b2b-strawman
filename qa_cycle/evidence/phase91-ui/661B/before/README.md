# 661B — observe-first "before" captures (task 661.8)

These captures were taken on `main` @ `551778100`, with no edits, on 2026-11-25. The stack was the fresh e2e mock-auth stack A (`:3001` / `:8081`), and the data came from the legal-lifecycle, legal-depth-ii and default suites (see `../../baseline/README.md`). The user was Alice (owner). Login went through the `http://localhost:3001/mock-login` UI ("Sign In").

**Deviation from OBS (named).** The Playwright MCP Chrome profile belongs to the user's live session (memory `env_playwright_mcp_profile_lock_obs`), so the captures come from a Playwright **library** script. It runs `chromium.launch()` from `frontend/node_modules/@playwright/test` 1.59.1. The script is committed byte-for-byte as executed: `scripts/capture-before.cjs`. Run it from the worktree root with `node qa_cycle/evidence/phase91-ui/661B/before/scripts/capture-before.cjs qa_cycle/evidence/phase91-ui/661B/before`.

How the script sets things up:

- **Theme:** localStorage `kazi-theme` = `light` / `dark`, set by an init script. This is the app's own mechanism (`components/theme/theme-config.ts:6`, which next-themes reads, plus the head script).
- **Sidebar state:** the cookie `sidebar_state` = `true` / `false` (`components/ui/sidebar.tsx:22`).
- **Screenshots:** viewport size, `deviceScaleFactor` 1.

The script was fixed twice during dry runs into `/tmp`: the matters API path, the mock user-menu trigger, the portal `/login` wait, crops, and the hex probe. The committed file is the one that produced every file here. Raw per-tile PNGs (27) and the Chromium trace were kept out of the tree under EVIDENCE-BUDGET. Every tile appears unscaled in the contact sheets.

Surfaces:

- **Matter detail:** "Estate Late Peter Moroka …" (`/org/e2e-test-org/matters/28288998-…`), the ACTIVE client matter with the largest trust balance.
- **DRAFT fee note:** `b85781c8-…`
- **APPROVED fee note:** `60bb72c5-…`

## Captures (8 PNG + 6 JSON)

| File | Content |
|---|---|
| `shell-1024.png` | Matter detail at 1024×768: expanded/collapsed × light/dark (2×2) |
| `shell-1440.png` | Matter detail at 1440×900: expanded/collapsed × light/dark (2×2) |
| `shell-390-sheet.png` | 390×844: closed and mobile Sheet open ("Toggle menu"), light and dark |
| `marketing-1024-light.png` | Marketing `/` at 1024×768, light, unauthenticated |
| `portal-1024-light.png` | Portal `/login` and `/home` at 1024×768, light |
| `dark-accent-buttons.png` | Fee-note header: **Approve** (DRAFT) and **Send fee note** (APPROVED), `variant="accent"`, light and dark |
| `dark-row-menu-focused.png` | Row menu (`Actions for …`, settings → Matters → Custom fields) opened by pointer, then ArrowDown ×1 (focus "Edit") and ×2 (focus "Delete"), light and dark |
| `light-controls.png` | Light: the theme toggle (mock-mode user popover, `auth-header-controls.tsx`), the dialog close button (New Matter dialog header) plus the full dialog, and an open Select (settings → firm → General, "Default Currency") |
| `probes.json` | Computed styles as raw `lab()`/`oklab()` values plus canvas-resolved sRGB hex |
| `perf.json` | CDP `Performance.getMetrics` deltas and a trace summary (§18.6.4) |
| `live-selectors.json` | Live DOM counts for SEL-COMPAT |
| `console.json` | Console errors and warnings across all pages |
| `notes.json` | Chosen ids and run notes |

**Portal capture.** The portal was the worktree's own, run with `next dev --port 3012` and `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`. The script requested a magic link for `contact@acme.example.com` (`request-link` → `exchange`), seeded `portal_jwt` / `portal_customer` / `portal_last_org_id`, and routed `:8081/**` with `Origin: http://localhost:3002` plus ACAO headers. `/home` rendered h1 "Home" for Acme Corp. The Next dev-tools "N" badge shows bottom-left on both tiles; the after shot must use the same `next dev` setup.

## Computed-style probes (sRGB hex; from `probes.json`)

| Probe | Light | Dark |
|---|---|---|
| Rail `[data-slot=sidebar-inner]` bg / fg | `#07080b` / `#ffffff` | `#0c0e12` / `#ffffff` |
| Active nav item `[data-sidebar=menu-button][data-active=true]` bg / fg | `#ffffff` @ 0.06 / `#ffffff` | `#ffffff` @ 0.06 / `#ffffff` |
| Accent button `button[data-variant=accent]` ("Approve", "Send fee note") bg / fg | `#00a38f` / `#ffffff` | `#00a38f` / `#ffffff` |
| Focused menu item ("Edit", ArrowDown ×1) bg / fg | `#00a38f` / `#ffffff` | `#00c2b1` / `#ffffff` |
| Focused menu item ("Delete", ArrowDown ×2) bg / fg | `#00a38f` / `#e7000b` | `#00c2b1` / `#ff6467` |
| `main#main-content` bg / fg | `#f4f6f8` / `#14171c` | `#07080b` / `#ffffff` |
| Header (floating) bg / fg | `#fdfdff` @ 0.90 / `#14171c` | `#14171c` @ 0.90 / `#ffffff` |
| 390 mobile Sheet bg / fg | `#07080b` / `#ffffff` | `#0c0e12` / `#ffffff` |
| Select highlighted item ("ZAR — South African Rand") | `#00a38f` / `#ffffff` | — |
| Theme toggle "Light" (on) / "Dark", "System" (off) | `#00a38f`/`#ffffff`; transparent/`#14171c` | — |
| Dialog close button | opacity 0.7, fg `#14171c`, bg transparent | — |

The accent button is teal-600 in **both** themes, and the dark focused menu item is teal-500 (`.dark --accent`). This is the "dark untouched" reference for the 661.14 dark accent check.

## Performance proxy (§18.6.4) — matter-detail load from `/dashboard`

Method: CDP `Performance.getMetrics` delta from the dashboard (settled) to the matter page (h1 visible + 1.5 s), 3 runs per theme, 1440×900. Values are in milliseconds.

| | RecalcStyleDuration (median) | LayoutDuration (median) | RecalcStyleCount | LayoutCount |
|---|---|---|---|---|
| Light | **15.5 ms** (15.4 / 15.5 / 15.9) | **5.2 ms** (4.9 / 5.2 / 5.3) | 31–34 | 6–7 |
| Dark | **17.4 ms** (17.0 / 17.4 / 17.9) | **5.1 ms** (4.8 / 5.1 / 5.2) | 32–35 | 6–7 |

A separate Chromium trace of the same load, light, with the `devtools.timeline` categories, gave UpdateLayoutTree 22.3 ms, Layout 6.71 ms and Paint 2.78 ms. The trace file itself is not committed (1.6 MB).

## SEL-COMPAT — before

| Selector / invariant | Present at (HEAD `551778100`) | Live check |
|---|---|---|
| Exactly one `<main id="main-content">` | `frontend/app/(app)/org/[slug]/layout.tsx:142` | matter: 1 `main`, 1 `main#main-content`; dashboard: 1 / 1 |
| Exactly one h1 inside `#main-content` (`sidebar-navigation.spec.ts:38-39`: `page.locator("#main-content")` → `getByRole("heading", { level: 1 }).first()`) | `frontend/e2e/tests/navigation/sidebar-navigation.spec.ts:38`, `:39` | matter: 1 h1 ("Estate Late Peter Moroka …"); dashboard: 1 h1 ("Home") |
| `navigation[name="Main navigation"]` | spec: `e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts:104`, `:264`; `e2e/tests/navigation/sidebar-navigation.spec.ts:80`. Source: `components/shell/app-sidebar.tsx:264` (`<nav aria-label="Main navigation">`) | 1 on matter and dashboard |
| day-00 link names, visible | `day-00-firm-setup.spec.ts:109-110`: "Matters", "Clients", "Diary", "Conflict check" | — (legal-lifecycle 105/105) |
| day-00 link names, absent | `day-00-firm-setup.spec.ts:112-113`: "Projects", "Customers", "Invoices", "Court Calendar" | — |
| day-00 module links | `day-00-firm-setup.spec.ts:268-270`: "Diary", "Conflict check", "Adverse parties" | — |
| Fees / Trust start collapsed | `day-00-firm-setup.spec.ts:272-275` (clicks the "Fees" and "Trust" buttons to expand); source `lib/nav-items.ts:174` (fees), `:228` (trust) `defaultExpanded: false` | `shell-1440.png` shows FEES collapsed (chevron right) |
| Trust links after expand | `day-00-firm-setup.spec.ts:276-277`: "Overview", "Transactions", "Client ledgers", "Investments"; "Tariffs" at `:274` | — |
| Absence of "Interest" | `day-00-firm-setup.spec.ts:279` (`toHaveCount(0)`) | — |
| `org-brand-accent` testid | **not referenced in `e2e/`**. Source `components/shell/app-sidebar.tsx:230`; vitest `components/__tests__/app-sidebar.test.tsx:456` | 1 on matter and dashboard |
| "Search, Command K" | not referenced in `e2e/`. Source `components/shell/app-header.tsx:41` and `app-sidebar.tsx:244`; vitest `app-header.test.tsx:59,61,71`, `app-sidebar.test.tsx:167,445`. Two specs click a page-level **"Search"** (`exact: true`) button: `day-07-14-work-trust.spec.ts:226` and `day-30-billing.spec.ts:240`, so the header name must never become exactly "Search" | 2 per page (header + sidebar pill) |
| Strict `locator("main")` uses in `e2e/` | 28 lines in 12 files (`grep -rnE "locator\(['\"]main['\"]\)" frontend/e2e`). The task file says 44, probably counted differently; the invariant (exactly one `<main>`) holds either way | — |

## Console

There is one error across every page captured: `marketing` → "Failed to load resource: 404". It is a marketing-page asset and is outside the firm shell. There were no hydration warnings on any firm-app page.

## Observations (recorded, not fixed — not in 661B scope)

1. **Destructive menu item on the teal focus fill.** In the custom-fields row menu, "Delete" is red text (a className; `data-variant` is `default`). Focused, it sits on the teal fill: light `#e7000b` on `#00a38f`, dark `#ff6467` on `#00c2b1`. The contrast is visibly poor (`dark-row-menu-focused.png`, right column). This is a candidate for the kinks register.
2. **390 matter-detail header overflows horizontally.** The "Back to Matters" + "Generate Document" + "Generate …" row runs past the viewport (`shell-390-sheet.png`, closed tiles). This is a candidate for the kinks register.

## Not captured

Nothing. Every requested surface was captured.
