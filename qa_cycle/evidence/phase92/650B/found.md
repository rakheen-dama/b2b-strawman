# 650B found — OBS gate

The slice's behaviour matched the acceptance criteria on every check (a–i), including save, correct, the
locked month and no balance moves. There is one readability finding in the new memo table, F-OBS-1. It was
fixed in `c0c3a69df`.

- **F-OBS-1 — LOW (cosmetic, 650B) — FIXED in `c0c3a69df` — the memo table's Source column is squeezed at 1280 px and breaks words
  mid-word.** At a 1280 px viewport, the Source cell of "Interest and charges at source" is **61 px wide**.
  The `[overflow-wrap:anywhere]` cell class then splits ordinary words across lines: "correcte|d" and
  "statemen|t". The probe measured these breaks (`gates/obs/result-h.json` → `widths.1280.sourceCells[*].brokenWords`
  = `["corrected"]`, `["statement", "re-issued"]`; the "re-issued" break is the normal one at the hyphen).
  A short source note becomes a 7–8 line column, and the "List locked" badge also wraps onto two lines.
  At 1440 px the cell is 170 px wide and no word breaks. No text is clipped at either width: the clip
  probe passes with 0 bad elements of 29. Likely cause: the three money column headers are long and do
  not wrap ("GROSS INTEREST (AT SOURCE)", "GROSS CHARGES (AT SOURCE)", "VAT IN THE CHARGES"), so they
  take the width. The free-text column gets what is left.
  Reproduce: lock nothing, record any memo with a two-word-plus note, then open Trust › Other trust
  creditors at 1280 × 1000 (`gates/obs/h-table-1280.png` vs `gates/obs/h-table-1440.png`).
  Possible fix directions (product/orchestrator call): shorter or wrapping money headers, or a `min-w`
  on the Source column.
  **Fix (`c0c3a69df`):** the money headers wrap; Source uses `break-words` instead of `[overflow-wrap:anywhere]` and has
  `min-w-40`. Re-probe (`gates/README.md` "Re-probe after fix c0c3a69df"): Source is 220 px at 1280 and 262 px at
  1440, with 0 mid-word breaks and 0 clipping.

Observations (pre-existing or harness, outside the slice; reported, not fixed here):

- **O-OBS-1 — harness, not product: run 1 false positive.** The first OBS run used the source note
  "FNB interest advice IA-2026-09". The raw-code regex's ISO-date arm (`\d{4}-\d{2}`) matched it, so c, e
  and h failed on the test's own data. The product showed nothing raw. The note was changed to "IA/SEP26",
  the stack was wiped, and run 2 is the result of record (`gates/obs-log-summary.txt`).
- **O-OBS-2 — no server-side memo refusal is reachable from the UI.** The client schema refuses VAT above
  the charges, a future month, negative amounts, more than two decimals, a blank source note and more than
  500 characters before the request is sent. It mirrors D-7, and the month input also has `max`. The
  server's own refusal sentences ("The VAT cannot be more than the gross charges it is included in.",
  "A memo can be saved for this month or an earlier one, not for …") therefore never reach the dialog in
  normal use. They are covered by `TrustInterestMemoIntegrationTest`. The dialog's inline error path was
  observed with an aborted request instead ("An unexpected error occurred", g).
