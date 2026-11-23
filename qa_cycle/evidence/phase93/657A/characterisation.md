# 657A — task 657.1 characterisation

## Run 1 — main production code (commit 1 only: test + `TrustBooksFixture.statementDirect`)

Base: `main` @ `4299bc058` (no 657A production change). Run 2026-11-23.

```
./mvnw test -Dtest="OpeningBalanceAsDepositCharacterisationIntegrationTest,TrustEquationCharacterisationIntegrationTest"
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustEquationCharacterisationIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.OpeningBalanceAsDepositCharacterisationIntegrationTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
```

`OpeningBalanceAsDepositCharacterisationIntegrationTest.openingBalancePostedAsDepositAtCutover_isOutstanding_andEq1FailsByItsAmount`
(C = `YearMonth.from(today).minusMonths(4).atEndOfMonth()` = 2026-07-31 on the run date) — an opening balance of
R300 000.00 posted as an ordinary `DEPOSIT` dated C, on an account whose statement at C closes at R300 000.00:

| Figure | Value |
|---|---|
| cashbook at C | 300 000.00 |
| outstanding deposits at C | 300 000.00 |
| adjusted bank balance (bank + deposits − payments) | 600 000.00 |
| EQ-1 difference (adjusted bank − cashbook) | **300 000.00** — out by exactly the posted amount |

The hazard is real on main: the bank balance at C is counted twice (once in the statement, once as an outstanding deposit).

## Run 2 — `TrustEquationCharacterisationIntegrationTest` on main production code

Same run as Run 1: `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0` (paths 1–8, 11).

## Run 3 — the same two classes on the branch head (all 657A backend changes)

Tier 2a (`./mvnw test -Dtest=…` with the 17 targeted classes, after commit `b1c72f811`; the commits after it are frontend
and bookkeeping only):

```
OpeningBalanceAsDepositCharacterisationIntegrationTest   tests=1   failures=0
TrustEquationCharacterisationIntegrationTest             tests=21  failures=0
```

Both also green in the Tier 2b importer sweep (213 classes, 2163 `<testcase>`, 0 failures, 29 skipped = the
`TrustInterestChargesFundGroupTest` assumptions for the 29 non-Fund registry types).

The characterisation test is unchanged and still green after 657A: an ordinary `DEPOSIT` at C is still outstanding until a line
shows it — the take-on types (`TAKEON_CLIENT` & co., `cleared_at_cutover`), not a change to `DEPOSIT`, are the fix.
`TrustOpeningPostingIntegrationTest.takeOnPath_balancesAtCutover_andOnlyTheTwoItemsAreOutstanding` is the after-picture:
the same kind of opening position posted through `recordOpening` leaves only the two items outstanding (deposits 5 000.00,
payments 20 000.00) and EQ-1 at C holds with the statement closing at 418 500.00.

## Run 4 — the pinned characterisation file is untouched

```
git diff main -- backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustEquationCharacterisationIntegrationTest.java
(empty — 0 lines)
```
