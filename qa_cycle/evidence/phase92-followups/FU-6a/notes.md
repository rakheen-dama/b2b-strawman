# FU-6a — refuse statement lines dated after today (Phase 92 F-647C)

Scope: the future-dated-line half of FU-6 only. The duplicate same-end-date statement / replace /
discard half (F-646C-1, F-652C-1) is a separate PR and is not touched here.

## Defect and verification on this head (base `5019e87b5`)

Register row "F-647C (future-dated line)" (`qa_cycle/evidence/phase92-exit/defect-register.md`),
sources 647A found.md #3, 647C found.md #4, 647B found.md F-4, 649C found.md N-9.

Verified before the fix:
- `TrustReconciliationService.importBankStatement` had no date check on the parsed lines. The
  parsers (`parser/*CsvParser`) have no clock and no date rule.
- `StatementLinePostingService.recordFromLine` had no date check.
- The only "not in the future" rule on the reconciliation path was the period end
  (`requireStatementFitsPeriodEnd`). The register cites `TrustReconciliationService.java:864`; on
  this head it is at `:907` (the line moved, the rule is the same).
- The section 86(4) interest path (`TrustInvestmentService.recordInterestFromLine` and
  `recordInterest` with a line) already refused: the entry is dated the line's date, and
  `record(...)` refuses an entry date after `LocalDate.now(clock)` ("The entry date cannot be in
  the future."). So the gap was the Fund, charge and unidentified-deposit classifications, plus the
  import.

Reproduced red on unmodified code: `red-run.txt` (import 201, record 201).

## Root cause

Neither the import nor the shared record rules (`StatementLines`) checked the line date. A row
recorded from a future-dated line carries that date, so:
- its unmatch contra (`resolveReversalDate`) would have to be future-dated, which is refused;
- the as-at-today Fund card and the row-dated running balance disagree until that date;
- the harness's EQ-4 rule (d) ("as at today an item is HELD exactly when it is open") fails for a
  future-dated unidentified deposit.

## Rule (founder ruling F-647C)

"Today" is `LocalDate.now(clock)`, the SA business date from the app `Clock` bean
(`Clock.system(KaziTime.ZONE)`). The fee-transfer (`resolveFeeTransferDate`), reversal
(`resolveReversalDate`), investment and period-end rules use the same thing. A line dated today is
allowed. A line dated tomorrow or later is refused.

- **Import:** after a successful parse and before anything is stored (no S3 object, no statement,
  no lines), any parsed line dated after today refuses the whole import. Lines are never dropped.
  400 `InvalidStateException`:
  - title "Cannot import bank statement"
  - detail "This statement has a line dated 22 November 2026, which is after today. Import the
    statement once the bank has processed that line."
  The date is the first offending line in file order, formatted with
  `VariableFormatter.formatDateText` (the house "d MMMM yyyy").
- **Record from line:** a new shared rule, `StatementLines.requireNotAfterToday(line, today)`, is
  called in `recordFromLine` straight after the existing `requireUnmatched` (under the line lock,
  before the statement lock and before any write). 400:
  - title "Cannot record statement line" (`RECORD_TITLE`)
  - detail "This statement line is dated 22 November 2026, which is after today. Record it on or
    after that date."
  This guards lines stored before the import refused them.
- **Section 86(4) interest:** unchanged. It is already refused by the entry-date rule, with its own
  title and detail ("Cannot record investment interest" / "The entry date cannot be in the
  future."). It is pinned by a new test. I did not reroute it through the new helper, to keep
  `TrustInvestmentService` out of this diff. The wording therefore differs by classification. A
  later polish could call `StatementLines.requireNotAfterToday` in
  `TrustInvestmentService.lockUnmatchedLine`.

Wording follows ADR-357: there are no client/matter nouns in the new copy, and no
customer/project. `TerminologyRemnantScanTest` is green.

## Manual match / auto-match decision: left alone

Neither path posts anything. They only link an existing posted row to the line.
- None of the defect's consequences come from a link. Unmatching a matched line clears the link
  and reverses nothing (`TrustReconciliationService.unmatch`, non-RECORDED branch). So the "cannot
  unmatch until its date" symptom does not arise. The Fund card and the running balances are
  unaffected.
- The ruling covers "recording from / importing". Refusing matches would go beyond it, and could
  block a firm from matching a line already stored to a row it legitimately posted.
- Auto-match candidates are posted rows. Any date restriction on them belongs to the posting
  paths, not to matching.

This is conservative in that it changes no behaviour the ruling did not ask for. If the founder
wants matches refused too, it is a two-line follow-up (`manualMatch`, and a filter in
`autoMatchStatement`).

## Paths changed

- `backend/src/main/java/.../reconciliation/TrustReconciliationService.java`: the
  `requireNoLineAfterToday(parsed)` call in `importBankStatement` and its private helper
- `backend/src/main/java/.../reconciliation/StatementLines.java`: `requireNotAfterToday`
- `backend/src/main/java/.../reconciliation/StatementLinePostingService.java`: `Clock` injected; the
  new rule is called in `recordFromLine`; Javadoc updated
- Tests:
  - `BankStatementImportErrorIntegrationTest`: +2 tests
  - `StatementLinePostingIntegrationTest`: +3 tests

## Tests

New:
- `BankStatementImportErrorIntegrationTest.aLineDatedAfterToday_refusesTheWholeImport_andStoresNothing`:
  the lines are dated today−2, tomorrow and tomorrow+3. Asserts a 400 with title and detail naming
  tomorrow, nothing in storage and no statement. RED→GREEN.
- `BankStatementImportErrorIntegrationTest.aLineDatedToday_imports201`: the boundary. Green on
  both.
- `StatementLinePostingIntegrationTest.aLineDatedAfterToday_isRefused_forEveryClassification`:
  lines seeded through the fixture tables, dated tomorrow, for FUND_INTEREST, CHARGE_REFUND,
  UNIDENTIFIED_DEPOSIT, FUND_SWEEP and BANK_CHARGE. Asserts a 400, that nothing moves
  (`TrustBooksHarness.assertMoves` with `Expected.none()`), that `matched_count` is unchanged and
  that the lines stay UNMATCHED. RED→GREEN.
- `StatementLinePostingIntegrationTest.investmentInterest_fromALineDatedAfterToday_isRefused`: pins
  the existing section 86(4) refusal. Green on both.
- `StatementLinePostingIntegrationTest.aLineDatedToday_isRecorded_andUnmatches`: the boundary;
  record, then a same-day unmatch. Green on both.

Evidence:
- `red-run.txt`: 35 run, 2 failed as expected.
- `green-run.txt`: 35 run, 0 failed.
- `tier2-run.txt`: **Tier 2, 371 tests, 0 failures, 0 errors, 0 skipped**. It covers:
  - the whole `reconciliation` package, including `parser`
  - every test class that imports statements or records lines:
    - books: `TrustIntegrityServiceIntegrationTest`, `TrustMonthlyListIntegrationTest`,
      `TrustPositionAsAtIntegrationTest`
    - creditor: `TrustInterestMemoIntegrationTest`, `TrustCreditorAccountIntegrationTest`,
      `UnidentifiedDepositIntegrationTest`
    - other: `TrustInvestmentInterestIntegrationTest`, `TrustReportQueryIntegrationTest`,
      `dashboard.TrustPositionIntegrationTest`, `TrustWireContractTest`, and
      `InformationRequestControllerTest` (a grep hit on "bank-statements", included to be safe)
  - the ArchUnit guards: `LayerDependencyRulesTest`, `TestConventionsTest`,
    `TerminologyRemnantScanTest`, `TrustPostingRulesTest`, and `ZoneAwareTimeArchTest` (added
    because the change uses `LocalDate.now(clock)`)

The full `./mvnw verify` was not run here; the orchestrator runs it.

## Test-data changes

None. No existing test or fixture imports or records a line dated after today. Every existing CSV
fixture uses past fixed dates or dates relative to the clock (`pastDate()` = today−10), and
Tier 2 is green with no data edits.

## Frontend

No change needed. Both refusals reach the user as the ProblemDetail detail:
- **Import:** `uploadBankStatement` reads `detail?.detail || detail?.title` (`reconciliation/actions.ts:109-116`),
  and `components/trust/BankStatementUpload.tsx:46,100-103` renders `error`.
- **Record:** `recordFromStatement` returns `error.message`. `ApiError`'s message is
  `detail?.detail || detail?.title` (`lib/api/client.ts:147`), and
  `components/trust/RecordFromStatementDialog.tsx:136,396-398` renders it.

## Residual risk

- **Existing data is not migrated.** A statement imported before this fix may already hold lines
  dated after today:
  - Unrecorded lines are now refused at record time until their date. They can still be matched
    or excluded.
  - Rows already recorded from such lines stay as they are. They cannot be unmatched until their
    date (the reversal rule), and the card and running-balance disagreement persists until then.
    It resolves on its own once the date passes.
  - Not scanned or repaired here.
- **Matching is unchanged.** Manual and auto-match of a stored future-dated line are still
  allowed; see the decision above.
- **Section 86(4) wording.** That refusal keeps its own wording, which differs from the other
  classifications.
- **Clock edges.** "Today" is the SA business date. A bank that value-dates a line for the next SA
  day can make an import fail until midnight SAST. This is intended under the ruling.
