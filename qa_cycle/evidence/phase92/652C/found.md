# 652C — found

## F-652C-1 — a re-upload after auto-match strands the first statement's matches

- Source: 652C scout (brief §8), read from the code; reported, NOT fixed in 652C (D-6: F-646C-1 OUT → FU-6). Pre-existing: the same happens on `main` 30ad1643b with the orphaned second draft.
- Defect: auto-match candidates exclude transactions already linked to a statement line (`TrustTransactionRepository.findUnmatchedCandidates`: `t.bankStatementLineId IS NULL`). If the user auto-matches statement A, goes back in the wizard and uploads a corrected statement B, the draft is re-pointed to B (652C get-or-create), but the deposits/payments matched on A's lines are not offered to B, and the wizard no longer shows A's lines to unmatch them. A also stays `IMPORTED` with its (now unwanted) lines, so the dashboard's `UNRESOLVED_STATEMENT_LINES` (`findUnresolvedInUnreconciledStatements`) keeps counting any unmatched line of A.
- Pinned by: `TrustReconciliationDraftIntegrationTest.reUpload_returnsTheDraftRepointedToTheNewStatement_andRecalculated` asserts A's matched line is left `MANUALLY_MATCHED` and B's lines start unmatched (652C D-5 — re-pointing changes only the reconciliation).
- Fix: the "replace" half of F-646C-1 — a replace intent on import (`replacesStatementId` or a wizard affordance), a retired-statement status, and releasing what the old lines hold (matches, exclusions, 647A record-from-statement postings). Owner: **FU-6**.
- For the 652C OBS: re-upload **before** running auto-match on the first statement (or record this behaviour if observed).
