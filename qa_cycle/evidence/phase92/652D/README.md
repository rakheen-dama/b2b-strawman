# 652D evidence: closed primary account in the statement builders (Phase 92 E8)

- Test: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementBuilderClosedPrimaryAccountIntegrationTest.java`, committed as **26ade7dae**. Its assertions are unchanged by the fix.
- Base: `main` **68fad6c80**.

## Red on main 68fad6c80 (before the fix)
Files: `red-before-surefire.txt` and `red-before-mvn-summary.txt`.

```
Tests run: 8, Failures: 3, Errors: 0, Skipped: 0, Time elapsed: 30.98 s <<< FAILURE!
```

| Case | Test | Result |
|---|---|---|
| control | `fixture_accountsAndLedgersAreAsDescribed` | PASS. K and P are CLOSED; P is primary GENERAL; ledger(P,B)=0; ledger(R,B)=10 000; the s86 main account holds 7 000 for C |
| (a) only account closed | `a_*` (3 tests) | PASS. The closed sole account is printed with its history, opening R0 and closing R0 |
| (b) closed primary, money on R | `b_primaryClosed_statementOfAccount_readsTheAccountHoldingTheMoney` | **FAIL**: `[SoA trust closing balance: client B holds R10 000 on the replacement account; the closed primary holds R0 for them] expected: 10000.00 but was: 0.00` (:255) |
| (b) | `b_primaryClosed_clientTrustStatement_readsTheAccountHoldingTheMoney` | **FAIL**: `[Client Trust Statement account: the open account holding client B's money] expected: "CPA Replacement Trust Account" but was: "CPA Closed Primary Trust Account"` (:276) |
| (b) | `b_primaryClosed_bothDocumentsRender_withTheMoneyHeld` | **FAIL**: the rendered SoA did not contain `Closing balance: </strong>R10&nbsp;000,00`. It printed R0,00, and "Trust balance held" was R0,00 (:289) |
| (c) s86 primary "main" | `c_observation_section86MainAccount_bothBuildersReadIt` | PASS (see the observation below) |

## Root cause
Both builders took the client's activity accounts from `TrustTransactionRepository.findDistinctTrustAccountIdsByCustomerId`, which are ordered by id. When there were several, they preferred the tenant's **primary GENERAL** account and checked neither its status nor whether it held anything for the client:
- SoA: `StatementOfAccountContextBuilder.buildTrustBlock`, `StatementOfAccountContextBuilder.java:548-572` on main (the primary lookup is at `:560-565`).
- CTS: `ClientTrustStatementContext.selectAccount`, `ClientTrustStatementContext.java:165-186` on main (the primary lookup is at `:172-178`).

Closing an account does not clear its primary flag (`TrustAccountService.closeTrustAccount`, `TrustAccountService.java:332`), and the flag cannot be moved (see found.md iv). So a closed primary went on being chosen for every client who had ever been on it. The documents printed R0,00 and the old history, and the money actually held was hidden. On the SoA that R0 also reached `summary.trust_balance_held`, which is persisted with the statement.

## Fix: orchestrator DEFAULT ruling 652D D-1
One shared rule: `ClientTrustStatementContext.statementAccountId(clientId, candidates, asAt, ledger, accounts)`. Both builders call it.
1. One candidate → use it as-is. This is case (a); the single-account path makes no new query.
2. Several candidates → keep those where the client's general-ledger balance (`ClientLedgerService.getClientBalanceAsOfDate`) as at the document date is non-zero. If none qualifies, keep them all.
3. Tie-break within that set: the primary GENERAL account if it is in the set, otherwise the first id.

The document date is `periodEnd` for the SoA and today (`LocalDate.now(clock)`) for the CTS. A closed account holds R0 after its close (the close guard), so it is never chosen over an account that holds the client's money. A historical SoA for a period before the move still prints the old account.

## Review fix: orchestrator DEFAULT ruling 652D D-2 (invested money counts)
Review finding (PR #1818): step 2 of D-1 read only the general client ledger. `INV_PLACE` has a −1 effect on that ledger, so a client whose money on the replacement account is entirely invested under s86(4) has general R0 there, the same as on the closed primary. The filter kept nothing, the tie-break chose the closed primary, and the invested block was read against it: the invested money disappeared from both documents.

D-2: "holds money" = general balance **plus** the client's s86(4) invested balance from that account, as at the document date, non-zero. New `ClientLedgerService.getClientInvestedBalanceAsOfDate` (module-guarded, wraps `TrustLedgerQueryRepository.clientInvestedBalance`), used by `ClientTrustStatementContext.statementAccountId` on the multi-candidate path only.

Reproduction: case (d) in the same IT. Client D is paid out of P before the close, then R8 000 is deposited on R and placed in full in a s86(4) investment from R. Red against the D-1 helper (`red-d2-surefire.txt`, `Tests run: 10, Failures: 2`):
- CTS: `expected: "CPA Replacement Trust Account" but was: "CPA Closed Primary Trust Account"` (:355)
- SoA: the trust block (P's DEPOSIT/PAYMENT, closing 0.00) did not contain key `"invested"` (:335)

Green after D-2: `Tests run: 10, Failures: 0`. Unit case `buildTrustBlock_multiAccountCustomer_accountWhoseMoneyIsAllInvestedWinsOverClosedPrimary` added to `StatementOfAccountContextBuilderTest`.

## Green after the fix
File: `green-after-surefire.txt`.

After D-1: `Tests run: 8, Failures: 0`. After D-2 (current file):

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 -- in ...StatementBuilderClosedPrimaryAccountIntegrationTest
```

Tier 2 targeted run after D-2: the `verticals.legal.statement` and `verticals.legal.trustaccounting.ledger` packages, `ClientTrustStatementContextIntegrationTest`, `CustomerContextBuilderTest`, and every other test class referencing `ClientLedgerService` (`TrustPostingDateZoneIntegrationTest`, `TrustTransactionServiceTest`, `TrustWireContractTest`). 21 classes, 194 tests, 0 failures, 0 errors. (After D-1: 18 classes, 137 tests, 0 failures.) The full `./mvnw verify` belongs to the orchestrator and Backend CI.

## Case (c) observation, §16.14.4: does not bite
The demo seeder's main account is a primary **SECTION_86** account. A client on that one account takes the single-candidate path, so both documents print it correctly (case (c) is green both before and after the fix). The primary-GENERAL lookup only runs for multi-account clients, and only as the tie-break. The dashboard half of §16.14.4 (`DashboardService.java:614`) is outside 652D and is not changed.

## OBS
**PASS** (orchestrator-run, 2026-11-19 16:26–16:32Z). Fresh mock-auth stack built from head `5c2f17f64`. Details are in `obs/obs.md`; screenshots and psql output are in `obs/`.

What the fixture did: the primary GENERAL account received a deposit of R10 000 and a payment of R10 000, so it ended at R0. It was then closed with the one REST call (no UI can close an account), and it is still `is_primary=t`. A non-primary replacement account then received a R10 000 deposit.
- **SoA, 1–19 Nov:** Trust Activity on the replacement account, closing balance R10 000,00, trust balance held R10 000,00. The stored snapshot has `trust_balance_held = 10000.00`.
- **CTS:** "Account: OBS 652D Replacement Trust Account", closing balance R10 000,00.
- **Backend log:** "has activity on 2 trust accounts; using 9d2ea361…".
- **psql:** primary 0.00, replacement 10000.00.

Backend verify: 7 389 testcases, 0 failures, 0 errors, 26 skipped @5c2f17f64. CI Backend passed @5c2f17f64.

## Out of scope
See `found.md`, items (i)–(v) and F-652D-CR1, F-652D-O1..O3. Each is reported, not fixed in 652D.
