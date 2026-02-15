# InvoiceService N+1 Query Batch-Loading Fix

**Priority**: High
**Component**: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`
**Pattern to follow**: `MyWorkService.getMyTasks()` batch-loading pattern

## Problem

`InvoiceService` has 3 N+1 query patterns where entities are loaded individually inside loops. For an invoice with N time entries, these methods generate O(N) or O(3N) queries instead of O(1).

The rest of the codebase (`MyWorkService`, `ActivityService`, `DashboardService`) already uses correct batch-loading patterns — `InvoiceService` is the only service with this problem.

## Affected Methods

### 1. `createDraft()` (~line 147-225) — 3N queries

The loop loads each time entry, its task, and its member individually:

```java
for (UUID timeEntryId : timeEntryIds) {
  var timeEntry = timeEntryRepository.findOneById(timeEntryId)...;  // N queries
  var task = taskRepository.findOneById(timeEntry.getTaskId());     // N queries
  String description = buildTimeEntryDescription(timeEntry);        // N more (see #2)
}
```

### 2. `buildTimeEntryDescription()` (~line 1054-1076) — 2 queries per call

Called inside `createDraft()` loop. Does per-entry lookups for task title and member name:

```java
private String buildTimeEntryDescription(TimeEntry timeEntry) {
  taskRepository.findOneById(timeEntry.getTaskId())...;    // 1 query
  memberRepository.findOneById(timeEntry.getMemberId())...; // 1 query
}
```

### 3. `deleteDraft()` (~line 442-454), `voidInvoice()` (~line 900-910), `approve()` (~line 697-707) — N queries each

All three methods loop over time entry IDs and load each individually to unlink `invoiceId`:

```java
for (UUID teId : timeEntryIdsToUnlink) {
  timeEntryRepository.findOneById(teId).ifPresent(te -> te.setInvoiceId(null));
}
```

## Fix

### For `createDraft()` + `buildTimeEntryDescription()`

Batch-load all entities upfront, then iterate with maps:

```java
// Batch load
var timeEntries = timeEntryRepository.findAllById(timeEntryIds);
var timeEntryMap = timeEntries.stream().collect(Collectors.toMap(TimeEntry::getId, te -> te));

var taskIds = timeEntries.stream().map(TimeEntry::getTaskId).filter(Objects::nonNull).distinct().toList();
var taskMap = taskRepository.findAllById(taskIds).stream().collect(Collectors.toMap(Task::getId, t -> t));

var memberIds = timeEntries.stream().map(TimeEntry::getMemberId).filter(Objects::nonNull).distinct().toList();
var memberMap = memberRepository.findAllById(memberIds).stream().collect(Collectors.toMap(Member::getId, m -> m));

// Then loop using maps
for (UUID timeEntryId : timeEntryIds) {
  var timeEntry = timeEntryMap.get(timeEntryId);
  // ... validation logic stays the same, just use taskMap.get() instead of repository call
  String description = buildTimeEntryDescription(timeEntry, taskMap, memberMap);
}
```

Refactor `buildTimeEntryDescription()` to accept maps:

```java
private String buildTimeEntryDescription(
    TimeEntry timeEntry, Map<UUID, Task> taskMap, Map<UUID, Member> memberMap) {
  String taskTitle = Optional.ofNullable(taskMap.get(timeEntry.getTaskId()))
      .map(Task::getTitle).orElse("Untitled");
  String memberName = Optional.ofNullable(memberMap.get(timeEntry.getMemberId()))
      .map(Member::getName).orElse("Unknown");
  return taskTitle + " -- " + timeEntry.getDate() + " -- " + memberName;
}
```

### For `deleteDraft()`, `voidInvoice()`, `approve()`

Replace individual loads with batch load:

```java
if (!timeEntryIdsToUnlink.isEmpty()) {
  var timeEntries = timeEntryRepository.findAllById(timeEntryIdsToUnlink);
  for (var te : timeEntries) {
    te.setInvoiceId(null);
  }
  // Hibernate auto-flushes dirty entities; batch_size: 25 batches the UPDATEs
}
```

## Validation

- All existing `InvoiceService` integration tests must still pass
- No new tests needed — this is a pure performance refactor with identical behavior
- Optionally verify query count reduction by temporarily enabling `hibernate.generate_statistics: true` in `application-test.yml`

## Reference

Good batch-loading examples already in the codebase:
- `MyWorkService.getMyTimeEntries()` — multi-level batch loading (entries → tasks → projects)
- `ActivityService.getProjectActivity()` — batch actor resolution
- `InvoiceService.buildResponse()` — already uses `resolveProjectNames()` batch pattern (same file!)
