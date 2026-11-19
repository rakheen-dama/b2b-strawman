# 652C — found

## F-652C-1 — a re-upload after auto-match strands the first statement's matches

- Source: 652C scout (brief §8), read from the code; reported, NOT fixed in 652C (D-6: F-646C-1 OUT → FU-6). Pre-existing: the same happens on `main` 30ad1643b with the orphaned second draft.
- Defect: auto-match candidates exclude transactions already linked to a statement line (`TrustTransactionRepository.findUnmatchedCandidates`: `t.bankStatementLineId IS NULL`). If the user auto-matches statement A, goes back in the wizard and uploads a corrected statement B, the draft is re-pointed to B (652C get-or-create), but the deposits/payments matched on A's lines are not offered to B, and the wizard no longer shows A's lines to unmatch them. A also stays `IMPORTED` with its (now unwanted) lines, so the dashboard's `UNRESOLVED_STATEMENT_LINES` (`findUnresolvedInUnreconciledStatements`) keeps counting any unmatched line of A.
- Pinned by: `TrustReconciliationDraftIntegrationTest.reUpload_returnsTheDraftRepointedToTheNewStatement_andRecalculated` asserts A's matched line is left `MANUALLY_MATCHED` and B's lines start unmatched (652C D-5 — re-pointing changes only the reconciliation).
- Fix: the "replace" half of F-646C-1 — a replace intent on import (`replacesStatementId` or a wizard affordance), a retired-statement status, and releasing what the old lines hold (matches, exclusions, 647A record-from-statement postings). Owner: **FU-6**.
- For the 652C OBS: re-upload **before** running auto-match on the first statement (or record this behaviour if observed).

## F-652C-2 — stale tab keeps Complete enabled after the 409 (polish, not fixed)
Observed in OBS run 2 (check e, head bc4684518): after tab A's Complete is refused with 409 "This reconciliation now uses a different statement", tab A still shows its old (balanced) figures and Complete stays enabled; the follow-up recalculation is also refused, so the figures never refresh. Re-clicking only returns another 409 and changes nothing; the banner tells the user to reload. Data-safe. Follow-up: disable Complete (or force a reload) after a stale-statement refusal. Severity LOW. Evidence: obs/run2/obs-run2.md, e3/e4 screenshots.

## Review Mediums and later CodeRabbit round (recorded at merge, PR #1817 → ca2ece798) — all LOW, not fixed
- **F-652C-3** (reviewer A @19157ccb0): a legacy DRAFT with a null `bank_statement_id` now gets the stale-statement 409 from calculate/complete instead of the old 400 "Missing bank statement"; it recovers through create (which re-points the draft).
- **F-652C-4** (reviewer A @19157ccb0): the missing-body case on calculate/complete is only tested with `{}`, not with no body at all.
- **F-652C-5** (reviewer A @93c8af2d5): the lock-side "Month already locked" guard in `TrustMonthlyListService.lock` is no longer exercised by any test (defence in depth behind the create-time 409; 652C D-8).
- **F-652C-6** (reviewer B @19157ccb0): the canonical lock-order Javadoc in `TrustPostingService` (~45–58) doesn't name the reconciliation row that create/calculate/complete now lock first; the wizard's line handlers drop `errorTitle` on a stale-statement 409 (detail still explains); there is no "reload" affordance although the detail says "Reload the reconciliation" (see F-652C-2).
- **F-652C-7** (CodeRabbit later round @c92ddc0e4): `obs/obs-refusal.cjs:10` and `obs/obs.cjs:21` resolve Playwright from an absolute author path (evidence scripts only).
- **F-652C-8** (CodeRabbit later round): `TrustReconciliationDraftIntegrationTest` ~426–436 / 488–490 — the HTTP refusal cases should assert ProblemDetail `status`/`title`/`detail` (service-level assertions already pin title and detail).
- **F-652C-9** (CodeRabbit later round, nitpick): `ExpectedStatementRequest` is nested in `TrustReconciliationService` rather than a `dto` sub-package — same as its sibling `CreateReconciliationRequest`; a move touches the wire pins on both sides.
