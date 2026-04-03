# CR-002: Task Checklists — Assessment & Recommendation

**Status**: REJECTED — recommend lightweight alternative
**Date**: 2026-02-21
**Assessor**: Architecture review

---

## Executive Summary

CR-002 proposes reusing the existing checklist system (built for customer compliance/onboarding) to also serve as task sub-steps. After thorough investigation of both systems, **this change request is rejected**. The reuse would couple a compliance-critical system to a lightweight workflow need, introducing risk and complexity disproportionate to the value delivered.

A lightweight **task sub-items** feature is recommended instead.

---

## Why Reuse Is Harmful Here

### 1. Semantic Mismatch — Compliance Engine vs. Todo List

The checklist system is **compliance infrastructure**. It was designed (ADR-061) with:

| Capability | Customer Checklists | Task Sub-Steps (actual need) |
|-----------|-------------------|--------------------------|
| Document attachment requirements | Yes — `requires_document`, FK to `documents(id)` | No |
| Dependency chains between items | Yes — `depends_on_item_id`, DFS cycle detection | No |
| Required vs. optional items | Yes — `required` boolean gates instance completion | No — all items equal |
| Template → Instance snapshotting | Yes — items copied at instantiation, immune to template edits | Overkill — tasks are ephemeral |
| Compliance pack seeding | Yes — platform packs seeded on tenant provision | No |
| Lifecycle gating | Yes — gates ONBOARDING → ACTIVE customer transition | No — tasks have no lifecycle state machine |
| Auto-instantiation triggers | Yes — fires on PROSPECT → ONBOARDING | No natural trigger on tasks |
| `customerType` filtering | Yes — INDIVIDUAL/COMPANY/TRUST/ANY | No equivalent on tasks |
| Audit trail granularity | Yes — per-item audit events | Nice-to-have, not required |

What task sub-steps actually need: **a list of checkable items with a title and order**. That's it. The checklist system delivers this at ~10x the conceptual weight.

### 2. Making `customer_id` Nullable Weakens a Compliance Constraint

The current schema has `customer_id UUID NOT NULL` on `checklist_instances`. This is a **hard guarantee** — every checklist instance belongs to exactly one customer. Making it nullable to accommodate tasks:

- Introduces a CHECK constraint that the application must never violate (`customer_id XOR task_id`)
- Every existing query on the `checklist_instances` table must now reason about "is this a customer row or a task row?"
- The `checkLifecycleAdvance()` cascade (instance completion → customer ACTIVE transition) must branch on parent type
- If a bug creates a row with both `customer_id` and `task_id` null (bypasses CHECK), the instance is orphaned with no parent — a data integrity issue in a compliance system

The NOT NULL constraint is a safety net. Removing it to serve a different use case is introducing risk in a compliance-critical path.

### 3. Template Pollution

Adding `applicable_to` (CUSTOMER/TASK/ANY) to `checklist_templates` means:

- The template management UI must now filter/sort by applicability
- Compliance pack seeding must set `applicable_to = 'CUSTOMER'` on all seeded templates
- Admins see task templates mixed with compliance templates in settings
- The `customerType` column becomes meaningless for task-scoped templates (always `'ANY'`)

Templates were designed for compliance workflows. They have fields (`source`, `pack_id`, `pack_template_key`, `customer_type`) that have no meaning in a task context. Rather than extending them, we'd be ignoring half their columns.

### 4. Service Layer Contamination

`ChecklistInstanceService` currently has a clean single responsibility: manage checklist instances for customer onboarding. The proposed changes add:

- `createFromTemplateForTask()` — parallel code path
- `getInstancesWithItemsForTask()` — parallel code path
- `checkTaskAdvance()` — new cascade logic
- Parent-type branching in `checkInstanceCompletion()`
- Null checks on `customerId` throughout

Every method now needs to ask "am I dealing with a customer or a task?" This is the **shotgun surgery** code smell — a single change (adding task scope) requires modifications across the entire service.

### 5. Disproportionate Effort

The proposed implementation touches **12+ backend files** and **7+ frontend files** across 4 slices. For what amounts to a checkable list on a task detail page. The effort-to-value ratio is poor.

---

## Recommended Alternative: Lightweight Task Items

Instead of broadening the checklist system, build a purpose-fit **task sub-items** feature. This is a simple, self-contained addition that doesn't touch the compliance system at all.

### Schema (new table, single migration)

```sql
CREATE TABLE IF NOT EXISTS task_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title       VARCHAR(500) NOT NULL,
    completed   BOOLEAN NOT NULL DEFAULT false,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_task_items_task_sort
    ON task_items (task_id, sort_order);
```

That's the entire schema. No templates, no instances, no dependency chains, no document requirements.

### Backend

| Component | Description |
|-----------|-------------|
| `TaskItem.java` | ~30-line entity: id, taskId, title, completed, sortOrder, timestamps |
| `TaskItemRepository.java` | `findByTaskIdOrderBySortOrder(UUID)`, `deleteByTaskId(UUID)` |
| `TaskItemService.java` | CRUD + toggle + reorder. ~80 lines |
| `TaskItemController.java` | Nested under task: `GET/POST /api/tasks/{id}/items`, `PUT/DELETE /api/tasks/{id}/items/{itemId}`, `PUT /api/tasks/{id}/items/{itemId}/toggle` |
| `TaskItemDtos.java` | Request/response records |

**No changes to**: `ChecklistInstance`, `ChecklistInstanceService`, `ChecklistTemplate`, `CustomerLifecycleService`, or any compliance code.

### Frontend

- Add a **"Sub-Items"** section to the task detail sheet (above the tabs, not as a tab — sub-items are primary task content, not metadata)
- Inline checkboxes with add/remove/reorder
- Progress indicator: "3/5 items complete"
- Simple, tight component — no accordion, no dependency visualization, no document upload

### Optional Enhancement: Completion Gating

If the product wants sub-items to gate task completion:

```java
// In TaskService.updateTask():
if ("DONE".equals(newStatus) && !"DONE".equals(currentStatus)) {
    long incomplete = taskItemRepository.countByTaskIdAndCompleted(taskId, false);
    if (incomplete > 0) {
        throw new InvalidStateException("Task items incomplete",
            "Cannot mark done — " + incomplete + " sub-item(s) still unchecked");
    }
}
```

This is 5 lines in `TaskService`, not a cross-system cascade.

### Effort Estimate

**1 epic, 2 slices** (vs. 2 epics, 4 slices for the reuse approach):
- Slice A: Backend entity + service + controller + tests
- Slice B: Frontend sub-items section + toggle interactions + tests

---

## Comparison Table

| Dimension | CR-002 (Reuse Checklists) | Recommended (Task Items) |
|-----------|--------------------------|-------------------------|
| Files modified | 19+ (12 backend, 7 frontend) | 8 (5 backend, 3 frontend) |
| Slices | 4 | 2 |
| Compliance system impact | High — nullable FK, service branching | Zero |
| Schema changes to existing tables | Yes — `checklist_instances`, `checklist_templates` | No — new table only |
| Risk of regression | Medium — compliance lifecycle cascade modified | None — self-contained |
| Template management complexity | Increased (applicability filter, mixed lists) | None |
| Covers 90% of the use case? | Yes | Yes |
| Covers the remaining 10%? | Yes (doc requirements, dependency chains) | No — and that's fine |
| Reversibility | Hard — schema change to compliance tables | Easy — drop one table |

---

## What If We Need the Other 10% Later?

If task sub-steps eventually need document requirements, dependency chains, or template-based instantiation, we can revisit the architecture then. But:

1. **YAGNI** — In 15 completed phases, no user has asked for document-gated task sub-steps
2. **The accounting firm vertical** (nearest fork target) needs checklist-driven tasks, but those are *task-level* checklists (e.g., "annual return preparation"), not sub-item-level document requirements. The lightweight approach serves this.
3. If the need materializes, we can build a bridge at that point — e.g., an endpoint that instantiates a checklist template's items as task sub-items (one-time copy, no ongoing coupling)

---

## Decision

**Reject CR-002 as designed.** The "reuse" approach couples a compliance system to a workflow feature, introducing risk and complexity disproportionate to the value.

**Approve the lightweight alternative:** Build a self-contained `task_items` table with simple CRUD, inline checkboxes in the task detail sheet, and optional completion gating. Half the effort, zero compliance risk, covers the actual use case.
