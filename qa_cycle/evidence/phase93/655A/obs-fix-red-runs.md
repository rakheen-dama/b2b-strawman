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
