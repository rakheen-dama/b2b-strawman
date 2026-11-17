# 646D — defects and open points found outside the slice (reported, NOT fixed)

Phase rule: a defect outside the stated scope is written here, reported, and re-specced — never added.

## F-646D-1 — A back-dated fee transfer has no lower bound (open point, orchestrator ruling D8)

- Where: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java`
  `resolveFeeTransferDate` (646D) — refuses only a future date (ruling #6 says only "never in the future").
- Scenario: a client deposit dated 10 April; a fee transfer dated 5 April is accepted. Sufficiency is
  checked on the client's balance at approval (`requireClientCovers`, current balance), so the transfer
  posts and the client ledger is in debit *as at* 5 April (and at every date between 5 and 10 April).
  A date in a month that has already been reconciled is also accepted and silently changes that month's
  as-at figures.
- Why deferred: ruling #6 sets no lower bound; the period lock (locked month) arrives in **650A** and the
  "client ledger in debit" integrity flag in **649C**. No rule was added here (orchestrator ruling D8).
- Suggested owner: 650A (locked-month refusal on posting dialogs and server) + 649C (integrity flag).

## F-646D-2 — The harness checks the dated balance reads only as at today (follow-up, optional)

- Where: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TrustBooksHarness.java`
  (`assertServicesAgree`) — compares `getCashbookBalance(id)` / `getTotalTrustBalance(id)` with the
  oracle only when `asAt == today` (brief O11).
- 646D adds `asAt` overloads to both reads; they are proven by `TrustPositionIntegrationTest`
  (`balanceEndpointsAnswerAsAtTheRequestedDate`) but the harness does not assert them at an arbitrary date.
- Why deferred: test-harness hardening, +1 file, not a defect. Suggested owner: the next HARNESS slice.
