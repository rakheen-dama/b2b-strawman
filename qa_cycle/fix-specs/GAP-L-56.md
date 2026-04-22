# Fix Spec: GAP-L-56 — Time-entry creation blocked by PROSPECT-lifecycle gate

## Problem

Day 21 Checkpoints 21.1–21.5 (all **FAIL / BLOCKED**, per
`qa_cycle/checkpoint-results/day-21.md` lines 28–35) halt because every
per-task **Log Time** submission against Sipho Dlamini's RAF matter surfaces the
inline error:

> Cannot create time entry for customer in PROSPECT lifecycle status

Sipho (`8fe5eea2-75fc-4df2-b4d0-267486df68bd`) has `lifecycle_status = PROSPECT`
from Day 2 onward (DB probe: `day-21.md:23`). No scripted day 2–15 transitions
this customer, and Day 10 trust deposit does not flip the lifecycle. Result:
`tenant_5039f2d497cf.time_entries` contains 0 rows after Day 21 — **Day 28
fee-note generation is blocked** because there are no billable hours to bill.

A second symptom from the same gate: the org-level `/my-work/timesheet` save
path returns HTTP 200 with zero persisted rows (`day-21.md:35`). The root cause
for both paths is the same `CustomerLifecycleGuard` invocation, but the
timesheet 200-no-rows behaviour is documented separately in the **Fix** section
(it is a batch-endpoint design choice — PROSPECT errors are collected into
`errors[]`, not raised to HTTP 4xx).

Same gate family as GAP-L-35 (matter custom fields) re-expressed on a new
entity (`time_entries`). L-35 remains OPEN/deferred per status.md; this fix
deliberately scopes only `CREATE_TIME_ENTRY`.

## Root Cause (confirmed, not hypothesised)

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`
lines 18–25:

```java
switch (action) {
  case CREATE_PROJECT, CREATE_TASK, CREATE_TIME_ENTRY -> {
    if (status == LifecycleStatus.PROSPECT
        || status == LifecycleStatus.OFFBOARDING
        || status == LifecycleStatus.OFFBOARDED) {
      throwBlocked(action, status);
    }
  }
  ...
}
```

`CREATE_TIME_ENTRY` is bucketed with `CREATE_PROJECT` / `CREATE_TASK` — all
three reject PROSPECT customers with the same exception message template at
line 45:

```java
"Cannot create " + action.label() + " for customer in " + status + " lifecycle status"
```

The only caller for `CREATE_TIME_ENTRY` is
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryValidationService.java`
line 51:

```java
customerLifecycleGuard.requireActionPermitted(
    customer, LifecycleAction.CREATE_TIME_ENTRY)
```

invoked from `validateProjectAndCustomer(projectId)` (line 38) which is called
by **both** `TimeEntryService.createTimeEntry` (line 84) **and**
`TimeEntryBatchService.createBatch` (line 93).

### Silent 200-no-rows secondary symptom (timesheet save)

`TimeEntryBatchService.createBatch`
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryBatchService.java`
lines 69–185) wraps each per-entry transaction in a try/catch that catches
`InvalidStateException` (line 164) and appends a row to `errors[]` (lines
164–167) rather than propagating. The controller
(`TimeEntryController.java:109-114`) returns `ResponseEntity.ok(result)` —
i.e. **always HTTP 200**, with failure detail in the `errors` collection.

The frontend client handles this correctly when the grid submits entries —
`frontend/components/time-tracking/weekly-time-grid.tsx:217-230` branches on
`result.totalErrors === 0 / > 0` and surfaces `toast.error("Save failed — N
errors")` plus per-cell error labels. QA's "200 with 0 rows, no toast"
observation is therefore consistent with an **empty batch request** being
submitted — the earlier probe showed `Request body: []`. An empty batch returns
`totalCreated=0, totalErrors=0` and the client short-circuits before any toast
(line 196 `if (entries.length === 0) return;`).

**Net effect:** once L-56 is fixed and the dialog submission succeeds, the
timesheet save path will persist rows normally. The "silent 200" behaviour is
not a bug in the batch endpoint — it is the documented partial-success
contract. QA should re-verify the grid after the guard relaxation; the grid is
correct only if the user has actually typed a number AND tab-blur recorded it
into `cellValues`. If the grid still shows 0 rows after fix+retry with an
obvious typed value, file a separate grid-state-binding gap (off the L-56
critical path).

## Fix

**Single-file, single-case change** in `CustomerLifecycleGuard.java`:
separate `CREATE_TIME_ENTRY` from the `CREATE_PROJECT` / `CREATE_TASK` arm and
allow it for every lifecycle status except `OFFBOARDED`.

### File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`

Replace the `switch (action)` block (lines 18–39) with:

```java
switch (action) {
  case CREATE_PROJECT, CREATE_TASK -> {
    if (status == LifecycleStatus.PROSPECT
        || status == LifecycleStatus.OFFBOARDING
        || status == LifecycleStatus.OFFBOARDED) {
      throwBlocked(action, status);
    }
  }
  case CREATE_TIME_ENTRY -> {
    // Time entries are record-keeping on work already performed. They are
    // permitted against PROSPECT and OFFBOARDING customers (e.g. consultation
    // hours logged before client-activation, or final billing hours after
    // lifecycle close initiated). Only OFFBOARDED (terminal) is blocked —
    // after a customer is fully off-boarded, time tracking is closed.
    if (status == LifecycleStatus.OFFBOARDED) {
      throwBlocked(action, status);
    }
  }
  case CREATE_INVOICE -> {
    if (status != LifecycleStatus.ACTIVE && status != LifecycleStatus.DORMANT) {
      throwBlocked(action, status);
    }
  }
  case CREATE_DOCUMENT -> {
    if (status == LifecycleStatus.OFFBOARDED) {
      throwBlocked(action, status);
    }
  }
  case CREATE_COMMENT -> {
    // Always allowed
  }
}
```

**Rationale (option A from status.md triage):** time entries are low-level
record-keeping, not client-facing transactions. Day 10 trust deposits already
work on PROSPECT customers, so time entries being blocked is an inconsistency.
Relaxing only `CREATE_TIME_ENTRY` keeps the `CREATE_PROJECT` / `CREATE_TASK`
gate semantics intact (both still block PROSPECT) and keeps `CREATE_INVOICE`
strict (still requires ACTIVE/DORMANT).

Option B (auto-transition PROSPECT → ACTIVE on first billable hour) was
considered and rejected: it couples a cross-cutting lifecycle transition to a
low-level write, requires an event, and invites regression in the Day 10
trust-deposit path that already posts without transitioning.

### Test changes

Add one test in
`backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java`
(or the closest existing integration suite — it already has
`@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration.class)`,
per the convention in `backend/CLAUDE.md`).

```java
@Test
void createTimeEntry_prospectCustomer_succeeds() throws Exception {
  // Create a PROSPECT customer, link it to a project, sync a member,
  // POST /api/tasks/{taskId}/time-entries → expect 201 Created.
  //
  // Use TestCustomerFactory.createCustomerWithStatus(..., LifecycleStatus.PROSPECT)
  // rather than TestEntityHelper.createCustomer (which creates PROSPECT via API
  // — also fine).
}
```

Optional: update `CustomerLifecycleGuardTest` (or equivalent unit test) to
cover the new allow-PROSPECT branch for `CREATE_TIME_ENTRY` and retain the
OFFBOARDED-blocked assertion. If no such unit test exists, skip — the
integration test is sufficient.

## Scope

- **Backend only.**
- Files to modify:
  - `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`
    (split one switch case, ~12 lines changed)
  - `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryIntegrationTest.java`
    (add one test, ~25 lines)
- Files to create: none
- Migration needed: **no** (pure logic change, no schema)
- Env / config: none

## Verification

1. **Backend restart required** (Java source change, no hot-reload):
   `bash compose/scripts/svc.sh restart backend`. NEEDS_REBUILD = true.
2. Run targeted unit+integration suite first:
   ```
   ./mvnw test -Dtest='CustomerLifecycleGuard*,TimeEntry*,TimeEntryBatch*' -q
   ```
   Expect EXIT=0 including the new PROSPECT test.
3. QA re-runs **Day 21 Phase A**:
   - **21.1** — navigate to RAF matter Time tab; record whether the (still
     known) missing `+ Log Time` CTA is the only Phase-A issue.
   - **21.2** — open per-task Log Time dialog; fill duration 2h30m, description,
     billable=on. No LSSA tariff dropdown is still expected (separate
     scenario gap, not blocking).
   - **21.3** — log unresolved rate-zero warning (separate LOW gap).
   - **21.4** (**BLOCKER** — primary assertion): submit the Log Time dialog.
     Dialog must close cleanly and the task row must show the logged hours.
     DB probe:
     ```sql
     SELECT count(*), sum(duration_minutes)
     FROM tenant_5039f2d497cf.time_entries;
     ```
     Expect `(1, 150)` after first submit.
   - **21.5** — submit a second Log Time on a different task. Expect 2 rows
     persisted. No PROSPECT inline error.
4. QA re-runs the **timesheet save probe** (`day-21.md:35`): type a number in
   a cell, tab-blur, click Save. Expect `toast.success` and `time_entries`
   count increments. If the grid STILL fails with `Request body: []`, that is
   a separate grid-state-binding bug — file a new LOW gap (NOT rolled into
   L-56 fix scope).
5. **Regression spot-check**:
   - Create an OFFBOARDED customer, attempt a time entry → expect the
     `InvalidStateException` with `"Cannot create time entry for customer in
     OFFBOARDED lifecycle status"` still fires.
   - Create an ACTIVE customer, attempt a time entry → expect success (should
     already pass and not change).

## Estimated Effort

**S (< 30 min)** — one switch-case split + one integration test. Zero
migration, zero API surface change, zero config, zero frontend. Regression
surface is narrow (CREATE_TIME_ENTRY has exactly one caller) and the test
covers the new permitted path.

## Parallelisation Notes

**SAFE for parallel Dev execution alongside GAP-L-57.** No file overlap:
- L-56 touches only `backend/.../compliance/CustomerLifecycleGuard.java` +
  one backend test file.
- L-57 touches only `frontend/app/(app)/org/[slug]/legal/disbursements/actions.ts`
  (+ optional dialog test).

Two Dev agents can work in separate worktrees simultaneously without merge
conflicts. Both fixes can land independently; no cross-dependency.

## Status Triage

**SPEC_READY.** Minimal, surgical, on-path for Day 28 fee-note generation.
Root cause confirmed by direct code read (CustomerLifecycleGuard.java:19 +
TimeEntryValidationService.java:51). Cousin gap GAP-L-35 remains
OPEN/deferred and is NOT bundled into this fix.
