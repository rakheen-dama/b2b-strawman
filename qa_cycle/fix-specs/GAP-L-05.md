# Fix Spec: GAP-L-05 — Activity feed renders "unknown" for task-assignment events

## Problem

From `status.md` Gap Tracker (Day 4 / 4.6, MED):

> Activity feed shows task title as literal "unknown" for task-assignment events. On Sipho's matter
> Recent Activity: "Bob Ndlovu assigned task 'unknown'" appears alongside correctly-titled events
> (claimed, logged-time, generated-doc). Root cause likely: `task.assigned` event payload captures
> task_id but doesn't resolve title, while other events resolve title at write-time.

User-impact: degrades the 90-day audit-trail readability that the demo hangs the "Enterprise
compliance" story on. Every assignee change in a project's activity feed loses its task identity.

## Root Cause (validated, not hypothesis)

The **activity feed does not read `task.assigned` events** — activity feed renders **audit events**
of type `task.updated`. The domain event `TaskAssignedEvent` (emitted in
`TaskService.updateTask` at line 568) IS populated correctly (`taskTitle` field on line 581). The
bug lives entirely in the audit-event side.

Chain:

1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java:538-558` — when
   a task is updated (including assignee change), service writes one audit event of type
   `task.updated` whose `details` map is produced by `AuditDeltaBuilder` (line 539):
   ```java
   var details = new AuditDeltaBuilder()
       .track("title", oldTitle, title)                            // line 540
       .trackAsString("assignee_id", oldAssigneeId, assigneeId)    // line 544
       ...
       .buildMutable();
   details.put("project_id", task.getProjectId().toString());
   ```
   `AuditDeltaBuilder.track(...)` **only includes a field if oldVal != newVal**
   (`AuditDeltaBuilder.java:33-42`). When the user changes **only** the assignee, `title` does not
   change, so `"title"` is NOT in the details map.

2. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:117-118`
   — the formatter detects an assignment change:
   ```java
   if (details.containsKey("assignee_id")) {
     return "%s assigned task \"%s\"".formatted(actorName, title);
   }
   ```
   where `title = getTitle(details)`. `getTitle()` (lines 129-145) looks up `details.get("title")`;
   when absent, it **returns the literal string `"unknown"`** (line 144).

So the output is exactly: `"Bob Ndlovu assigned task \"unknown\""` — matching the QA
observation verbatim. The other events work (claimed, time-entry) because those audit-event
writers populate `title` as a plain string, not as a delta (e.g. lines 719, 782).

Validated by reading:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditDeltaBuilder.java` (track skips unchanged fields)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java:538-558` (update audit site)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/activity/ActivityMessageFormatter.java:114-145`

## Fix

Single-file change in `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java`.

Make `title` always present in the `task.updated` audit details (as a plain string representing the
task's **current** title) so the formatter can render the task identity regardless of which field
changed. Keep the delta `{from, to}` entry only for genuine title changes — add a separate
non-delta key for the current value.

Pick one of the two equivalent approaches; prefer **Option A** for consistency with how the
`task.created` audit-event below writes a plain string (line 331 `auditDetails.put("title", task.getTitle())`).

### Option A (recommended) — always write current title as plain string

In `TaskService.updateTask` around line 548-551, after `buildMutable()`, add:

```java
// Always include the current task title so the activity feed can identify the task,
// even when only non-title fields (assignee, status, priority) changed.
details.putIfAbsent("title", task.getTitle());
details.put("project_id", task.getProjectId().toString());
```

This overwrites only when the delta builder didn't already track a title change. When there IS a
title change, the delta map `{from: "Old", to: "New"}` wins (via `track("title", ...)` earlier)
and `getTitle()` (lines 134-143 of `ActivityMessageFormatter`) already handles the Map shape by
returning the `"to"` value — so both the "renamed title" and "unchanged title" cases render
sensibly.

**Verification that `getTitle()` Map handling works**: `ActivityMessageFormatter.getTitle`
lines 134-143 already extracts `"to"` from a Map and falls back to `"from"` — so when a title
delta is present, the activity message reads `"%s updated task \"%s\""` with the *new* title.
When only assignee changed (no delta for title), `getTitle()` reads the plain string from
`putIfAbsent("title", task.getTitle())`.

### Option B (alternative, smaller textual change) — always overwrite title as plain string

If maintainers prefer strict "one source of truth for title", replace the delta tracking of title
with a plain string. This loses the rename audit trail, so **reject Option B** unless the team
decides renames don't matter.

## Scope

- **Backend**: YES (one method, one file).
- **Frontend / Gateway / Keycloak theme / Seed / Config / Migrations**: NO.

Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` (add one line inside `updateTask`)

Files to create:
- (Optional but recommended) `backend/src/test/java/io/b2mash/b2b/b2bstrawman/activity/TaskAssignmentActivityMessageTest.java` — a focused regression for this bug.

Migration needed: NO.
KC restart required: NO.
Backend restart required: YES (Java source change, no hot-reload — per project CLAUDE.md).
Frontend restart required: NO.

## Verification

### Unit / integration test

Add one integration test in the activity feed area (pick the closest existing
`ActivityMessageFormatterTest` or write a new `TaskAssignmentActivityIntegrationTest`):

```java
@Test
void taskAssignedEventShowsTaskTitleNotUnknown() throws Exception {
  // Given a task with a known title
  String projectId = TestEntityHelper.createProject(mockMvc, ownerJwt, "Matter A");
  String taskId = TestEntityHelper.createTask(mockMvc, ownerJwt, projectId, "Draft particulars of claim");

  // When only the assignee is changed (title unchanged)
  mockMvc.perform(patch("/api/tasks/" + taskId)
      .contentType(APPLICATION_JSON)
      .with(ownerJwt)
      .content("""
          {"assigneeId": "%s"}
          """.formatted(otherMemberId)))
      .andExpect(status().isOk());

  // Then the activity feed message includes the task title, not "unknown"
  var resp = mockMvc.perform(get("/api/projects/" + projectId + "/activity").with(ownerJwt))
      .andReturn().getResponse().getContentAsString();

  assertThat(resp)
      .contains("assigned task \"Draft particulars of claim\"")
      .doesNotContain("assigned task \"unknown\"");
}
```

### Manual reproduction in <5 min

1. On the running Keycloak dev stack, log in as Bob.
2. Open any active matter's Tasks tab; create a task named "Test assignment bug".
3. Edit the task; change the assignee only (do not rename).
4. Open the matter's Activity tab (or Recent Activity card).
5. Expect: `"Bob ... assigned task \"Test assignment bug\""`.
6. Defect repro: before fix, message reads `"... assigned task \"unknown\""`.

### Re-run QA checkpoint

Day 4 / 4.6 — scroll the Sipho matter Activity feed and confirm all assignment events render
task titles. Cross-check against the other events (claimed, logged-time, generated-doc) that
already rendered correctly.

## Estimated Effort

**S (~20 min)** — one-line change plus one test. Backend rebuild + restart adds ~1 min.

## Out of Scope / Follow-up

- **Audit formatter hardening**: `ActivityMessageFormatter.getTitle()` returning the literal string
  `"unknown"` as a fallback is a trap elsewhere. A broader cleanup would replace the string
  `"unknown"` with `"a task"` or null-render the title entirely — but that's a formatter-wide
  refactor and not required to fix this bug. Leave for a future pass.
- **Retroactive backfill of historical audit events**: existing audit rows that were written
  without `title` will still display "unknown" unless the DB rows are updated. Not in scope — the
  QA scenario re-runs on a fresh tenant and the number of affected legacy rows is small. Document
  in release notes.
- **`task.status_changed` event path**: the formatter's same `formatTaskUpdated` branch checks
  for `status` in details and renders `"%s changed task \"%s\" status to %s"`. The same
  `getTitle()` fallback applies. The fix (always include `title`) silently fixes this sibling
  case too — no additional work needed.

## Notes

- **Not a domain-event bug**: `TaskAssignedEvent.taskTitle` IS populated correctly and is used by
  the notification fan-out path (`NotificationEventHandler`). The notification emails are
  unaffected.
- **Option A chosen over Option B** to preserve the rename audit trail. `AuditDeltaBuilder.track`
  is the right mechanism for rename history; `putIfAbsent` with the current title fills the
  readability gap without removing the delta.
- **Naming convention**: the plain-string key is `"title"` to match `task.created` (line 331 of
  `TaskService.java`) and the `getTitle()` lookup. No new key needed.
