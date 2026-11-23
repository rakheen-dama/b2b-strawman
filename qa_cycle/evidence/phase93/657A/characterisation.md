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
