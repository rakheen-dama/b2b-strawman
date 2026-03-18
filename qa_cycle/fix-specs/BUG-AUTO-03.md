# Fix Spec: BUG-AUTO-03 — AutomationEventListener missing trigger type mapping for TaskCompletedEvent (and DocumentGeneratedEvent)

## Problem

When a task is marked as completed via the UI, the backend publishes a `TaskCompletedEvent` (not a `TaskStatusChangedEvent`). The `TriggerTypeMapping` class only maps `TaskStatusChangedEvent` to `TASK_STATUS_CHANGED` — it has no entry for `TaskCompletedEvent`. The automation engine logs "No trigger type mapping for event class TaskCompletedEvent, skipping" and the Task Completion Chain seeded rule never fires.

Same issue affects `DocumentGeneratedEvent` — no mapping exists.

Evidence: QA checkpoint T7.2 — completed task "Gather financial data", backend log showed:
```
Recorded audit event: type=task.completed, entity=task/d5ce474f-...
No trigger type mapping for event class TaskCompletedEvent, skipping
```

`INVOICE_STATUS_CHANGED` and `PROPOSAL_SENT` mappings confirmed working.

## Root Cause (confirmed)

Three files need changes:

### 1. TriggerTypeMapping (missing entry)

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerTypeMapping.java`, lines 26-44.

The `MAPPINGS` map includes `TaskStatusChangedEvent.class -> TASK_STATUS_CHANGED` but NOT `TaskCompletedEvent.class`. The `TaskService.completeTask()` method (line 739) publishes `TaskCompletedEvent`, not `TaskStatusChangedEvent`. These are separate event classes — `TaskStatusChangedEvent` is published for general status changes (e.g., TODO -> IN_PROGRESS), while `TaskCompletedEvent` is published specifically for the complete action.

### 2. AutomationContext (ClassCastException risk)

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java`, line 49.

The `TASK_STATUS_CHANGED` case hard-casts the event to `TaskStatusChangedEvent`:
```java
case TASK_STATUS_CHANGED -> buildTaskStatusChanged((TaskStatusChangedEvent) event, context);
```

If `TaskCompletedEvent` is added to the mapping, this cast will throw `ClassCastException`. Need to handle `TaskCompletedEvent` too.

### 3. TriggerConfigMatcher (won't match toStatus)

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerConfigMatcher.java`, lines 66-77.

`deriveNewStatus()` only handles `TaskStatusChangedEvent`, not `TaskCompletedEvent`. For a `TaskCompletedEvent`, it falls to `default -> null`, so `toStatus: "COMPLETED"` in the seeded rule's trigger config won't match.

## Fix

### Step 1: Add TaskCompletedEvent to TriggerTypeMapping

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerTypeMapping.java`

Add import:
```java
import io.b2mash.b2b.b2bstrawman.event.TaskCompletedEvent;
```

Add entry to the `MAPPINGS` map (after the existing TaskStatusChangedEvent entry):
```java
Map.entry(TaskCompletedEvent.class, TriggerType.TASK_STATUS_CHANGED),
```

### Step 2: Handle TaskCompletedEvent in AutomationContext

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java`

Add import:
```java
import io.b2mash.b2b.b2bstrawman.event.TaskCompletedEvent;
```

Change line 49 from:
```java
case TASK_STATUS_CHANGED -> buildTaskStatusChanged((TaskStatusChangedEvent) event, context);
```

To:
```java
case TASK_STATUS_CHANGED -> {
    if (event instanceof TaskCompletedEvent tce) {
        buildTaskCompleted(tce, context);
    } else {
        buildTaskStatusChanged((TaskStatusChangedEvent) event, context);
    }
}
```

Add new method `buildTaskCompleted`:
```java
private static void buildTaskCompleted(
    TaskCompletedEvent event, Map<String, Map<String, Object>> context) {
  var task = new LinkedHashMap<String, Object>();
  task.put("id", uuidToString(event.entityId()));
  task.put("name", event.taskTitle());
  task.put("status", "COMPLETED");
  task.put("previousStatus", null);  // TaskCompletedEvent doesn't carry old status
  task.put("projectId", uuidToString(event.projectId()));
  context.put("task", task);

  var project = new LinkedHashMap<String, Object>();
  project.put("id", uuidToString(event.projectId()));
  project.put("name", detailValue(event, "project_name"));
  context.put("project", project);

  var customer = new LinkedHashMap<String, Object>();
  customer.put("id", detailValue(event, "customer_id"));
  customer.put("name", detailValue(event, "customer_name"));
  context.put("customer", customer);
}
```

### Step 3: Handle TaskCompletedEvent in TriggerConfigMatcher

File: `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerConfigMatcher.java`

Add import:
```java
import io.b2mash.b2b.b2bstrawman.event.TaskCompletedEvent;
```

In `deriveNewStatus()` (line 67), add a case before or after `TaskStatusChangedEvent`:
```java
case TaskCompletedEvent _ -> "COMPLETED";
```

In `deriveOldStatus()` (line 81), add:
```java
case TaskCompletedEvent _ -> null;  // completion doesn't carry previous status
```

### Step 4 (optional): Add DocumentGeneratedEvent mapping

If `DocumentGeneratedEvent` should trigger automations, add it to `TriggerTypeMapping`. However, there is no `DOCUMENT_GENERATED` trigger type in the enum. Two options:
- (a) Map it to `DOCUMENT_ACCEPTED` (semantically wrong)
- (b) Leave unmapped for now — log confirms it's not blocking QA (no seeded rules use this trigger)

**Recommendation**: Skip for now. Log as a future enhancement. The QA test plan does not test `DocumentGeneratedEvent` triggers.

## Scope

Backend only

Files to modify:
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerTypeMapping.java`
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationContext.java`
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerConfigMatcher.java`

Files to create: none

Migration needed: no

## Verification

Re-run Track 7 (T7.2):
1. Enable Task Completion Chain rule (or it should be enabled after BUG-AUTO-02 fix)
2. Navigate to a project, mark a task as completed
3. Check execution history — rule should fire with status COMPLETED
4. Verify a follow-up task was created (the seeded rule has a CreateTask action)

Also re-run Track 2 (T2.3):
1. Create a custom rule with TASK_STATUS_CHANGED trigger, condition newStatus = COMPLETED
2. Complete a task
3. Verify the custom rule fires

## Estimated Effort

S (< 30 min) — add mapping entry, add instanceof check + context builder, add switch case
