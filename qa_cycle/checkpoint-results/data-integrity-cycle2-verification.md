# Data Integrity QA — Cycle 2 Verification Results

**Date**: 2026-03-18
**Agent**: QA Agent (Cycle 2)
**Purpose**: Verify 2 FIXED items from Cycle 1

---

## GAP-DI-02: Comments on ARCHIVED Projects (PR #755)

**Expected**: HTTP 400 when creating/updating comments on ARCHIVED projects
**Previous behavior**: HTTP 201 (comment created)

### Test 1: Create comment on ARCHIVED project
- **Project**: Website Redesign (`3ca78384-4e98-4991-8c6b-0725bb89c562`, status=ARCHIVED)
- **Request**: `POST /api/projects/{id}/comments` with body `{"entityType":"PROJECT","entityId":"{id}","body":"...","visibility":"SHARED"}`
- **Response**: HTTP 400
  ```json
  {
    "detail": "Project is archived. No modifications allowed.",
    "instance": "/api/projects/3ca78384-4e98-4991-8c6b-0725bb89c562/comments",
    "status": 400,
    "title": "Project is archived"
  }
  ```
- **Result**: **PASS** — lifecycle guard correctly blocks comment creation on archived projects

### Test 2: Control — Create comment on ACTIVE project
- **Project**: Annual Tax Return 2026 — Kgosi (`2f06a239-d87d-47dd-a29a-36177af3d30a`, status=ACTIVE)
- **Request**: Same format as Test 1
- **Response**: HTTP 201 (comment created, id=`e0200cac-de2b-4442-8aab-2890f2d28e9a`)
- **Result**: **PASS** — no false positives; ACTIVE projects still accept comments

### Verdict: **VERIFIED**

---

## GAP-DI-03: Audit DELETE Blocked by DB Trigger (PR #756)

**Expected**: SQL DELETE on `audit_events` raises exception from `prevent_audit_delete()` trigger
**Previous behavior**: `DELETE 1` — row silently deleted

### Test 1: Attempt DELETE on audit_events
- **Schema**: `tenant_7d218705360b`
- **Row count before**: 207
- **Command**: `DELETE FROM audit_events WHERE id = (SELECT id FROM audit_events LIMIT 1);`
- **Response**:
  ```
  ERROR:  audit_events rows cannot be deleted
  CONTEXT:  PL/pgSQL function prevent_audit_delete() line 3 at RAISE
  ```
- **Row count after**: 207 (unchanged)
- **Result**: **PASS** — trigger blocks DELETE with clear error message

### Test 2: Verify trigger registration
- **Command**: Query `pg_trigger` for `audit_events` table
- **Result**:
  | Trigger Name | Type | Enabled |
  |---|---|---|
  | `audit_events_no_delete` | 11 (BEFORE DELETE) | O (enabled) |
  | `audit_events_no_update` | 19 (BEFORE UPDATE) | O (enabled) |
- **Result**: **PASS** — both UPDATE and DELETE triggers present and enabled

### Verdict: **VERIFIED**

---

## Summary

| ID | Fix (PR) | Verification | Result |
|----|----------|-------------|--------|
| GAP-DI-02 | #755 | Comment on ARCHIVED project returns 400; ACTIVE returns 201 | VERIFIED |
| GAP-DI-03 | #756 | DELETE on audit_events raises trigger exception; row count unchanged | VERIFIED |

**All FIXED items verified. No regressions detected.**
