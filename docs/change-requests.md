# Change Requests

UX improvements and design refinements identified during product walkthrough testing. These aren't bugs (functionality works correctly) but improve the user experience.

**Format**: Each entry gets an ID (CR-NNN), priority, affected area, description, and implementation guidance.

**Priorities**: `high` (significantly impacts usability), `medium` (noticeable improvement), `low` (nice-to-have polish)

---

## CR-001: Compact the document upload zone

**Priority**: medium
**Area**: Frontend — Documents panel (`FileUploadZone`)
**Found in**: Project detail page, Documents tab

**Description**: The drag-and-drop upload zone takes up disproportionate vertical space relative to its function. It renders as a full-width dashed-border box with `p-6` padding, a `size-8` icon, and two lines of text — always visible below the documents table. For a secondary action, it dominates the layout.

**Current Implementation**: `frontend/components/documents/file-upload-zone.tsx` lines 87-107 — `p-6` padding, `size-8` Upload icon, two `<p>` tags for instructions and file size limit. Used in `documents-panel.tsx` (line 470), `org-document-upload.tsx`, and `customer-document-upload.tsx`.

**Suggested Change**:
1. Reduce padding from `p-6` to `px-4 py-3`
2. Switch from stacked vertical layout to a single inline row: icon + text on one line
3. Reduce icon from `size-8` to `size-4` or `size-5`
4. Merge the two text lines into one: "Drop files here or click to browse (max 100 MB)"
5. Keep the drag-over highlight state as-is — it's useful feedback
6. The component is reused in 3 places (`documents-panel.tsx`, `org-document-upload.tsx`, `customer-document-upload.tsx`), so the change propagates automatically

**Affected Files**:
- `frontend/components/documents/file-upload-zone.tsx` — layout and spacing changes only

---

## CR-002: Ability to attach checklists to tasks

**Status**: RESOLVED — rejected checklist reuse, implemented lightweight task sub-items (PR #279)
**Decision doc**: `docs/cr-002-task-checklists-design.md`

**Priority**: low
**Area**: Both — Tasks + Checklists
**Found in**: Task management workflow
**Requires**: Architecture decision before implementation

**Description**: Tasks are currently lightweight (title, description, billing flag). There's no way to break a task into trackable sub-steps. Adding checklist support to tasks would enable structured workflows — e.g. "Prepare annual return" with items like "Collect IRP5s", "Verify tax certificates", "Submit to SARS".

**Current State**:
- Checklists are scoped to **customers only** — `ChecklistInstance` links to `customerId` via `ChecklistInstanceService`
- The checklist system has three layers: `ChecklistTemplate` (org-level definition) → `ChecklistInstance` (attached to a customer) → `ChecklistInstanceItem` (individual completable items)
- Tasks (`Task` entity) have: title, description, status, assignee, billable flag, project link. No sub-item or checklist capability.

**Preferred Approach** (founder preference, pending architect review):
Reuse the existing checklist system by broadening its scope from customer-only to customer + task. This avoids building a parallel sub-task system and leverages existing template/instance/item infrastructure.

**Architecture Questions** (to resolve before implementation):
1. **Scope broadening**: Should `ChecklistInstance` gain a polymorphic parent (e.g. `entityType` + `entityId` replacing the current `customerId` FK)? Or a separate `taskId` nullable FK alongside `customerId`?
2. **Template applicability**: Checklist templates currently have `customerType` filtering. Tasks would need a different filter dimension — perhaps `applicableTo` (CUSTOMER, TASK, BOTH)?
3. **Auto-instantiation**: Checklist templates can auto-instantiate when a customer enters onboarding. Should task checklists auto-attach based on task type/tags, or only be manually attached?
4. **Lifecycle coupling**: Customer checklists gate the ONBOARDING → ACTIVE transition. Should task checklists gate task status transitions (e.g. can't mark DONE until checklist is complete)?
5. **Task maturity**: Tasks are thin right now. Is this the right time to add structure, or should tasks first gain other properties (due dates, priorities, sub-tasks) in a broader "task enrichment" phase?

**Affected Systems** (estimated, pending architecture):
- `ChecklistInstance` / `ChecklistInstanceService` — broaden scope
- `ChecklistTemplate` — add task applicability
- `ChecklistInstanceController` — new endpoints for task-scoped checklists
- Task detail frontend — render checklist panel
- Migration — schema changes to `checklist_instances` table

---

## CR-003: Project template tasks should include sub-items

**Status**: IMPLEMENTED — PR #282
**Decision doc**: `docs/cr-003-template-task-sub-items.md`

**Priority**: low
**Area**: Both — Project Templates + Task Sub-Items
**Found in**: Project creation from template
**Follows**: CR-002 (task sub-items, merged in PR #279)

**Description**: When a project is created from a template, preset `TemplateTask` rows become real `Task` rows — but the new task sub-items feature (PR #279) isn't wired into the template system. Templates can define tasks with title, description, assignee role, and estimated hours, but cannot define sub-items per task. A template for "Annual Return Preparation" should be able to preset sub-items like "Collect IRP5s", "Verify tax certificates" on each task.

**Current Implementation**: `ProjectTemplateService.instantiateTemplate()` loops over `TemplateTask` rows and creates `Task` entities. `TemplateTask` (`template_tasks` table) has: `name`, `description`, `estimatedHours`, `sortOrder`, `billable`, `assigneeRole`. No sub-item concept exists on the template side.

**Suggested Change**:
1. New migration (V34): `template_task_items` table — `id`, `template_task_id` (FK CASCADE), `title` (VARCHAR 500), `sort_order` (INT)
2. New entity: `TemplateTaskItem` (~40 lines) + `TemplateTaskItemRepository` (1 query method)
3. In `ProjectTemplateService.instantiateTemplate()`, after creating each `Task`, query its `TemplateTaskItem` rows and create `TaskItem` rows with matching titles and sort orders (~10 lines in the existing loop)
4. Extend `TemplateTask` DTOs to accept/return `items: [{title, sortOrder}]`
5. Frontend: template editor form — add nested sub-item inputs per template task (the most work)
6. 3-4 new test cases in existing `ProjectTemplateIntegrationTest`

**Estimated Effort**: ~1 slice (backend is trivial, frontend template editor is the bulk)

**Affected Files**:
- `backend/src/main/resources/db/migration/tenant/V34__*.sql` — new table
- `backend/.../projecttemplate/TemplateTaskItem.java` — new entity
- `backend/.../projecttemplate/TemplateTaskItemRepository.java` — new repo
- `backend/.../projecttemplate/ProjectTemplateService.java` — extend instantiation loop
- `backend/.../projecttemplate/ProjectTemplateDtos.java` — extend DTOs
- `frontend/components/project-templates/template-task-form.tsx` — nested sub-item inputs

---

<!-- TEMPLATE — copy this for new change requests:

## CR-NNN: [Short description]

**Priority**: [high/medium/low]
**Area**: [Backend/Frontend/Both] — [specific domain]
**Found in**: [Page or scenario]

**Description**: [What the current experience is vs. what would be better]

**Current Implementation**: [File, line, what it does now]

**Suggested Change**:
1. [Step-by-step implementation guidance]

**Affected Files**:
- [file path and what needs changing]

-->
