# Fix Spec: BUG-EMAIL-01 — Status vocabulary mismatch between frontend dropdown and backend TriggerConfigMatcher

## Problem

Automation rules created via the UI with a task status filter of "DONE" silently fail to match `TaskCompletedEvent`. The user creates a rule like "When task status changes to Done, send email" -- the rule saves successfully but never fires.

A secondary instance of the same bug affects invoice status: the frontend dropdown offers "VOID" but `TriggerConfigMatcher.deriveNewStatus()` returns "VOIDED" for `InvoiceVoidedEvent`.

## Root Cause (confirmed)

Two independent vocabularies exist for status values, and the frontend uses one while the backend matcher uses the other:

**Task status mismatch:**
- Frontend `trigger-config-form.tsx` line 24: dropdown value is `"DONE"` (from `TaskStatus` enum: `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`)
- Backend `TriggerConfigMatcher.deriveNewStatus()` line 68: `TaskCompletedEvent` maps to `"COMPLETED"`
- The `StatusChangeTriggerConfig.toStatus()` stores `"DONE"` but is compared against `"COMPLETED"` -- never matches

**Invoice status mismatch:**
- Frontend `trigger-config-form.tsx` line 41: dropdown value is `"VOID"` (from `InvoiceStatus` enum: `DRAFT`, `APPROVED`, `SENT`, `PAID`, `VOID`)
- Backend `TriggerConfigMatcher.deriveNewStatus()` line 76: `InvoiceVoidedEvent` maps to `"VOIDED"`
- The `StatusChangeTriggerConfig.toStatus()` stores `"VOID"` but is compared against `"VOIDED"` -- never matches

The root cause is that `TriggerConfigMatcher` invented its own status vocabulary (`"COMPLETED"`, `"VOIDED"`) instead of using the actual enum values (`"DONE"`, `"VOID"`) that the entities and frontend already agree on.

## Fix

**Fix in backend `TriggerConfigMatcher.deriveNewStatus()`** (single source of truth = entity enums):

| Event class | Current derived status | Correct derived status |
|---|---|---|
| `TaskCompletedEvent` | `"COMPLETED"` | `"DONE"` |
| `InvoiceVoidedEvent` | `"VOIDED"` | `"VOID"` |

File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerConfigMatcher.java`

```java
// Line 68: change "COMPLETED" to "DONE"
case TaskCompletedEvent _ -> "DONE";

// Line 76: change "VOIDED" to "VOID"
case InvoiceVoidedEvent _ -> "VOID";
```

Also update `AutomationContext.buildTaskCompleted()` (line 115) for consistency:
```java
// Line 115: change "COMPLETED" to "DONE"
task.put("status", "DONE");
```

And `AutomationContext.buildInvoiceStatusChanged()` (line 204) for InvoiceVoidedEvent:
```java
// Line 204: change "VOIDED" to "VOID"
invoice.put("status", "VOID");
```

**Why fix in backend, not frontend:**
- The `TaskStatus` enum (`DONE`) and `InvoiceStatus` enum (`VOID`) are the canonical values used in the database, API responses, and `TaskStatusChangedEvent.newStatus()` (which passes `taskStatus.name()`).
- The frontend dropdown already uses the correct enum values.
- Changing the backend matcher to match the enum values ensures consistency across the whole stack.
- `ProjectCompletedEvent -> "COMPLETED"` and `ProjectArchivedEvent -> "ARCHIVED"` are correct because those match the project status enum values.

## Scope

- `TriggerConfigMatcher.java` -- 2 lines changed in `deriveNewStatus()`
- `AutomationContext.java` -- 2 lines changed (status field values in `buildTaskCompleted` and `buildInvoiceStatusChanged`)
- Existing tests in `ConditionEvaluatorTest.java` and `AutomationEndToEndTest.java` may need updates if they assert on `"COMPLETED"` or `"VOIDED"` values
- No frontend changes needed
- No database migration needed

## Verification

1. Create an automation rule via UI: trigger = "Task Status Changed", toStatus = "Done"
2. Complete a task -- the rule should now fire
3. Create an automation rule: trigger = "Invoice Status Changed", toStatus = "Void"
4. Void an invoice -- the rule should now fire
5. Existing tests pass (update assertions from `"COMPLETED"` to `"DONE"` and `"VOIDED"` to `"VOID"`)
6. Verify `TaskStatusChangedEvent` (non-completion transitions like OPEN -> IN_PROGRESS) still match correctly -- these already use `event.newStatus()` which returns the enum `.name()`, so no change needed

## Estimated Effort

Small -- 4 lines of production code + test assertion updates. ~30 minutes including test verification.
