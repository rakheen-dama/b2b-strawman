# CR-003: Project Template Tasks Should Include Sub-Items

**Status**: INVESTIGATED — ready for implementation
**Priority**: low
**Effort**: ~1 slice
**Depends on**: PR #279 (task sub-items) — merged

---

## 1. Problem Statement

When a project is created from a template, `TemplateTask` rows become real `Task` rows — but the task sub-items feature (PR #279) isn't wired into the template system. A template for "Annual Return Preparation" should be able to preset sub-items like "Collect IRP5s", "Verify tax certificates" on each task.

Additionally, "Save as Template" from an existing project does not capture the task's sub-items.

---

## 2. Current State Analysis

### What exists today

| Layer | Component | State |
|-------|-----------|-------|
| Backend | `TemplateTask` entity | 7 fields, no sub-item concept |
| Backend | `TaskItem` entity (PR #279) | `id`, `taskId`, `title`, `completed`, `sortOrder`, `createdAt`, `updatedAt` |
| Backend | `ProjectTemplateService.instantiateTemplate()` | Creates `Task` per `TemplateTask` — no items |
| Backend | `ProjectTemplateService.instantiateFromTemplate()` | Same loop (scheduler path) — no items |
| Backend | `ProjectTemplateService.duplicate()` | Deep-copies tasks — no items |
| Backend | `ProjectTemplateService.saveFromProject()` | Copies project tasks to template — no items |
| Backend | `ProjectTemplateService.create()` / `update()` | Saves `TemplateTask` from `TemplateTaskRequest` — no items field |
| Frontend | `TemplateEditor.tsx` | Task rows with name, desc, hours, role, billable — no nested items UI |
| Frontend | `SaveAsTemplateDialog.tsx` | Selects project tasks to copy — does not show/copy sub-items |
| DB | `V30__project_templates_recurring_schedules.sql` | Creates `template_tasks` — no items table |
| DB | `V33__create_task_items.sql` | Creates `task_items` for real tasks |

### Touch points (7 backend changes, 2 frontend changes)

**Backend service methods that need sub-item handling:**
1. `create()` — save `TemplateTaskItem` rows alongside `TemplateTask` rows
2. `update()` — delete-all + re-insert pattern already used for tasks; extend to items
3. `duplicate()` — copy items when copying tasks
4. `saveFromProject()` — query `TaskItemRepository` for each selected task, create `TemplateTaskItem` rows
5. `instantiateTemplate()` — after saving each `Task`, create `TaskItem` rows from `TemplateTaskItem`
6. `instantiateFromTemplate()` — same as above (scheduler path)
7. `buildResponse()` — include items in the response DTO

**Frontend components:**
1. `TemplateEditor.tsx` — nested sub-item inputs per task row
2. `SaveAsTemplateDialog.tsx` — show sub-items when saving from project (read-only, auto-included)

---

## 3. Proposed Solution

### 3.1 Database: `V34__create_template_task_items.sql`

```sql
CREATE TABLE IF NOT EXISTS template_task_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_task_id UUID NOT NULL REFERENCES template_tasks(id) ON DELETE CASCADE,
    title            VARCHAR(500) NOT NULL,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_template_task_items_task_sort
    ON template_task_items (template_task_id, sort_order);
```

**Design rationale**: Mirrors `task_items` structure but without `completed` (templates define structure, not state). `ON DELETE CASCADE` matches the existing pattern — when a `template_tasks` row is deleted, its items cascade.

### 3.2 Backend Entity: `TemplateTaskItem`

New file: `projecttemplate/TemplateTaskItem.java` (~35 lines)

| Field | Column | Type | Notes |
|-------|--------|------|-------|
| `id` | `id` | `UUID` | PK, `@GeneratedValue(UUID)` |
| `templateTaskId` | `template_task_id` | `UUID` | FK (bare UUID, no `@ManyToOne`) |
| `title` | `title` | `String` | NOT NULL, max 500 |
| `sortOrder` | `sort_order` | `int` | NOT NULL |
| `createdAt` | `created_at` | `Instant` | NOT NULL, immutable |
| `updatedAt` | `updated_at` | `Instant` | NOT NULL |

Follows the exact same pattern as `TemplateTask` — bare UUID FK, no JPA associations, cascade via SQL.

### 3.3 Backend Repository: `TemplateTaskItemRepository`

```java
public interface TemplateTaskItemRepository extends JpaRepository<TemplateTaskItem, UUID> {
    List<TemplateTaskItem> findByTemplateTaskIdOrderBySortOrder(UUID templateTaskId);
    List<TemplateTaskItem> findByTemplateTaskIdInOrderBySortOrder(Collection<UUID> templateTaskIds);

    @Modifying
    void deleteByTemplateTaskId(UUID templateTaskId);

    @Modifying
    void deleteByTemplateTaskIdIn(Collection<UUID> templateTaskIds);
}
```

The `In` variants enable batch operations — load/delete items for all tasks of a template in one query instead of N+1.

### 3.4 DTO Changes

**`TemplateTaskRequest`** — add optional items list:
```java
public record TemplateTaskRequest(
    @NotBlank @Size(max = 300) String name,
    String description,
    BigDecimal estimatedHours,
    int sortOrder,
    boolean billable,
    String assigneeRole,
    @Valid List<TemplateTaskItemRequest> items  // NEW — nullable, empty = no items
) {}
```

**`TemplateTaskItemRequest`** (new nested record):
```java
public record TemplateTaskItemRequest(
    @NotBlank @Size(max = 500) String title,
    int sortOrder
) {}
```

**`TemplateTaskResponse`** — add items list:
```java
public record TemplateTaskResponse(
    UUID id, String name, String description,
    BigDecimal estimatedHours, int sortOrder,
    boolean billable, String assigneeRole,
    List<TemplateTaskItemResponse> items  // NEW
) {}
```

**`TemplateTaskItemResponse`** (new nested record):
```java
public record TemplateTaskItemResponse(UUID id, String title, int sortOrder) {}
```

### 3.5 Service Changes

All changes are **additive** within existing methods:

#### `create()` — after saving each `TemplateTask`, save its items:
```java
templateTaskRepository.save(task);
if (taskReq.items() != null) {
    for (var itemReq : taskReq.items()) {
        templateTaskItemRepository.save(
            new TemplateTaskItem(task.getId(), itemReq.title(), itemReq.sortOrder()));
    }
}
```

#### `update()` — delete items via cascade (already handled by `deleteByTemplateId` on tasks), then re-insert:
The existing `templateTaskRepository.deleteByTemplateId(id)` is a JPQL `DELETE` that won't trigger SQL `ON DELETE CASCADE`. Need to delete items explicitly first:
```java
var existingTaskIds = templateTaskRepository.findByTemplateIdOrderBySortOrder(id)
    .stream().map(TemplateTask::getId).toList();
if (!existingTaskIds.isEmpty()) {
    templateTaskItemRepository.deleteByTemplateTaskIdIn(existingTaskIds);
}
templateTaskRepository.deleteByTemplateId(id);
// ... then re-insert tasks + items as in create()
```

#### `duplicate()` — after copying each task, copy its items:
```java
var taskCopy = new TemplateTask(...);
templateTaskRepository.save(taskCopy);
var items = templateTaskItemRepository.findByTemplateTaskIdOrderBySortOrder(task.getId());
for (var item : items) {
    templateTaskItemRepository.save(
        new TemplateTaskItem(taskCopy.getId(), item.getTitle(), item.getSortOrder()));
}
```

#### `saveFromProject()` — after creating each `TemplateTask`, query the real task's `TaskItem`s and copy:
```java
templateTaskRepository.save(templateTask);
var taskItems = taskItemRepository.findByTaskIdOrderBySortOrder(taskId);
for (var item : taskItems) {
    templateTaskItemRepository.save(
        new TemplateTaskItem(templateTask.getId(), item.getTitle(), item.getSortOrder()));
}
```

#### `instantiateTemplate()` and `instantiateFromTemplate()` — after saving each `Task`, create `TaskItem`s:
```java
projectTaskRepository.save(task);
var templateItems = templateTaskItemRepository.findByTemplateTaskIdOrderBySortOrder(tt.getId());
for (var item : templateItems) {
    taskItemRepository.save(new TaskItem(task.getId(), item.getTitle(), item.getSortOrder()));
}
```

#### `buildResponse()` — include items in `TemplateTaskResponse`:
Batch-load all items for the template's tasks to avoid N+1:
```java
var taskIds = tasks.stream().map(TemplateTask::getId).toList();
var allItems = templateTaskItemRepository.findByTemplateTaskIdInOrderBySortOrder(taskIds);
var itemsByTaskId = allItems.stream()
    .collect(Collectors.groupingBy(TemplateTaskItem::getTemplateTaskId));
// Then when building each TemplateTaskResponse, pass itemsByTaskId.getOrDefault(task.getId(), List.of())
```

### 3.6 Frontend: `TemplateEditor.tsx`

Extend the `TaskRow` interface:
```typescript
interface TaskItemRow {
  key: string;
  title: string;
}

interface TaskRow {
  // ... existing fields
  items: TaskItemRow[];  // NEW
}
```

Per task row, add a collapsible sub-items section:
- "Add sub-item" button (+ icon)
- Each sub-item: title input + remove button
- Reorder via up/down arrows (matching existing task reorder pattern)
- Items are lightweight — title only, no description/hours/role

On save, map `items` to `TemplateTaskItemRequest[]` in the payload.

When editing an existing template, initialize `items` from `TemplateTaskResponse.items`.

### 3.7 Frontend: `SaveAsTemplateDialog.tsx`

When a project task has sub-items, show them indented below the task checkbox (read-only, auto-included when the parent task is selected). No individual item selection — all or nothing with the parent task.

### 3.8 Frontend: Types

Update `TemplateTaskResponse` and `TemplateTaskRequest` interfaces in `lib/api/templates.ts`:
```typescript
export interface TemplateTaskItemResponse {
  id: string;
  title: string;
  sortOrder: number;
}

export interface TemplateTaskResponse {
  // ... existing fields
  items: TemplateTaskItemResponse[];
}

export interface TemplateTaskItemRequest {
  title: string;
  sortOrder: number;
}

export interface TemplateTaskRequest {
  // ... existing fields
  items?: TemplateTaskItemRequest[];
}
```

---

## 4. Architecture Decision

### ADR-065: Template Task Sub-Items via Dedicated Table

**Status**: Proposed
**Context**: CR-003 requires project templates to define sub-items per task, so that instantiated projects get pre-populated task sub-items.

**Decision**: Add a `template_task_items` table that mirrors `task_items` structure (minus `completed`). Template task items are purely structural definitions — completion state only exists on real `TaskItem` rows after instantiation.

**Alternatives Considered**:

1. **JSON column on `template_tasks`** — Store items as `JSONB` array on the existing table.
   - Pro: No new table, no migration
   - Con: No referential integrity, can't query items independently, breaks the relational pattern used everywhere else

2. **Reuse `task_items` table with a polymorphic FK** — Add `entity_type` + `entity_id` columns to `task_items` to support both tasks and template tasks.
   - Pro: Single table for all sub-items
   - Con: Mixes runtime state (`completed`) with template definitions; polymorphic FKs complicate queries and lose FK constraints; `task_items` already has `ON DELETE CASCADE` to `tasks(id)` which wouldn't work for template tasks

3. **Store items in `TemplateTaskRequest` JSON only** — Don't persist items in the DB; embed them in the template task's description field as structured text.
   - Pro: Zero schema changes
   - Con: Lossy, not queryable, breaks the clean instantiation pipeline

**Consequences**:
- New `template_task_items` table (V34 migration)
- New `TemplateTaskItem` entity + repository (~50 lines total)
- 7 service method updates (all additive, ~5-10 lines each)
- DTOs extended with nested items (backward-compatible — items default to empty list)
- Frontend template editor gets nested sub-item inputs per task

---

## 5. Affected Files Summary

### New Files (3)
| File | Description |
|------|-------------|
| `backend/.../projecttemplate/TemplateTaskItem.java` | Entity (~35 lines) |
| `backend/.../projecttemplate/TemplateTaskItemRepository.java` | Repository interface (~15 lines) |
| `backend/src/main/resources/db/migration/tenant/V34__create_template_task_items.sql` | Migration (~12 lines) |

### Modified Files — Backend (5)
| File | Change |
|------|--------|
| `backend/.../projecttemplate/dto/TemplateTaskRequest.java` | Add `items` field |
| `backend/.../projecttemplate/dto/TemplateTaskResponse.java` | Add `items` field + new `TemplateTaskItemResponse` record |
| `backend/.../projecttemplate/ProjectTemplateService.java` | 7 methods updated (~60 lines added) |
| `backend/.../task/TaskItemRepository.java` | No change needed (already has `findByTaskIdOrderBySortOrder`) |
| `backend/.../projecttemplate/ProjectTemplateController.java` | No change needed (DTOs flow through automatically) |

### Modified Files — Frontend (3)
| File | Change |
|------|--------|
| `frontend/lib/api/templates.ts` | Add item types to interfaces |
| `frontend/components/templates/TemplateEditor.tsx` | Nested sub-item inputs per task (bulk of frontend work) |
| `frontend/components/templates/SaveAsTemplateDialog.tsx` | Show sub-items under selected tasks |

### Test Files (2-3 new/modified)
| File | Change |
|------|--------|
| `backend/.../projecttemplate/ProjectTemplateIntegrationTest.java` | 3-4 new test cases for item round-trip |
| `frontend/__tests__/templates/TemplateEditor.test.tsx` | Tests for add/remove sub-items |
| `frontend/__tests__/templates/SaveAsTemplateDialog.test.tsx` | Test that sub-items display |

---

## 6. Backward Compatibility

All changes are **additive and backward-compatible**:

- Existing templates with no sub-items continue to work — `items` defaults to empty list
- API responses gain an `items: []` field on each task — no breaking change for consumers
- API requests with no `items` field are valid — the field is optional/nullable
- The `TaskItem` entity is unmodified — only the template side gains new structure
- Database `ON DELETE CASCADE` handles cleanup — no orphan risk

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| N+1 queries on `buildResponse` | Medium | Low (perf) | Batch load via `findByTemplateTaskIdIn` |
| JPQL `deleteByTemplateId` skips SQL cascade | High | Medium (orphans) | Explicitly delete items before tasks in `update()` |
| Frontend complexity in nested form | Low | Medium (UX) | Keep items minimal (title only), reuse existing up/down pattern |
| `saveFromProject` doesn't load items | Certain (current state) | Medium (data loss) | Fix in this CR — query `TaskItemRepository` per selected task |

---

## 8. Implementation Order

1. **V34 migration** — create `template_task_items` table
2. **Entity + Repository** — `TemplateTaskItem`, `TemplateTaskItemRepository`
3. **DTO changes** — extend request/response records
4. **Service changes** — all 7 methods in `ProjectTemplateService`
5. **Backend tests** — integration tests for create, update, instantiate, duplicate, saveFromProject
6. **Frontend types** — update `lib/api/templates.ts`
7. **Frontend editor** — nested sub-item UI in `TemplateEditor.tsx`
8. **Frontend save-as-template** — show items in `SaveAsTemplateDialog.tsx`
9. **Frontend tests** — component tests
