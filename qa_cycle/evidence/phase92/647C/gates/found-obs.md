# 647C gates — found items (reported, not fixed)

Gate run on PR #1795 @ 6e485f412, 2026-11-17. **One defect in 647C's scope (F-1, visual).** No source was edited.

## F-1 (647C scope, UX/visual): the Fund ledger's Running balance column is pushed out of view at common laptop widths

- **Where:** `frontend/components/trust/FundLedgerTable.tsx` (new in 647C), rendered by
  `app/(app)/org/[slug]/trust-accounting/other-creditors/page.tsx` in the `fund-ledger-section` card.
- **Observed:**
  - At a 1440×1100 viewport, the last column's header reads "RUNNING BALANC" and the balances read "R 172,5",
    "R 115,0", "R 57,5" and "R 1 234,5". See `obs/20-f-after-unmatch-other-creditors.png`,
    `obs/20-f-after-unmatch-fund-ledger-crop.png` and `obs/15-d-*`.
  - At 1280×1100 the **whole Running balance column** and part of "Recoverable · VAT R 7,50" are off-screen
    (`obs/22-probe-fund-ledger-1280-crop.png`).
  - At 1920 it fits (`obs/22-probe-fund-ledger-1920-crop.png`).
- **Measured** by `probe-clip.cjs`, a read-only probe; see `obs/probe-clip.json`:

  | Viewport | `<table data-testid=fund-ledger-table>` width | `div.relative.w-full.overflow-x-auto` clientWidth | Hidden |
  |---|---|---|---|
  | 1280 | 1 102 px | 926 px | 176 px (the Running balance column and part of Bank charge) |
  | 1440 | 1 102 px | 1 086 px | 16 px (the last digit of every balance, and the header's "E") |
  | 1920 | 1 182 px | 1 182 px | 0 |

  The cells themselves are not truncated: each `td` has scrollWidth = clientWidth = 141, and the text is
  "R 172,50". The table is simply wider than its `overflow-x-auto` wrapper, so the right edge is reachable only by
  horizontal scrolling. On macOS the overlay scrollbar gives no cue that it is there.
- **Why it matters:** the running balance is the slice's headline figure (§16.4.2). At 1440 a reader sees
  "R 172,5", a plausible-looking wrong number, and at 1280 sees no balance at all.
- **Not caught by the data checks.** `obs.cjs` reads `innerText`, which includes off-screen text, so the (d) and (f)
  figure checks pass. The defect is only visible in the screenshots.
- **Likely contributors** (not verified by an edit):
  - nowrap content in the Type badges (e.g. "Interest received for the Legal Practitioners' Fidelity Fund");
  - the Amount and Bank charge cells;
  - seven columns inside a padded card, beside the 255 px sidebar.
- **Suggested owner:** 647C, before merge, or the orchestrator's call. It is a layout fix in `FundLedgerTable` (e.g.
  let the type badge wrap, or move charge detail under the type), plus a check at 1280/1440.

## Observations (not defects)

- **O-1 (spec behaviour, observed live).** The statement line "TRUST TO BUSINESS FEE TRANSFER" (−8 500.00, the
  lifecycle's Moroka fee transfer) was stored with `suggested_classification = BANK_CHARGE` at import, because
  "FEE" is a debit keyword. Auto-match then matched it by reference to `FEE/MOROKA/mwkqkcdh76db` and cleared the
  suggestion (OBS b). If such a line had *not* auto-matched, the dialog would pre-select "Bank charge" for a fee
  transfer. This is the substring false positive already recorded in `../found.md` #3; the firm must still confirm.
- **O-2 (a11y, low, product call).** The Fund ledger's Amount column prints unsigned amounts ("R 57,50" for both the
  refund in and the charge out). Direction is shown only by colour and an `aria-hidden` arrow icon (`data-direction`
  carries it in the DOM). A screen-reader user hears the same text for money in and money out. The Running balance
  disambiguates.
- **O-3 (harness).** `tree-before.txt` was taken after the `gates/` dir was created, as in 647A. Playwright
  `fullPage` screenshots draw the fixed sidebar mid-page; the element crops are exact.
