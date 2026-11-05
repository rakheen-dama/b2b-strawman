# 633B — §5b row 19 (backend) + G88-F31 DTO reproduction at HEAD (builder, 2026-11-05)

Worktree `/Users/rakheendama/Projects/2026/worktree-epic-633B`, branch
`phase89/04B-deadline-events-backfill` @ `5566c72b12c98d6b9e6d0aa9d48e30d83f190cda` (identical to
`main`). The new tests were written first and run **before any `src/main` change**. Embedded Postgres
(zonky), `test` profile.

## 1. Live path — court date / tracker created through the services (runtime red)

`DeadlinePortalSyncIntegrationTest` (initial version, HEAD APIs only: `CourtCalendarService`,
`PrescriptionTrackerService`, `PortalDeadlineViewRepository`). Legal-za tenant, matter owned by
client A (`projects.customer_id = A`), co-client B linked via `customer_projects`.

```
$ cd backend && ./mvnw test -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest="DeadlinePortalSyncIntegrationTest" -q > /tmp/mvn-epic-633B-red.log 2>&1     # exit=1

[ERROR] Tests run: 3, Failures: 1, Errors: 2, Skipped: 0, Time elapsed: 10.99 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.customerbackend.service.DeadlinePortalSyncIntegrationTest
[ERROR] ...DeadlinePortalSyncIntegrationTest.trackerCreateProjectsAPrescriptionRow -- Time elapsed: 0.314 s <<< ERROR!
[ERROR] ...DeadlinePortalSyncIntegrationTest.createCourtDate_projectsAPortalRowForTheOwningClient -- Time elapsed: 0.016 s <<< ERROR!
[ERROR] ...DeadlinePortalSyncIntegrationTest.cancelDeletesTheRow_precondition_rowExistsAfterCreate -- Time elapsed: 0.040 s <<< FAILURE!
[ERROR] Failures:
[ERROR]   DeadlinePortalSyncIntegrationTest.cancelDeletesTheRow_precondition_rowExistsAfterCreate:132
[ERROR] Errors:
[ERROR]   DeadlinePortalSyncIntegrationTest.createCourtDate_projectsAPortalRowForTheOwningClient:118->courtRow:180 » NoSuchElement No value present
[ERROR]   DeadlinePortalSyncIntegrationTest.trackerCreateProjectsAPrescriptionRow:150 » NoSuchElement No value present
[ERROR] Tests run: 3, Failures: 1, Errors: 2, Skipped: 0
```

Failure bodies (surefire XML, `<testcase>` elements = 3):
```
<error message="No value present" type="java.util.NoSuchElementException">   (createCourtDate → findByCustomerIdAndSourceEntityAndId(A, COURT_DATE, id).orElseThrow())
<error message="No value present" type="java.util.NoSuchElementException">   (tracker create → findByCustomerIdAndSourceEntityAndId(A, PRESCRIPTION_TRACKER, id).orElseThrow())
<failure message="Expecting Optional to contain a value but it was empty." type="java.lang.AssertionError">
```

=> a court date created through `CourtCalendarService.createCourtDate` (and a prescription tracker
created through `PrescriptionTrackerService.create`) on a client-owned matter writes **no**
`portal.portal_deadline_view` row. Red for the right reason: nothing publishes, nothing listens.

```
$ grep -rn CourtDateScheduledEvent backend/src/main | wc -l
1        # the only hit is the Javadoc of DeadlinePortalSyncService saying the event does "not yet exist"

$ sed -n 123,124p backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/service/DeadlinePortalSyncService.java
  // TODO (Phase 55): wire onCourtDateScheduled / onCourtDateCancelled and onPrescriptionDeadlineSet
  //   once their respective domain events are added to the sealed DomainEvent interface.
```

## 2. Existing tenants (backfill) + trust `matterName` (compile red)

`PortalDeadlineBackfillRunnerTest` and `PortalTrustReadModelRepositoryTest` reference types / members
that do not exist at HEAD:

```
$ ./mvnw compile test-compile -q > /tmp/mvn-epic-633B-red-compile.log 2>&1     # exit=1
[ERROR] src/test/java/.../customerbackend/repository/PortalTrustReadModelRepositoryTest.java:[47,27] cannot find symbol
[ERROR] src/test/java/.../customerbackend/repository/PortalTrustReadModelRepositoryTest.java:[52,21] invalid method reference
[ERROR] src/test/java/.../customerbackend/repository/PortalTrustReadModelRepositoryTest.java:[64,21] invalid method reference
[ERROR] src/test/java/.../customerbackend/service/PortalDeadlineBackfillRunnerTest.java:[82,22] cannot find symbol
[ERROR] src/test/java/.../customerbackend/service/PortalDeadlineBackfillRunnerTest.java:[97,11] cannot find symbol
[ERROR] src/test/java/.../customerbackend/service/PortalDeadlineBackfillRunnerTest.java:[240,38] cannot find symbol
  symbol:   method matterName()                       (PortalTrustBalanceView — no matter name at HEAD: G88-F31)
  symbol:   class PortalDeadlineBackfillRunner        (no deadline backfill at HEAD)
  symbol:   class PortalDeadlineBackfillService
  symbol:   variable PortalDeadlineBackfillRunner
  symbol:   method courtDateSource(java.util.UUID,java.util.UUID,java.util.UUID,java.lang.String,java.time.LocalDate,java.lang.String,<nulltype>,<nulltype>)
```

=> no runner projects pre-existing court dates / trackers into the portal (§5b row 19, existing
tenants), and the portal trust summary has no `matterName` (G88-F31 DTO half).

Implementation started only after this capture.
