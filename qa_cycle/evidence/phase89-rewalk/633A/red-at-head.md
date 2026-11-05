# 633A — G88-F19 reproduction at HEAD (builder, 2026-11-05 ~02:50 SAST)

Worktree `phase89/04A-portal-tasks-global-v3` @ `c1c9172d9` (identical to `main`). The new tests were
written first and run **before any `src/main` change**. Embedded Postgres (zonky), `test` profile.

## 1. Live path + global V3 — T1, T2 (red) and T4 (characterisation, green)

```
$ cd backend && ./mvnw test -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest="TemplateTaskPortalProjectionIntegrationTest,GlobalV3BackfillMarkersTest,TaskServicePortalEventTest" \
    -q > /tmp/mvn-epic-633A.log 2>&1        # exit=1

[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 -- in ...projecttemplate.TemplateTaskPortalProjectionIntegrationTest
[ERROR] ...TemplateTaskPortalProjectionIntegrationTest.instantiatingOnAClientLinkedMatterProjectsEveryTemplateTaskInTemplateOrder <<< FAILURE!
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0 -- in ...customerbackend.GlobalV3BackfillMarkersTest
[ERROR]   TemplateTaskPortalProjectionIntegrationTest.instantiatingOnAClientLinkedMatterProjectsEveryTemplateTaskInTemplateOrder:127
[ERROR]   GlobalV3BackfillMarkersTest.migrateScratchGlobalFromV2:74 » Flyway No migration with a target version 3 could be found. Ensure target is specified correctly and the migration exists.
[ERROR] Tests run: 11, Failures: 1, Errors: 1, Skipped: 0
```

T1 failure body (surefire XML):
```
Expecting actual:
  []
to contain exactly (and in same order):
  ["Initial consultation & case assessment",
    "Letter of demand",
    "Issue summons / combined summons",
    "File plea / exception / counterclaim",
    "Discovery -- request & exchange documents",
    "Pre-trial conference preparation",
    "Trial / hearing attendance",
    "Post-judgment -- taxation of costs / appeal",
    "Execution -- warrant / attachment"]
```
=> a 9-task legal-za template ("Litigation (Personal Injury / General)") instantiated through
`ProjectTemplateService.instantiateTemplate` on a client-linked matter projects **0 of 9** tasks into
`portal.portal_tasks` (expected 9, got 0). This is G88-F19: the portal only shows tasks that were later
edited (the `TaskService` update paths publish), hence "2 of 9" in the browser walk.
The second T1 case (no client → 0 rows) passed at HEAD, as expected.

Per-class surefire counts (`<testcase>` elements):
```
TaskServicePortalEventTest                      testcases=8 errors=0 failures=0   (green — guards the extraction)
TemplateTaskPortalProjectionIntegrationTest     testcases=2 errors=0 failures=1   (red)
GlobalV3BackfillMarkersTest                     testcases=1 errors=1 failures=0   (red — @BeforeAll: no global V3)
```

## 2. Existing tenants — T3 (red = "no runner at HEAD")

`PortalTaskBackfillRunnerTest` / `PortalTaskBackfillRunnerUnitTest` cannot compile at HEAD:
```
$ ./mvnw compile test-compile -q     # exit=1
[ERROR] src/test/java/.../customerbackend/service/PortalTaskBackfillRunnerTest.java:[17,38] cannot find symbol
[ERROR] src/test/java/.../customerbackend/service/PortalTaskBackfillRunnerUnitTest.java:[13,60] cannot find symbol
  symbol:   class PortalBackfillMarkerRepository
  symbol:   class PortalTaskBackfillRunner
  symbol:   class PortalTaskBackfillService
  symbol:   class PortalTaskEventPublisher
  symbol:   variable PortalTaskBackfillRunner
```
=> there is no mechanism at HEAD that projects the tasks of already-instantiated matters on existing tenants.

## 3. Browser "2 of 9"

Orchestrator-owned (`qa_cycle/evidence/phase89-rewalk/633A/before/`), not taken by the builder.
