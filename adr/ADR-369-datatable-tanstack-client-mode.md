# ADR-369: DataTable on TanStack Table: client mode by default, URL-state contract, saved-views slot

**Status**: Accepted

**Context**:

Kazi has no reusable table: 48 files import `components/ui/table` and ≈53 contain a raw `<table>`. Phase 90 builds a DataTable primitive and moves the matters and clients lists, the trust dashboard tables and the profitability tables onto it ([architecture §15](../architecture/phase90-ui-modernization.md)). The two lists that matter most work like this at HEAD (rulings §3.3):

- **Fetch-all, no paging.** `matters/page.tsx` calls `listMatterRows(params)` (`GET /api/matters`), and `clients/page.tsx` calls `listClientRows` (`GET /api/clients`). Both return a **plain array**. Every row renders, with a footer such as "Showing N matters" (`data-testid="matters-footer"`). The backend has no search parameter and no page contract on these endpoints. `ListMattersParams.size` exists, but the page forwards it to the backend; it is not a UI page size.
- **Search and sort run in the server component, with state in the URL.**
  - Search uses `searchMatterRows` / `searchClientRows` on `?search=`.
  - Sort uses `parseMatterSort` + `sortMatterRows` on **`sort`/`dir`** for matters, and `parseClientSort` + `sortClientRows` on **`sortBy`/`sortDir`** for clients. The param names differ between the two pages.
  - Header links come from `buildSortHref` (`lib/matters-list.ts`) and preserve every other query param.
- **Filters.**
  - Matters: status chips (`ProjectStatusFilter`, `data-testid="project-status-filter"`, links writing `status=`).
  - Clients: lifecycle and completeness filters.
  - Both: a tags filter sheet that is hidden on a saved view.
- **Saved views.** The page resolves `?view=` with `resolveViewSelection(SYSTEM_VIEWS, savedViews, …)` (`lib/constants/system-views.ts`). The view's `columns` drive `resolveMatterColumns` / `resolveClientColumns`. `ViewSelectorClient` writes `?view=` and drops the `clearOnSavedView` params (`["tags"]` for matters, `["tags","lifecycleStatus"]` for clients). Filtering for a saved view executes on the server ([ADR-055](ADR-055-saved-view-filter-execution.md)).
- **Server-rendered rows.** `matters-table.tsx` (280 lines) and `clients-table.tsx` (331) are React Server Components. Rows are the client components `MatterTableRow` / `ClientTableRow`: click-to-open, `data-testid="matter-row"` / `"client-row"`, with clicks on links, buttons and menus ignored. Low-priority columns hide through container queries (`@min-[67rem]`, `@7xl`).
- **e2e coupling.** Specs use `getByRole("row").filter({ has: getByRole("link", { name, exact }) })`, `getByTestId("client-row")`, `getByTestId("project-status-filter").getByRole("link", { name: "Closed" })` plus the URL `status=CLOSED`, and `#main-content getByRole("table")` with named `columnheader`s (`finance/reports.spec.ts`, `finance/profitability.spec.ts`). The helpers `openMatter` / `openClient` (`e2e/helpers/legal-lifecycle.ts`, `legal-depth-ii.ts`) expect the entity's link on the first screen, **without paging**.

**Options Considered**:

1. **Server-paginated TanStack (`manualPagination`, `manualSorting`, `manualFiltering`) against paged backend endpoints** — the "scalable" textbook shape.
   - Pros: Bounded payloads for large firms. Matches [ADR-055](ADR-055-saved-view-filter-execution.md)'s server-execution stance.
   - Cons: `/api/matters` and `/api/clients` have no page or search contract, so this needs Java changes (paging plus a search param). Those slices would exceed the phase's ≤ 2 Java-slice budget, which belongs to the chart endpoints ([ADR-372](ADR-372-read-only-chart-series-endpoints.md)). Paging also hides rows that `openMatter` / `openClient` expect on the first screen, and it changes visible behaviour in a visual phase.
2. **Client mode with TanStack owning search, sort and filter in memory** — the RSC passes all rows; TanStack's row models do the rest; the URL is not involved.
   - Pros: Instant sort and search with no navigation.
   - Cons: It breaks the URL contract: shareable links, back button, `buildSortHref` preserving filters, and `?view=` composing with `?sort=`. It moves search semantics from the server's `searchMatterRows` into a second implementation that can drift. The changed behaviour would need re-spec.
3. **Client-rendered TanStack DataTable in "client mode": rows fetched and ordered by the RSC exactly as today, sort and search state kept in the existing URL params, pagination off by default, server-paginated mode designed but not built (CHOSEN).**
   - Pros: Behaviour parity: same params, same server-side search and sort, every row visible. Table semantics are preserved for role selectors. No Java. Additive features (column visibility, CSV of visible rows, optional selection, opt-in page size) come from TanStack at no backend cost.
   - Cons: Sorting stays a navigation (an RSC re-render), not an in-memory reorder. TanStack is in the list pages' client bundle. Column definitions must live in client modules.
4. **A heavyweight grid (AG Grid, MUI DataGrid)** — feature-complete out of the box.
   - Pros: Virtualisation, grouping and pinning for free.
   - Cons: Not shadcn or Tailwind-native, so it is themed separately from the token layer. It defaults to `role="grid"` semantics, which would break the ≈829 `getByRole` selectors' assumptions. It is a large dependency for features Kazi doesn't need.
5. **Keep hand-rolled tables and share styles only** — a CSS and class-name pass over today's tables.
   - Pros: Smallest diff.
   - Cons: No column visibility, export or selection. The 48 copies stay duplicated, and the "new page" rule ([ADR-367](ADR-367-design-system-layering.md)) has no table primitive to point to.

**Decision**: Option 3 — `DataTable` is a client component in `frontend/components` on `@tanstack/react-table` that renders rows the server component has already fetched, searched, filtered and sorted, keeps sort and search in each page's existing URL params, renders every row unless a table opts into a page size, and gives saved views, search and filters toolbar slots without changing how they work.

**Rationale**:

The contract in §15.2.4 of the [architecture doc](../architecture/phase90-ui-modernization.md) governs prop names, URL-key mapping and column-module filenames. `@tanstack/react-table` is pinned at 8.21.3, the phase's first new runtime dependency.

1. **The URL-state contract.**
   - Each page passes `sort: { key, dir, hrefFor }`, where `sort.hrefFor` is built from the page's existing `buildSortHref`: matters use `sort`/`dir` and clients use `sortBy`/`sortDir`. A header is a link to `sort.hrefFor(sortKey)`, which replaces only those keys and keeps every other param, as today.
   - TanStack runs with `manualSorting: true`, so the order of rows is the order the server sent them in.
   - Search stays `?search=`, written by the existing `MatterSearch` / `ClientSearch` components in the search slot.
   - Filters stay the existing chips and sheets in the filter slot.
   - DataTable never reads or writes `?view=`: the saved-views slot renders `ViewSelectorClient` unchanged. The view's `columns` remain the source of truth for which columns render. The toolbar's column toggle can only hide columns the view shows. It is ephemeral state that resets on navigation, and it is persisted only by saving a view through the existing dialogs.
2. **Opt-in paging.** When a table opts into a page size, the page index is local TanStack state and nothing new is written to the URL (architecture §15.3.3). If a later slice persists the page in the URL, its params must be **namespaced and must not collide** with existing ones; matters already forwards `size` to the backend. Matters and clients do **not** opt in this phase, so the first screen shows every row, as e2e expects.
3. **Server-paginated mode** (`manualPagination` + `pageCount` + URL page) is a designed seam only. It is built when a backend endpoint pages and a slice needs it.
4. **Semantics and selectors are preserved.**
   - DataTable renders the existing `components/ui/table` elements, so the roles stay `table`/`row`/`columnheader`/`cell`, not `role="grid"`.
   - `rowHref` and `rowTestId` reproduce the click-to-open contract and test ids of `MatterTableRow` / `ClientTableRow`, and those two row components are deleted (after a grep for other importers).
   - Column metadata carries a `priority` that maps to the existing container-query classes.
   - Both empty-state variants survive: filtered ("No matters match this view." + "Clear filters") and unfiltered (`EmptyState`).
   - The sticky header uses `position: sticky` inside the table container.
   - On small screens the table scrolls horizontally inside its container rather than switching to a card list. A card list would change the roles that e2e selects on, and the frontend Playwright projects run only `devices["Desktop Chrome"]`, so no mobile spec exists to re-anchor them.
5. **Additive capabilities only.** CSV export writes the **visible** rows and columns from data already on the client, so it can't export a column the user can't see. Row selection exists only to scope that export. No bulk mutation is added; that would be a new feature with a new authorization path.
6. **RSC boundary.** Column definitions, cell renderers and `href` builders are functions, which can't be passed as props from a server component (the same constraint as `lib/nav-items.ts`). Each table therefore has a client column module (`components/matters/matter-columns.tsx`, `components/clients/client-columns.tsx`). The server component passes serialisable rows plus the resolved column ids.

**Consequences**:

- Positive:
  - Matter and client lists keep their behaviour, their URLs and their e2e selectors, and gain column toggle, CSV of visible rows and a consistent toolbar. No backend slice is spent on tables.
  - One table primitive serves the trust dashboard (`transactions-table`) and profitability tables. The existing `profitability-export-csv` button stays as it is.
  - The paged mode is already designed when a future backend paging endpoint arrives.
- Negative:
  - Sorting is a server round-trip per click, as it is today. It is not instant.
  - The full row set is serialised into a client component on list pages. Payloads still grow with firm size; that is today's fetch-all behaviour, unchanged, and paging is deferred to the designed seam.
  - The `@tanstack/react-table` client bundle loads on list pages. `matters-table.test.tsx`, `clients-table.test.tsx` and `projects-page.test.tsx` / `clients-page.test.tsx` are rewritten around the client component, while asserting the same rows, links and test ids.
  - Each consumer has to keep its own URL-key mapping in `sort.hrefFor`. Unifying `sort`/`dir` with `sortBy`/`sortDir` is out of scope (a URL change).

**Related**: [ADR-055](ADR-055-saved-view-filter-execution.md) (saved-view filters execute on the server; unchanged), [ADR-367](ADR-367-design-system-layering.md) (DataTable is app-coupled, so it lives in `frontend/components`), [ADR-368](ADR-368-admincn-template-adoption.md) (toolbar adapted from the template's `views/apps/users/list/` and `datatable-invoice.tsx`), [ADR-370](ADR-370-chart-kit-and-palette.md) (charts' "view as table" alternative is a plain `<table>`, deliberately not DataTable), [ADR-372](ADR-372-read-only-chart-series-endpoints.md) (where the Java budget goes instead); [architecture §15](../architecture/phase90-ui-modernization.md).
