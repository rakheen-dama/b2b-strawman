# Fix Spec: GAP-DI-03 — Audit events can be DELETED via direct SQL

## Problem

QA test T4.11.2 demonstrated that audit events can be deleted via direct SQL (`DELETE FROM audit_events WHERE id = ...` returns `DELETE 1`). The `prevent_audit_update()` trigger (V12 migration) only fires on UPDATE operations, not DELETE. This means anyone with database write access can tamper with the audit trail by deleting records, undermining the append-only integrity guarantee.

## Root Cause (hypothesis)

The Flyway migration `V12__create_audit_events.sql` (at `backend/src/main/resources/db/migration/tenant/V12__create_audit_events.sql`) creates:
- A `prevent_audit_update()` function (line 32-36) that raises an exception on UPDATE
- A `audit_events_no_update` trigger (lines 38-51) bound to `BEFORE UPDATE` on `audit_events`

There is no corresponding `prevent_audit_delete()` function or `BEFORE DELETE` trigger. The architecture doc (`architecture/phase6-audit-compliance-foundations.md`, line 784) also only specifies the UPDATE trigger — the DELETE trigger was omitted from the original design.

## Fix

Create a new Flyway migration `V74__prevent_audit_delete.sql` that adds a DELETE trigger alongside the existing UPDATE trigger.

**Migration content:**

```sql
-- V74: Add DELETE trigger to audit_events for full append-only enforcement
-- Companion to V12's prevent_audit_update() — blocks DELETE as well as UPDATE

CREATE OR REPLACE FUNCTION prevent_audit_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events rows cannot be deleted';
END;
$$ LANGUAGE plpgsql;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON t.tgrelid = c.oid
    JOIN pg_namespace n ON c.relnamespace = n.oid
    WHERE t.tgname = 'audit_events_no_delete'
      AND n.nspname = current_schema()
  ) THEN
    EXECUTE 'CREATE TRIGGER audit_events_no_delete
        BEFORE DELETE ON audit_events
        FOR EACH ROW EXECUTE FUNCTION prevent_audit_delete()';
  END IF;
END $$;
```

The pattern mirrors the existing `prevent_audit_update()` trigger in V12, including:
- Schema-scoped `IF NOT EXISTS` check via `pg_trigger` + `pg_namespace` (fixes the per-tenant schema isolation issue documented in Phase 13 cleanup)
- `BEFORE DELETE` trigger fires before the row is removed
- `RAISE EXCEPTION` prevents the deletion and rolls back the transaction

## Scope

Backend only — database migration.

**Files to modify:** None

**Files to create:**
- `backend/src/main/resources/db/migration/tenant/V74__prevent_audit_delete.sql`

**Migration needed:** Yes (V74 — next available tenant migration number)

## Verification

Re-run T4.11.2:
1. Connect to test database: `docker exec -it e2e-postgres psql -U postgres -d app`
2. Set schema: `SET search_path TO tenant_<hash>;`
3. Attempt: `DELETE FROM audit_events WHERE id = '<any-id>';`
4. Expected: `ERROR: audit_events rows cannot be deleted`

## Estimated Effort

S (< 30 min)
