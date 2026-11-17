# 647D — found while building (out of slice; NOT fixed here)

Each item is reported for re-spec. None was changed in 647D.

## F-647D-1 — Unmatching the line of an item whose allocation (or Fund payment) was undone back-dates a contra into the allocated window (EQ-4 (b) as at a past date) — HYPOTHESIS, derived, not reproduced

- **Where:** `UnidentifiedDepositService.lockHeldForUnmatch` (647B) admits any item that is `HELD` with no resolution; `TrustTransactionService.reverseForUnmatch` dates the opening row's contra on the **line's** date (§16.3.5: "the bank never did what the row said").
- **Path:** line received D0 → recorded as unidentified (item +L at D0) → allocation dated D1 approved (item −L at D1) → allocation undone dated D2 (item +L at D2; item `HELD` again, resolution cleared) → line unmatched (opening contra −L dated **D0**).
- **Arithmetic (item's own posted NON_CLIENT rows):** as at any D with D1 ≤ D < D2: +L (D0) − L (contra, D0) − L (ALLOC_OUT, D1) = **−L**. EQ-4 (b) requires 0 or L; the unidentified ledger reads in debit for those past dates. As at today: +L − L − L + L = 0 and the item is `REVERSED` (not open) — so `assertMoves` / `assertFirmBalanced(today)` stay green; only `assertBalancedAsAt(account, D)` for D in [D1, D2) would fail. The same shape follows a Fund payment that was paid and then reversed (bounced) before the line is unmatched.
- **Why not fixed:** 647B owns the unmatch guard and 646C/649C own as-at dating; the fix is a design call (refuse an unmatch once the item has ever had a posted resolution, or date the opening contra differently). Not reproduced by a test in 647D (scope: the slice does not add an unmatch-after-undo path). Per the "audit hypotheses need integration verification" rule this is a hypothesis until a test reproduces it.
- **Suggested owner:** orchestrator ruling → 649C (or a 647B follow-up).

## F-647D-2 — The client-matter link is checked when an allocation is requested, not when it is approved (note)

- `allocate` runs `TrustClientMatterRule.requireClientMatter` at request time; the approval hook posts the `UNIDENT_ALLOC_IN` for the requested client and matter without re-checking the link. A matter unlinked from the client between request and approval would still receive the credit. This matches today's payments (`recordPayment` checks at record time only; `performApprovalCompletion` does not re-check), so it is recorded as a consistency note, not a defect of this slice.
