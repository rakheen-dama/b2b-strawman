# ADR-396: Fee-note detail hero: tiles derived on the client from the live fee note

**Status**: Accepted (deviates from [ADR-373](ADR-373-detail-hero-kpi-strip.md)'s server-only tile loaders for the fee-note detail page only; ADR-373's decision — hero with a KPI strip from existing reads, failing gates omit tiles — is unchanged)

**Context**:

ADR-373 gave the matter and client detail pages a `DetailHero` with a 0–4 tile strip. Their tiles are built by `import "server-only"` loaders (`matters/[id]/hero-tiles.ts`, `clients/[id]/hero-tiles.ts`) using the shared `lib/hero-tiles.ts` helpers `tileFrom` and `overdueHint` (642B), which is also `server-only` (`:1`). Those pages' header data is server state: a change re-renders the page.

Phase 91's requirements ask for the same hero on the fee-note detail (`invoices/[id]`): the number as identifier, client and matter subtitle, status, existing actions, and a strip of amount, paid, balance due and due date or days overdue, "using the `hero-tiles.ts` server-only pattern". At HEAD:

- The fee-note page is a server component that fetches the fee note once (`invoices/[id]/page.tsx:51`) and hands it to `InvoiceDetailClient` → `useInvoiceDetail`, which holds it in `useState(initialInvoice)` (`use-invoice-detail.ts:39`) and replaces it with `setInvoice(result.invoice)` after every action — approve, send, record payment, void, line edits (`:126…319`) — **without** a server re-render.
- The h1 (the number, or "Draft fee note") lives in the client `InvoiceHeaderActions` (`:47`); `DetailHero` has no `"use client"` and renders inside client header cards (`detail-hero.tsx:55-59`).
- Every figure the strip needs is already on the page for the same viewer: "Total", "Amount paid", "Balance due" (`invoice-totals-section.tsx:47,55,59`), "Due:" (`invoice-header-actions.tsx:54`), and the days-overdue strings "{n}d overdue" / "Due today" / "{n}d until due" (`collections-history-table.tsx:138-142`). `invoice-balance-due` and `totals-balance-due` are asserted by e2e and vitest.
- The page gate is `INVOICING` or admin/owner (`:43-44`); nothing on the page is redacted further.

**Options Considered**:

1. **A server-only `invoices/[id]/hero-tiles.ts` loader (the ADR-373 pattern).**
   - Pros: Same shape as matters and clients; `tileFrom` reused.
   - Cons: The tiles would show the pre-action Paid and Balance after "Record Payment" while the totals section and the part-payment indicator below show the new values — two balances on one screen that disagree until a reload. `tileFrom` takes a `WidgetResult`, which a single fee-note read is not.
2. **The server loader plus `router.refresh()` after every action.**
   - Pros: Keeps the server pattern and makes it consistent.
   - Cons: Changes the actions' flow (an extra server round trip and full refetch of nine reads after every edit), exposes the page to the next 16.2.6 stalled-refresh behaviour, and is a behaviour change in a visual phase.
3. **A pure mapper `feeNoteHeroTiles(invoice, todayIso): HeroTile[]` in `lib/fee-note-hero.ts` (no `server-only`), called by `InvoiceHeaderActions` on the same `invoice` state the totals use; `todayIso` computed once on the server and passed down (chosen).**
   - Pros: Hero, totals and indicators are always the same object, so they cannot disagree; no new read and no extra round trip; the mapper is unit-testable with a fixed date; no hydration mismatch near midnight because the client never computes "today".
   - Cons: A second way of building hero tiles in the codebase; `overdueHint` and `tileFrom` are not reused (they do not fit a single object anyway).
4. **No strip; restyle the header only.**
   - Pros: Smallest change.
   - Cons: Misses the requirement and leaves the fee note the only detail page without the KPI strip.

**Decision**: Option 3. `InvoiceHeaderActions` composes `DetailHero` with `title` = today's h1 children (no identifier chip — the number already is the title), `subtitle` = today's meta row, `status` = the existing `StatusBadge`, `actions` = today's buttons, and `tiles = feeNoteHeroTiles(invoice, todayIso)`:

| Tile | Label | Value | Shown when |
|---|---|---|---|
| `total` | "Total" | `invoice.total` | always |
| `paid` | "Amount paid" | `invoice.amountPaid ?? 0` | SENT, PARTIALLY_PAID, PAID |
| `balance` | "Balance due" | `invoice.balanceDue ?? invoice.total` | SENT, PARTIALLY_PAID, PAID |
| `due` | "Due" | the formatted due date, hint `formatDaysOverdue(days)` with the `--danger` icon when overdue, only while a balance is owed | not a draft, `dueDate` present |

`DetailHero.title` is widened from `string` (`components/shared/detail-hero.tsx:25`) to `ReactNode`, because the fee-note h1 contains a `HelpTip`; this is backward compatible (matters and clients pass strings) and E6B re-runs their header tests and adds a `ReactNode`-title case. `formatDaysOverdue` exists as two identical private copies (`invoices/collections/collections-client.tsx:31-35`, `collections-history-table.tsx:138-142`); they become one `lib/format.ts` export (E6A moves it, E6B repoints the history table). Tile testids are new (`fee-note-hero-tile-<id>`); `invoice-balance-due` and `totals-balance-due` are never duplicated. The strip repeats labels the totals block shows lower on the page, so text locators on those labels become ambiguous: E6B scopes `invoice-detail.test.tsx:302` `getByText("Amount paid")` with `within(…)` to the totals block, greps `frontend/e2e` for unscoped "Total", "Amount paid", "Balance due" and "Due" locators, and keeps "Subtotal" out of the hero (e2e `feeNoteTotalsRow` anchors on it, `helpers/legal-lifecycle.ts:494-497`). The labels' placement, the repetition and the dropped identifier chip were approved by founder decision FD-5 (ruled 2026-11-22, as recommended).

**Rationale**:

ADR-373's rule is about where data comes from (existing reads, gates respected) and how a failing gate renders (omitted). Where the data lives is a property of the page: matters and clients hold header data on the server, the fee note holds it on the client. Computing the tiles where the state is keeps the strip consistent with the rest of the screen without changing any action's flow, and the only server-provided input, the business date, is passed down to keep server and client render identical.

**Consequences**:

- Positive:
  - The fee note gets the same hero as matters and clients; after any action the strip, the totals and the part-payment indicator agree.
  - No new endpoint, read or server action; the strip shows only figures the viewer already sees on the page.
- Negative:
  - Two tile-building idioms (server loaders for matters/clients, a client mapper here); a future detail page must pick by where its state lives (written down here).
  - `todayIso` is fixed at page render; a tab left open across midnight shows yesterday's "days overdue" until the next navigation (acceptable; same as every server-rendered date in the app).
  - If fee-note amounts ever become redacted for some viewers, the totals section and the strip must be redacted together.
  - The hero and the totals block show the same labels twice on one page; tests must locate them by scope or testid, never by bare text.

**Related**: [ADR-373](ADR-373-detail-hero-kpi-strip.md) (DetailHero and tile rules), [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md) (`amountPaid`, `balanceDue`, due dates), [ADR-367](ADR-367-design-system-layering.md) (DetailHero and the mapper stay in `frontend`); [architecture §18.3.6, §18.10 X1/X2/X3, FD-5](../architecture/phase91-ui-modernization-ii.md); slice E6B.
