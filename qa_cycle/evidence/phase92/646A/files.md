# 646A file list (task 646.1)

Scout count, re-grepped at build time (OQ #1 ruled 2026-11-16: waiver extended to ≈80 files; plan as written = 78, +3 optional Javadoc-only). Copied from the slice brief §0 and §4.

| Family | Estimate (task file row 646A) | Real (plan as written, grep + compile-forced) |
|---|---|---|
| Main — new | ≈ 8 | **6** (`TrustTransactionType`, `TrustLedger`, `TrustLedgerEffect`, `TrustPostingService`, `TrustLedgerQueryRepository`, `ClientBalanceRow`) |
| Main — changed / deleted | ≈ 20 | **25** (24 changed + `ClientTrustBalanceProjection` deleted) **+ 3 optional Javadoc-only** |
| Migration | 1 | **1** (tenant `V16__phase92_trust_posting_core.sql` — V16 confirmed next free; global unaffected) |
| Tests — new | ≈ 6 | **6** |
| Tests — changed | ≈ 12 | **34** |
| Frontend | 3 | **6** (3 product files + 3 vitest files) |
| **Total** | **≈ 48** | **78** (81 with the optional Javadoc-only files) |

**Main 31 / migration 1 / test 40 / frontend 6 = 78** (+63 % over ≈48).


**Commit order (recommended):** (1) registry + posting service + migration → (2) trust-module readers → (3) outside-module readers → (4) fixtures and tests → (5) the frontend files.

**Intermediate commits will NOT compile on their own, and that is unavoidable.** Commit 1 drops the card columns and the entity fields/mutators, so every reader (commits 2–3) and every fixture (commit 4) fails to compile until its own commit lands. Commit 1 also changes the migration, which makes every direct-write test red at runtime until commit 4. Build in this order for review, run Tier 1 / Tier 2 **only at the end** (or locally across uncommitted work), and say in the PR body that commits 1–3 are intentionally non-compiling review units.

`$B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `$TA` = `$B/verticals/legal/trustaccounting`, `$T` = `backend/src/test/java/io/b2mash/b2b/b2bstrawman`, `$TT` = `$T/verticals/legal/trustaccounting`, `$MIG` = `backend/src/main/resources/db/migration/tenant`.

### Family 1 — registry + posting service + migration (9 files: 6 new Java, 2 changed Java, 1 new migration)

| File | Action | What it does / what changes |
|---|---|---|
| `$TA/transaction/TrustTransactionType.java` | NEW | The enum: 7 constants. Nested `Approval {NONE, REQUIRED, WITH_PAIR, WITH_ORIGINAL}`, `Reversal {IMMEDIATE, NEEDS_APPROVAL, PAIR, VIA_PAIR, OWNER, NONE}`, `Origin {FIRM, BANK}` (nested keeps the file count down; separate files are acceptable). `of(String)`, `find(String)` → `Optional`, `effectFor(BigDecimal)`. See §7.1 for the attribute table and proposed source. |
| `$TA/transaction/TrustLedger.java` | NEW | `enum TrustLedger { CLIENT, CLIENT_INVESTMENT, NON_CLIENT, INTER_ACCOUNT }` |
| `$TA/transaction/TrustLedgerEffect.java` | NEW | `record TrustLedgerEffect(TrustLedger ledger, BigDecimal cashbookEffect, BigDecimal ledgerEffect)` with `negated()` and `static zero(TrustLedger)` |
| `$TA/transaction/TrustPostingService.java` | NEW | `record`, `recordAwaitingApproval`, `postOnApproval`, `postReversal` — the only caller of `applyPosting` (§7.2) |
| `$TA/transaction/TrustLedgerQueryRepository.java` | NEW | `extends Repository<TrustTransaction, UUID>`; native SQL with interface projections; every derived balance (§7.3) |
| `$TA/transaction/ClientBalanceRow.java` | NEW | Projection `getCustomerId()`, `getBalance()`, `getLastTransactionDate()` (replaces `ledger/ClientTrustBalanceProjection`) |
| `$TA/transaction/TrustTransaction.java` | CHANGE | Fields `ledger` (`@Enumerated(STRING)`, `length = 20`, default `CLIENT`), `cashbookEffect`, `ledgerEffect` (default `ZERO`, `precision 15 scale 2`), `postedAt` (`Instant`) + getters; package-private `applyPosting(TrustLedgerEffect effect, Instant postedAt)` |
| `$TA/ledger/ClientLedgerCard.java` | CHANGE | Remove `balance`, `total*`, `lastTransactionDate`, all six mutators and their getters. Keep `id`, `trustAccountId`, `customerId`, `createdAt`, `updatedAt`. The Javadoc says "per-client lock anchor (ADR-378, ADR-231)". |
| `$MIG/V16__phase92_trust_posting_core.sql` | NEW | §16.7.2 body verbatim (§9) |

(`ClientLedgerCard` is in this family because the column drop and the entity must change together. Its repository moves in family 2.)

### Family 2 — trust-module readers and writers (14 files)

| File | Uses today (line @ `ad1d63a04`) | Change |
|---|---|---|
| `$TA/transaction/TrustTransactionRepository.java` | 7 sign `CASE` queries: `findClientHistoryWithRunningBalance` `:89-131`, `calculateCashbookBalance` `:176-196`, `calculateClientBalanceAsOfDate` `:198-223`, `findMonthlyBalanceMovements` `:225-263`, `calculateClientMatterBalancesAsOfDate` `:308-334`, `calculateBalanceByProjectId` `:336-355`, `calculateMatterClientBalance` `:357-383`; outstanding pair `:404-428` names `INTEREST_*` | Rewrite in place: running window `SUM(t.ledger_effect) OVER (…)` (keep the row filter — T11); trend → `SUM(t.cashbook_effect)` and `SUM(t.ledger_effect) FILTER (WHERE t.ledger = 'CLIENT')`, `WHERE t.posted_at IS NOT NULL`. **Delete** `calculateCashbookBalance`, `calculateClientBalanceAsOfDate`, `calculateClientMatterBalancesAsOfDate`, `calculateBalanceByProjectId`, `calculateMatterClientBalance` (moved to `TrustLedgerQueryRepository`). Outstanding pair: leave semantics (646C deletes it); optionally drop the dead `INTEREST_*` names. Update the Javadocs. |
| `$TA/transaction/TrustTransactionService.java` | 9 card mutators `:378, :552, :559, :1036, :1038, :1040, :1353, :1568, :1574`; card balance reads `:500, :506, :1010-1011, :1502`; `CREDIT_TYPES`/`DEBIT_TYPES` `:1217-1221`, used `:995, :1299-1300`; upsert `:1701-1729`; `calculateMatterClientBalance` `:1669`; `calculateCashbookBalance` `:1611` | Inject `TrustPostingService` + `TrustLedgerQueryRepository`. Every write of a new row → posting service. Sufficiency → derived balance under the anchor lock (T5). Sets → registry (T12). The upsert becomes the lock-anchor upsert (`lockClient`). Reversal → `postingService.postReversal` with **HEAD semantics** (T1). §8.3.1 has the method-by-method plan. |
| `$TA/ledger/ClientLedgerCardRepository.java` | `calculateTotalTrustBalance`, `sumBalancesForCustomer`, `sumBalancesByCustomerIdIn`, `findByTrustAccountId(Pageable)` sorted by UI | Remove the three sums. Keep `findByTrustAccountIdAndCustomerId`, `findByAccountAndCustomerForUpdate`, `findByTrustAccountId`. ADD `@Modifying @Query(nativeQuery = true) int insertLockAnchorIfAbsent(accountId, customerId)` (recommended home for the upsert, usable by the seeder). |
| `$TA/ledger/ClientTrustBalanceProjection.java` | projection of `sumBalancesByCustomerIdIn` | **DELETE** (replaced by `transaction/ClientBalanceRow`) |
| `$TA/ledger/ClientLedgerService.java` | `CREDIT_TYPES`/`DEBIT_TYPES` `:50-54`, `computeLedgerEffect` `:276-291`, card balance and `getLastTransactionDate` in `toResponse` `:311-329`, `calculateTotalTrustBalance` `:229`, `calculateClientBalanceAsOfDate` `:221, :244`, `findByTrustAccountId(pageable)` `:162-163` | `ClientLedgerCardResponse` drops `totalInterestCredited`; balance and `lastTransactionDate` derived; `id`/`createdAt`/`updatedAt` from the anchor; `listClientLedgers` paged in SQL, sort stripped, `ORDER BY balance DESC` (T4); `getTotalTrustBalance` → `clientLedgerTotal(id, null)`; statement uses `txn.getLedgerEffect()`; `LedgerStatementLine` gains `BigDecimal ledgerEffect` (additive JSON field on `/statement`). |
| `$TA/ledger/LedgerTotals.java` | `interestCredited` component; `plus("INTEREST_CREDIT")` | Remove `interestCredited` (and its `case`); `derivedBalance()` without it; keep the rest. Javadoc: grouped by type, sign = `type.ledgerSign()`. |
| `$TA/reconciliation/TrustReconciliationService.java` | `CREDIT_TYPES`/`DEBIT_TYPES` `:46-53`, `isSignCompatible` `:755-763`; `calculateCashbookBalance` `:635`; `calculateTotalTrustBalance` `:636`; injects `ClientLedgerCardRepository` `:60,:72,:81` | Sign match on `candidate.getCashbookEffect().signum()` (T6); totals from `ledgerQueries.cashbook(accountId, null)` / `clientLedgerTotal(accountId, null)` (equation unchanged until 646C); drop the card repository dependency. Outstanding calls unchanged. |
| `$TA/report/ClientLedgerStatementQuery.java` | `calculateClientBalanceAsOfDate` `:61`; `isCreditType` sign set `:96-98` | Opening → `ledgerQueries.clientBalance(acc, cust, dateFrom.minusDays(1))`; credit/debit by `tx.getLedgerEffect().signum() > 0` (the same classification for every kept type). |
| `$TA/report/TrustReceiptsPaymentsQuery.java` | `isReceiptType` sign set `:115-117` | Classify by `tx.getLedgerEffect().signum() > 0`. **Not** `cashbook_effect`: transfers would fall out of both columns and change the report. `ledger_effect` reproduces today's rows exactly. |
| `$TA/report/ClientTrustBalancesQuery.java` | cards `:67-71`, `card.getBalance()` `:105`, `card.getLastTransactionDate()` `:115`, `calculateClientBalanceAsOfDate` `:103`, `interestCredited` `:112` | Rows from `ledgerQueries.clientBalances(acc, asOfDate)` (membership = any posted `CLIENT` row; balance as at `asOfDate`/now — T7); `totalInterestCredited` → `BigDecimal.ZERO` (column kept); `lastTransactionDate` from the row; drop the card repository dependency. |
| `$TA/interest/InterestService.java` | own `CREDIT_TYPES`/`DEBIT_TYPES` `:56-60`; `calculateClientMatterBalancesAsOfDate` `:803`; posting body `:511-668` incl. `addInterestCredit` `:606`, `INTEREST_LPFF` save `:614-632`; approve body `:458-508` | T13: delete the posting (and approve) bodies after the guard; `accumulateSegment` → `txn.getLedgerEffect()`; opening via `ledgerQueries.clientMatterBalances`; delete the dead helpers. |
| `$TA/investment/TrustInvestmentService.java` | `ledgerCard.getBalance()` `:129-139` | `ledgerQueries.clientBalance(accountId, clientId, null).compareTo(principal) < 0` → the same refusal. Drop the card repository dependency if unused. |
| `$TA/TrustAccountService.java` | `calculateBalanceByProjectId` `:137`; `calculateTotalTrustBalance` `:275` | `ledgerQueries.matterBalance(projectId)`; `ledgerQueries.clientLedgerTotal(id, null)` (the closing guard; the message is unchanged). Drop the card repository dependency. |
| `$TA/transaction/MatterBalanceRow.java` | Javadoc `{@link …calculateClientMatterBalancesAsOfDate}` | (optional) re-point the Javadoc to `TrustLedgerQueryRepository#clientMatterBalances` |

Untouched in the module (checked): `TrustTransactionController` (reverse signature changes in 649B), `TrustTransactionTypeLabels` (646E), `ClientLedgerController` (the DTO shape comes from the service), `ClientTrustStatementContext` (consumes `getClientLedgerStatement`; no type sets), `TrustReportPackSeeder` (column kept), `CustomerTypeTotalRow`, `TrustMonthlyMovementProjection`, `ClientHistoryRow`, `InterestAllocationLine` (Javadoc mention only), `dto/MatterTrustBalanceResponse` (Javadoc mention only — optional re-point).

### Family 3 — readers outside the module (10 files)

| File | Uses today | Change (figure per §16.3.2 consumer table) |
|---|---|---|
| `$B/customerbackend/service/TrustLedgerPortalSyncService.java` | `signedAmount` sign switch `:547-557`; `isPortalEligible` `!REVERSAL` `:533-538`; Javadocs `:74, :236-240, :425, :540-544` | `signedAmount` → `txn.getLedgerEffect()`; eligibility = `TrustTransactionType.find(type).map(TrustTransactionType::clientVisible).orElse(false)` plus the existing client/matter checks (`REVERSAL.clientVisible = false` — T16); keep `PORTAL_STATUSES`; `onInterestPosted` stays (651A). |
| `$B/verticals/legal/statement/StatementOfAccountContextBuilder.java` | `TRUST_CREDIT_TYPES`/`TRUST_DEBIT_TYPES` `:102-116`, used `:598-606` | Classify each `LedgerStatementLine` by `line.ledgerEffect().signum()`: > 0 → deposits, < 0 → payments. Delete both sets (T15). |
| `$B/integration/accounting/sync/TrustBoundaryGuard.java` | `clientLedgerCardRepository.sumBalancesForCustomer` `:122` | `ledgerQueries.generalBalance(customerId)` (`CLIENT` ledger, all accounts, no date bound). Keep the `DataAccessException` catch. |
| `$B/verticals/legal/collections/TrustAwareCollectionsAdvisor.java` | `sumBalancesForCustomer` `:52` | `ledgerQueries.generalBalance(customerId)`; update the class Javadoc (`:22`, `:33`). |
| `$B/project/ProjectService.java` | `sumBalancesByCustomerIdIn` `:495` | `ledgerQueries.generalBalancesByClient(clientIds)` (the same accessor names on the row). |
| `$B/customer/CustomerService.java` | `sumBalancesByCustomerIdIn` `:329` | Same as ProjectService. |
| `$B/verticals/legal/closure/gates/TrustBalanceZeroGate.java` | `trustTransactionRepository.calculateBalanceByProjectId` `:48` | `ledgerQueries.matterBalance(project.getId())` (`CLIENT` + `CLIENT_INVESTMENT`). |
| `$B/integration/ai/skill/complianceaudit/ComplianceDataCollectorService.java` | `calculateTotalTrustBalance` `:286-287` | `ledgerQueries.clientLedgerTotal(account.getId(), null)`. |
| `$B/activity/ActivityMessageFormatter.java` | `case "INTEREST_CREDIT" -> "interest credit"` `:730` | Remove that case (646.7). Leave `:321-322` (`trust_interest_credit.recorded`) — not in the task; audit rows of that key may exist in history. |
| `$B/demo/seed/LegalDemoDataSeeder.java` | `saveTrustTxn` → `trustTransactionRepository.save` `:280-301` | §8.16.1: create the two anchors; deposits via `record`, payments via `recordAwaitingApproval` + `postOnApproval(…, createdBy)`; inject `TrustPostingService` (+ `ClientLedgerCardRepository`); drop the `TrustTransactionRepository` injection if unused. |

Untouched outside the module (checked): `DashboardService` (composes `getCashbookBalance` / `getTotalTrustBalance` and the in-place-rewritten trend query; Javadoc `:546` optional), `mcp/tool/TrustTools` (reads via `ClientLedgerService`; description is 646D), `PortalEmailNotificationChannel` (labels — 646E), `dto/MatterTrustBalanceResponse` (Javadoc only).

### Family 4 — fixtures and tests (40 files: 6 new, 34 changed)

**New (6)**

| File | Content |
|---|---|
| `$TT/transaction/TrustTransactionTypeRegistryTest.java` | Plain JUnit (no Spring). Every constant's attributes against a literal table; names ≤ 20; `label`/`clientLabel` non-blank, no `_`, not equal to `name()`; `of("NOPE")` → `InvalidStateException`; `of` of every `name()` round-trips; `effectFor` signs. `REVERSAL.effectFor` throws (no effect of its own). |
| `$TT/transaction/TrustTransactionTypeSchemaTest.java` | `@SpringBootTest` (pattern `TrustAccountingSchemaTest`): provision a tenant; `SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace WHERE c.conname = 'chk_trust_txn_type' AND n.nspname = ?`; parse the quoted names; assert the set equals `Arrays.stream(values()).map(Enum::name)`. |
| `$TT/transaction/TrustPostingRulesTest.java` | ArchUnit, `@AnalyzeClasses(packages = "io.b2mash.b2b.b2bstrawman", importOptions = ImportOption.DoNotIncludeTests.class)`; rules (a) and (b) of T10 (pattern §7.6). It lives in the `transaction` package, so it can name `TrustTransaction`/`TrustPostingService`. |
| `$TT/TrustPostingCoreMigrationTest.java` | Pattern `template/ContainsTrustDataMigrationTest` (§7.7): scratch schema; tenant Flyway `target("15")`; seed old shapes with raw SQL; migrate `target("16")`; assert acceptance criterion 3. |
| `$TT/ledger/DerivedLedgerBalanceIntegrationTest.java` | Harness-backed (use `TrustBooksFixture`): fresh account per method; post via the services; compare `TrustLedgerQueryRepository` figures (client, matter cell, `generalBalancesByClient`, `cashbook`, `clientLedgerTotal`, `clientBalances`) with the oracle (`TrustBooksHarness.snapshot` / `clientLedger`); a client whose rows are posted through `TrustPostingService.record` with **no** anchor appears in `ClientLedgerService.listClientLedgers` and `ClientTrustBalancesQuery`; `@AfterAll assertFirmBalanced`. |
| `$T/demo/seed/LegalDemoTrustBooksIntegrationTest.java` | Provision a tenant (pattern: characterisation path 11 — `provisioningService.provisionTenant(slug, …)`, `organizationRepository.findByExternalOrgId(slug)`, `legalDemoDataSeeder.seed(schema, orgId)`); inside `ScopedValue.where(RequestScopes.TENANT_ID, schema)`: `harness.assertBalanced(account)`, snapshot cashbook 27000.00 / clientLedgers 27000.00; derived per-client balances = {30000.00, −3000.00}; exactly one negative. |

**Changed (34)** — the reason column is what forces the edit.

| # | File (`$T/…`) | Forced by | Change |
|---|---|---|---|
| 1 | `testutil/TrustBooksHarness.java` | v0 SQL reads `client_ledger_cards.balance` | Internals → effect columns (§10.2); add EQ-6; Javadoc "Version 1 (646A)". Public API unchanged. |
| 2 | `testutil/TrustBooksFixture.java` | `saveDirect` writes posted rows without `posted_at` | Delete `saveDirect` (H-b retired) or re-implement it through `TrustPostingService` (§10.4). `handSetApproved` stays (RECORDED → APPROVED on a posted row passes the CHECK). |
| 3 | `testutil/TestTrustBalanceFactory.java` | `new ClientLedgerCard` + `addDeposit` | New signature `seedTrustBalance(TrustAccountRepository, TrustPostingService, UUID customerId, BigDecimal amount, UUID recordedBy)`: save the INVESTMENT account, then `postingService.record(new TrustTransaction(acc, "DEPOSIT", amount, customerId, null, null, "TEST-TRUST-…", "Test trust balance", LocalDate.now(), "RECORDED", recordedBy))`. Tests may use `LocalDate.now()` (ArchUnit imports main classes only), but prefer a fixed past date. |
| 4 | `verticals/legal/trustaccounting/TrustEquationCharacterisationIntegrationTest.java` | path 11 pin | Flip path 11 only (§10.3). |
| 5 | `verticals/legal/trustaccounting/transaction/TrustReversalEquationIntegrationTest.java` | H-b uses `saveDirect` | Close H-b (§10.4); drop the `cardless` tenant if it is unused; H-a/H-c/H-d byte-identical. |
| 6 | `collections/CollectionsReadApiTest.java` | factory signature | Pass `TrustPostingService` + `ownerMemberId`; drop the `ClientLedgerCardRepository` autowire. |
| 7 | `collections/CollectionsTriageServiceTest.java` | factory signature; Javadoc `:39` says "seed a real ClientLedgerCard" | Same as #6. |
| 8 | `customer/ClientListRowIntegrationTest.java` | factory signature (2 calls `:236, :241`) | Pass the posting service + `ownerId`. |
| 9 | `project/MatterListRowIntegrationTest.java` | factory signature `:244` | Same. |
| 10 | `integration/accounting/sync/TrustBoundaryGuardUnitTest.java` | mocks `sumBalancesForCustomer` `:41, :57` | Mock `TrustLedgerQueryRepository.generalBalance`. |
| 11 | `integration/ai/skill/complianceaudit/ComplianceDataCollectorServiceTest.java` | mocks `calculateTotalTrustBalance` `:117` | Mock `clientLedgerTotal(id, null)`. |
| 12 | `verticals/legal/closure/gates/TrustBalanceZeroGateTest.java` | mocks `calculateBalanceByProjectId` `:43-96` | Mock `TrustLedgerQueryRepository.matterBalance`. |
| 13 | `verticals/legal/collections/TrustAwareCollectionsAdvisorTest.java` | mocks `sumBalancesForCustomer` `:36-65` | Mock `generalBalance`. |
| 14 | `integration/accounting/sync/TrustBoundaryGuardTest.java` | `new ClientLedgerCard` + `addDeposit` `:260-261`; direct APPROVED `PAYMENT` `:186-194` | Post through `TrustPostingService` (deposit `record`; payment `recordAwaitingApproval` + `postOnApproval`). |
| 15 | `customerbackend/service/TrustLedgerPortalSyncServiceIntegrationTest.java` | direct APPROVED/RECORDED rows `:426-436, :472-482, :532-561, :598-621`; `INTEREST_CREDIT` row `:285-300` | Helpers `recordApprovedTransaction` / `recordRecordedTransaction` post via `TrustPostingService`. Rewrite `interestPostedEventUpsertsInterestCreditRow` as "interest-posted fan-out re-projects the account" with a `DEPOSIT` row (the listener lives until 651A), or delete it and name it in the PR. |
| 16 | `verticals/legal/statement/StatementOfAccountContextBuilderTest.java` | `LedgerStatementLine` constructor `:795-815, :1534`; `INTEREST_*` lines `:313-319`, comments `:336-338` | Pass `ledgerEffect` (signed); remove the `INTEREST_*` lines and re-count the expected deposits/payments; keep `DISBURSEMENT_PAYMENT` as a negative-effect line if it stays. |
| 17 | `project/MatterTrustBalanceEndpointTest.java` | direct RECORDED unmattered `DEPOSIT` `:144-160` | `postingService.record(new TrustTransaction(..., null /*matter*/, ...))` — expectations unchanged (the unmattered row counts on no matter). |
| 18 | `verticals/legal/disbursement/DisbursementServiceTest.java` | direct APPROVED `PAYMENT` `:711-726` (`seedApprovedTrustTransaction`) | Post via `recordAwaitingApproval` + `postOnApproval`. The `AWAITING_APPROVAL` direct write at `:640-650` is unposted and passes the CHECK — leave it. |
| 19 | `activity/ActivityMessageFormatterTest.java` | `trust_interest_credit.approved` → "Alice approved the interest credit" `:1649-1650` | Expected text becomes the `default` noun ("… the trust transaction") — or delete the case; `:1712` (`.recorded` key) stays. |
| 20 | `verticals/legal/trustaccounting/TrustAccountingSchemaTest.java` | `clientLedgerCardBalanceConstraintPreventsNegative` inserts dropped columns `:88-115` | Delete it (named in the PR) or replace it with "`client_ledger_cards` has only the anchor columns". The 10-table list stays. |
| 21 | `verticals/legal/trustaccounting/interest/InterestServiceIntegrationTest.java` | `getBalance()` `:640-648, :694-702` | Pre/post balance via `TrustLedgerQueryRepository.clientBalance(account, customer, null)` (or the harness). |
| 22 | `…/interest/InterestMatterAllocationIntegrationTest.java` | `legacyApprovedPayment` direct APPROVED `PAYMENT` `:461-482` | Post via `recordAwaitingApproval` + `postOnApproval` (no sufficiency check — the overdraft shape is kept). |
| 23 | `…/investment/TrustInvestmentPlacementGuardIntegrationTest.java` | `calculateBalanceByProjectId` `:422`; card `getBalance` `:432` | `ledgerQueries.matterBalance`, `clientBalance(…, null)`. |
| 24 | `…/investment/TrustInvestmentWithdrawReverseRaceIntegrationTest.java` | same `:313, :323` | Same (the race expectations are unchanged — R-7). |
| 25 | `…/ledger/ClientLedgerControllerTest.java` | `jsonPath("$.totalInterestCredited")` `:185` | Remove that line (or assert `doesNotExist()`). |
| 26 | `…/ledger/ClientLedgerRunningBalanceIntegrationTest.java` | own `CREDIT_TYPES`/`DEBIT_TYPES` oracle `:78-81, :230`; `calculateClientBalanceAsOfDate` `:198` | The oracle may stay type-based (drop the `INTEREST_*` names) — it is an independent oracle; the balance call → `ledgerQueries.clientBalance(acc, cust, date)`. |
| 27 | `…/ledger/ClientLedgerTotalsIntegrationTest.java` | `INTEREST_CREDIT` row + `addInterestCredit` `:361-390`; `totalInterestCredited` asserts `:152, :163, :253` | Delete the interest case and its asserts (name them in the PR); keep the reconciling identity without interest. |
| 28 | `…/ledger/LedgerTotalsTest.java` | `interestCredited`, `plus("INTEREST_CREDIT")` `:22-27, :76`; `new LedgerTotals(7 args)` `:36` | Drop the interest component; the `INTEREST_LPFF` "ignored" case becomes "unknown type leaves totals unchanged". |
| 29 | `…/transaction/TrustClientMatterRequiredIntegrationTest.java` | direct APPROVED `PAYMENT` + `card.addPayment` `:373-389`; `getBalance` `:508` | Post via the posting service; balance via `ledgerQueries`. |
| 30 | `…/transaction/TrustFeeTransferReversalCascadeTest.java` | `ledgerBalance` helper `getBalance` `:745-750`; hand-set AWAITING → APPROVED on a `PAYMENT` `:788-791` (**violates the CHECK**) | Approve through `transactionService.approveTransaction` as the second owner (the `approve` helper already exists at `:738`) or `postingService.postOnApproval`; balance via `ledgerQueries`. |
| 31 | `…/transaction/TrustMatterDebitGuardIntegrationTest.java` | unmattered `INTEREST_CREDIT` + `addInterestCredit` `:524-553`; `calculateBalanceByProjectId` `:612`; `getBalance` `:627` | Replace the interest seed with an unmattered `DEPOSIT` posted via `postingService.record` (same money shape — client-level credit, no matter), or delete that case; balances via `ledgerQueries`. |
| 32 | `…/transaction/TrustPostingDateZoneIntegrationTest.java` | hand-set AWAITING → APPROVED on a `FEE_TRANSFER` `:210-213` (**violates the CHECK**); `getLastTransactionDate` `:219-224, :393-397` | Approve via `postOnApproval` (or the service as a second member); last date via `ClientLedgerService.getClientLedger(…).lastTransactionDate()` (derived — T8). |
| 33 | `…/transaction/TrustTransactionServiceTest.java` | ~20 card reads (`:303-310, :346-353, :564-575, :759-763, :849-853, :916-922, :993-1028, :1459-1467, :1573-1579, :1734-1738, :2087-2090`); `checkConstraint_preventsNegativeBalance` `:770-804`; hand-set AWAITING → APPROVED on a `PAYMENT` `:1769-1771` (**violates**) | Card reads → `ClientLedgerService.getClientLedger(…)` (balance, `totalDeposits`, `totalPayments`, `lastTransactionDate` are all on the response) or `ledgerQueries`; delete `checkConstraint_preventsNegativeBalance` (named in the PR); fix `:1770` (`approveNonAwaitingTransaction_returns400` — approve it properly once through the service as approver, then assert the second approve is refused). Rename `deposit_upsertsClientLedgerCardWithCorrectBalance` / `totalTrustBalance_equalsSumOfLedgerCardBalances` to say "derived". |
| 34 | `…/transaction/TrustTransferTargetMatterIntegrationTest.java` | `calculateBalanceByProjectId` `:312`; `getBalance` `:327` | `ledgerQueries`. |

Builder grep to re-confirm before editing (the list is the grep, not this table — OQ #4):
```bash
cd /Users/rakheendama/Projects/2026/worktree-epic-646A/backend/src/test/java
grep -rlE 'ClientLedgerCard|calculateTotalTrustBalance|sumBalancesFor|sumBalancesByCustomerIdIn|calculateBalanceByProjectId|calculateClientBalanceAsOfDate|calculateMatterClientBalance|calculateCashbookBalance|calculateClientMatterBalancesAsOfDate|INTEREST_CREDIT|INTEREST_LPFF|interestCredited|totalInterestCredited|seedTrustBalance|LedgerStatementLine\(|new LedgerTotals\(' --include='*.java' . | sort
grep -rn -B12 'Repository.save\(\|Repository.saveAndFlush\(' --include='*.java' . | grep -E '"(RECORDED|APPROVED|REVERSED)"' | sort -u   # direct posted writes
grep -rn 'setStatus("APPROVED")\|setStatus("RECORDED")\|setStatus("REVERSED")' --include='*.java' . # check each target row was already posted
```
Re-confirmed as needing **no** change: `TrustReportQueryIntegrationTest` (key `totalInterestCredited` kept), `TrustTransactionTypeLabelsTest` (646E), `TrustCoexistenceTest` (table list), `BaselineSchemaParityTest` (pinned to V1 — `tenant-baseline.snapshot` must NOT be re-recorded), `LegalDemoDataSeederTest` (status-agnostic), `InterestPeriodGuardIntegrationTest` (comment-only mentions), `InterestPostingGuardIntegrationTest` (title/detail unchanged).

### Family 5 — frontend (6 files)

| File | Change |
|---|---|
| `frontend/components/trust/ledger-totals-breakdown.tsx` | Remove `"totalInterestCredited"` from the `Pick` and the `{ key: "interest", sign: "+", label: "Interest", … }` row; Javadoc becomes "Deposits + Transfers in − Payments − Fee transfers − Refunds − Transfers out = Balance". |
| `frontend/lib/types/trust.ts` | `ClientLedgerCard`: delete `totalInterestCredited: number;` (`:135`) and fix the comment `:131-132`. **Do not** touch the `TrustTransactionType` union / label map / `isInflowType` (646E). |
| `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx` | Delete the "Total Interest" `<th>` (`:213-215`) and its `<td>` (`:251-253`) — `pnpm typecheck` fails otherwise. |
| `frontend/__tests__/trust/ledger-totals-breakdown.test.tsx` | `LEDGER`: drop `totalInterestCredited`, `balance: 3750`; the "no 'less'" test lists `["deposits", "balance"]`; add `expect(screen.queryByTestId("ledger-total-interest")).toBeNull()`. |
| `frontend/__tests__/trust-client-ledgers.test.tsx` | `:221-253` "shows Total Interest and Total Refunds columns" → "shows Total Refunds and no Total Interest column" (`queryByText("Total Interest")` null; drop the 1.34 assertions; fix `balance`/`totalRefunds` so the row still reconciles); remove `totalInterestCredited` from `makeLedgerCard` `:120`. |
| `frontend/__tests__/trust/client-ledger-page.test.tsx` | `:286-301`: title "Deposits − Payments − Fee transfers − Refunds = Balance"; replace `expect(total("interest"))…` with `expect(screen.queryByTestId("ledger-total-interest")).toBeNull()`; remove `totalInterestCredited` from the fixtures `:119, :320`. |

Leftover untyped fixture keys (`trust-tabs-settings.test.tsx:344,387,424`, `trust-coexistence.test.tsx:226,315`, `trust/trust-balance-card-matter-mode.test.tsx:80`) are plain object literals that are not typed `ClientLedgerCard`, so they neither break `typecheck` nor change behaviour. Leave them, for minimal impact. No Playwright spec references the interest total or column (grep of `frontend/e2e`, `portal/e2e`: none).


## Build-time re-grep (stage A — families 1–3, main code)

- Tenant migration series V1…V15 confirmed at build time: **V16 is next free**, taken as `V16__phase92_trust_posting_core.sql`.
- Main files actually changed: **34 Java** (6 new, 27 changed, 1 deleted — `ClientTrustBalanceProjection`) + **1 migration** = 35. That is the plan's 31 + the 3 optional Javadoc-only files, all three taken because the §13.0 grep flags them: `transaction/MatterBalanceRow.java`, `dto/MatterTrustBalanceResponse.java` (both family 2), `dashboard/DashboardService.java` (family 3).
- No main file outside the plan was needed. Family 4 (tests, 40) and family 5 (frontend, 6) are stage B. Running total stays at the ruled ≈ 81.

## Build-time re-grep (stage B — family 4 tests, family 5 frontend)

- Tests: **41** (6 new + 35 changed) — the plan's 34 changed + `TrustInvestmentSchemaTest` (OQ #4 extra: its raw-SQL setup inserts a `RECORDED` deposit, which `chk_trust_txn_posting` now refuses without `posted_at`; the insert now carries the effect columns).
- Beyond the brief's hand-set list, three more hand-set statuses violated the CHECK and were rewritten through the product: `TrustTransactionServiceTest` (`RECORDED → AWAITING_APPROVAL` deposit in `reverseAwaitingApprovalTransaction_throwsInvalidStateException`, `RECORDED → REJECTED` deposit in `cashbookBalance_excludesRejectedTransactions`) and `TrustPostingDateZoneIntegrationTest` (`AWAITING_APPROVAL → APPROVED` refund in `refundReversal_isDatedSastToday`).
- Frontend: **6** as planned.
- Total diff vs `e0b2be311`: **82 code files** (35 main incl. migration + 41 test + 6 frontend) + this evidence file.
