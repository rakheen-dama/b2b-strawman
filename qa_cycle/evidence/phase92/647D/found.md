# 647D — found while building

Reported while building; the fix-cycle-1 outcome (orchestrator rulings R1–R8) is recorded on each item.

## F-647D-1 — Unmatching the line of an item whose allocation (or Fund payment) was undone back-dates a contra into the allocated window (EQ-4 (b) as at a past date) — FIXED in-slice (refusal), fix cycle 1

- **Status (fix cycle 1, ruling R1):** reproduced, then FIXED in-slice by refusal in commit 0b52f1db6 (fix cycle 1, `epic-647D/unidentified-deposits-resolve`; see the PR's "Fix cycle 1" section). Red run: `red-F-647D-1.txt` — the unmatch succeeded (200) and EQ-4 as at the allocation date read the item at −70.00. Fix: `UnidentifiedDepositService.lockHeldForUnmatch` refuses (400 "Cannot unmatch statement line" / "This deposit has already been allocated or paid to the Fund and that was later undone, so its statement line can no longer be unmatched.") once any `UNIDENT_ALLOC_OUT` / `UNIDENT_FUND_PAY` naming the item has posted (`TrustTransactionRepository.existsPostedResolutionOfItem`), reversed since or not; a rejected (never posted) resolution does not block. Test: `UnidentifiedDepositIntegrationTest.unmatch_afterAnUndoneAllocationOrFundPayment_isRefused_EQ4HoldsAsAtEveryDate` (allocation and Fund-payment paths; nothing moves; EQ-4 green before, inside and after the window).
- **Original report (hypothesis, kept for the record):**

- **Where:** `UnidentifiedDepositService.lockHeldForUnmatch` (647B) admits any item that is `HELD` with no resolution; `TrustTransactionService.reverseForUnmatch` dates the opening row's contra on the **line's** date (§16.3.5: "the bank never did what the row said").
- **Path:** line received D0 → recorded as unidentified (item +L at D0) → allocation dated D1 approved (item −L at D1) → allocation undone dated D2 (item +L at D2; item `HELD` again, resolution cleared) → line unmatched (opening contra −L dated **D0**).
- **Arithmetic (item's own posted NON_CLIENT rows):** as at any D with D1 ≤ D < D2: +L (D0) − L (contra, D0) − L (ALLOC_OUT, D1) = **−L**. EQ-4 (b) requires 0 or L; the unidentified ledger reads in debit for those past dates. As at today: +L − L − L + L = 0 and the item is `REVERSED` (not open) — so `assertMoves` / `assertFirmBalanced(today)` stay green; only `assertBalancedAsAt(account, D)` for D in [D1, D2) would fail. The same shape follows a Fund payment that was paid and then reversed (bounced) before the line is unmatched.
- **Why not fixed:** 647B owns the unmatch guard and 646C/649C own as-at dating; the fix is a design call (refuse an unmatch once the item has ever had a posted resolution, or date the opening contra differently). Not reproduced by a test in 647D (scope: the slice does not add an unmatch-after-undo path). Per the "audit hypotheses need integration verification" rule this is a hypothesis until a test reproduces it.
- **Suggested owner:** orchestrator ruling → 649C (or a 647B follow-up).

## F-647D-2 — The client-matter link is checked when an allocation is requested, not when it is approved — FIXED for allocations, fix cycle 1

- **Status (fix cycle 1, ruling R3, commit 0b52f1db6):** the approval hook (`UnidentifiedDepositService.onResolutionApproved`) now runs `TrustClientMatterRule.requireClientMatter(client, matter)` before posting the `UNIDENT_ALLOC_IN`; a failure is a 400 "Matter not linked to client" that rolls the approval back (the allocation stays pending and can be rejected). Test: `allocation_approval_rechecksTheClientMatterLink_andARefusalLeavesItPending` (link through `POST /api/clients/{c}/matters/{m}`, unlink through `DELETE`, then approve). **Payments are unchanged:** `recordPayment` still checks the link at record time only and `performApprovalCompletion` does not re-check — out of scope here.
- **Original note:**

- `allocate` runs `TrustClientMatterRule.requireClientMatter` at request time; the approval hook posts the `UNIDENT_ALLOC_IN` for the requested client and matter without re-checking the link. A matter unlinked from the client between request and approval would still receive the credit. This matches today's payments (`recordPayment` checks at record time only; `performApprovalCompletion` does not re-check), so it is recorded as a consistency note, not a defect of this slice.

## Fix cycle 1 — recorded, not changed here

- **Refactor suggestion (for 649C):** `UnidentifiedDepositService` (~760 lines) holds the list, the hold (open / unmatch hooks), the two requests and the three resolution hooks. Split suggestion: the resolution hooks into their own `UnidentifiedResolutionService` implementing `UnidentifiedResolutionHooks`. Deferred by ruling R7; not done in 647D.

## F-647D-3 — The shared trust pickers do not forward the form field's id / aria attributes (a11y) — recorded, not fixed (ruling R8)

- **Where:** `frontend/components/trust/TrustEntityPickers.tsx` (`TrustCustomerPicker`, `TrustMatterPicker`): rendered inside `FormItem` with a `FormLabel`, but the trigger button does not receive the `FormControl` slot's `id` / `aria-describedby` / `aria-invalid`, so the label is not programmatically tied to the picker and a field error is not announced on it.
- **Pre-existing:** `RecordDepositDialog` (and the other trust dialogs using the pickers) have the same gap; 647D's `AllocateUnidentifiedDialog` inherits it.
- **Why not fixed:** a shared component change outside the slice's scope (ruling R8). Suggested fix: wrap the picker trigger in `FormControl` (or forward `id`/`aria-*` props) in the shared pickers, with one vitest case per picker.
