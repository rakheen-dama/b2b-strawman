# 647C gates round 2 — found items (reported, not fixed)

Gate run on PR #1795 @ df8550113, 2026-11-17. No source was edited. Round 1's items are in `../gates/found-obs.md`.

## F-1 (647C scope, visual): Fund ledger Running balance clipped / off-screen — **FIXED** (observed)

- **Fix under test:** df8550113 changes only `frontend/components/trust/FundLedgerTable.tsx`:
  - the Type badge gets `className="shrink whitespace-normal"`;
  - the Bank charge column gets `cellClassName: "whitespace-normal"`;
  - "VAT R x" sits in a `whitespace-nowrap` span.
- **In the build:** `stack1-proof.txt` and `stack2-proof.txt` show the fix in both the SSR chunk
  (`frontend_17gbpfc._.js`) and the static chunk (`3zibpceod4jyz.js`):
  - `Badge,{variant:"neutral",className:"shrink whitespace-normal",children:e.original.typeLabel}`
  - `{id:"charge",meta:{label:"Bank charge",cellClassName:"whitespace-normal"}`
  - `" · ",(0,t.jsxs)("span",{className:"whitespace-nowrap",children:["VAT ",…]})`

  The compiled CSS contains a `.shrink{` rule.
- **Measured** by `probe-clip.cjs` (read-only) on stack #1, after OBS run RQH2F, with the Fund ledger at 5 rows
  (`obs/probe-clip.json`). The "visible right" is the wrapper's content box intersected with every clipping ancestor
  and the viewport; no ancestor clips.

  | Viewport | Table offsetWidth | Wrapper (`relative w-full overflow-x-auto`) clientWidth / scrollWidth | Wrapper visible right | "RUNNING BALANCE" header right | Max balance right (span / td) | Page horizontal overflow |
  |---|---|---|---|---|---|---|
  | 1280×1100 | **926** | 926 / 926 | 1231 | **1231** | **1223 / 1231** | none (1280 = 1280) |
  | 1440×1100 | **1086** | 1086 / 1086 | 1391 | **1391** | **1383 / 1391** | none |
  | 1920×1100 | **1182** | 1182 / 1182 | 1679 | **1679** | **1671 / 1679** | none |

  Round 1 for comparison: table 1 102 px against wrappers of 926 / 1 086 / 1 182 px.
  The table is `w-full`, so it now fills its wrapper exactly instead of exceeding it. The static estimate of
  ~782 px is its content minimum, not its rendered width.
- **Checks** (all true at all three widths, `probe-clip.json → widths[].checks`):
  - `tableFitsWrapper`
  - `balanceHeaderInsideVisible`: the header text is "RUNNING BALANCE", on 1 line
  - `everyBalanceInsideVisible`: 5 of 5 rows
  - `balanceOneLine`
  - `amountOneLine`
  - `badgesReadable`: every badge is inside the visible box, scrollW ≤ clientW and scrollH ≤ clientH under the
    badge's `overflow-hidden`, and there is no ellipsis
  - `noPageHorizontalOverflow`
- **Crops** (`obs/22-probe-fund-ledger-{1280,1440,1920}-crop.png`, `-rightcols.png`, and the OBS crop
  `obs/20-f-after-unmatch-fund-ledger-crop.png` at 1440):
  - every figure is shown in full: "R 172,50", "R 115,00", "R 57,50", "R 115,00", "R 1 234,56";
  - the header reads "RUNNING BALANCE" in full;
  - the Amount figures are on one line, with their arrow icons.
- **Type badges, once wrapped:**

  | Badge | Wrap |
  |---|---|
  | Long Fund labels (e.g. "Interest received for the Legal Practitioners' Fidelity Fund") | 3 lines at 1280 (160 px wide), 2 lines at 1440 (228 px) and at 1920 (269 px) |
  | Short badges ("Bank charge", "Reversal", "Reversed") | 1 line |

  The badges break only between words (`overflow-wrap: normal`, `word-break: normal`) and stay legible in the crops.
  The badge keeps `rounded-full`, so a 3-line badge at 1280 is a rounded pill; this is cosmetic.
- **"Recoverable · VAT R 7,50"** now wraps to 2 lines ("Recoverable ·" / "VAT R 7,50") at all three widths. "VAT
  R 7,50" stays together, as the fix intends.
- **Probe run 1 vs run 2:** run 1 (`obs/probe-clip-run1.json`, `obs/22-probe-run1-*`) reported
  `amountOneLine: false` everywhere. That was a probe artefact. The Amount cell is an `inline-flex` span holding an
  `aria-hidden` arrow svg plus the text, and run 1 counted the svg's rect as a second line top. The amount text
  itself uses non-breaking spaces. Run 2 counts text nodes only, and every Amount is 1 line. All other run 1
  measurements are identical to run 2.

**Verdict: F-1 FIXED at 1280, 1440 and 1920.**

## New items

### N-1 (NOT 647C scope, intermittent): depth-ii run 1 — matter Time tab showed "No time tracked yet" after logging 2h

- **Test:** `day-30-45-soa-writeoff.spec.ts:227`, "[setup] Alice (Thandi): Log 2h on Sipho's matter — letter of
  demand", in run 1 of depth-ii on stack #2. Run 1 summary: 32 passed / **1 failed** / 4 skipped / 7 did not run
  (the rest of the serial group).
- **Error:** `expect(getByRole('tabpanel')).toContainText(/Letter of demand\s*2h/)` timed out after 15 s. The panel
  read "From To No time tracked yet Log time on tasks to see matter time summaries here"
  (`depth-ii-run1-failure/test-failed-1.png`, `error-context.md`).
- **The write succeeded.** The backend logged `TimeEntryService Created time entry 64ac7a41… for task 3af9d86f…` at
  14:18:35.70 UTC. Read-only SQL shows the entry: 120 min, dated 2026-11-17, on task "Letter of demand" of
  "Ndlovu v RAF — Depth II mwks0u2q90e6". So the Time tab's summary was **stale**, not empty.
- **Re-run, once.** The spec cannot be re-run alone on the same stack at retry 0: it would add a second rate override
  over the first and find 4h, not "2h". It also needs Day 5's run state. I therefore re-ran
  `day-05-disbursements.spec.ts`, its declared prerequisite, which starts a fresh run id, together with
  `day-30-45-soa-writeoff.spec.ts` on the same stack. Result: **20 passed / 1 skipped / 0 failed**. The skip is the
  baseline fixme `:361`. The same test passed in 4.3 s (`depth-ii-rerun.log`, `depth-ii-rerun-summary.txt`).
- **Touches 647C?** No. It concerns the matter Time tab and time tracking. df8550113 changes only
  `FundLedgerTable.tsx`, which renders only on Other trust creditors. 647C's other surfaces are the statement parser,
  recon record dialog and Fund ledger API.
- **Is it new?** Round 1 at 6e485f412 ran this test green.
- **Hypothesis, not verified:** it fits the known next@16.2.6 lost-React-ping stall (memory note
  `next_16_2_6_lost_react_ping`), where a `revalidatePath` after an action is not picked up for up to ~30 s,
  longer than the test's 15 s wait.
- **Suggested owner:** the orchestrator decides whether to log it against the time-tracking/Next.js flake backlog.
  The full depth-ii count for this round is NOT a clean 40/4 (see README).

### O-4 (low, cosmetic, predates df8550113): Reference values break mid-token

The Reference column has `cellClassName: "whitespace-normal [overflow-wrap:anywhere]"`. That line is unchanged by
df8550113. References such as `RFD/OBS647C/RQH2F` therefore break wherever the column edge falls.

| Width | Break |
|---|---|
| 1440 | "RFD/OBS647C/RQ" / "H2F" |
| 1920 | "RFD/OBS647C/RQH2" / "F" |
| 1280 | "LPFF/OBS647" / "C/RQH2F" |

Round 1 at 1920 also broke them mid-token: "LPFF/OBS647C" / "/QP4KZ", which breaks before a "/", not a normal break
opportunity. Because the Type badges now shrink, the browser's auto layout spreads the width differently, so the
breaks land elsewhere. A user reading or copying a bank reference sees it split. A possible follow-up, not a
blocker: `break-all` on "/" boundaries only, or a min-width on Reference.

## Carried from round 1 (unchanged, re-observed)

- **O-1:** the fee-transfer statement line got BANK_CHARGE at import, which auto-match cleared (`obs-result.json`
  steps.b checks `suggestedAtImportWasBankCharge`, `suggestionClearedByAutoMatch`).
- **O-2:** the Amount is unsigned; its direction is shown by colour and an `aria-hidden` arrow only. This is
  unchanged.
- **O-3:** `fullPage` screenshots draw the fixed sidebar mid-page; the crops are exact.
