# ADR-395: DataTable server-paged mode

**Status**: Accepted (refines [ADR-369](ADR-369-datatable-tanstack-client-mode.md) Rationale point 3, which designed a server-paginated seam and deferred it; ADR-369's decision — client mode by default, URL-owned state — is unchanged)

**Context**:

ADR-369 built `DataTable` in client mode: rows arrive already searched, filtered and sorted by the server component; `pageSize` paginates **locally** with a page-size select, and "the page index is local state (nothing is written to the URL)" (`data-table-pagination.tsx:25-27`). It designed a server-paginated mode (`manualPagination` + `pageCount` + URL page) as a seam "built when a backend endpoint pages and a slice needs it".

Phase 91 moves the trust sub-pages and the firm audit trail onto DataTable, and those tables page on the server today:

- **Link pagers** on `?page=` (zero-based) in transactions (`transactions/page.tsx:224-231,488-520`), client ledgers, a client's history and reconciliation: text "Page {n} of {m}", "Previous"/"Next" as `Button asChild variant="outline"` → `Link`, disabled buttons at the ends, hidden when there is one page. A filter change drops `page` (`filterUrl`, `:214-222`).
- **Phase 92's `LedgerPager`** (`other-creditors/page.tsx:452-496`) is the same pager as a function of `{page: {number, totalPages}, hrefFor}`, already used beside DataTable for two lists on `page` and `itemsPage`.
- **The audit log** pages with buttons that call `goToPage` → `router.push(buildPath(qs))` inside `startTransition` (`audit-log-client.tsx:448-458`), in a `nav aria-label="Audit log pagination"` that is always visible and shows "No events" at zero pages (`:945-985`).
- E2E selects these pagers by text and role (`helpers/trust-balanced-month.ts:507-509` reads `/^Page \d+ of \d+$/` and button "Next" exact on the report runner); the local pager's `aria-label`s ("Go to previous page") differ from the visible names.
- Trust tables are asserted by per-row testids (`transaction-row-<id>`, `history-row-<id>`, …), while `rowTestId` is a constant string.

**Options Considered**:

1. **Leave server-paged tables hand-rolled, on kit table styling only.**
   - Pros: No kit change.
   - Cons: The trust sub-pages — the long tail's most-used tables — stay outside the table primitive, its column priorities, cells and empty slot; four copies of the pager remain.
2. **Fetch every page and use local paging (`pageSize`).**
   - Pros: No new mode.
   - Cons: Changes data volume and backend calls (a firm's full transaction history in one payload), and loses `?page=` deep links and the back button — behaviour changes in a visual phase.
3. **TanStack `manualPagination` with `state.pagination` controlled by the URL, and DataTable navigating on page change.**
   - Pros: The textbook TanStack server mode.
   - Cons: DataTable would have to know how to build URLs or call the router — page-specific knowledge the sort contract deliberately keeps in the page (`hrefFor`); TanStack's page state adds nothing when the rows already are the page.
4. **Infinite scroll or "Load more".**
   - Pros: Modern feel.
   - Cons: New behaviour and new copy; breaks page deep links; not parity.
5. **An additive `pagination` prop — `{pageIndex, pageCount, hrefFor}` (links) or `{pageIndex, pageCount, onPageChange}` (buttons) — rendered as a kit pager lifted from `LedgerPager`, with TanStack untouched and local paging, CSV and selection excluded at the type level (chosen).**
   - Pros: Mirrors the existing `sort` union (`hrefFor` vs `onSort`); the page keeps building its URLs exactly as today; both pager kinds in the product are covered; today's copy, roles and names are kept; one pager instead of five.
   - Cons: A second paging mode in one component; a discriminated union on the props; column visibility is off by default in this mode because its state would reset on every page navigation.

**Decision**: Option 5.

- `DataTable` gains `pagination?: { pageIndex: number; pageCount: number; hrefFor: (pageIndex: number) => string } | { pageIndex: number; pageCount: number; onPageChange: (pageIndex: number) => void }`, plus `paginationLabel?: string` (wraps the pager in a `nav` with that name, only where one exists today) and `paginationShowSingle?: boolean` (default `false`: nothing when `pageCount <= 1`).
- With `pagination`, `pageSize`, `exportCsv` and `selectable` are `never` (a CSV of one page would be a false export), and `columnVisibility` defaults to `false`.
- The pager (`components/data-table/data-table-page-links.tsx`) renders "Page {n} of {m}" and "Previous"/"Next" with those visible texts as their accessible names: links (or disabled buttons at the ends) for `hrefFor`, buttons for `onPageChange`.
- TanStack keeps `manualPagination` and receives no pagination state; the rows are the page the server sent.
- `rowTestId` also accepts `(row) => string`, so per-row testids survive.
- **Only from a client wrapper.** The props are functions, so DataTable — in this mode as in every other — is rendered only from a `"use client"` module (`data-table.tsx:3-7`, Phase 90 OQ #19; the `MattersDataTable`/`ClientsDataTable` precedent). Each server-paged page renders a thin per-page wrapper and passes serializable props only: rows, `pageIndex`, `pageCount`, the current query as a plain object, the base path and any serializable row context (e.g. `currentMemberId`). The wrapper builds `hrefFor` with `buildPageHref(basePath, query, pageIndex, param)` (`lib/page-href.ts`, pure; keeps every param and sets `page` or `itemsPage`, reproducing today's `pageUrl`), plus `rowHref`, per-row testids and row actions from existing client components (e.g. `ApprovalBadge`, `ReversalButton`). The audit log is already a client component and passes its `goToPage` as `onPageChange`.
- Built in slice E4B with other-creditors as its first consumer (its existing client tables `FundLedgerTable`/`UnidentifiedDepositsTable` take the pager props), before the audit-trail (E5B), connection-log (E5F) and trust (E7A, E7B) slices. A table that cannot reach parity on this mode stays on kit table styling and is listed in its slice's PR.

**Rationale**:

Parity is the constraint: the URL keeps owning the page, the backend keeps paging, the links keep their params and the pager keeps its words. The prop shape copies the one DataTable already uses for sort, so pages that already own `pageUrl`/`goToPage` pass them in unchanged, and the audit log keeps its transition-wrapped `router.push`. Keeping TanStack out of paging avoids two sources of truth for the page number.

**Consequences**:

- Positive:
  - Trust and audit tables join the primitive with no behaviour change; four hand-rolled pagers and `LedgerPager` are retired.
  - Server-paged tables send one page of rows to the client, never more.
- Negative:
  - DataTable has two paging modes; the type union and tests must keep them exclusive.
  - No CSV and no column menu on server-paged tables (an all-pages export would need a backend export, which is out of scope).
  - Every page change resets any local table state, as sort already does.
  - Each server-paged page needs its own client wrapper file (counted in its slice's budget).

**Related**: [ADR-369](ADR-369-datatable-tanstack-client-mode.md) (the seam this builds), [ADR-367](ADR-367-design-system-layering.md) (DataTable is app-coupled), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (running balances stay server-side on paged history), [ADR-376](ADR-376-non-client-trust-creditor-ledger.md) (the other-creditors lists that already page); [architecture §18.2.5, §18.3.7, §18.5.1](../architecture/phase91-ui-modernization-ii.md); slices E4B, E5B, E5F, E7A, E7B.
