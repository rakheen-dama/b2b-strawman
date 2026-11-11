# Phase 90 — Kazi UI Modernization: Design-System Primitives, App Shell, Dashboards and Hero Surfaces

> **Status: READY for `/architecture` — start building only after G89 Batch E (PR #1752) and Batch F (`fix/g89-f-tooling-docs`) have merged.** Scope agreed in `/ideate` (2026-11-11). Batch E rewrites frontend layout and copy across many of the files this phase touches; starting before it lands guarantees conflicts. Phase 84 (take-on) is unaffected and may be sequenced before or after this phase by the founder.
>
> **Why this phase exists.** Kazi is going in front of design-partner firms. Its UI works but looks assembled rather than designed: no shared table, page-header, chart or KPI primitive; dashboards that are mostly cards and plain tables; matter and client detail pages that are 680–760-line monoliths; dark-mode tokens that nothing ever turns on. To a managing partner, an inconsistent UI reads as a risky small vendor before they see the trust engine. This phase builds a real design-system layer on shadcn/ui and moves the surfaces that sell onto it.
>
> **Founder decisions (2026-11-11):**
> 1. **Scope = foundation + hero surfaces.** Build the primitives, then migrate the firm dashboard, matters list + detail, clients list + detail, the trust-accounting dashboard and reports/profitability. The other ≈45 pages under `org/[slug]` adopt the primitives when next touched — no whole-app sweep.
> 2. **Look = evolve Signal Deck.** Keep the Kazi identity (slate scale, teal accent, Sora display / IBM Plex Sans body / JetBrains Mono figures) so the marketing site stays consistent. Borrow the **structure** of the admincn template: shadcn `sidebar`, floating header, KPI cards with deltas and sparklines, cleaner density.
> 3. **Template code may be adapted.** The founder has confirmed the paid admincn licence permits reuse inside Kazi. Template components are **translated, not copied verbatim**: the template is shadcn `base-vega` on Base UI (`render={...}` props); Kazi is shadcn `new-york` on Radix (`asChild`). Every adapted component lands in Kazi's conventions.
> 4. **Detail pages = hero header + tabs** (not a side rail). A richer hero (identifier, title, status, a 4-tile KPI strip) above the existing tab bar; tab content stays full-width.
> 5. **Backend boundary = small read-only endpoints allowed.** Where a chart truly needs a time series the backend does not serve, an additive read-only aggregate endpoint is permitted. No schema change, no migration, no write path.
> 6. **Dark mode is switched on, light by default.** Users opt in with a Light / Dark / System toggle in the user menu.
> 7. **Extras in scope:** dark mode and new charts on reports/profitability/trust. **Out:** client portal, theme customizer.
>
> **Scouting note (verified against current code, 2026-11-11 — do NOT re-derive):**
> - **Stack.** `frontend/`: next 16.3.8 (pnpm patch), react 19.2.3, tailwindcss 4.2.4 (CSS-first, no config file), tw-animate-css, motion 12, recharts **3.7**, cmdk, sonner, @dnd-kit, lucide-react 0.563. shadcn `components.json`: style `new-york`, rsc on, baseColor neutral, Radix via the unified `radix-ui` 1.4.3 package. **No TanStack Table.** Fonts are loaded in `frontend/app/layout.tsx`.
> - **Tokens.** `packages/ui/styles/globals.css` (163 lines) holds the OKLCH slate scale (hue 260), teal-500/600, semantic tokens, `chart-1..5`, fonts and radius 0.5rem. `frontend/app/globals.css` (71 lines) imports it and adds sidebar tokens (slate-950 in **both** modes). Background is oklch 94% grey, cards are near-white with `shadow-sm`, buttons are pill-shaped.
> - **Dark mode.** A `.dark` token block and `@custom-variant dark` exist, and there are ≈3,847 `dark:` classes. **No ThemeProvider, no toggle, nothing applies `.dark`**, so every one of those classes is unobserved.
> - **Primitives.** `@b2mash/ui` holds button, badge, card, input, label, separator, skeleton and cn. `frontend/components/ui` holds alert, alert-dialog, avatar-circle, checkbox, collapsible, command, dialog, dropdown-menu, form, popover, progress, radio-group, relative-date, select, sheet, switch, table, tabs, textarea and tooltip. **There is no shadcn `sidebar`, no `chart`, no data-table and no PageHeader.**
> - **Shell.** `app/(app)/org/[slug]/layout.tsx` (173 lines) has an inline sticky h-14 header with mobile menu, breadcrumbs, NotificationBell and auth controls. The sidebar is `components/desktop-sidebar.tsx` (138, dark, Motion indicator), `mobile-sidebar.tsx` (128, Sheet), `nav-zone.tsx`, `sidebar-user-footer.tsx`, `sidebar-utility-footer.tsx`, with nav config in `lib/nav-items.ts` (329, Phase 87 IA — **content stays, chrome changes**). Breadcrumbs: `components/breadcrumbs.tsx` (206) + `breadcrumb-label-provider.tsx`. Command palette: `command-palette-dialog.tsx` + `command-palette-provider.tsx`. Page entrance animation: `org/[slug]/template.tsx`.
> - **Dashboards.** `dashboard/page.tsx` (189) is a 12-column grid of `components/dashboard/*-widget.tsx` cards: diary, tasks, WIP, debtors, trust position, FICA, matter health, activity and sensitive events. `my-work/` holds TodaysAgenda, WeeklyRhythmStrip, TimeBreakdown and WeeklyTimeSummary. `reports/`, `reports/[reportSlug]` and `reports/profitability` (via `components/profitability/profitability-content.tsx`) are **tables only**. `trust-accounting/page.tsx` (426) is ≈25 Cards plus a table.
> - **Charts today.** There are about 3 live chart usages: recharts `DonutChart` (only in `my-work/time-breakdown.tsx`), and hand-rolled SVG `MiniProgressRing` and `MicroStackedBar`. **Dead code:** `horizontal-bar-chart`, `radial-gauge`, `kpi-card`, `sparkline-chart` and `sparkline` have no consumers. The comments in `lib/chart-theme.ts` contradict its values.
> - **Unused data the dashboard can show today.** `GET /api/dashboard/kpis` (`dashboard/dto/KpiResponse.java`) returns `activeMatterCount`, `totalHoursLogged`, `billablePercent`, `overdueTaskCount`, `averageMarginPercent`, a `List<TrendPoint(period, value)>` trend and a `previousPeriod` `KpiValues`. Financial fields are null for non-admin members (`withFinancialsRedacted`). **No frontend code calls it.** The other existing reads are `/api/dashboard/{activity,fica-outstanding,matter-health,trust-position,unbilled-wip,personal}`; `UnbilledWipResponse.byFeeEarner`; `/api/reports/profitability`, `/api/reports/utilization`, `/api/matters/{id}/profitability`, `/api/clients/{id}/profitability`, `/api/matters/{id}/time-summary[/by-member|/by-task]`; and the collections `/debtors` endpoint.
> - **Matters.** The list is `matters/page.tsx` (294) + `components/matters/matters-table.tsx` (280), with search, a filters Sheet, a status filter and saved views (`components/views/*`). The detail page is `matters/[id]/page.tsx` (680): `MatterHeaderCard` (129) + `components/matters/matter-tabs.tsx` (312), 22 grouped, module-gated tabs driven by `?tab=`. Create/edit: `components/projects/create-project-dialog.tsx` (331), `edit-project-dialog.tsx` (282), `matters/new/`.
> - **Clients.** The list is `clients/page.tsx` (272) + `components/clients/clients-table.tsx` (331), with a lifecycle filter, a filters sheet and saved views. The detail page is `clients/[id]/page.tsx` (756): `ClientHeaderCard` (221) + `customer-grouped-tabs.tsx` (219) via `components/shared/grouped-tab-bar.tsx`. Create/edit: `create-customer-dialog.tsx` (729) and `edit-customer-dialog.tsx` (556).
> - **Visual debt.**
>   - There is no reusable DataTable: 48 files import `ui/table` and ≈53 contain a `<table`.
>   - 94 inline copies of the `font-display text-3xl` h1 exist, plus ≈5 other heading variants.
>   - ≈217 hand-rolled `rounded-* border border-slate-*` divs do the job of `<Card>`.
>   - 37 raw hex colours appear in components.
>   - `TrustBalanceCard` is duplicated (`components/trust/` and `components/customers/`).
>   - Folders are split (`components/projects` vs `matters`, `customers` vs `clients`) — **leave this as is; see Out of Scope.**
> - **Test coupling.** 58 Playwright specs select mainly by `getByRole` (≈829), `getByTestId` (≈284), `getByLabel` (177) and `getByText` (125); the source has ≈614 `data-testid`. `frontend/e2e/helpers/screenshot.ts` is used by 12 specs, but **no screenshot baselines are committed** (`frontend/e2e/screenshots/*` and `portal/e2e/screenshots/*` hold only `.gitkeep`). G89 Batch F may introduce opt-in visual compares; if it does, see Constraints. The PNGs in `documentation/screenshots/` and `frontend/public/screenshots/` are docs and marketing images and will go stale.
> - **Template reference** (gitignored, at `shadcn-nextjs-admincn-admin-template-1.0.0/default-layout/src`). It is Next 16.2.6, React 19.2.4, Tailwind v4, shadcn `base-vega` (Base UI 1.7), recharts 3.8 via the shadcn `components/ui/chart.tsx`, and TanStack Table 8.21. The patterns to borrow:
>   - Shell: `components/layout/Sidebar.tsx` (shadcn `ui/sidebar`, grouped, collapsible), `Header.tsx` (floating card header) and `CommandMenu.tsx`.
>   - KPI and charts: `views/dashboards/statistics/statistics-card-03.tsx` (icon, ± delta chevron, value, badge), the sparkline KPI cards (income/expense area, profit line, revenue bar, radial), and the chart set under `views/dashboards/charts/` (area, bar, composed, progress-bar).
>   - Tables: `views/datatables/datatable-invoice.tsx` (status-badge columns), `views/apps/users/list/` (toolbar, filters, column toggle, bulk-action bar, pagination, row selection, CSV export) and `views/datatables/export-button`.
>   - Pages: `ui/timeline.tsx`, `views/pages/empty-state/empty-state-v2`, and the "Corporate" and "Modern Minimal" presets in `utils/theme-presets.ts` as density references.

## System Context

Kazi is a legal-only (`legal-za`) practice-management SaaS. Its firm app is a Next.js 16 App Router frontend on shadcn/ui (new-york, Radix) and Tailwind v4, with shared primitives in `@b2mash/ui`. Phase 87 fixed the information architecture: legal-native vocabulary, attorney's-week navigation and consolidated settings. Phases 88–89 fixed correctness. The data model and API are sound. The presentation layer is not: each page hand-builds its own tables, headers, cards and charts, so the app looks inconsistent and there are almost no visualisations, even though the backend already serves KPI trends, WIP by fee earner, profitability, utilization, trust position and debtors.

## Objective

Give Kazi a coherent, modern visual system built on shadcn/ui, and put the four surfaces a design-partner firm sees first onto it. Concretely:
1. A token layer that evolves Signal Deck, with **working** dark mode (light default, opt-in).
2. A shadcn `sidebar`-based app shell with a floating header.
3. A small set of reusable primitives: **PageHeader, DataTable, StatCard, a chart kit, EmptyState and DetailHero**.
4. A firm dashboard that opens on a KPI row with deltas and sparklines.
5. Matter and client list and detail pages on DataTable and DetailHero.
6. Real charts on the trust dashboard and on reports/profitability.

Done means these surfaces look like one product in both themes at 1024 and 1440 px, the legal Playwright suites pass on a fresh stack, and the dead chart code is gone.

## Constraints & Assumptions

- **Radix stays.** No migration to Base UI. Template components are translated: `render={<X/>}` becomes `asChild` + child, Base UI primitives become the equivalent shadcn new-york (Radix) components, and the result passes the frontend lint, typecheck and format rules. Add shadcn components through the shadcn CLI (`sidebar`, `chart`, `breadcrumb` if needed, `toggle-group`, `scroll-area`, …) so they match `components.json`. Install `@tanstack/react-table` (latest 8.x) as the one new runtime dependency for DataTable; justify any other new dependency in an ADR.
- **Where primitives live.** Shared, framework-agnostic primitives (StatCard, EmptyState, PageHeader, chart tokens) go into `@b2mash/ui` so the portal can adopt them later. App-coupled pieces (DataTable with saved views and URL state, the sidebar shell, DetailHero wiring) stay in `frontend/components/`. `/architecture` decides the exact split and records it in an ADR. The portal itself is not migrated this phase.
- **Identity.** The slate (hue 260) + teal palette and the three font families are kept. Tokens may be retuned (background lightness, card elevation, border contrast, radius, density, chart palette) to approach the template's cleaner feel. The `chart-1..5` palette is re-derived for legibility in **both** themes, with categorical, sequential and status colours documented, and `lib/chart-theme.ts` is made truthful. The marketing site (`app/page.tsx`, `components/marketing/`) is not restyled but must not visibly break.
- **Dark mode.** Add a `next-themes` ThemeProvider (or equivalent) on the `.dark` class with **light as the default** and `enableSystem` available as an option. The toggle offers Light / Dark / System and sits in the user menu; the preference persists per browser. Every surface this phase touches must be observed in dark mode. Untouched long-tail pages must stay **legible** in dark mode: no white-on-white or black-on-black. Where a page can't be fixed this phase, record it in a dark-mode debt list in the closure report rather than silently shipping it broken. The sidebar tokens stop being slate-950 in both modes unless `/architecture` keeps that deliberately.
- **Labels, tab names, routes and `data-testid`s do not change.** E2E specs select by role and text; this is a visual phase. If a primitive changes a role (for example a `<table>` replaced by `role="grid"`), the slice updates the affected specs and says so in the PR. Changing visible copy is a product decision outside this phase.
- **Behaviour parity on migrated lists.** The matter and client DataTables keep everything today's tables do: search, filter sheet, status/lifecycle filter, saved views (`components/views/*`), sorting, empty states, row links and module/capability gating. URL-driven state stays URL-driven. New capabilities (column visibility, row selection, CSV export of the visible rows, page-size control) are additive. **No bulk mutation actions are added**, because they would be new features with new authorization paths.
- **Capability-gated data stays gated.** KPI tiles and charts that show financial data must respect the existing redaction (null financial fields for non-admin members, module gating). A null value renders as an intentional "—" or a hidden tile, never as 0.
- **Backend: read-only aggregate endpoints only, and only when needed.** For each chart, `/architecture` first checks whether an existing endpoint already returns the series. Likely gaps are fees billed vs collected per month, trust balance per month and aged-debtor buckets (`/debtors` may already carry these). A new endpoint is: additive, `GET`, tenant-scoped through the existing filter chain, capability-checked like its sibling endpoints, computed from existing tables with **no migration, no new entity, no write**, covered by an integration test through the real service path, and dated with the SA business clock (`Clock` injection, ADR-358). Every slice that touches Java runs the full `backend/scripts/verify.sh`. Keep the number of backend-touching slices minimal (target ≤ 2).
- **Visual baselines.** If G89 Batch F's opt-in visual compares have landed by build time, regenerating baselines for the surfaces this phase redesigns is **authorised by this phase**. Each slice names the baselines it regenerates and why. Baselines for surfaces this phase did not touch must not change.
- **Accessibility.** Charts carry an accessible name and a text or table alternative (shadcn `ChartTooltip` plus a visually-hidden summary or a "view as table" toggle). Colour is never the only signal for status: pair it with an icon or label. Focus rings, keyboard navigation of the sidebar and DataTable, and AA contrast are checked in both themes.
- **Performance.** Charts are client components behind server-fetched data. Avoid large client bundles on the dashboard (load recharts only where needed). There must be no hydration warnings from the theme provider (`suppressHydrationWarning` on `<html>`). This is the stack where memory `next_16_2_6_lost_react_ping` applies; do not paper over stalls with timeouts.
- **Slicing (memory `feedback_slice_by_verify_cost`).** One PR per slice. A gap's backend and frontend work ship together. Aim for ≈12–20 files and ≤ ~1500 new lines per slice. Frontend-only slices run FE-GATE (`pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build`). Every UI slice also re-runs the legal Playwright suites (memory `feedback_ui_changes_need_e2e_suite_run`) and is observed in a real browser at **1024 and 1440 in light and dark**, with screenshots in the PR. UI decisions go through the `frontend-design`, `vercel:shadcn`, `vercel:nextjs` and `dataviz` skills, not first principles.

## Section 1 — Tokens, Theme and Dark Mode

- Retune `packages/ui/styles/globals.css` and `frontend/app/globals.css`. Covers: background, card, popover, border, ring, muted, sidebar and chart tokens for `:root` and `.dark`; the radius scale; a density decision (row heights, card padding); shadow and elevation steps. Keep the OKLCH form.
- Re-derive `chart-1..5` and add status tokens (success, warning, danger, info) used by charts and badges. Make `lib/chart-theme.ts` read from the CSS variables and fix its comments.
- Add the ThemeProvider, the user-menu toggle and persistence (light default).
- Audit the ≈3,847 `dark:` classes **on the surfaces in scope**. Where a token already covers it, replace the pair (`bg-white dark:bg-slate-900`) with a semantic token (`bg-card`).
- Replace the 37 raw hex colours with tokens where they sit on in-scope surfaces.
- Delete the dead chart components: `horizontal-bar-chart`, `radial-gauge`, `kpi-card`, `sparkline-chart`, `sparkline`. Grep repo-wide first to confirm there are no consumers.

## Section 2 — App Shell

- Replace `desktop-sidebar.tsx`, `mobile-sidebar.tsx` and `nav-zone.tsx` with the shadcn `sidebar` component. It needs collapsible icon mode (state in a cookie, `SidebarTrigger`, keyboard shortcut), grouped zones from `lib/nav-items.ts` (content unchanged), active-item indication, badges where nav items already have counts, and the user and utility footers. Mobile uses the sidebar's built-in Sheet mode.
- Turn the header in `org/[slug]/layout.tsx` into a floating header component. It holds the sidebar trigger, breadcrumbs (keep `breadcrumbs.tsx` and the label provider), a command-palette trigger with ⌘K hint, NotificationBell, the theme toggle and the user menu.
- Restyle the command palette dialog to match. Its indexing and behaviour are unchanged.
- Keep `org/[slug]/template.tsx` entrance motion subtle, or remove it if it conflicts with the new shell. Record the decision.

## Section 3 — Primitives

- **PageHeader.** Title, optional description, optional breadcrumb slot, and an actions slot (primary plus overflow). It replaces the inline h1 pattern on in-scope pages. Long-tail pages adopt it later.
- **DataTable.** TanStack-based, with server- or client-side mode. Covers: a column definition helper; a toolbar with a search slot, a filter-chip slot, the saved-views slot and column visibility; sorting; pagination with page size; optional row selection; CSV export of visible rows; status-badge cell helpers (adapting `datatable-invoice.tsx`); loading skeleton and empty state; sticky header; a mobile behaviour decision (horizontal scroll vs card list). URL state for sort/filter/page where the current page already uses the URL.
- **StatCard.** Label, value (JetBrains Mono figures, ZAR formatting via the existing currency helper), delta vs previous period (direction, colour, sr-only text), optional sparkline, optional icon, loading and redacted states. Adapt `statistics-card-03` and the sparkline cards.
- **Chart kit.** Add the shadcn `chart` component (`ChartContainer`, `ChartTooltip`, `ChartLegend`) on recharts 3, with Kazi presets: area/line trend, stacked and grouped bar, horizontal bar (ranking), donut, and radial/progress. Each preset includes ZAR/hours/percent formatters, axis and grid defaults, empty and loading states, and an accessible table alternative. Rebuild the existing `DonutChart`, `MiniProgressRing` and `MicroStackedBar` call sites on the kit.
- **EmptyState.** Icon, title, description, optional action (adapt `empty-state-v2`). Used by DataTable and the dashboard widgets.
- **DetailHero.** Identifier chip, title, subtitle line (client / fee earner / dates), status badge, an actions slot, and a **4-tile KPI strip** built from StatCard in a compact variant. Responsive: the tiles wrap to 2×2 at 1024.
- **Activity timeline.** Adapt `ui/timeline.tsx` for the dashboard activity widget and the matter/client Activity tabs. It's in scope only if those tabs are already being touched by Sections 5–6; otherwise defer it.

## Section 4 — Firm Dashboard

- Add a KPI row of StatCards with delta and sparkline, fed by `GET /api/dashboard/kpis`: active matters, hours logged, billable %, overdue tasks, and average margin % (admin/owner only). The deltas come from `previousPeriod`. The existing `date-range-selector.tsx` drives the period if the endpoint accepts a range; `/architecture` confirms.
- Restyle every existing widget onto Card + EmptyState + the chart kit. Covers diary, tasks, unbilled WIP (add a bar chart by fee earner from `byFeeEarner`), debtors (aged buckets via the kit), trust position, FICA outstanding, matter health, recent activity and sensitive events.
- Rework the grid for 1024 and 1440 so the information hierarchy reads as: today's work → money (WIP, debtors, trust) → risk (FICA, matter health, sensitive events) → activity.
- My Work gets the new primitives where it already uses them (TimeBreakdown donut, WeeklyTimeSummary). Its layout is not redesigned.

## Section 5 — Matters (list + detail)

- Move `matters-table.tsx` onto DataTable with parity (Constraints), and `matters/page.tsx` onto PageHeader.
- Replace `MatterHeaderCard` with DetailHero on `matters/[id]`. The KPI strip shows WIP / unbilled, trust balance, amount outstanding, and next diary date or court date, from existing reads (`/api/matters/{id}/profitability`, `unbilled-summary`, trust and diary reads). `/architecture` maps each tile to its source and redaction rule.
- Restyle `matter-tabs.tsx` (the 22 grouped, gated tabs with `?tab=`) without renaming or reordering tabs: a cleaner underline or pill style, overflow handling at 1024, and a sticky tab bar below the hero.
- Use the page split that falls out of the hero extraction to bring `matters/[id]/page.tsx` well below its current 680 lines, but **do not refactor the tab panels' internals**. Restyling the Overview tab's metric strip onto StatCard is in scope.
- Create/edit dialogs: visual alignment only (spacing, sections, footer actions). No field or validation changes.

## Section 6 — Clients (list + detail)

- Move `clients-table.tsx` onto DataTable with parity (lifecycle filter, saved views).
- On `clients/[id]`, replace `ClientHeaderCard` with DetailHero. The KPI strip shows matters open, trust balance, outstanding / debtors, and FICA status. Restyle `grouped-tab-bar.tsx` to match the matter tabs; because it's shared, check its other consumers.
- Deduplicate `TrustBalanceCard` into one component used by both trust and client surfaces.
- `create-customer-dialog.tsx` (729) and `edit-customer-dialog.tsx` (556): visual alignment only, same rule as matters.

## Section 7 — Trust Dashboard, Reports and Profitability Charts

- `trust-accounting/page.tsx`: lead with a StatCard row (trust bank balance, client ledger total, reconciliation status, last reconciled date), then a **trust balance trend** chart and the existing tables on DataTable. The trust sub-pages are not redesigned; they inherit only the tokens and shell.
- `reports/profitability` (`profitability-content.tsx`, `project-profitability-table.tsx`, `utilization-table.tsx`): add a **fees billed vs collected** trend (if a series exists or is added), **utilization by fee earner** (horizontal bar from `/api/reports/utilization`) and **matter profitability** ranking (bar or scatter from `/api/reports/profitability`), with the tables on DataTable. CSV export stays as it is.
- `reports/` index: cards via PageHeader and Card. `reports/[reportSlug]` renders results on DataTable only if the change stays inside one slice's file budget; otherwise leave it.
- Any new read-only aggregate endpoint (Constraints) lands in the slice of the chart that needs it.

## Section 8 — Exit Gate

- Run the legal Playwright suites (`legal-lifecycle`, `legal-depth-ii`) and the default suite on a **fresh** stack (`e2e-down` → `e2e-up`, per memory `env_playwright_legal_configs_dirty_tree`), plus FE-GATE. Run the full backend verify on main if any Java landed.
- Observe in a real browser on the Keycloak stack, at 1024 and 1440 in light **and** dark: dashboard, matters list, a matter detail with ≥ 3 tabs, clients list, a client detail, the trust dashboard and profitability. Screenshots go to `qa_cycle/evidence/phase90-ui/`. Also check a non-admin member to confirm the redacted KPIs render "—" or are hidden.
- The closure report lists: the before/after metrics (inline h1 count, ad-hoc `<table>` count on in-scope surfaces, raw-hex count, dead-chart-file count, chart usage count), the dark-mode debt list for long-tail pages, and the long-tail adoption backlog (pages still on old patterns).
- Refresh the docs and marketing screenshots under `documentation/screenshots/` that show redesigned surfaces, or list them as stale in the closure report.

## Out of Scope

- **Client portal** restyle. The portal keeps its current look; it may pick up shared `@b2mash/ui` token changes, but those must not break it. Run PORTAL-GATE if `@b2mash/ui` changes.
- Theme customizer, colour presets, per-user density/radius/sidebar-variant settings.
- Migrating to Base UI or shadcn `base-vega`; changing the shadcn style.
- Redesigning the other ≈45 pages under `org/[slug]` (settings, fee notes, diary, compliance, conflict check, trust sub-pages, documents, team, AI): they get tokens and shell only.
- Renaming `components/projects`→`matters` and `components/customers`→`clients`, or Project/Customer component names. That's a separate mechanical PR, outside this phase.
- Any copy, label, tab-name, route or IA change. Any new feature, bulk action, write endpoint, entity or migration.
- Kanban, calendar, mail or chat apps from the template. The marketing site redesign.
- Splitting the 22 matter tabs into a different grouping (Phase 87's IA stands).

## ADR Topics to Address

ADR numbering continues from ADR-366.
1. **Design-system layering.** What goes into `@b2mash/ui` versus `frontend/components`; the rule for future pages ("new page = PageHeader + DataTable/Card + kit, no inline h1/table").
2. **Template adoption policy.** Base UI → Radix translation, where adapted code is credited, and the licence note (founder-confirmed 2026-11-11).
3. **DataTable architecture.** TanStack Table, server vs client mode, URL-state contract, how saved views plug in, mobile strategy.
4. **Chart kit and palette.** shadcn `chart` on recharts 3, categorical/sequential/status tokens, the accessibility alternative, lazy loading.
5. **Dark mode policy.** Light default, opt-in, semantic tokens over `dark:` pairs, the debt list for the long tail.
6. **Read-only aggregate endpoint pattern** (only if any is added). Shape, capability checks, SA clock, why no persistence or materialisation.
7. **Detail page hero.** KPI strip sourcing and redaction, and why a hero header was chosen over a side rail (founder decision 4).

## Style & Boundaries

- This is a visual phase: if a slice needs a behaviour change, stop and re-spec. The exceptions are the additive DataTable capabilities and the read-only endpoints listed above.
- Order the work tokens → shell → primitives → surfaces → gate. No surface slice starts before the primitives it uses have merged.
- Every UI slice has a real-browser observation at 1024 and 1440 in light and dark, with screenshots in the PR. "Unit tests green" is not PASS (root `CLAUDE.md` §3).
- Each primitive has vitest coverage for its states (loading, empty, redacted, delta up/down, error).
- Grep repo-wide before deleting anything (memory: spec §8 lesson).
- CLAUDE.md quality gates apply unchanged. Prettier `format:check` is part of green. Review is CodeRabbit plus two `feature-dev:code-reviewer` passes.
- Do not touch: trust-accounting domain logic, MCP, AI skills, template packs, migrations.

## Next step

After G89 Batches E and F merge, run `/architecture requirements/claude-code-prompt-phase90.md`, then `/breakdown 90`.
