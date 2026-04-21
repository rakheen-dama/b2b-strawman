# Phase 68 — Portal Client-POV Curated Screenshots

This directory holds curated PNG captures of the **Portal Client-POV 90-Day** lifecycle (Phase 68 / Epic 500) for marketing, blog, walkthrough, sales-deck, and demo use across the three vertical profiles (`legal-za`, `accounting-za`, `consulting-za`).

## Status

Placeholder. PNGs are populated **manually** by a human operator after a clean Playwright run of `portal/e2e/tests/portal-client-90day/*.spec.ts` against a live **Keycloak dev stack** with three pre-provisioned vertical tenants (`mathebula-partners`, `ledger-collective`, `keystone-consulting`).

The deferred capture is documented in `tasks/phase68-gap-report.md` (`GAP-013` — curated PNGs deferred to live-stack run).

## No `captureScreenshot` helper in the portal codebase

Unlike `frontend/e2e/helpers/screenshot.ts` (which exposes `captureScreenshot(page, name, { curated: true })` for the firm-side specs), the **portal codebase does not have a curated-capture helper**. The four 500A spec files (`day-00-07.spec.ts`, `day-14-30.spec.ts`, `day-45-75.spec.ts`, `day-85-90.spec.ts`) only invoke `expect(page).toHaveScreenshot(...)` for regression baselines (which land under `portal/e2e/screenshots/portal-v2/portal-client-90day/`).

Curated PNGs in this directory must therefore be captured **by hand** via one of:

1. **Playwright MCP** (`mcp__playwright__browser_navigate` + `mcp__playwright__browser_take_screenshot` with `filename: "<name>.png"`).
2. **Manual browser capture** against the live portal at `http://localhost:3002` (Cmd+Shift+4 / dev-tools "capture full size screenshot").
3. A bespoke `pnpm exec playwright test` invocation that calls `await page.screenshot({ path: "documentation/screenshots/portal/<name>.png" })` from a one-off curated spec (not shipped — left as a follow-up tooling improvement; see `GAP-014` in the gap report).

Adding a portal-side `captureScreenshot` helper that mirrors the frontend pattern is tracked as a follow-up tooling gap in `tasks/phase68-gap-report.md`.

## Capture procedure (manual)

Prerequisites:

1. Keycloak dev stack up: `bash compose/scripts/dev-up.sh && bash compose/scripts/keycloak-bootstrap.sh && bash compose/scripts/keycloak-seed.sh`.
2. `bash compose/scripts/svc.sh start all` (backend, gateway, frontend, portal) — verify with `bash compose/scripts/svc.sh status`.
3. Three firm-side lifecycles run to seed prerequisite state per tenant:
   ```bash
   /qa-cycle-kc qa/testplan/demos/legal-za-90day-keycloak.md           # → mathebula-partners (Days 0–21 minimum)
   /qa-cycle-kc qa/testplan/demos/accounting-za-90day-keycloak-v2.md   # → ledger-collective (Days 0–60)
   /qa-cycle-kc qa/testplan/demos/consulting-agency-90day-keycloak.md  # → keystone-consulting (Days 0–30)
   ```
4. Within each firm-side run, ensure a portal contact exists per tenant (`sipho.portal@example.com`, `zola.portal@example.com`, `thembi.portal@example.com`).

Capture each shot:

1. Open `http://localhost:3002`, log in via magic-link as the appropriate portal contact for the target tenant.
2. Navigate to the page described in the manifest below.
3. Wait for `networkidle` (or visually wait until the page settles — module-gate + `use-portal-context` resolution can take ~500 ms).
4. Capture full-page PNG and save to `documentation/screenshots/portal/<name>.png`.
5. For mobile shots (`mobile-drawer-open-*.png`, `acceptance-flow-mobile.png`): resize the browser window (or Playwright viewport) to **375×812** (iPhone equivalent) before capturing.
6. For the digest-email shot (`digest-email.png`): open Mailpit at `http://localhost:8025`, find the most recent weekly digest for the target portal contact, capture the **email body iframe** (not the Mailpit chrome).

## Prescribed shots — 16 PNGs (mirrors the Phase 68 lifecycle in `qa/testplan/demos/portal-client-90day-keycloak.md` §7.2)

### Per-vertical shells (9 PNGs — 3 profiles × 3 surfaces)

1. **`desktop-shell-legal-za.png`** — Portal desktop nav + `/home` shell on the `legal-za` tenant (`mathebula-partners`, `sipho.portal@example.com`). Sidebar shows: Home, Info Requests, Projects, Trust, Deadlines, Invoices, Settings.
2. **`desktop-shell-accounting-za.png`** — Portal desktop nav + `/home` shell on the `accounting-za` tenant (`ledger-collective`, `zola.portal@example.com`). Sidebar shows: Home, Info Requests, Engagements, Deadlines, Invoices, Settings (no Trust, no Retainer).
3. **`desktop-shell-consulting-za.png`** — Portal desktop nav + `/home` shell on the `consulting-za` tenant (`keystone-consulting`, `thembi.portal@example.com`). Sidebar shows: Home, Info Requests, Projects, Retainer, Invoices, Settings (no Trust, no Deadlines).
4. **`mobile-drawer-open-legal-za.png`** — Mobile sidebar (Sheet) open at viewport 375×812, legal-za tenant.
5. **`mobile-drawer-open-accounting-za.png`** — Mobile sidebar (Sheet) open at viewport 375×812, accounting-za tenant.
6. **`mobile-drawer-open-consulting-za.png`** — Mobile sidebar (Sheet) open at viewport 375×812, consulting-za tenant.
7. **`home-populated-legal-za.png`** — `/home` with non-empty info-request cards, deadline reminders, and invoice nudges (legal-za, after Day 21 of the lifecycle).
8. **`home-populated-accounting-za.png`** — `/home` populated (accounting-za, after Day 30+ of the lifecycle).
9. **`home-populated-consulting-za.png`** — `/home` populated (consulting-za, after Day 30 of the lifecycle).

### Profile-gated detail surfaces (3 PNGs — one per gated module)

10. **`trust-detail.png`** — `/trust` page with deposit history, current balance, and recent transactions (**legal-za only** — captured from `mathebula-partners` after Day 21 trust deposit).
11. **`retainer-detail.png`** — `/retainer` page with hour-bank remaining, period bar, and consumption-by-week breakdown (**consulting-za preferred** — captured from `keystone-consulting` after Day 30).
12. **`deadlines-list.png`** — `/deadlines` populated with upcoming items (statutory + matter milestones) (**accounting-za preferred** — captured from `ledger-collective` after Day 7).

### Cross-profile flows (4 PNGs — agnostic to tenant choice)

13. **`settings-notifications.png`** — `/settings/notifications` with the cadence control (Weekly / Biweekly / Monthly) and per-channel toggles, mid-edit (Day 85 — biweekly selected, save button enabled).
14. **`invoice-payment.png`** — `/invoices/[id]` either mid-pay (Pay Now CTA visible) or paid-receipt state (PAID badge + receipt download link). Pick whichever tenant has a payable invoice on Day 45.
15. **`acceptance-flow-mobile.png`** — Proposal-acceptance flow at viewport 375×812. Captures the proposal review screen with Accept/Decline CTAs visible. Captured from any profile with a seeded proposal on Day 14.
16. **`digest-email.png`** — Mailpit screenshot of a delivered weekly digest email **body iframe** (not the Mailpit list view). Captured from the Day 7 (or Day 90 final) digest send.

## Naming convention

- Per-vertical variants are suffixed with `-{profile}.png` (e.g. `desktop-shell-legal-za.png`).
- Single-tenant or cross-profile shots use a descriptive kebab-case name without a profile suffix (e.g. `invoice-payment.png`).
- All filenames are lowercase, hyphenated, end in `.png`. No day-prefix (these are demo/marketing assets, not regression baselines indexed by lifecycle day).
- Mirrors the convention used by `documentation/screenshots/legal-vertical/phase67/` (Phase 67) and `documentation/screenshots/consulting-vertical/` (Phase 64–65).

## Convention

Curated captures are **non-regression** — intended for human-readable artifacts (blog posts, sales decks, README walkthroughs, the demo cycle in `tech-evolution/`). They live in `documentation/screenshots/`.

**Regression baselines are separate** — they live under `portal/e2e/screenshots/portal-v2/portal-client-90day/` and are managed by Playwright `--update-snapshots`. See `portal/e2e/screenshots/portal-v2/portal-client-90day/README.md` for the baseline-capture procedure.
