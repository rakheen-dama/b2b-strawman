# ADR-367: Design-system layering: `@b2mash/ui` primitives vs app-coupled frontend components, and the "new page" rule

**Status**: Accepted

**Context**:

Phase 90 builds six primitives (PageHeader, DataTable, StatCard, a chart kit, EmptyState, DetailHero) plus the shadcn `sidebar` and `chart` components, and moves the dashboard, matter and client list/detail pages, the trust dashboard and reports/profitability onto them ([architecture §15](../architecture/phase90-ui-modernization.md)). Kazi already has two primitive homes and no rule for choosing between them:

- **`packages/ui` (`@b2mash/ui`)** is hand-maintained (no `components.json`): `src/{badge,button,card,input,label,separator,skeleton,cn}.ts(x)`, one `exports` entry per file in `packages/ui/package.json` plus `./styles/globals.css`, re-exported from `src/index.ts`. Its only runtime dependencies are `class-variance-authority`, `clsx`, `radix-ui` 1.4.3 and `tailwind-merge`, with React as a peer. It is consumed by the firm app **and the portal**: 31 portal source files import it, and `portal/app/globals.css` imports `@b2mash/ui/styles/globals.css`, so any `:root` token or primitive change reaches the portal. `frontend/CLAUDE.md` requires base primitives to be imported from `@b2mash/ui`, not `@/components/ui/*`. `Card` emits `data-slot="card" | "card-title" …`, which `e2e/tests/finance/profitability.spec.ts` selects on.
- **`frontend/components/ui`** holds the shadcn new-york (Radix) components the CLI writes (`alert`, `dialog`, `dropdown-menu`, `table`, `tabs`, `tooltip`, …; 20 files). `sidebar` and `chart` will land there too, because `frontend/components.json` points the `ui` alias at it.

The visual debt this phase measures is the absence of a rule: 94 inline `font-display text-3xl` h1s, ≈53 hand-rolled `<table>`s (48 files import `ui/table`), ≈217 `rounded-* border border-slate-*` divs doing `<Card>`'s job, and 37 raw hex colours. The existing `EmptyState` (`frontend/components/empty-state.tsx`, 48 importers) imports `next/link` and takes a `LucideIcon` component reference. Currency formatting goes through `frontend/lib/format.ts`, which loads `@/lib/intl-polyfill` before re-exporting `@b2mash/shared`'s `formatCurrency` (en-ZA during SSR, GAP-L-12). The portal is out of scope this phase but is expected to adopt shared primitives later ([ADR-076](ADR-076-separate-portal-app.md) keeps it a separate Next.js app).

**Options Considered**:

1. **Everything shared in `@b2mash/ui` (DataTable, chart kit, sidebar shell, DetailHero, EmptyState)** — one home for every primitive, so the portal can adopt any of them later.
   - Pros: The portal could adopt any primitive without a move later. Single import path.
   - Cons: `@b2mash/ui` would gain `next/*`, `recharts`, `@tanstack/react-table` and `lucide-react` dependencies, plus coupling to frontend-only modules (saved views in `components/views/*`, server actions, `lib/nav-items.ts`, capability hooks). Every slice that touches a primitive would run PORTAL-GATE. The portal bundle would pull dependencies it doesn't use. And moving `EmptyState` would mean editing all 48 importers in one slice.
2. **Everything in `frontend/components`; `@b2mash/ui` gets token changes only** — zero portal coupling.
   - Pros: No portal risk beyond tokens. Fewer PORTAL-GATE runs.
   - Cons: It contradicts the requirements' constraint that framework-agnostic primitives go into `@b2mash/ui` "so the portal can adopt them later". StatCard and PageHeader would be moved or duplicated as soon as the portal is restyled.
3. **Split by a dependency rule: `@b2mash/ui` takes presentational, framework-agnostic, dependency-free primitives and the tokens; everything that knows about Next.js, data fetching, URL state, capabilities or a charting library stays in `frontend/components` (CHOSEN).**
   - Pros: The rule can be checked mechanically from the import graph. `@b2mash/ui` keeps its current dependency set. The portal gains reusable StatCard and PageHeader without new dependencies. App-coupled pieces stay next to the modules they use.
   - Cons: Some primitives are split across the boundary (StatCard in `ui`, its sparkline and currency formatting in `frontend`), so callers wire two pieces. Every `packages/ui` slice still runs PORTAL-GATE.
4. **A third workspace package (`@b2mash/ui-app` or `@b2mash/charts`) for app-level primitives** — a separate home for heavier shared pieces.
   - Pros: Keeps `@b2mash/ui` lean while making heavier pieces shareable.
   - Cons: A new package needs its own `exports`, lint, typecheck and workspace wiring, and it would have one consumer, so it is premature. The portal adopting DataTable or charts is not planned.

**Decision**: Option 3 — `@b2mash/ui` holds the tokens and only presentational, dependency-free primitives, everything that knows about Next.js, URL state, capabilities or a chart library stays in `frontend/components`, and every new or reworked page under `org/[slug]` follows the "new page" rule.

**Rationale**:

1. **The boundary is the dependency set, not a judgement call.**
   - **What goes where.** `@b2mash/ui` gets PageHeader, StatCard (default and compact variants) and the new chart, status and elevation tokens in `styles/globals.css`. It gets nothing that imports `next/*`, `recharts`, `@tanstack/*`, `lucide-react` or any `frontend/` module. DataTable, the chart kit, EmptyState, DetailHero and the sidebar shell stay in `frontend/components`.
   - **PageHeader** has the props title, description, a breadcrumb slot and an actions slot. It renders exactly one `<h1>`, because 98 e2e call sites across 38 files use `getByRole("heading", { level: 1, name })` and the `openMatter`/`openClient` helpers wait on it.
   - **StatCard** takes `value: ReactNode` (pre-formatted by the caller), an optional `delta` of shape `{change, unit, polarity, comparisonLabel}` from which it renders the signed figure, the direction icon and the sr-only text, and `icon`/`sparkline` as `ReactNode` slots. It has ready, loading, redacted and error states. The full prop contract is in §15.2.3 of the [architecture doc](../architecture/phase90-ui-modernization.md).
   - So StatCard never formats currency itself. That keeps the intl polyfill in `frontend/lib/format.ts` on the only path that formats ZAR, and keeps `recharts` out of `@b2mash/ui`.
   - DataTable is app-coupled by definition: it holds URL state, the saved-views slot and TanStack ([ADR-369](ADR-369-datatable-tanstack-client-mode.md)). So is the chart kit: recharts presets on shadcn `chart`, plus SVG micro presets that share the kit's tokens and accessibility contract ([ADR-370](ADR-370-chart-kit-and-palette.md)). So is DetailHero, which wires capability-gated tiles ([ADR-373](ADR-373-detail-hero-kpi-strip.md)).
2. **EmptyState stays where it is.** Rulings §3.6 forbid a second EmptyState and forbid a move unless every importer changes in the same slice. With 48 importers and a `next/link` dependency, a move would be a large mechanical diff that the portal doesn't need yet. The component is restyled in place (adapting the template's `empty-state-v2`). It is promoted to `@b2mash/ui`, with a link-agnostic `action` slot, in the phase that restyles the portal.
3. **Verify cost.** Only the slices that touch `packages/ui` (tokens, PageHeader, StatCard) pay for PORTAL-GATE. Surface slices touch `frontend/` only and run FE-GATE plus the legal Playwright suites. Portal-visible change this phase is therefore limited to token retunes and the new exports, which the portal doesn't import.
4. **The "new page" rule.** A page added or substantially reworked under `org/[slug]` uses:
   - PageHeader for its title (no inline `font-display text-3xl` h1);
   - DataTable for tabular data (no raw `<table>` or bare `ui/table` outside DataTable);
   - `Card` from `@b2mash/ui` for grouped content (no hand-rolled `rounded border` card divs);
   - the chart kit for any chart, and EmptyState for empty results;
   - semantic tokens, with no raw hex and no `dark:` pair where a token exists ([ADR-371](ADR-371-dark-mode-light-default.md)).

   The ≈45 long-tail pages adopt the rule when next touched. There is no sweep (founder decision 1).

**Consequences**:

- Positive:
  - One written rule for reviewers. The exit gate's before/after metrics (inline h1 count, ad-hoc `<table>` count, raw-hex count) measure adoption of it.
  - The portal can import `@b2mash/ui/stat-card` and `@b2mash/ui/page-header` later with no dependency change. The exact export names are fixed by the primitives slice; verify at build.
  - `@b2mash/ui` keeps the same four runtime dependencies, and the portal bundle doesn't grow.
- Negative:
  - Each new `@b2mash/ui` primitive needs an `exports` entry and an `src/index.ts` re-export, and its slice runs PORTAL-GATE.
  - StatCard callers format values and build sparklines themselves. Thin frontend wrappers (KPI tile, hero tile) are expected, which adds one level of indirection.
  - EmptyState stays app-coupled until the portal phase, so the portal can't share it yet.
  - The rule is enforced by review and the exit metrics, not by a lint rule. A lint rule (for example, banning `ui/table` imports outside DataTable) is deferred until the long tail has shrunk enough not to flood the lint output.

**Related**: [ADR-076](ADR-076-separate-portal-app.md) (portal is a separate app that shares `@b2mash/ui`), [ADR-205](ADR-205-chart-theming-strategy.md) (chart theme object; amended by ADR-370), [ADR-368](ADR-368-admincn-template-adoption.md) (how template code enters either layer), [ADR-369](ADR-369-datatable-tanstack-client-mode.md), [ADR-370](ADR-370-chart-kit-and-palette.md), [ADR-371](ADR-371-dark-mode-light-default.md), [ADR-373](ADR-373-detail-hero-kpi-strip.md); [architecture §15](../architecture/phase90-ui-modernization.md).
