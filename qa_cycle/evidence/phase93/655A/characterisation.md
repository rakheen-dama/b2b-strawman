# 655A — task 655.1 characterisation (dormancy of a client with no activity rows)

## Run 1 — main production code (commit 1 only: the test, no production change)

Base: `main` @ `567bf5496` (no 655A production change). Run 2026-11-23, from
`/Users/rakheendama/Projects/2026/worktree-epic-655A/backend`.

```
./mvnw test -Dtest=ImportedClientDormancyIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 29.00 s -- in io.b2mash.b2b.b2bstrawman.compliance.ImportedClientDormancyIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

`ImportedClientDormancyIntegrationTest.activeClientWithNoActivityRows_goesDormantAtTheFirstRun` — a client
persisted `ACTIVE` through the 8-argument constructor (`TestCustomerFactory.createActiveCustomer`, saved with
`CustomerRepository.save`) with no time entries, tasks, documents, fee notes or comments, then
`CustomerLifecycleService.executeDormancyTransitions()` (the job's own entry, as `DormancyScheduledJob` calls it)
in the tenant scope:

| Figure | Value |
|---|---|
| lifecycle status before the run | `ACTIVE` |
| lifecycle status after the first run | **`DORMANT`** |
| why | `DORMANCY_SQL`'s `HAVING GREATEST(...) IS NULL` branch: no activity row at all |

The hazard is real on main: a client born `ACTIVE` with no activity rows — which is exactly what a take-on commit
creates — goes `DORMANT` at the first 02:00 dormancy run (R-9). 655A adds `MAX(c.taken_on_at)` to the three
`GREATEST(...)` expressions (C-2); this case is kept afterwards as "a non-imported client's result is unchanged".

## Run 2 — a second defect on the same path, reproduced before its fix (branch, cast unchanged from main)

While evolving the test to the post-fix cases (655.8: "with `taken_on_at` moved back 91 days and no activity →
DORMANT"), the run failed with an error, not an assertion. A fourth case was then added that uses **no take-on code at
all** — a client persisted `ACTIVE`, linked to a matter, with one task whose `updated_at` is moved back 120 days (a
non-null `last_activity` older than the 90-day threshold) — and run with `runDormancyCheck` exactly as on main
(the cast at `CustomerLifecycleService.java:239-240` was not touched by 655A until this run):

```
./mvnw test -Dtest=ImportedClientDormancyIntegrationTest
[ERROR] Tests run: 4, Failures: 0, Errors: 2, Skipped: 0
[ERROR] ImportedClientDormancyIntegrationTest.clientWithActivityOlderThanTheThreshold_goesDormant
        » ClassCast class java.time.Instant cannot be cast to class java.sql.Timestamp
[ERROR] ImportedClientDormancyIntegrationTest.importedClient_withNoActivityForTheThresholdAfterTakeOn_goesDormant
        » ClassCast class java.time.Instant cannot be cast to class java.sql.Timestamp
  at io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService.lambda$runDormancyCheck$0(CustomerLifecycleService.java:240)
  at io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService.runDormancyCheck(CustomerLifecycleService.java:251)
  at io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService.executeDormancyTransitions(CustomerLifecycleService.java:269)
```

| Figure | Value |
|---|---|
| `last_activity` Java type from the native query (Hibernate 7) | `java.time.Instant` |
| code on main | `((Timestamp) lastActivity).toInstant()` — throws for every candidate with any activity |
| effect on main | `runDormancyCheck()` / `executeDormancyTransitions()` throw for the whole tenant as soon as one ACTIVE client's latest activity is older than the threshold; only clients with **no** activity at all ever went dormant |
| why 655A must fix it | the take-on instant is activity: every imported client idle for 90 days takes this path |

Fix (in 655A, logged as a D-row and in `found.md`): `toInstant(Object)` accepts `Instant`, `Timestamp` and
`OffsetDateTime`. After it, the same class: `Tests run: 4, Failures: 0, Errors: 0` (with
`CustomerLifecycleServiceTest` and `CustomerLifecycleControllerTest` green in the same run).

## Run 3 — the four cases on the branch head (after the 655A changes)

| Case | Result |
|---|---|
| `activeClientWithNoActivityRows_goesDormantAtTheFirstRun` (the characterisation, non-imported) | DORMANT — unchanged |
| `importedClient_staysActiveAtTheFirstRun` (through upload → map → validate → attest → commit → drain) | ACTIVE, not a candidate |
| `importedClient_withNoActivityForTheThresholdAfterTakeOn_goesDormant` (`taken_on_at` − 91 days) | DORMANT |
| `clientWithActivityOlderThanTheThreshold_goesDormant` (non-imported, old task) | DORMANT |
