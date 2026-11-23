# 655A OBS fixes: red runs (written first, run against head 529740894 before any fix)

Both defects reproduced by a failing test on the unfixed code (root CLAUDE.md §4).

## OBS-655A-1: a row that matches an existing client is not marked, and no filter lists those rows

### Backend: the row list cannot narrow to the duplicates the card counts

Test: `ClientsTakeOnIntegrationTest#theDuplicatesFilter_listsExactlyTheValidRowsTheDuplicatesCardCounts`

```
cd backend && ./mvnw test -Dtest='ClientsTakeOnIntegrationTest#theDuplicatesFilter_listsExactlyTheValidRowsTheDuplicatesCardCounts+aRefusedRiskRatingOrSourceOfFunds_isNotReplacedByTheBlankDefault' -q
[ERROR] Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
[ERROR]   ClientsTakeOnIntegrationTest.theDuplicatesFilter_listsExactlyTheValidRowsTheDuplicatesCardCounts:921
Expecting actual:
  ["DF-A", "DF-B"]
to contain exactly (and in same order):
  ["DF-B"]
but some elements were not expected:
  ["DF-A"]
```

`GET …/rows?status=VALIDATED&duplicatesOnly=true` ignored the flag and returned every valid row.

### Frontend: label, message and filter

Tests: `__tests__/take-on/take-on-wizard.test.tsx` > "marks a valid row that matches an existing client and filters to those rows (OBS-655A-1)"; `__tests__/take-on/take-on-actions.test.ts` > "asks for the rows that match an existing record with duplicatesOnly (OBS-655A-1)"

```
cd frontend && pnpm exec vitest run __tests__/take-on/take-on-wizard.test.tsx __tests__/take-on/take-on-actions.test.ts
 FAIL  __tests__/take-on/take-on-actions.test.ts > fetchTakeOnRows — query > asks for the rows that match an existing record with duplicatesOnly (OBS-655A-1)
AssertionError: expected "vi.fn()" to be called with arguments: [ Array(1) ]
-   "/api/take-on/batches/b1/rows?status=VALIDATED&duplicatesOnly=true&page=0&size=50",
+   "/api/take-on/batches/b1/rows?status=VALIDATED&page=0&size=50",

 FAIL  __tests__/take-on/take-on-wizard.test.tsx > TakeOnWizard — validation > marks a valid row that matches an existing client and filters to those rows (OBS-655A-1)
Expected element to have text content:
  Duplicate — will be skipped
Received:
  Valid
 ❯ __tests__/take-on/take-on-wizard.test.tsx:614:64
 Test Files  2 failed (2)
      Tests  2 failed | 28 passed (30)
```

## OBS-655A-2: a refused risk rating is stored and shown as "Not risk-rated"

Test: `ClientsTakeOnIntegrationTest#aRefusedRiskRatingOrSourceOfFunds_isNotReplacedByTheBlankDefault` (same run as above)

```
[ERROR]   ClientsTakeOnIntegrationTest.aRefusedRiskRatingOrSourceOfFunds_isNotReplacedByTheBlankDefault:953
Expecting actual:
  {"addressLine1"="1 Main Road", "beneficialOwnershipStatus"="NOT_APPLICABLE", "cddCompletedDate"="2024-02-01", "cddRecordsLocation"="Archive box 1", "city"="Johannesburg", "country"="ZA", "email"="rating.refused@example.test", "ficaClientType"="NATURAL_PERSON", "name"="Rating Refused", "riskRating"="UNRATED", "screeningDate"="2024-02-01", "screeningStatus"="SCREENED_CLEAR", "sourceOfFundsStatus"="NOT_REQUIRED", "sourceRef"="RR-1"}
not to contain key:
  "riskRating"
```

The row whose file says `VERY_HIGH` (ERROR, `ENUM_INVALID` on `riskRating`, which passed) had `riskRating = UNRATED` in its mapped values. The test stops at the first failed assertion. The source-of-funds half of the same test, which maps the "Rating" column to `sourceOfFundsStatus`, runs after the fix.

## Green after the fix

- Both backend tests pass (the source-of-funds half too). Tier 2 (`takeon/**` plus `TakeOnWireContractTest`, the architecture scanners `LayerDependencyRulesTest`, `TenantScopeBindingTest`, `TestConventionsTest`, `PortalLinkBuilderArchTest`, `ZoneAwareTimeArchTest`, `TakeOnEntryPointsArchTest`, `TrustPostingRulesTest`, `project/WireVocabularyContractTest`, `LegalOnlyProfileReplacementTest`, `AiFoundationWithoutAssistantTest`, `ClientLedgerControllerTest`): **284 testcases, 0 failures, 0 errors, 0 skipped**, 29 classes (surefire XML `<testcase>` count).
- FE-GATE from `frontend/`: lint 0 errors (77 warnings, none in take-on files) · typecheck 0 · format:check clean · vitest **494 files, 4756 passed, 2 skipped** · build 0.
- Decisions: D-131, D-132, D-133 in `tasks/phase93-decisions.md`.

## Delta review M-1: anonymising a client redacts another live client's take-on rows by a shared old email

Test: `ClientsTakeOnIntegrationTest#anonymisingAClientWhoseEarlierEmailAnotherClientNowHolds_leavesThatClientsRowsAlone` (#18). Real write paths: A taken on with `first.address@`, A's email changed to `shared.address@` then `own.address@` through `PUT /api/clients/{id}` (the `customer.updated` audit rows), B taken on with `shared.address@` (CREATED), a later batch row for the same address (SKIPPED_DUPLICATE, matched to B), then `POST /api/clients/{A}/anonymize`.

Command (on head 07c556e1e + the new test, before the fix):

```
cd backend && ./mvnw test -Dtest='ClientsTakeOnIntegrationTest#anonymisingAClientWhoseEarlierEmailAnotherClientNowHolds*' -Dsurefire.failIfNoSpecifiedTests=false
```

```
[ERROR]   ClientsTakeOnIntegrationTest.anonymisingAClientWhoseEarlierEmailAnotherClientNowHolds_leavesThatClientsRowsAlone:1265 [the other client's take-on row keeps its values]
Expecting actual:
  "{"redacted": true}"
to contain:
  "shared.address@example.test"
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

B's CREATED row was redacted by A's anonymisation. A first draft of the test (A taken on directly with the shared address) could not reach the scenario: B's row FAILED at commit ("This row could not be committed…") because A's CREATED row keeps `dedup_key = email:shared.address@` after the email change and `uq_take_on_rows_created_key` rejects B's — so the test takes A on with a different address first (D-134).

Green after the fix: the same command, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

Tier 2 after the fix (`takeon/**`, `datarequest/*`, the customer tests that use `CustomerRepository`, `CustomerLifecycleEntityTest`, `retention/RetentionServiceTest` + `RetentionControllerTest`, `PortalContactDirectoryIntegrationTest`, `AuditEventTypeRegistryTest`, `architecture/*`, `project/WireVocabularyContractTest` and every other `@AnalyzeClasses`/`ClassFileImporter` scanner): **54 classes, 508 testcases, 0 failures, 0 errors, 0 skipped** (surefire XML `<testcase>` count).
