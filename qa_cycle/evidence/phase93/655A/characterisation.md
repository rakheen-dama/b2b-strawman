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
