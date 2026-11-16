# Phase 91 — Kazi UI Modernization II: Light-Mode Colour Scheme, Kink Register, Settings and the Daily-Use Long Tail

> **Status: READY for `/architecture`. Start building only after Phase 90 closes**, meaning Epic 644 (exit gate) has merged and its closure report exists. Scope was agreed in `/ideate` on 2026-11-12. Phase 91 consumes three outputs of 644: the **dark-mode debt list**, the **long-tail adoption backlog** and the before/after metrics. Starting earlier would blur Phase 90's exit metrics and collide with 642/643 on shared files (`grouped-tab-bar.tsx`, `trust-accounting/actions.ts`).
>
> **Why this phase exists.** Phase 90 built the design system: tokens, dark mode, the shadcn sidebar shell, PageHeader, DataTable, StatCard, the chart kit, EmptyState and DetailHero. It moved the four surfaces a design partner sees first onto that system. Everything else still uses the old patterns. A demo that opens on the polished dashboard and then clicks into Settings, a fee note or a trust ledger falls back into the old look. Only 3 of 57 pages under `org/[slug]` use `PageHeader`. The new primitives are also being used at the wrong size: "No tasks assigned" and "No custom fields configured" render as full empty-page blocks inside small sections.
>
> **Founder decisions (2026-11-12):**
> 1. **Scope = daily-use pages + settings, with a light pass on the rest.** The full migration covers settings (5 families, 31 tabs), fee notes, trust sub-pages, diary, My Work, timesheet and notifications. Compliance, conflict check, the legal registers, team, documents, AI reviews and platform-admin get a **light pass** only: PageHeader, the correct EmptyState size, and DataTable where a table already exists.
> 2. **Revisit the light-mode colour scheme.** The founder wants an alternative to "black side-menu, white main area". This **reverses a deliberate Phase 90 ruling** (`architecture/phase90-ui-modernization.md:234` kept the slate-950 light sidebar as "Signal Deck's strongest identity mark"). It is therefore decided design-first: candidates are rendered on real pages, the founder picks one, and a new ADR records the reversal. Dark mode keeps a dark sidebar.
> 3. **Settings = settings shell + section cards.** Keep Phase 87's five families and `?tab=` tabs. Restyle the settings nav, give it a mobile form, and put every tab's content into titled section cards with right-aligned actions. Same routes, same labels.
> 4. **Empty custom fields = a compact one-liner, visible to everyone.** This is a purely visual change with no visibility or role logic.
> 5. **Observe-first kink hunt.** The phase opens with a real-browser walk of every page that produces a **kinks register**. The founder approves which register items are fixed in this phase; the rest go to a backlog.
>
> **Scouting note (verified against `main` @ `38c34ce92`, 2026-11-12; re-check line numbers at build time because 642–644 will move them):**
> - **Primitive adoption.** `PageHeader` is used by 3 of 57 `org/[slug]` pages, and 76 files under `org/[slug]` still contain an inline `<h1`.
> - **Files that render a `<Table`/`<table` directly:** settings 11, trust-accounting 7, invoices 4, legal 1.
> - **EmptyState** (`frontend/components/empty-state.tsx`, adapted from admincn in 639B) has `variant = "default" | "compact" | "inline"`, documented as "default = page-level; compact = widgets and chart cards; inline = one line in a table body". There are **63 call sites in 53 files, and only 8 pass `variant`**. The other ~55 render the page-level block wherever they sit. Known offenders:
>   - `components/my-work/assigned-task-list.tsx:65` ("No tasks assigned")
>   - `components/dashboard/my-tasks-widget.tsx:59`
>   - `components/my-work/urgency-task-list.tsx`
>   - `components/field-definitions/CustomFieldSection.tsx:527` ("No custom fields configured" / "Configure Fields"). This one is rendered in four places: `invoices/[id]/page.tsx`, `components/customers/client-fields-tab.tsx`, `components/projects/matter-fields-tab.tsx` and **`components/tasks/task-detail-sheet.tsx`**, a narrow Sheet where the page-level block is at its worst.
>   - `matter-fields-tab.tsx:37` hand-rolls its own `py-12` empty block.
>   - The copy for these lives in `lib/messages/en/empty-states.json` (e.g. `:27` "No tasks assigned to you"). **Copy does not change.**
> - **Light-mode colours.**
>   - `frontend/app/globals.css:29-36`: `--sidebar: var(--slate-950)`, white foreground, `--sidebar-primary: var(--teal-500)`.
>   - `packages/ui/styles/globals.css:148-167`: `--background: oklch(97.2% 0.004 260)`, `--card: oklch(99.6% 0.002 260)`, `--primary: var(--slate-950)` (so primary buttons are black too), `--accent: var(--teal-600)`, `--ring: oklch(58% 0.13 183)`.
>   - The `.dark` block is at `packages/ui/styles/globals.css:217` and the dark sidebar at `frontend/app/globals.css:48` (`oklch(16.5% 0.008 260)`).
>   - **The portal imports `@b2mash/ui/styles/globals.css` (`portal/app/globals.css:12`)**, so any change to the shared `--primary`/`--background` reaches the portal and the marketing site.
>   - The firm brand colour is applied as `--brand-color` by `frontend/components/shell/app-sidebar.tsx:153-189` (an accent bar on the rail).
> - **Settings.**
>   - `settings/layout.tsx` (20 lines) renders `SettingsSidebar` inside `<aside className="hidden w-60 shrink-0 md:block">`, which means **no settings nav below `md`** unless the family tabs compensate. The kink walk confirms this.
>   - There are five families, each rendered by `components/settings/settings-family-page.tsx` + `settings-family-tabs.tsx` with `?tab=` loaders in `settings/<family>/_tabs/index.ts`:
>     - **firm** (8): general, roles, audit-trail, data-protection, data-requests, compliance, features, subscription
>     - **matters** (7): templates, naming, custom-fields, tags, checklists, request-templates, request-settings
>     - **fees** (6): tariffs, tax, time-recording, fee-runs, collections, trust-accounting
>     - **documents** (4): templates, clauses, acceptance, email
>     - **connections** (6): xero, mcp, ai, notifications, packs, automations
>   - The sub-pages are the template editor (`documents/templates/[id]/edit`, `new`), the checklist editor (`matters/checklists/[id]`, `[id]/edit`, `new`), request templates (`[id]`, `new`), matter templates (`[id]`), `connections/xero/sync-log`, `connections/ai/history` and `connections/automations/executions`.
>   - The forms are in `components/settings/*` (18 files).
> - **Daily-use page sizes (lines of `page.tsx`):**
>   - Fee notes: `invoices` 352, `invoices/[id]` 224, `invoices/billing-runs` 178 (+ `[id]`, `new`), `invoices/collections` 96, `invoices/unbilled` 120.
>   - Trust: `trust-accounting/transactions` 479, `client-ledgers` 317 (+ `[clientId]`), `investments` 293, `interest` 289, `reconciliation` 279 (+ `new`), `reports` 117.
>   - Personal: `my-work` 240, `my-work/timesheet` 91, `diary` 80, `notifications` 34.
>   - Light-pass pages: `compliance` 114 (+ `requests`, `requests/[id]`), `conflict-check` 84, `legal/tariffs` 60, `legal/disbursements` 56 (+ `[id]`), `legal/adverse-parties` 53, `team` 61, `ai/reviews` 89, `information-requests/[id]`, and `platform-admin/{access-requests,billing,demo}`.
> - **Phase 90 assets to reuse, not rebuild:**
>   - `PageHeader`, `StatCard`, `DetailHero` (+ the `hero-tiles.ts` server-only pattern from 641B/642B, and the shared `tileFrom`/`overdueHint` module that 642B extracts)
>   - `DataTable` (639C; `frame`, `stickyHeader`, `containerClassName`, memoised columns, `formatDateInSA` for client-rendered dates)
>   - the chart kit + micro presets
>   - the 642C GroupedTabBar underline style
>   - `components/dashboard/widget-parts.tsx`

## System Context

Kazi is a legal-only (`legal-za`) practice-management SaaS. The firm app is Next.js 16 App Router on shadcn/ui (new-york, Radix) and Tailwind v4, with shared primitives in `@b2mash/ui`.
- **Phase 87** fixed the information architecture: legal vocabulary, an attorney's-week nav, and five consolidated settings families.
- **Phase 90** added a coherent design system and moved the dashboard, matters, clients, the trust dashboard and profitability onto it, in light and dark mode.

The remaining ~50 firm pages still hand-build headers, tables, cards and empty states. They work and are labelled correctly, but they no longer look like the same product as the Phase 90 surfaces.

## Objective

Make the whole firm app look like one product:
1. Settle the **light-mode colour scheme** with the founder and apply it through tokens.
2. Produce and fix an approved **kinks register** from a real-browser walk.
3. Apply one **EmptyState sizing rule** everywhere.
4. Put **Settings** on a settings shell with section cards.
5. Move **fee notes, the trust sub-pages, diary, My Work, timesheet and notifications** fully onto the Phase 90 primitives.
6. Give the remaining pages a **light pass**.

**Done means:**
- Every `org/[slug]` page uses `PageHeader` (or a documented exception).
- No section-level empty state renders at page size.
- Every in-scope surface is observed at 1024 and 1440 px in light and dark, as owner and as member.
- The legal Playwright suites pass on a fresh stack.

## Constraints & Assumptions

- **Everything in Phase 90's constraints carries over unchanged unless this file overrides it** (`requirements/claude-code-prompt-phase90.md` § Constraints). In particular:
  - **Radix stays**; shadcn components come through the CLI.
  - **Labels, tab names, routes and `data-testid`s do not change.**
  - Migrated lists keep **behaviour parity**: search, filters, saved views, sorting, URL state, row links and gating.
  - Capability-gated data stays gated: redaction shows as "—" or a hidden tile, never as 0.
  - **No bulk mutations**; DataTable's CSV, column visibility and page size are additive.
  - Charts carry accessible alternatives, and colour is never the only status signal.
  - The memory `next_16_2_6_lost_react_ping` applies: never mask stalls with timeouts.
- **Backend: target zero.** This is a frontend phase. A read-only aggregate endpoint is allowed **only** if a StatCard or hero tile on an in-scope page has no existing data source, and `/architecture` must first prove that no existing read covers it. The Phase 90 endpoint rules apply: additive `GET`, tenant-scoped, capability-checked, no migration or entity or write, a real-path integration test, the SA `Clock`, and a full `backend/scripts/verify.sh`. At most one backend-touching slice, and it must be declared in the breakdown.
- **Copy freeze.** Visible copy, including empty-state titles and descriptions and `lib/messages/en/*.json`, stays as it is. The 638B ruling holds: new words count as new copy. Exceptions:
  - the colour-scheme decision, which is visual only;
  - any register item the founder explicitly approves as a copy change, which is recorded in the register with the decision.
- **Colour changes are scoped deliberately.** The portal and the marketing site import the shared tokens. A light-mode change meant for the firm app must not leak into the portal or `app/page.tsx` / `components/marketing/` unless the founder chooses that in Section 1. `/architecture` decides the mechanism (app-only overrides in `frontend/app/globals.css` vs shared tokens) and records it. PORTAL-GATE runs whenever `@b2mash/ui` changes.
- **Accessibility in the new scheme.** Every candidate in Section 1 must pass WCAG AA in light mode:
  - body text on canvas and on card ≥ 4.5:1;
  - sidebar text and the active item on the rail ≥ 4.5:1;
  - primary-button label ≥ 4.5:1 (e.g. white on `teal-600` is about 3.7:1 and **fails**; `teal-700` passes);
  - focus ring ≥ 3:1 against both rail and canvas.

  Put the contrast table in the PR.
- **Settings forms: visual alignment only.** Section cards, headings, spacing and footer actions change. Fields, validation, server actions, save semantics (per-section vs per-page, exactly as today) and tab loaders do not. If a form currently saves several sections with one button, it keeps doing so. The section-card primitive must support both "one save per card" and "one save for the page".
- **Settings `?tab=` mechanics and loaders are unchanged** (Phase 87 S9, ADR-341). Only the active tab's loader runs. The mobile settings nav uses the same links and the same URL.
- **Slicing** (memory `feedback_slice_by_verify_cost`). One PR per slice, aiming for about 12–20 files and ≤ ~1500 new lines.
  - Frontend-only slices run FE-GATE (`pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build`).
  - **Every UI slice re-runs the legal Playwright suites** (memory `feedback_ui_changes_need_e2e_suite_run`; run them against `:3001` with `PLAYWRIGHT_BASE_URL=http://localhost:3001`). Every UI slice is also observed in a real browser at **1024 and 1440 in light and dark**, with screenshots in the PR.
  - **The orchestrator's OBS gates the merge, not the reviewers' approvals.** Phase 90 lesson: in 640A, 640B and 641A, two approving reviews missed layout defects that only the browser showed.
  - Container-query breakpoints are checked against **measured** container widths.
- **Sticky and scroll behaviour.** Use the Phase 90 rules: the floating-header offset `top-[calc(var(--app-header-height)+0.5rem)]`; a single `<main>` (CLI `SidebarInset` renders a `<div>`, and about 30 e2e call sites use strict-mode `locator("main")`); `stickyHeader={false}` on tables that scroll horizontally at 1024.
- UI decisions go through the `frontend-design`, `vercel:shadcn`, `vercel:nextjs` and `dataviz` skills, not first principles (memory `feedback_ui_questions_use_vercel_experts`).

## Section 1 — Light-Mode Colour Scheme (design-first, founder decision gate)

The founder wants an alternative to the near-black rail next to a near-white canvas with black primary buttons. Dark mode is out of scope for this section; it keeps its dark rail.

**Step 1 — candidates, prototyped and not merged.** On a throwaway branch, render **three candidate light schemes** on four real pages: the dashboard, a matter detail, the settings firm/general tab and the fee-notes list. Shoot each at 1440 and 1024, with the sidebar expanded and collapsed. Each candidate is defined as token values only (`--sidebar-*`, `--background`, `--card`, `--border`, `--primary`, `--primary-foreground`, `--ring`, plus the sidebar active-item treatment). Starting points:
- **A — Light rail, inset canvas (recommended starting point).** The rail sits on a cool off-white tone (around `oklch(96–97% 0.005 260)`) with slate-700 text. Content sits in the shadcn `SidebarInset` card (`variant="inset"`: white, rounded, hairline border, soft elevation), so the work area becomes the raised surface. The active nav item is a teal-tinted pill (teal-50/teal-950 text range) with the teal-500 indicator. Primary buttons are evaluated in two forms: slate-900 (continuity) and teal-700 (brand-forward). This is the Linear/Vercel/admincn pattern and the calmest option.
- **B — Tinted rail.** A soft slate-teal tinted rail (around `oklch(93–95% 0.01–0.02 200–260)`) clearly distinct from a white canvas, with slate-900 text and a white active pill with a teal indicator. It keeps a visible two-zone structure without the black slab.
- **C — Deep-teal rail.** Keep a dark rail but move it from black to a deep ink-teal (around `oklch(25–30% 0.04–0.06 195)`), with teal-700 primary buttons. This keeps the strongest brand mark and warms it up.

For every candidate, show:
- the org `--brand-color` accent bar, using a real non-teal firm colour (e.g. burgundy) to check for clashes;
- the floating header;
- a DataTable;
- StatCards with up and down deltas;
- a chart (to check the `chart-1..5` legibility that Phase 90 validated against the old canvas);
- status badges.

Include the AA contrast table (Constraints) for each candidate.

**Step 2 — founder picks** (or asks for a variant). The decision, including whether primary buttons change and whether the portal and marketing site follow, is recorded in a **new ADR that supersedes the Phase 90 sidebar ruling**. Do not edit ADR-367–374, because `adr/` is append-only.

**Step 3 — implement.** Change the tokens only, in the file(s) `/architecture` picks per the scoping constraint. Then:
- remove any hard-coded light-rail colours the shell still carries (grep `app-sidebar.tsx`, `app-header.tsx` and `components/shell/*` for `slate-950`, `text-white` and `bg-white/`);
- re-run the `dataviz` palette checks against the new canvas and card;
- re-shoot the Phase 90 exit-matrix surfaces in light mode only, so the 644 evidence set has an "after Phase 91 scheme" companion.

This slice merges **before any other Phase 91 surface slice**, so all later observations use the final scheme.

## Section 2 — Kinks Register (observe-first)

- Walk every `org/[slug]` page in a real browser on the `:3001` mock-auth stack, after the legal lifecycle suite has run so pages have data. Cover 1024 and 1440 px, light and dark, as **Alice (owner)** and **Carol (member)**, and the 390px mobile sheet for the shell and settings. Include the task detail sheet, every create/edit dialog reachable from in-scope pages, and the settings sub-pages.
- Record each kink with: page, route, persona, viewport, theme, screenshot path, category (**density / empty-state sizing / overflow and truncation / dark-mode break / inconsistent primitive / sticky and scroll / focus and keyboard / other**), proposed fix, and the Phase 91 section that would own it.
- Seed the register with what is already known:
  - EmptyState sizing (Scouting note);
  - the settings nav hidden below `md`;
  - 644's dark-mode debt list and adoption backlog;
  - the Phase 90 carry-forwards: sidebar density F2 (Trust below the fold at 1440×900; Fees, Trust and Firm below the fold in the 390px sheet), DataTable sort chevrons adding 60px at 1024 (641A F1), command-palette focus not returned on close, and a failed sensitive-events read rendering a false all-clear (640B). Any of these that 644B already fixed are marked as such.
- Output: `qa_cycle/evidence/phase91-ui/kinks-register.md` plus screenshots. **The founder approves the register** before the surface slices start, marking each item in-phase, backlog or won't-fix with a reason. Items that need copy, IA or behaviour changes are flagged and must not be silently fixed (root `CLAUDE.md` §6–§7).
- Approved kinks are fixed in the section that owns their page. Cross-cutting kinks (shell, tokens, a shared primitive) go to Section 3 or a dedicated slice.

## Section 3 — EmptyState Sizing Rule + Cross-Cutting Kinks

- **The rule**, recorded in an ADR and in the EmptyState JSDoc:
  - `default` is used **only** when the empty state is the whole content of a page or of a page-level tab panel;
  - `compact` is for a section, card, widget, chart, dialog body or Sheet;
  - `inline` is for a table body or a single-line slot.

  If the kit needs a smaller variant than `compact` (for example one row with an icon, text and a trailing link), add it here and do not hand-roll it at call sites.
- **Sweep every call site** (about 63) and set an explicit `variant`. Add a lint or vitest guard that fails when `<EmptyState` is used without `variant` (or default the prop to `compact` and require `variant="default"` to be explicit; `/architecture` chooses). Also replace hand-rolled empty blocks that do the same job, such as `matter-fields-tab.tsx:37` and other `py-12 text-center` empties found by grep. Copy is unchanged.
- **Custom fields (founder decision 4).** `CustomFieldSection`'s no-fields state becomes a **compact one-liner for everyone**: the icon, the existing title, and the existing "Configure Fields" link/button inline, at the same visibility and gating as today. This applies wherever `CustomFieldSection` renders (fee-note detail, client and matter Fields tabs, task detail sheet). `matter-fields-tab.tsx`'s own empty block follows the same visual treatment. No visibility or role logic changes.
- **"No tasks assigned"** (My Work list, Home tasks widget, urgency list) uses `compact`, and the section keeps its heading. The empty section must not be taller than about one populated row group. `/architecture` sets the height budget.
- **Approved cross-cutting kinks from the register**: shell density (F2), DataTable chevron width (F1) and palette focus return. Each goes in its own slice, unless the breakdown justifies grouping (CLAUDE.md §7 same-bug-class rule).

## Section 4 — Settings Shell

- **Settings layout.** Restyle `SettingsSidebar` as a secondary nav on the Phase 90 tokens: grouped by family, with the active family and tab indicated the same way as the main sidebar, and sticky under the floating header. **Below `md`**, add a settings nav that does not exist today, for example a family `Select` or a horizontal scrolling family bar above the content. It uses the same hrefs, and the walk confirms whether the family tabs already compensate.
- **Family tabs.** `settings-family-tabs.tsx` adopts the 642C GroupedTabBar underline style (tokens, `aria-selected`/`data-state` indicator, horizontal `ScrollArea` with edge fades at 1024). `?tab=` mechanics, ids, labels, order and loader behaviour are unchanged.
- **New primitive: `SettingsSection`** (or a `Card` composition, which `/architecture` names). It has a title, an optional description, content, and an optional footer with right-aligned actions plus a dirty/saving state slot. It supports both per-card save and page-level save. It has a destructive variant for danger-zone blocks (e.g. data-protection anonymise, disconnect Xero) that is visually distinct, uses no new copy and keeps the existing confirmation dialogs. It goes in `@b2mash/ui` or `frontend/components` per ADR-367's layering rule. Vitest covers all of its states.
- Each settings family page gets a `PageHeader` (existing title; description only where one already exists).

## Section 5 — Settings Content (31 tabs + sub-pages)

- Migrate every tab's content onto `SettingsSection`, with any list or table on DataTable (client mode, `frame={false}` inside a card), and status chips on the Phase 90 badge helpers. Group the slices by family:
  - **firm**: general, roles, audit-trail (DataTable with today's filters and paging), data-protection, data-requests, compliance, features, subscription;
  - **matters**: templates, naming, custom-fields, tags, checklists, request-templates, request-settings;
  - **fees**: tariffs, tax (`tax-rate-table`), time-recording, fee-runs, collections, trust-accounting;
  - **documents + connections**: templates, clauses, acceptance, email; xero, mcp, ai, notifications, packs, automations.
- Sub-pages (the template editor, checklist editor, request-template and matter-template detail, `xero/sync-log`, `ai/history`, `automations/executions`) get PageHeader + SettingsSection + DataTable for their logs. The **Tiptap template editor's internals are not touched**; only its page frame changes.
- Pack cards (`pack-card.tsx`) and the MCP connector card adopt the Card and StatusBadge vocabulary. Install/uninstall flows are unchanged.
- Every settings slice observes as owner **and** as a member or custom role to confirm that gated tabs and sections still hide exactly as today.

## Section 6 — Fee Notes

- **List** (`invoices/page.tsx`, 352 lines): PageHeader, the list on DataTable with parity (status filter, search, any saved views or URL state it has today, row links, CSV), and a StatCard row **only from existing reads** (`/architecture` maps each tile to its source; for example outstanding, overdue and billed this period, with redaction rules). Use `formatDateInSA` for client-rendered dates.
- **Detail** (`invoices/[id]/page.tsx`): a DetailHero with the fee-note number as identifier, the client and matter subtitle, the status badge, the existing actions, and a KPI strip (amount, paid, balance due, due date or days overdue) from the page's existing reads, using the `hero-tiles.ts` server-only pattern and the shared `tileFrom`/`overdueHint` module. Line items and payments sit in tables on DataTable or the kit table styling. The custom-fields section follows Section 3.
- **Billing runs** (list, detail, new wizard), **collections** and **unbilled**: PageHeader + DataTable + StatCard where a summary exists today. The billing-run wizard's steps and validation are unchanged and get visual alignment only.

## Section 7 — Trust Sub-Pages

- `client-ledgers` (+ `[clientId]`), `transactions` (479 lines), `reconciliation` (+ `new`), `interest`, `investments` and `reports`: PageHeader, tables on DataTable with parity (filters, date ranges, running balance column, approval status chips, row actions as today), and StatCard summaries only where the page already shows summary figures.
- Reuse 643A's `trust-transactions-table.tsx` wrapper where the columns match; do not fork it.
- **No trust domain logic changes.** Running balances stay server-side (ADR-361), the interest-run approval surface keeps its behaviour (ADR-362), and Section 35 report generation is untouched.
- The reconciliation wizard (`reconciliation/new`) gets visual alignment only.

## Section 8 — Diary, My Work, Timesheet, Notifications

- **Diary** (`diary/page.tsx`): PageHeader; the list or agenda on the kit's table or list styling. Postponed, cancelled and heard entries are visually distinct with icon and label, not colour alone.
- **My Work**: PageHeader. `assigned-task-list.tsx` and `urgency-task-list.tsx` move onto DataTable (client mode, `frame={false}`) or a kit list, with Section 3's empty states. Phase 90's TimeBreakdown and WeeklyTimeSummary work is not redone.
- **Timesheet**: PageHeader. The grid keeps its entry behaviour and keyboard handling, with visual alignment only.
- **Notifications**: PageHeader, the list on kit styling, and a `default` empty state (it is the whole page).

## Section 9 — Light Pass

For `compliance` (+ `requests`, `requests/[id]`), `conflict-check`, `legal/{adverse-parties,disbursements(+[id]),tariffs}`, `team`, `ai/reviews`, `information-requests/[id]` and `platform-admin/{access-requests,billing,demo}`:
- PageHeader;
- the correct EmptyState variant;
- DataTable **only where a table already exists**, with parity;
- tokens over `dark:` pairs on the touched lines;
- approved register items for these pages.

No hero, no new StatCards and no layout redesign. Group these by verify cost into about 2 slices.

## Section 10 — Exit Gate

Same shape as Phase 90's 644:
- **Suites:** the legal Playwright suites (`legal-lifecycle`, `legal-depth-ii`) and the default suite on a **fresh** stack (`e2e-down` → `e2e-up`), plus FE-GATE and PORTAL-GATE (and BE-VERIFY on `main` if any Java landed).
- **Observation matrix:** every in-scope surface at 1024 and 1440 in light and dark, plus owner and member for settings and fee notes, and 390px for the shell and settings nav. Screenshots go to `qa_cycle/evidence/phase91-ui/`.
- **Closure report:**
  - kinks register outcomes (fixed / backlog / won't-fix, each with a reason);
  - before/after metrics: `PageHeader` adoption out of 57, inline `<h1` count, direct `<Table` count by area, EmptyState calls without `variant` (target 0), and raw-hex count;
  - the updated dark-mode debt list;
  - any documented `PageHeader` exceptions;
  - the light-scheme ADR link.
- Re-shoot the `documentation/screenshots/` and marketing screenshots that show the app shell in light mode, or list them as stale.

## Out of Scope

- **Client portal** restyle, unless the founder chooses in Section 1 that the portal follows the new light scheme, in which case that is limited to tokens.
- Marketing site redesign, beyond keeping it from visibly breaking (and following the scheme only if chosen in Section 1).
- Any change to settings IA: the five families, the tab set, tab order, and merging or splitting tabs (Phase 87 stands).
- Any copy, label, tab-name, route or `data-testid` change, unless it is an approved register item flagged as a product decision.
- New features, bulk actions, write endpoints, entities or migrations. Changes to trust domain logic, MCP, AI skills, template packs or the Tiptap editor internals.
- A theme customizer, per-user colour or density settings, or more than one light scheme shipped.
- Renaming component folders (`components/projects`→`matters`, `customers`→`clients`), which remains a separate mechanical PR.
- Fully redesigning the light-pass pages (Section 9) beyond the stated pass.

## ADR Topics to Address

ADR numbering continues from **ADR-385** (Phase 90 used 367–374, including 374 for the theme init script; Phase 92, which runs before this phase, used 375–384). Epics continue after Phase 92's 645–652.
1. **Light-mode colour scheme.** Supersedes the Phase 90 sidebar ruling (§15, `architecture/phase90-ui-modernization.md:234`). Covers: the candidates, the founder's pick, rail and canvas and primary-button tokens, `--brand-color` treatment, AA contrast evidence, and the portal/marketing scope decision with its mechanism.
2. **EmptyState sizing rule.** The variants, when each applies, the enforcement mechanism (lint, vitest guard or default-prop change), and the custom-fields one-liner as the worked example.
3. **Settings shell and `SettingsSection`.** Layering per ADR-367, per-card vs page-level save, the danger-zone variant, the mobile settings nav, and how it relates to `settings-family-page.tsx` and ADR-341.
4. **Fee-note detail hero** (only if it differs from ADR-373's DetailHero pattern): tile sourcing and redaction.
5. **Read-only aggregate endpoint** (only if one is added): justification and shape, per the Phase 90 pattern ADR.

## Style & Boundaries

- Order of work: **colour scheme (Section 1) → kinks register approved (Section 2) → EmptyState rule and cross-cutting fixes (Section 3) → settings shell (Section 4) → settings content (Section 5) → fee notes, trust, personal pages (Sections 6–8, independent of each other) → light pass (Section 9) → exit gate (Section 10).** No surface slice starts before the scheme slice and the register approval.
- This is a visual phase. If a slice needs a behaviour change, stop and re-spec. Register items that are really product decisions go back to the founder.
- **Observe first in every surface slice:** capture the before state (URLs, sort orders, filter behaviour, empty states, gated visibility per persona), then replay it after the change. The browser OBS, not reviewer approval, is the merge gate for layout.
- Each new primitive or variant has vitest coverage for its states. Grep repo-wide before deleting anything.
- CLAUDE.md quality gates apply unchanged. Prettier `format:check` is part of green. Review is CodeRabbit plus two `feature-dev:code-reviewer` passes (CodeRabbit's included-review cap is 1 per hour; wait, never buy the on-demand review).
- Target size: about 9 epics and 16–18 slices. If the breakdown exceeds 20 slices, split Sections 8–9 into Phase 92 rather than cramming.

## Next step

After Phase 90's Epic 644 merges, run `/architecture requirements/claude-code-prompt-phase91.md`, then `/breakdown 91`. Section 1's candidate prototype (Step 1) can be produced right after `/architecture`, so the founder's pick is ready before building starts.
