# AIVERIFY-004 — compliance-audit publish-report concurrency window

- **Severity**: Low (downgraded from the prior "Medium" hypothesis; benign, non-data-loss, low-probability)
- **Disposition**: **SPEC_READY (real but Low/edge-case window confirmed by a concrete interleaving)** — with a correction to the prior note's framing.
- **Area**: Backend (`compliance` — `publishReport`)
- **Effort**: S (one partial unique index, or a `SELECT … FOR UPDATE` advisory; no new tables)
- **Migration**: yes (one tenant Flyway migration if the unique-index option is chosen)

## Correction to the prior note

The deferred note said the **AIVERIFY-001 transaction split** "narrowed but didn't close a
concurrency-guard window in compliance-audit." That framing is **imprecise**. The AIVERIFY-001 split
lives in the skill-execution → **gate-creation** path (`AiExecutionPersistenceService` —
`@Transactional` per `:70/:99/:120`, `createGates` at `:137`). That path **creates** the PENDING
`PUBLISH_COMPLIANCE_REPORT` gate; it does **not** touch `publishReport`. The window described below
is in `ComplianceAuditReportService.publishReport`, which runs **later**, on gate **approval**
(`GateActionExecutor:104-110`), and is **independent** of the AIVERIFY-001 split. So this is a real
pre-existing window, not a residue of the 001 fix.

## Problem (confirmed, with a concrete failing interleaving)

`publishReport` maintains an "exactly one PUBLISHED compliance report" invariant by an
**unsynchronised archive-then-insert** (`ComplianceAuditReportService.java:47-78`):

```java
@Transactional
public ComplianceAuditReport publishReport(ComplianceAuditOutput output, UUID executionId, UUID memberId) {
  ...
  // (1) read the current PUBLISHED set (no lock)
  Page<ComplianceAuditReport> publishedReports =
      reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.PUBLISHED.name(), PageRequest.of(0, 10_000));
  // (2) archive each
  for (ComplianceAuditReport previous : publishedReports) { previous.archive(memberId); reportRepository.save(previous); }
  ...
  // (3) insert a new PUBLISHED report
  report.publish(memberId); report = reportRepository.save(report);
  ...
}
```

Facts (files read):
- `ComplianceAuditReport` has **no `@Version`** (`ComplianceAuditReport.java` — only a `status`
  String at `:42`, `publish()`/`archive()` guards at `:82-104`). Contrast the **gate**, which *does*
  have `@Version` (`AiExecutionGate.java:58`).
- The only uniqueness on the table is on **`execution_id`** —
  `V127__compliance_audit_tables.sql:52` `CREATE UNIQUE INDEX idx_compliance_audit_reports_execution
  ON compliance_audit_reports(execution_id)`. There is **no** uniqueness on
  "at most one row WHERE status='PUBLISHED'".
- Default isolation = READ COMMITTED; step (1) takes no row/predicate lock.

### Concrete interleaving that breaks the invariant

Two **distinct** compliance-audit executions A and B each have their own PENDING
`PUBLISH_COMPLIANCE_REPORT` gate (e.g. two audits run, two gates awaiting review). Reviewer 1 approves
gate_A and reviewer 2 approves gate_B at the same time:

| T1 (approve gate_A → publishReport(exec A)) | T2 (approve gate_B → publishReport(exec B)) |
|---|---|
| read PUBLISHED set = {P0} | |
| | read PUBLISHED set = {P0} |
| archive P0; insert P_A (PUBLISHED) | |
| | archive P0; insert P_B (PUBLISHED) |
| commit | commit |

Both insert succeed (`execution_id` differs, so the only unique index doesn't fire). End state:
**two PUBLISHED reports (P_A and P_B)**. `findReports` (`:131-135`) returns **all** PUBLISHED rows
paginated, so the report list/detail surfaces two "current" compliance reports — the singleton
invariant is violated. (The concurrent archive of the shared P0 by both transactions is a harmless
last-writer-wins; P0 ends ARCHIVED either way. The defect is the two new PUBLISHED inserts.)

### Why "benign / non-data-loss" is the honest classification

- No row is lost or corrupted; an **extra** PUBLISHED report appears (a duplicate-current, not a
  drop). A human can re-publish/archive to recover.
- Reachability is low: it needs **two distinct compliance audits with PENDING publish gates approved
  in the same ~millisecond window** by two reviewers. The single-gate double-approve path is already
  safe (the gate's `@Version` + `status==PENDING` guard in `AiExecutionGate.approve():81-92` forces
  one winner; the loser gets OptimisticLock / InvalidStateException). So the window is narrow and the
  effect is cosmetic-to-mild, hence **Low**, not Medium.

## Fix (minimal, deterministic)

Pick ONE (orchestrator decision):

**Option A (preferred — DB enforces the invariant):** add a tenant Flyway migration with a **partial
unique index**:
```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_compliance_audit_reports_single_published
  ON compliance_audit_reports ((status)) WHERE status = 'PUBLISHED';
```
The second concurrent insert then fails with a constraint violation → that transaction rolls back, the
gate-approve surfaces a 409, the reviewer retries cleanly. Deterministic, no app-level locking. (Add a
`@Version` to `ComplianceAuditReport` as well so the archive step is also lost-update-safe, optional.)

**Option B (app-level serialization):** in `publishReport`, take a lock before the read — either a
Postgres advisory lock (`SELECT pg_advisory_xact_lock(<const-for-this-table>)`) or a
`SELECT … FOR UPDATE` over the PUBLISHED rows — so the two transactions serialize on step (1). No
migration if advisory-lock; simplest correctness with the least surface change.

Recommend **Option A** (the invariant belongs in the schema; matches the existing partial-unique-index
precedent noted in `V110`).

## Scope

- Backend only, `compliance` package. One migration (Option A) **or** a few lines in `publishReport`
  (Option B). No change to the gate path, the AIVERIFY-001 split, or the skill code.
- One fix per PR (CLAUDE.md §7).

## Verification

- New `*ServiceTest` that drives two concurrent `publishReport(exec A)` / `publishReport(exec B)`
  calls on the same tenant (two threads / two `TransactionTemplate` executions) and asserts
  **exactly one** row ends `PUBLISHED` (Option A: the second throws a constraint violation;
  Option B: serialized, second archives the first).
- Regression: the single-publish happy path still yields one PUBLISHED + prior archived; the existing
  V8/AIVERIFY-008 publish-gate→report→detail flow still passes.
- `./mvnw verify` clean (full suite — the change is production behaviour, CLAUDE.md §5).

## If you disagree with shipping this

This is a defensible **WONT_FIX-as-Low** candidate too: it is cosmetic, non-data-loss, and requires a
near-simultaneous two-reviewer race that is implausible at a single firm's scale. The interleaving is
real (so it is **not** NOT-A-DEFECT), but the orchestrator may legitimately choose to log it as a
known Low and not spend a PR. Flagging that explicitly per CLAUDE.md §9 honesty — the fix is cheap
(Option A is ~3 lines of SQL) so shipping it is the cleaner call, but the severity does not force it.
