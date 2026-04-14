# Fix Spec: GAP-D6-04 — Activity feed comment events show "task" instead of task name

## Problem
Activity feed entries for comments show "commented on task 'task'" instead of the actual task name (e.g., "commented on task 'Pre-trial conference preparation'"). Observed during Day 6 checkpoint 6.6.

## Root Cause (confirmed)
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`, lines 49-51 and 152-163.

The `comment.created` event format is:
```java
"%s commented on %s \"%s\"".formatted(actorName, getParentType(details), getParentName(details))
```

`getParentType(details)` (line 152) reads `details.get("entity_type")` and lowercases it, producing `"task"`.
`getParentName(details)` (line 160) calls `getParentType(details)` — it ALSO returns the entity TYPE, not the entity NAME.

The comment audit details (from `CommentService.java`, lines 128-133) store:
```java
Map.of("body", body, "project_id", ..., "entity_type", entityType, "entity_id", entityId.toString(), "visibility", ...)
```

The entity name (task title) is NOT included in the audit details. Only `entity_type` ("TASK") and `entity_id` (UUID) are stored.

## Fix
Two changes needed:

### 1. Backend: Include entity name in comment audit details
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java`, around line 128.

Before building the audit details map, resolve the entity name:
- If `entityType` is `"TASK"`, look up the task title from `taskRepository.findById(entityId)`.
- If `entityType` is `"DOCUMENT"`, look up the document name from `documentRepository.findById(entityId)`.
- Add `"entity_name"` to the details map.

Change the audit details from:
```java
Map.of("body", body, "project_id", projectId.toString(), "entity_type", entityType, "entity_id", entityId.toString(), "visibility", resolvedVisibility)
```
to include the resolved name:
```java
Map.of("body", body, "project_id", projectId.toString(), "entity_type", entityType, "entity_id", entityId.toString(), "entity_name", resolvedEntityName, "visibility", resolvedVisibility)
```

Note: `Map.of()` only supports up to 10 entries, and this adds a 6th, so it's fine. The CommentService already has access to `TaskRepository` (check imports) or can be injected.

### 2. Backend: Fix ActivityMessageFormatter to use entity_name
File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java`, line 160.

Change `getParentName(details)` from:
```java
private String getParentName(Map<String, Object> details) {
    return getParentType(details);
}
```
to:
```java
private String getParentName(Map<String, Object> details) {
    Object entityName = details.get("entity_name");
    if (entityName instanceof String s && !s.isEmpty()) {
        return s;
    }
    return getParentType(details);
}
```

This is backward-compatible: existing audit events without `entity_name` will still show the entity type as fallback.

## Scope
Backend only.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java` (add entity_name to audit details)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java` (read entity_name from details)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatterTest.java` (update tests)

## Verification
Re-run Day 6 checkpoint 6.6 — activity feed should show "Bob Ndlovu commented on task 'Pre-trial conference preparation'" instead of "Bob Ndlovu commented on task 'task'".

## Estimated Effort
M (30 min - 2 hr)
