# Slice 626F: "before" reproduction (e2e mock-auth stack, 2026-10-28 ~01:12–01:18 UTC)

Stack: frontend :3001 (626C-equivalent image), backend :8081 (626E). Alice (owner), tenant `tenant_7d218705360b`. Viewport 1280x800.
Matter M2 `2cb472bd-ab3e-41f3-8ad8-91ae08a7ff2a` (ACTIVE, "626E draft fee note repro"). Draft fee note `852969e7-8cc1-4bca-83dd-7de636e74d8d` (ZAR 4657.50).

| # | What I did | What I saw | Verdict |
|---|---|---|---|
| R1 (OQ #1 / G-C4) | M2 → "Generate Statement of Account" → Preview & Save (internal draft). Sampled every 500 ms for 5 s. Then set `form.style.minWidth='0'` and sampled again. Then Generate Document → "Project Summary Report" and sampled again. | **SoA, before:** the grid column (`gridTemplateColumns`) and the `<form>`/a4-wrapper width grow without bound: form 62954 → 65690 → 68618 → … → 90842 px over 5 s (~+2.7–2.9k px per 500 ms, i.e. +48 px per ResizeObserver frame). The a4 outer `style.width` stays exactly `wrapper.clientWidth − 48` behind (62906 px at the 62954 sample). The grid column had reached 177098px when I inspected the DOM. Dialog shell stays 512 px; `documentElement.scrollWidth` stays 1280 = innerWidth (no page-level overflow; the dialog clips it). Download PDF button at x=63073…90961, y=771 (bottom 807 > 800); `elementFromPoint` at its centre = null, so it can't be clicked. Period end input and right half of the content are clipped off-dialog (02). **SoA with `min-width:0` on form:** stable at grid col 462 px, form 462, wrapper 462, a4 outer 462 px; preview container scrollWidth 486 vs clientWidth 462, so the paper still overflows 24 px on the right (03). Download button at (581,807) bottom 843: below the fold until the dialog grid is scrolled (scrollTop 108); after scrolling it is at (581,699)–(733,735) and `elementFromPoint` hits the BUTTON. **Generate dialog:** stable for all 11 samples: grid col 462, scroll item 462 (scrollW 486, so the same 24 px overflow), a4 outer 462 px; Download PDF at (529,647)–(681,683), and `elementFromPoint` hits the BUTTON (04). | REPRODUCED (runaway feedback loop) |
| R2 (fee-note preview 409 copy) | Fee note detail → Preview (opens `/api/invoices/{id}/preview` in a new tab). Then tried to get a 409 `invoice.preview.no_template` without SQL. | **Working preview:** renders legal-za v9 fee note with logo (`data:image/png` img, 120 px natural width), letterhead (address, Tel, Email, Web, VAT reg 4123456789), "DRAFT — not yet issued" banner, banking details. Money format inside the template is `R2 500,00`: no space after R, U+00A0 thousands separator, comma decimal. Other amounts: R562,50 / R4 312,50 / R300,00 / R45,00 / R345,00 / Subtotal R4 050,00 / VAT R607,50 / Total Due R4 657,50 (07). **409:** could not be triggered without SQL. (a) `resolveDefaultInvoiceTemplate()` for legal-za uses `findPackSeeded("legal-za","fee-note-za")`, which does not filter on `active` (DocumentTemplateRepository.java:45-52), so deactivating the template would not produce the 409 (inferred from code, not observed). (b) There is no reactivate path in the UI or API (only `DELETE /api/templates/{id}` = deactivate; TemplateActionsMenu shows "Deactivate" only while active), so deactivating would have needed SQL to undo. I did NOT deactivate. **Error rendering (observed with a 404):** the FE proxy `app/api/invoices/[id]/preview/route.ts` passes the backend body through unchanged. Browsing `/api/invoices/00000000-0000-4000-8000-000000000000/preview` shows a bare `text/plain` page with the raw ProblemDetail JSON `{"detail":"No invoice found with id …","instance":"…/preview","status":404,"title":"Invoice not found"}` (08). A 409 would render the same way, as raw JSON in a new tab. | PARTIAL (raw-JSON error page REPRODUCED via 404; the 409 itself NOT REPRODUCED, unreachable without SQL on legal-za) |
| R3 (tariff currency) | Fee note draft → "Add Tariff Items" (TariffLineDialog), selected 2(a) to see the summary, then Close (nothing added). Also checked /legal/tariffs (TariffItemBrowser). | Dialog: `R 780.00`, `R 1250.00`, `R 195.00`, `R 7800.00`, `R 4680.00`, `R 260.00`, `R 390.00`. Selected summary: "2(a) - Drawing of summons R 1250.00 … 1 item · R 1250.00". The button reads "Add to Invoice" (09, 10). Tariffs page: same `R 1250.00` format (11). Source: `tariff-line-dialog.tsx:24` and `tariff-item-browser.tsx:21`, both `` `R ${amount.toFixed(2)}` ``. For contrast, the same fee note page renders lines as `R 2 500,00` (a11y snapshot). | REPRODUCED |
| R4 (G-B6 "Not saved") | M2 → Generate Document → Project Summary Report → **Download PDF** (download-only, `saveToDocuments=false`). Checked Work › Generated documents, Work › Documents, the API and SQL. | New `generated_documents` row `b91daaa4-…` with `document_id = NULL`. In Generated documents it renders like a saved row ("Project Summary Report · Alice Owner · 28 Oct 2026 · 1.1 KB · PDF"), with no badge marking it as not saved. The only difference from the saved SoA row is the missing "Share with client" action; "Send for Acceptance" and "Delete" are still offered (06). Work › Documents lists only the saved SoA PDF. API returns `documentId: null, visibility: null` for it (r4-generated-documents-M2.json). Tenant-wide: INVOICE 12 rows / 0 saved / 12 not saved; PROJECT 31 / 17 / 14 (r4-generated-documents-sql.txt). | REPRODUCED |

## DOM chains (dialog content → a4-preview-wrapper)

SoA (`statement-of-account-dialog.tsx`):
1. `div[data-slot=dialog-content][data-testid=statement-of-account-dialog]`: fixed, `w-full max-w-[calc(100%-2rem)] sm:max-w-lg`, block (512 px)
2. `div.bg-background.grid.gap-4.rounded-xl.border.p-6.shadow-lg.max-h-[90vh].max-w-3xl.overflow-y-auto`: grid, overflow-x auto, gridTemplateColumns grew to 177098px
3. **`form.space-y-4`**: block, overflow visible, **min-width: auto**. This is the grid item.
4. `div[data-testid=statement-preview-container].max-h-[50vh].overflow-y-auto`: block, overflow auto
5. `div[data-testid=a4-preview-wrapper].rounded-lg.bg-slate-800.p-6`

Generate Document (`GenerateDocumentDialog.tsx`):
1. `div[data-slot=dialog-content]`: same shell, 512 px
2. `div.bg-background.grid.gap-4.rounded-xl.border.p-6.shadow-lg.max-w-3xl`: grid, overflow visible, gridTemplateColumns 462px
3. **`div.max-h-[60vh].space-y-4.overflow-y-auto`**: block, overflow auto (a scroll container, so its automatic minimum size is 0). This is the grid item.
4. `div[data-testid=a4-preview-wrapper].rounded-lg.bg-slate-800.p-6`

## OQ #1 hypothesis verdict: HELD

The hypothesis was: the SoA grid item is a `<form>` whose min-content contribution grows, while the Generate dialog's grid item is an overflow-y-auto div.
- `A4PreviewWrapper` sets `scale = clientWidth / 794`, and `clientWidth` includes the wrapper's own `p-6` padding (48 px). The scaled box therefore always comes out 48 px wider than the content box, which overflows by 24 px on the right.
- In the SoA dialog the grid item is the `<form>` (overflow visible, `min-width:auto`). Its min-content therefore includes that too-wide box, so the auto grid track widens by 48 px. The wrapper then widens, ResizeObserver fires, the scale goes up and the box widens by another 48 px, without end.
- In the Generate dialog the grid item is a scroll container, so it contributes a 0 automatic minimum. The track stays 462 px and the loop never starts. Only the 24 px overflow remains.
- Setting `min-width:0` on the SoA form stopped the growth immediately: 223946 px → 462 px within one sample, stable for 5 s.

## Surprises

- Both dialogs pass `max-w-3xl` to the inner div, but the outer Radix/motion shell has `sm:max-w-lg`, so both render 512 px wide, not 768.
- After `min-width:0` both dialogs still overflow the preview by 24 px. The padding/clientWidth bug in `a4-preview-wrapper.tsx` is the underlying cause, and the form `min-width:auto` amplifies it into a loop.
- The fee note preview's 409 `invoice.preview.no_template` is effectively unreachable for legal-za tenants. `findPackSeeded` ignores `active`, so only a missing platform `fee-note-za` row triggers it.
- No template reactivate path exists (UI or API). "Deactivate" on a platform template is one-way without SQL.
- An unsaved generated document still offers "Send for Acceptance".
- The tariff dialog CTA says "Add to Invoice" (legal terminology elsewhere is "fee note").
- There are three different ZAR formats in play: app `R 2 500,00`, template `R2 500,00`, tariff `R 1250.00`.
- The `01-soa-dialog-open.png` screenshot failed (the MCP output-root restriction), so the evidence starts at 02. Screenshots were taken via `.playwright-mcp/` and moved here. The raw MCP snapshots and console logs from this run are in `mcp-raw/`.

## State changed

| Change | Restored? |
|---|---|
| Generated an SoA on M2 via the dialog (internal draft, not shared, no email): `generated_documents` `69495796-f631-42eb-91a5-fae049a2242f` plus `documents` `cddad203-412c-42a9-9475-e4e14b155adf` ("statement-of-account-626e-draft-fee-note-repro-2026-10-28.pdf", INTERNAL) | NO. Needed to render the A4 preview (it only exists after generation). Can be deleted via the Generated documents row "Delete" if required. |
| Download-only generation on M2: `generated_documents` `b91daaa4-f5d0-4343-bfb7-7a32dbb2cf49` (Project Summary Report, `document_id` NULL) | NO. This is the R4 fixture, kept for the after-run. |
| DOM-only `min-width:0` on the SoA form | N/A (in-page, discarded on close) |
| Tariff dialog: selected one item, then Close | Nothing saved; fee note still has 2 lines (SQL count) |
| Templates: none deactivated | n/a |
