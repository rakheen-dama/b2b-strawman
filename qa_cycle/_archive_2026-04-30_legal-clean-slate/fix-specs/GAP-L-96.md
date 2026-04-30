# Fix Spec: GAP-L-96 — Closure does not insert a `retention_policies` MATTER row per ADR-249

## Problem

On matter close, `projects.retention_clock_started_at` is stamped with `closed_at`, but **no row is inserted into `retention_policies` for `record_type='MATTER'`**. The Mathebula tenant has only the two default rows (`AUDIT_EVENT` 2555 days, `CUSTOMER` 1825 days). `org_settings.legal_matter_retention_years` is NULL, so no per-matter retention is derivable through any other path.

ADR-249 (`adr/ADR-249-retention-clock-starts-on-closure.md`) requires an explicit retention policy row at closure. Currently `MatterClosureService.close()` line 174-176 explicitly carries `TODO(489C)` — the row is not yet persisted.

Evidence:
- `qa_cycle/checkpoint-results/day-60.md §Day 60 Cycle 46 Walk §60.11`.
- DB: `SELECT * FROM tenant_5039f2d497cf.retention_policies` returns 2 rows (no MATTER); `SELECT retention_clock_started_at FROM projects WHERE id='cc390c4f-…'` populated; `org_settings.legal_matter_retention_years=NULL`.

## Root Cause (verified)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java:174-176`:

```java
// TODO(489C): persist a retention_policies row inside performClose() and soft-cancel it in
// reopen(). For now the API returns only the computed retentionEndsAt — the retentionPolicyId
// field was removed from CloseMatterResponse because nothing was persisted behind it (C4).
```

The current `MatterClosureService.performClose` only sets `projects.retention_clock_started_at`. The `RetentionPolicy` entity (`retention/RetentionPolicy.java`) is per-tenant policy keyed by `(record_type, trigger_event)` UNIQUE constraint (`V29__customer_compliance_lifecycle.sql:221`) — NOT per-instance. ADR-249's prose references "entityType=PROJECT, entityId=projectId" but the table schema doesn't carry an entityId column, so the realistic interpretation is: insert a single tenant-wide `record_type='MATTER'` policy row on first matter closure (idempotent; subsequent closures re-use the same row).

`OrgSettings.DEFAULT_LEGAL_MATTER_RETENTION_YEARS = 5` (line 36) and `getEffectiveLegalMatterRetentionYears()` (line 940-944) already provide the 5-year default when `legalMatterRetentionYears` is NULL.

`RetentionPolicyService.create(recordType, retentionDays, triggerEvent, action)` (lines 33-60) handles the insert with idempotency via `ResourceConflictException` if `(record_type, trigger_event)` already exists.

## Fix

Add a single idempotent insert at the end of `MatterClosureService.performClose`, immediately after the existing audit + event publish (line 244):

```java
// ADR-249: ensure a tenant-wide retention policy exists for closed matters. Idempotent —
// subsequent closures reuse the same policy row (UNIQUE on (record_type, trigger_event)).
ensureMatterRetentionPolicy();
```

Add the helper:

```java
private void ensureMatterRetentionPolicy() {
  try {
    int years =
        orgSettingsService.getOrCreateForCurrentTenant().getEffectiveLegalMatterRetentionYears();
    int days = years * 365;
    retentionPolicyService.create("MATTER", days, "MATTER_CLOSED", "ARCHIVE");
  } catch (ResourceConflictException already) {
    // Idempotent — policy already seeded by an earlier closure on this tenant.
  } catch (RuntimeException e) {
    log.warn("Failed to seed MATTER retention policy on closure; close proceeds", e);
  }
}
```

Inject `RetentionPolicyService` via constructor.

Optionally: in `MatterClosureService.reopen` (line 320-379), do NOT deactivate the policy (per ADR-249, soft-cancel is per-instance — and we don't have per-instance rows). The `projects.retention_clock_started_at` remains the canonical retention anchor; reopen leaves it intact (line 73-74 doc). No retention-row mutation needed on reopen given the tenant-wide-policy interpretation.

## Scope

Backend only.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java` — constructor (inject `RetentionPolicyService`); `performClose` final block; new `ensureMatterRetentionPolicy()` helper.

Files to create: none.
Migration needed: no — existing `retention_policies` table accommodates the new row; `record_type` length 20 fits "MATTER".

## Verification

1. Restart backend.
2. As Thandi on a fresh ACTIVE matter (or use an existing CLOSED one and re-close the next available matter): click Close Matter → confirm.
3. DB:
   ```sql
   SELECT * FROM tenant_5039f2d497cf.retention_policies WHERE record_type='MATTER';
   ```
   Expect: 1 row (`record_type='MATTER'`, `retention_days=1825`, `trigger_event='MATTER_CLOSED'`, `action='ARCHIVE'`, `active=true`).
4. Close a SECOND matter on the same tenant. Re-run the SELECT → still 1 row (idempotent), no `ResourceConflictException` propagated to the caller.
5. Re-run Day 60 §60.11: PASS.

## Estimated Effort

**S (1 hour)** — constructor + helper + 2 lines in `performClose`. Idempotency relies on existing `RetentionPolicyService.create` semantics; no new query.

## Tests

`MatterClosureServiceTest`:
- `close_seedsMatterRetentionPolicyIfMissing` — fresh tenant, close matter → assert `retentionPolicyRepository.findByRecordTypeAndTriggerEvent("MATTER","MATTER_CLOSED")` returns a row with `retentionDays=1825`.
- `close_isIdempotentOnSecondClosure` — close once + close again → still exactly one MATTER policy row; second close does not throw.
- `close_usesTenantOverrideRetentionYears` — set `org_settings.legal_matter_retention_years=7` → close → assert `retentionDays=2555`.

## Regression Risk

`RetentionPolicyService.create` already exists, with `existsByRecordTypeAndTriggerEvent` check + `ResourceConflictException` swallow on idempotent re-seed. The MATTER policy row is independent of existing AUDIT_EVENT and CUSTOMER rows; no UNIQUE-constraint conflict.
The retention sweep job (`RetentionSweepService`) does not yet act on MATTER record types; this row will be inert until the sweeper learns to interpret `record_type='MATTER'` (out-of-scope for this fix; tracked under a future ADR-249 follow-up).

## Dispatch Recommendation

**Defer-to-later-cycle (LOW severity, ADR-249 compliance gap).** Day 61 walk is unaffected — `projects.retention_clock_started_at` carries the per-matter anchor today. The MATTER policy row is a compliance artefact for the retention sweeper, which doesn't operate on matters yet. Effort is small (S) but bundling with L-94 + L-95 + the verification cycle adds risk; cleaner to ship in a stand-alone cycle 48 PR alongside any sweeper enablement work.

If cycle 47 has spare Dev capacity after L-94/L-95, this is the safest LOW gap to bundle (single-file backend change, idempotent, zero UI surface).
