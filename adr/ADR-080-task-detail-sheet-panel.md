# ADR-080: Task Detail Surface — Slide-over Sheet Panel

**Status**: Accepted

**Context**:

Tasks are the most-used entity in the system — staff interact with them daily. Yet they have the weakest frontend surface. The current implementation (since Phase 5, task 45.5) uses inline row expansion inside the project detail page's `TaskListPanel`: clicking a task title toggles a `colSpan={6}` second `<TableRow>` showing `TimeEntryList` and `CommentSectionClient`. This was a reasonable v1 choice but has created a cascade of integration gaps:

1. **No assignee at creation**: `CreateTaskDialog` has 5 fields (title, description, priority, type, dueDate) but no member picker. The backend fully supports `assigneeId` on the Task entity, and leads/admins can assign via `PUT /api/tasks/{id}`. The claim-based workflow (ADR-019) is intentional for self-service, but pre-assignment at creation is a legitimate admin need.

2. **No dedicated task detail surface**: The inline expanded row has no room for tags, custom fields, or other metadata. There is no `/projects/[id]/tasks/[taskId]` route.

3. **Tags and custom fields not on tasks**: Phase 11 wired `TagInput` and `CustomFieldSection` to project and customer detail pages. The backend is fully ready — `Task.java` has `custom_fields` (JSONB) and `applied_field_groups` columns, `EntityTag` covers tasks polymorphically, and `FieldDefinition` includes `EntityType.TASK`. But the Phase 11 plan assumed a task detail page that doesn't exist.

4. **Saved views not on tasks**: The backend supports `entityType="TASK"` saved views — `TaskController` accepts `?view={viewId}`, `ViewFilterIntegrationTest` exercises TASK views. `ViewSelectorClient` is wired to projects and customers list pages but not to any task-displaying surface.

**Options Considered**:

1. **Dedicated task detail page** (`/projects/[id]/tasks/[taskId]`) — New route with full page layout for task metadata, tags, custom fields, time entries, comments.
   - Pros: Clean surface for all content. Linkable URL. Room to grow.
   - Cons: Navigates away from task list context. Additional route and layout to maintain. Users lose their scroll position and filter state in the task list.

2. **Enhanced inline expansion** — Add tags, custom fields, assignee selector, and saved views into the existing expanded table row.
   - Pros: User stays in list context. No new routes or components.
   - Cons: A `<TableRow>` expansion is fundamentally cramped for this amount of content. Mobile UX degrades significantly. Tags + custom fields + time entries + comments in a single row expansion becomes unwieldy. Saved views need a header placement that doesn't exist in the inline pattern.

3. **Slide-over Sheet panel (chosen)** — Right-side Sheet component that slides in when a task row is clicked, showing full task detail. URL updates via `?taskId={uuid}` search param for deep-linking.
   - Pros: Best of both worlds — task list remains visible/accessible while the detail panel provides full real estate. URL-addressable. Standard pattern in modern PM tools (Linear, Asana, Notion, ClickUp). The shadcn `Sheet` component already exists in the codebase (used for mobile sidebar). Responsive — goes full-width on mobile.
   - Cons: Slightly more complex than a plain page (needs controlled open/close state and URL sync). Sheet width needs careful tuning to not obscure too much of the list.

**Decision**: Option 3 — slide-over Sheet panel.

**Rationale**:

The inline expansion was a v1 scaffold that served its purpose — it proved tasks needed time entries and comments. But scaling it to include tags, custom fields, assignee selection, and saved views would create a cramped, unusable surface, particularly on mobile. A dedicated page (Option 1) solves the space problem but creates a navigation problem: task management is an inherently list-centric workflow where users rapidly context-switch between tasks. Navigating away from the list breaks this flow.

The slide-over panel pattern has become the de facto standard in project management tools because it resolves this tension: the list stays visible for orientation and quick switching, while the panel provides enough real estate for full detail. The `Sheet` component already exists in the codebase (`components/ui/sheet.tsx`, built on Radix Dialog) with support for all four sides, animations, and overlay. It is currently only used for the mobile sidebar — this is its natural next use case.

URL state via `?taskId={uuid}` makes the panel deep-linkable. Refreshing the page or sharing the URL re-opens the sheet to the same task. This provides the same addressability as a dedicated page without the navigation tradeoff.

**Backend impact**: Minimal. Only `CreateTaskRequest` needs an optional `assigneeId` field added. All other backend capabilities (tags, custom fields, saved views, time entries, comments) already exist and are tested.

**Consequences**:

- The inline row expansion in `TaskListPanel` will be removed — the Sheet replaces it entirely. No dual interaction model.
- `TaskDetailSheet` becomes the canonical task detail surface, reusable from both the project task list and potentially the My Work page.
- `ViewSelectorClient` can be wired into the `TaskListPanel` header (same pattern as projects/customers list pages).
- `TagInput` and `CustomFieldSection` integrate into the Sheet body (same pattern as project/customer detail pages).
- The `Sheet` component's default `sm:max-w-sm` width will need to be overridden to `sm:max-w-xl` for adequate task detail space.
- Future task-related features (attachments, subtasks, activity log) have a natural home in the Sheet panel via additional tabs or sections.
