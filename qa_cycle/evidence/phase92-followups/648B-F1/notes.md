# 648B F-1: reverse a section 86(4) interest entry from the UI

## Endpoint contract (no backend change)
- `POST /api/trust-investment-interest/{interestId}/reverse`, `MANAGE_TRUST`, no request body (no reason, no date). Returns `InvestmentInterestResponse`.
- `TrustInvestmentService.reverseInterest` refuses (400/409 ProblemDetail `detail`): already reversed; recorded from a statement line ("Unmatch the line to reverse it"); investment no longer ACTIVE/MATURED (withdrawn); trust account closed.
- `GET /api/trust-investments/{id}/interest` (`VIEW_TRUST`) lists the entries.

## UI
- `components/trust/InvestmentInterestEntriesDialog.tsx`: lists entries (SWR via the `fetchInvestmentInterest` action); each POSTED bank-advice entry has Reverse -> inline confirm (Keep entry / Reverse interest entry) -> `reverseInvestmentInterest(interestId)`. Backend detail shown in an alert on refusal; confirm disabled in flight; on success the list is re-fetched and `router.refresh()` updates the register totals and balance.
- Entry point: "Interest entries" button in the investment register Actions cell (`InvestmentPageClient`, prop `showInterestEntries`).
- Actions in `investments/actions.ts`: `fetchInvestmentInterest`, `reverseInvestmentInterest`.

## Gating
- Whole Actions cell is `hasManageTrust` (owner/admin/MANAGE_TRUST) and only for held (ACTIVE/MATURED), non-awaiting-approval investments: matches the server.
- Button only for CLIENT_INSTRUCTION (s86(4)) investments. Per entry: Reverse only when POSTED and no statement line; REVERSED shows a badge; line-recorded entries show the "unmatch the statement line" hint.
- No reason/date field because the endpoint takes none (deviation from the transaction reversal dialog, by contract). No idempotency key (F-11 is separate).

## Tests
`frontend/__tests__/trust-investment-interest-reverse.test.tsx` (8): control shown/hidden; Reverse only on reversible rows; confirm then action called with id + refresh + list updates; cancel; refusal detail; in-flight disable; load error.
Red (7 failed / 1 passed on unmodified code): `red-run.txt`. Green 8/8: `green-run.txt`. Full gate: `gate-run.txt` (lint, typecheck, format:check, test: 484 files, 4618 passed, 2 skipped).
