# 646C O-2: auto-match links left by a statement that `createReconciliation` refuses

Register row: `qa_cycle/evidence/phase92-exit/defect-register.md`, "646C O-2" (MEDIUM if reproduced).
Sources: `qa_cycle/evidence/phase92/646C/found.md`, `646C/gates/found-obs.md` O-2.

**Verdict: NOT REPRODUCED as a harm. Closed as not-a-defect. The guard test is kept.**

The precondition does hold: the links persist. What does not hold is the harm. The links cannot
silently hide an outstanding item from another draft, and a discard (FU-6b) releases them.

Checked on `origin/main` @ `34f45c07170ea48f471c67df08cb9e916979d957`.

## The actual flow on main

Paths are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`
(`frontend/` paths are relative to the repo root).

- The wizard auto-matches at step 3 and creates the reconciliation at step 4.
  - Auto-match: `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx:285`, `autoMatch(statement.id)`.
  - Create: `:317`, `createReconciliation(...)`.
  - So a statement is auto-matched before the period-fit rules run.
- `autoMatchStatement` (`reconciliation/TrustReconciliationService.java:732`) refuses only a RETIRED statement (`:741`, re-checked under the lock at `:824`).
  - The period-fit rules are relative to a period end the firm chooses (`requireStatementFitsPeriodEnd`, `:1178` at create, `:1536` at every calculate).
  - So no statement "cannot fit any period": one refused for 10 March fits a later period end.
  - Matches belong to the lines, not to a reconciliation (652C D-5, `continueDraft` Javadoc).
- **The links persist after the refusal.** This was observed: the guard test asserts it.
- A persisted link affects another draft in one way only. The outstanding-items query (`transaction/TrustLedgerQueryRepository.java:586`) treats a row as cleared when it is linked to any line dated on or before the period end.
  - If the line is real bank evidence, that is correct.
  - If the file was wrong, the bank side (the draft's own statement's closing balance) lacks the item. The draft then shows a bank-to-cashbook difference of exactly that amount, and completion is refused by G1, "Reconciliation does not balance" (`TrustReconciliationService.java:1599`).
  - If the refused statement took a row whose bank line also sits on the draft's statement, that line stays unmatched. G4, "Statement lines are unresolved", then refuses completion.
  - In both cases the effect is visible and blocking. It is never a silently wrong completed reconciliation.
- **The links are reachable.**
  - A discard (FU-6b) releases every AUTO_MATCHED / MANUALLY_MATCHED line through `releaseMatch` (`prepareRetirement`, `TrustReconciliationService.java:553`).
  - So does an unmatch of the line (`unmatch`, `:942` → `releaseMatch`, `:964`).
  - The discard is allowed here because the refused statement has no reconciliation. A discard is refused only while a draft uses the statement.

## Reproduction attempt (guard test)

The test is
`BankStatementReplaceDiscardIntegrationTest.refusedStatementsAutoMatchLinks_neverSilentlyHideAnOutstandingItem_andDiscardReleasesThem`.
It runs through the service, which is the wizard's call order:

1. Two deposits on a fresh account: R1 000 on 1 March and R500 on 3 March.
2. A wrong file is imported. It has a line on 1 March for R1 000, which coincides with the deposit, and a line on 20 March.
   - Auto-match links it: `autoMatched == 1`.
   - `createReconciliation(10 March)` refuses it: "Statement does not fit the period end".
   - No reconciliation row is stored.
   - **The deposit stays linked to the wrong file's line.**
3. The right statement has only the 3 March line. It auto-matches 1 line, and a draft to 10 March is created and calculated.
   - bank 500, cashbook 1 500, outstanding deposits 0 (the hidden item)
   - |bank-to-cashbook difference| = 1 000, `isBalanced = false`
   - complete is refused: "Reconciliation does not balance"
4. The wrong file is discarded.
   - It becomes RETIRED.
   - Its line becomes UNMATCHED.
   - The deposit is unlinked.
   - On recalculation: outstanding deposits 1 000, difference 0, balanced, complete → COMPLETED.

The run on main, with the test file only, is green. This is the expected result for a guard: it pins
the behaviour that makes O-2 harmless.

```
cd backend && ./mvnw test -Dtest='BankStatementReplaceDiscardIntegrationTest#refusedStatementsAutoMatchLinks*'
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 38.55 s -- in ...BankStatementReplaceDiscardIntegrationTest
[INFO] BUILD SUCCESS
```

## Why no fix

There are two conservative options. Each changes behaviour without removing a harm:
- **Refuse auto-match on a statement that cannot fit any open period.** No such statement exists. The fit depends on the period end chosen at step 4, which auto-match does not know.
- **Release the links on refusal.** That would throw away bank evidence on a statement that may be reconciled to a later period end. The firm already has an explicit release, discard (FU-6b). The refused statement is listed on the Reconciliation page with a Discard action (`frontend/components/trust/DiscardStatementDialog.tsx`, F-652C-1). The step-4 refusal text itself is the server's period-fit message and does not mention discard. That is a LOW UX point and not part of O-2.

## Residual (LOW, not filed as a defect)

A wrong file's coincidental link can block a draft (G1 or G4). The firm then has to work out that the
cause is the other statement. The figures shown are still the true consequence of the link, and
the discard is the documented way out.
