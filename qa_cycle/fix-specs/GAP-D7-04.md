# Fix Spec: GAP-D7-04 — Activity feed shows "a task" instead of real names

## Problem
Activity feed entries for time logging show "Bob Admin logged 2h on task 'a task'" instead of the actual task name.

## Root Cause (confirmed)
The `ActivityMessageFormatter.getTaskTitle()` method (lines 162-169 in `ActivityMessageFormatter.java`) falls back to "a task" when the `title` key is missing from audit event details:

```java
private String getTaskTitle(Map<String, Object> details) {
  Object title = details.get("title");
  if (title instanceof String s) {
    return s;
  }
  return "a task";
}
```

The issue is that time entry audit events are logged WITHOUT a `title` key in their details map. The time entry creation audit event likely only includes `duration_minutes`, `task_id`, etc. — but not the task's title.

Need to check how time entry audit events are built to confirm.

## Fix

**Option A (preferred): Include task title in time entry audit events**

Find where `time_entry.created` events are built (likely in `TimeEntryService`) and add `"title", task.getTitle()` to the details map.

**Option B (fallback): Resolve task name from task_id in formatter**

Add a `TaskRepository` dependency to `ActivityService` and resolve task names from `task_id` in the details when `title` is missing. This is more complex and adds a query per event.

## Scope
- 1 backend file (TimeEntryService or similar) — add `title` to audit event details
- No frontend changes
- No migration

## Verification
1. Log a time entry on a task
2. Navigate to the Activity tab on the matter
3. Verify the entry shows the real task name (e.g., "Carol Member logged 1h 30m on task 'Initial consultation & case assessment'")

## Estimated Effort
30 minutes
