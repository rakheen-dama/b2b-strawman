# 646D OBS — observations (reported, not fixed)

Nothing here is a defect in 646D's scope. Each item says what was observed and who should own it.

## O-1 — A future fee-transfer date is refused by the browser's own check, not by the app's sentence

- **Observed** (stack #1, OBS d, `obs/04-d1-dialog-crop.png`): typing 2026-11-18 into "Fee transfer date (optional)"
  (`max="2026-11-17"`) and clicking Record Fee Transfer shows Chromium's native constraint bubble
  **"Value must be 2026/11/17 or earlier."** (`validity.rangeOverflow = true`). The dialog stays open and nothing is
  created (FEE_TRANSFER count 2 → 2).
- The app's own sentence, "The fee transfer date cannot be in the future" (zod refine in `lib/schemas/trust.ts`), is
  **not rendered**. The `<form>` is not `noValidate`, so the native check blocks the submit event before
  react-hook-form validates (`aria-invalid` stays `false`).
- The server returns the app's sentence. The supplementary API probe got 400, "Invalid fee transfer" / "The fee
  transfer date cannot be in the future", and created nothing.
- Compare 646C: the reconciliation period-end input shows the app's sentence ("The period end cannot be in the
  future"), because there the Next button is not a form submit.
- **Impact:** low. The refusal is client-side and visible, but the wording and placement are the browser's, not the
  app's, and the zod refine is unreachable on this path.
- **Suggested owner:** orchestrator/UX call. Either accept the native bubble, or add `noValidate` to the dialog's form
  so the app sentence shows under the field (a one-line change). The other trust dialogs with `type="date"` + `max`
  may share this.

## O-2 — Under host load, the trend chart was screenshotted mid-draw (not a defect)

- In stack #1, both OBS overview screenshots (`02-…`, `09-…-trend-crop.png`) show the "Trust balance trend" axes and
  legend ("Cashbook", "Trust creditors") but no series line. The host load average was about 100.
- The chart's sr-only summary in the same DOM read the right figures, for example "Trust creditors from R 0,00 in
  Dec 2025 to R 287 350,00 in Nov 2026 (to date)". So did the API.
- **Supplementary probe** (fresh stack #3, `obs-trend-probe.cjs`, `obs/trend-probe/`):
  1. Alice recorded two deposits through the UI: R 2 000 dated 2026-10-20 and R 5 000 dated today.
  2. The recharts clip rect was 427/1018 px wide at 0 ms, the full 1018 px at 1 s, and removed by 3 s.
  3. From 3 s on, the series are drawn: flat at R 0 to Sep 2026, R 2 000 at Oct 2026, then dashed to R 7 000
     (to date) at Nov 2026 (`trend-15000ms.png`).
  4. API months 2026-10 = 2 000 / 2 000 and 2026-11 = 7 000 / 7 000 (partial) match the SQL month-ends.
- **Conclusion:** the stack #1 shots caught the draw animation. Not a defect, and 646D only renamed the series label.

## O-3 — The first OBS run hit two `trust-position-widget` elements for a moment (harness, not reproduced as user-visible)

- The first OBS run (`obs/obs-output-run1-fatal.log`) failed at its first locator: a Playwright strict-mode
  violation, with 2 elements for `trust-position-widget` right after navigating `/dashboard` → `/dashboard`.
  Mock login lands on the dashboard, and the script navigated there again.
- A separate probe, and both later states, counted exactly **1** widget once the page settled (`widgetCount: 1`).
  This is consistent with the old and new RSC trees briefly coexisting during the `animate-in` route transition.
- The failed run changed no state (trust txn count 6 before and after). The script now waits for a single widget.
- **Suggested owner:** none (harness note). If it ever shows up for a user, Phase 91 UI owns it.

## Known limit (restated, not new)

Before 647A, no product path posts a non-client creditor row. So in the browser, the trust-creditor total equals
the client-ledger total on every surface:
- investment / Fund ledger / unidentified / other / inter-account are all 0 in every API breakdown here;
- the non-zero non-client figures are proven in tests only (builder R1–R3).

F-646D-1 (no lower bound on the fee-transfer date) was **not exercised**. OBS deliberately dated the fee transfer
after the client's deposit, so the client ledger is never in debit as at any date (`obs-sums.out`, last block).
