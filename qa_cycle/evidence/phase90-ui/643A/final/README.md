# 643A final OBS: PR #1775 at 859d257d2

Stack: the mock-auth E2E stack (:3001/:8081), rebuilt fresh from worktree-epic-643A after the legal suites ran. The built bundle contains the 859d257d2 markers (`@6xl`, `overflow-wrap:anywhere`, `usePlotArea`). I did not rebuild or reseed it, and made no SQL writes.

Browser: Node scripts using `@playwright/test` chromium (nvm node 20, headless), signed in through `/mock-login` as Alice (or Carol via `#user-select`). Dark mode was set with `localStorage kazi-theme=dark` plus `colorScheme: dark`, and the `<html>` class was confirmed `dark`. Full-page shots grow the viewport to the document height and wait 3.5 s, because Playwright's fullPage capture restarts the chart animation.

## Scripts (run in this order, saved exactly as run)
1. `probe.cjs` → `probe-raw.json`: read-only APIs. Before the wizard there were no reconciliations.
2. `drive.cjs` → `drive-raw.json`, `summary-cards-pre-recon-1440.png`, `recon-step4-crash-1440.png`. It drives the UI wizard: Period End 2026-11-13, upload `statement-2026-11-13.csv` (9 lines), Run Auto-Match (8 matched), Review Matches. This created DRAFT `25bd2264…`, then the known step-4 crash appeared.
3. `observe.cjs` → `observe-raw.json` plus the screenshots below. It covers values, chart, table toggle, tooltip, keyboard, D1/D2 at 390/768/1024/1280/1440, dark mode, Carol and Home.
4. `legend-probe.cjs` → `legend-probe-out.json`: D3 legend, end labels and plot right edge at 1024/1440 in light and dark.

Compact results are in `final.json`.

## Verdicts
1. Reconciliation: **PASS.** Before: "Not yet reconciled / No reconciliation records found". After: "Unbalanced / Period to 13 Nov 2026, in draft", which matches trust-position (DRAFT, balanced=false, 2026-11-13). Home shows the same text, and so does Carol's tile.
2. Equalities: **PASS.** All of these are R 290 249,98: the tile, `cashbookBalance`, `/cashbook-balance`, and the table's last-row cashbook. All of these are R 290 275,50: "Client ledgers", `/total-balance`, and the last-row ledger. "Difference -R 25,52" is shown. The table has 12 rows ending with "Nov 2026 (to date)". There are 0 tables before the toggle and 1 after, with `aria-expanded` false then true.
3. D1: **PASS.** At 1024, 1280 and 1440 the table's scrollWidth equals its scroller's clientWidth (670/926/1086), and all 5 headers sit inside the card. At 390 and 768 the table scrolls inside the card (611 > 308/414 px); AMOUNT and STATUS are reached by that scroll. There is no page-level scroll at any width.
4. D2: **PASS.** All 4 values fit (scrollWidth = clientWidth) at every width, with 0 icon overlaps against value, label or hint. Columns: 1 at 390 and 768, 2 at 1024, 1280 and 1440 (2×2 at 1440 is the accepted ruling).
5. D3: **PASS.** The direct end labels are absent at 1024 and 1440 in both themes, because the end points are 25.52 apart (a collision). The legend shows "Cashbook | Client ledgers". The plot runs to 12 px from the SVG edge, so the 88 px margin is gone. The last-point tooltip reads "Nov 2026 (to date)" at 1024, 1280 and 1440, light and dark.
6. Screenshots: **PASS.** `document.scrollWidth` ≤ `innerWidth` at every width and theme.
7. Console: **PASS.** 0 errors, warnings or hydration messages across 27 observe loads and the legend probe. The only error was the known step-4 `TypeError … 'length'` in drive.
8. Keyboard: **PASS.** Tab ×28 reaches the chart `svg`, which shows a 2 px solid teal outline. ArrowRight and ArrowLeft move the tooltip (Jan → Feb → Jan), and ArrowRight ×12 ends on "Nov 2026 (to date)".

## Notes (not blocking the D1–D3 criteria)
- **N3:** `overflow-wrap:anywhere` lets the Reference column shrink to about 150 px at 1024 and about 70 px at 390/768. Short references break mid-token ("FEE/SIPHO/mwed3f|drbc50"), and the INT-CREDIT row is 8 lines tall at 1024 and 16 lines at 390/768. At narrow widths the table still scrolls sideways anyway. A min-width on the Reference column would keep short references whole (`trust-dashboard-1024-light.png`, `trust-dashboard-768-light.png`, `trust-dashboard-390-light.png`).
- **N4:** when the two balances are nearly equal (the normal trust case), the Cashbook line is completely hidden under Client ledgers. The legend and tooltip still identify it (`chart-tooltip-last-1440-light.png`).
- **N1 (unchanged):** the last x-axis tick reads "Nov 2026" without "(to date)".
- **N2 (pre-existing, not in the diff):** at 768 the PageHeader description is squeezed into a narrow column, and the shell's breadcrumb "Organization" runs under the Search button.
