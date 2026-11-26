# 664B: out-of-scope defects found while building (reported, not fixed; root CLAUDE.md §7)

## F-664B-1: `EditViewDialog` is dead code with the K-47 defect

- **Where:** `frontend/components/views/EditViewDialog.tsx:159` (`<DialogContent className="sm:max-w-lg">`) and `:171` (`<div className="space-y-4 py-2">`). This is the same unbounded markup that K-47 fixed in `CreateViewDialog.tsx`: a tall step 1 (one filter row per custom field) would run off the viewport with no scroll.
- **Why it is not visible:** no product module imports it. `git grep -n "EditViewDialog" -- frontend/` finds only the component itself and `__tests__/views/EditViewDialog.test.tsx`. `ViewSelectorClient.tsx:6` imports only `CreateViewDialog`.
- **Proposed:** delete the component and its test in their own PR, after confirming that no saved-view edit flow is planned. If it is revived instead, apply K-47's two class changes.
- **Not fixed in 664B:** orchestrator ruling 3 (K-47 covers `CreateViewDialog` only).

## F-664B-2: the kit pager has no zero-page label (needed by 665B, audit log parity)

- **Where:** `frontend/app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:944-955`. The audit log's always-visible pager shows "No events" when `totalPages === 0`. Its page text is also split over nodes: `Page <span class="font-medium">{n}</span> of {m}`.
- **Kit today (664B):** with `paginationShowSingle` and `pageCount === 0`, `DataTablePageLinks` renders "Page 1 of 0" verbatim (ADR-395 OQ-5 default). It has no empty-label slot, because COPY-FREEZE forbids inventing copy and 664B's tasks do not ask for one.
- **Ask:** spec a kit way to render the existing "No events" string at zero pages (for example a `paginationEmptyLabel` node) **before 665B**. The hotspot rule says kit gaps are reported, not patched in a surface slice.

## F-664B-3: an out-of-range `?page=` shows "Page 10 of 3" (pre-existing, kept for parity)

- **Where:** `/org/{slug}/trust-accounting/other-creditors?page=9` (and `?itemsPage=9`). Before-evidence: `664B/before/pager.json` → states `page=9 (out of range)` / `itemsPage=9 (out of range)`.
- **Behaviour (before and after 664B):** the out-of-range message and its "Go to the first page" link render, and the pager still says "Page 10 of 3". Its Previous link goes to `?page=8`, which is also out of range. Next is disabled.
- **Why kept:** the kit pager renders the server's `page.number` / `page.totalPages` exactly as `LedgerPager` did (the vitest case "keeps the pager beside the out-of-range message on a page past the end" pins the parity). Orchestrator ruling 5: out of scope.
- **Proposed fix (needs a decision):** clamp Previous to `pageCount - 1` past the end, or hide the pager when `pageIndex >= pageCount`. Either one changes behaviour, so it should be re-specced (for example in 667B with the other trust pagers).
