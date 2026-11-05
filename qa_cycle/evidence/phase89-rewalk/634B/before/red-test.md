# 634B red evidence (G88-M13, G88-M2, G-B8) — run at HEAD 7b7509f87, before any fix

Only test files were added/extended for these runs; no production code (`backend/src/main`, `frontend/components`, `frontend/app`, `frontend/lib`) had been changed.

## G88-M13 — Overview tiles

### Backend — OutstandingSummaryMatterFilterTest + MatterTrustBalanceEndpointTest (new test files only)

Command: `./mvnw test -Dtest="OutstandingSummaryMatterFilterTest,MatterTrustBalanceEndpointTest" -q` → TARGETED TESTS FAILED

```
[ERROR] Tests run: 7, Failures: 7, Errors: 0, Skipped: 0, Time elapsed: 13.41 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.project.MatterTrustBalanceEndpointTest
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 3.362 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.collections.OutstandingSummaryMatterFilterTest
[ERROR]   OutstandingSummaryMatterFilterTest.matterFilter_returnsOnlyThatMattersFeeNotes:107
            org.opentest4j.AssertionFailedError: expected: 1  but was: 2      (outstandingCount for ?matterId=A)
[ERROR]   OutstandingSummaryMatterFilterTest.matterWithNoLines_returnsZeros:126
            org.opentest4j.AssertionFailedError: expected: 0  but was: 2      (outstandingCount for ?matterId=<matter with no lines>)
[ERROR]   MatterTrustBalanceEndpointTest.matterWithoutTransactions_isZero:183->getOk:270 Status expected:<200> but was:<404>
[ERROR]   MatterTrustBalanceEndpointTest.memberOnTheMatter_seesIt:191->getOk:270 Status expected:<200> but was:<404>
[ERROR]   MatterTrustBalanceEndpointTest.nonMember_gets404ProblemDetail:197 JSON path "$.title" expected:<Matter not found> but was:<Not Found>
[ERROR]   MatterTrustBalanceEndpointTest.owner_getsOnlyThatMattersBalance:172->getOk:270 Status expected:<200> but was:<404>
[ERROR]   MatterTrustBalanceEndpointTest.trustModuleOff_still200WithZero:224->getOk:270 Status expected:<200> but was:<404>
[ERROR]   MatterTrustBalanceEndpointTest.unknownMatter_gets404ProblemDetail:205 JSON path "$.title" expected:<Matter not found> but was:<Not Found>
[ERROR]   MatterTrustBalanceEndpointTest.withoutViewTrust_gets403:216 Status expected:<403> but was:<404>
[ERROR] Tests run: 11, Failures: 9, Errors: 0, Skipped: 0
```

Reading: `GET /api/invoices/outstanding-summary` ignores `?matterId=` — matter A, and a matter with no fee-note lines at all, both report the firm-wide two fee notes (`noParam_staysFirmWide` and the FINANCIAL_VISIBILITY 403 case are green at HEAD, as they should be). `GET /api/matters/{matterId}/trust-balance` does not exist: every call is Spring's generic 404 ("Not Found" / no static resource), including the VIEW_TRUST 403 case — `TrustTransactionRepository.calculateBalanceByProjectId` has no endpoint.

### Frontend — vitest `frontend/__tests__/components/projects/kpi-dashboard.test.tsx` (+ `frontend/__tests__/invoices/outstanding-summary-action.test.ts`)

Command: `pnpm vitest run __tests__/components/projects/kpi-dashboard.test.tsx components/projects/project-members-panel.test.tsx __tests__/activity/activity-feed.test.tsx __tests__/invoices/outstanding-summary-action.test.ts` → exit 1 (same run as the G88-M2 / G-B8 sections below). For this run only, `defaultProps` also carried the HEAD prop `disbursementsEnabled: true` so the HEAD tile renders and shows its real symptom (removed afterwards — the prop no longer exists).

```
 FAIL  __tests__/components/projects/kpi-dashboard.test.tsx > KPIDashboard > renders the matter's outstanding fee-note balance (G88-M13)
Expected element to have text content:
  R 3 450,00
Received:
  --Outstanding fee notes
 FAIL  __tests__/components/projects/kpi-dashboard.test.tsx > KPIDashboard > renders R 0,00 when the matter owes nothing
Expected element to have text content:
  R 0,00
Received:
  --Outstanding fee notes
 FAIL  __tests__/components/projects/kpi-dashboard.test.tsx > KPIDashboard > hides the fee-note tile without FINANCIAL_VISIBILITY (absent) — never '--'
Error: expect(element).not.toBeInTheDocument()  expected document not to contain element, found <div …
 FAIL  __tests__/components/projects/kpi-dashboard.test.tsx > KPIDashboard > renders the matter trust balance when trust accounting is on
Expected element to have text content:
  R 50 000,00
Received:
  --Trust balance
 FAIL  __tests__/components/projects/kpi-dashboard.test.tsx > KPIDashboard > hides the trust tile when the read is absent (no VIEW_TRUST)
Error: expect(element).not.toBeInTheDocument()  expected document not to contain element, found <div …
 FAIL  __tests__/invoices/outstanding-summary-action.test.ts > fetchOutstandingSummaryAction (Phase 88 620C) > matterId → narrows the summary with ?matterId= (Phase 89 G88-M13)
  1st vi.fn() call:
-   "/api/invoices/outstanding-summary?matterId=m-1",
+   "/api/invoices/outstanding-summary",
```

Reading: `kpi-dashboard.tsx:206-221` hardcodes `value: "--"` for both money tiles regardless of data, and gates them on module flags only (so a caller without the capability still sees a "--" tile); the summary action has no matter parameter.

## G88-M2 — Team tab badge

Vitest `frontend/components/projects/project-members-panel.test.tsx` (colocated), same run:

```
       × owner who is lead shows Lead, with Owner as secondary text (G88-M2) 10ms
       × admin on the matter as member shows the Member badge with Admin as secondary text 5ms
       × owner on the matter as member shows the Member badge with Owner as secondary text 5ms
 FAIL  … > owner who is lead shows Lead, with Owner as secondary text (G88-M2)
TestingLibraryElementError: Unable to find an element with the text: Lead.
 FAIL  … > admin on the matter as member shows the Member badge with Admin as secondary text
TestingLibraryElementError: Unable to find an element with the text: Member.
 FAIL  … > owner on the matter as member shows the Member badge with Owner as secondary text
TestingLibraryElementError: Unable to find an element with the text: Member.
```

Reading: `project-members-panel.tsx:164-169` replaces the matter role with the org role for admins/owners, so the owner who leads the matter renders only an "Owner" badge — the "Lead" badge never appears in the row.

## G-B8 — Activity "Client portal" chip

### Backend — ActivityMessageFormatterTest (two cases added) — compile red

Command: `./mvnw compile test-compile -q` → COMPILE FAILED

```
[ERROR] COMPILATION ERROR :
[ERROR] …/activity/ActivityMessageFormatterTest.java:[1904,20] cannot find symbol
[ERROR] …/activity/ActivityMessageFormatterTest.java:[1905,20] cannot find symbol
[ERROR] …/activity/ActivityMessageFormatterTest.java:[1916,20] cannot find symbol
[ERROR] …/activity/ActivityMessageFormatterTest.java:[1917,20] cannot find symbol
[ERROR]   symbol:   method actorType()   (×2)
[ERROR]   symbol:   method source()      (×2)
```

Reading: `ActivityItem.java:10-19` has no `actorType` / `source` components — the formatter drops the audit row's `actor_type` / `source` (which `AcceptanceService` sets to `PORTAL_CONTACT` / `PORTAL`), so the feed cannot tell a client-portal action from a firm action.

### Frontend — vitest `frontend/__tests__/activity/activity-feed.test.tsx`, same run

```
     × marks the client-portal row with a 'Client portal' chip and no firm row (G-B8) 16ms
     × marks a PORTAL-source row even when the actor is SYSTEM 8ms
 FAIL  __tests__/activity/activity-feed.test.tsx > ActivityFeedClient > marks the client-portal row with a 'Client portal' chip and no firm row (G-B8)
TestingLibraryElementError: Unable to find an element by: [data-testid="activity-item"]
 FAIL  __tests__/activity/activity-feed.test.tsx > ActivityFeedClient > marks a PORTAL-source row even when the actor is SYSTEM
TestingLibraryElementError: Unable to find an element by: [data-testid="activity-item"]
 Test Files  4 failed (4)
      Tests  11 failed | 41 passed (52)
```

Reading: `activity-item.tsx` renders no source marker (and no per-row test id) — a PORTAL_CONTACT acceptance row looks identical to a firm row.
