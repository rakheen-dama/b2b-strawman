# ADR-023: "My Work" Cross-Project Query Pattern

**Status**: Accepted

**Context**: The "My Work" view is the first feature in the platform that queries across projects. All existing endpoints are either project-scoped (`/api/projects/{id}/tasks`) or org-scoped but single-entity (`/api/customers`). "My Work" must return:

1. Tasks assigned to the current member across **all** projects in the org.
2. Unassigned tasks in projects the member belongs to (via `project_members`).
3. Time entries logged by the member across all projects.

This raises questions about how to structure the queries, whether `ProjectAccessService` should be involved, and how the data is shaped for the frontend.

**Options Considered**:

1. **Client-side aggregation** — Frontend fetches the member's project list, then calls `GET /api/projects/{id}/tasks` for each project, merging results client-side.
   - Pros: Reuses existing per-project endpoints. No new backend code.
   - Cons: N+1 API calls (one per project). Slow for members in 10+ projects. No unified sorting/pagination. Wasteful — each call includes full project access checks.

2. **Backend aggregation with ProjectAccessService** — A new `MyWorkService` calls `ProjectAccessService.getAccessibleProjects(memberId)` to get the list of project IDs, then queries tasks filtered to those IDs.
   - Pros: Reuses the existing access control model. Consistent with how project-scoped endpoints work.
   - Cons: The access check is unnecessary for "my assigned tasks" — if a task is assigned to me, I implicitly have access. For unassigned tasks, the `project_members` JOIN already provides the access filter.

3. **Direct cross-project queries scoped by membership** — New JPQL queries in `TaskRepository` that filter by `assignee_id` or by `project_id IN (member's projects)`. No `ProjectAccessService` call — the query itself enforces the access boundary.
   - Pros: Single query per data set. Most efficient. The `WHERE assignee_id = :memberId` clause is its own authorization (you can only see tasks assigned to you). The `WHERE project_id IN (SELECT pm.project_id ...)` clause uses `project_members` as the authorization boundary — same data that `ProjectAccessService` would check.
   - Cons: Authorization is implicit (in the query) rather than explicit (via `ProjectAccessService`). New queries to write and test.

**Decision**: Direct cross-project queries scoped by membership (Option 3).

**Rationale**: The "My Work" view is inherently self-scoped — a member only sees their own tasks and their own projects' unassigned tasks. The authorization is embedded in the queries:

- **My assigned tasks**: `WHERE assignee_id = :memberId` — if you're the assignee, you have access. This is tautological and doesn't need a separate access check.
- **Unassigned in my projects**: `WHERE project_id IN (SELECT pm.project_id FROM project_members pm WHERE pm.member_id = :memberId)` — the `project_members` JOIN is the access boundary, same as what `ProjectAccessService` checks internally.

Calling `ProjectAccessService` for each project would add overhead without additional security value. The queries already enforce tenant isolation (via Hibernate `@Filter` or schema isolation) and membership scoping (via the `project_members` subquery or `assignee_id` filter).

The "My Work" endpoint requires only `ROLE_ORG_MEMBER` (any authenticated staff member). There's no concept of "admin seeing someone else's My Work" — it's strictly self-scoped.

**Query implementation**:

```java
// TaskRepository additions

@Query("""
    SELECT t FROM Task t
    WHERE t.assigneeId = :memberId
      AND t.status IN ('OPEN', 'IN_PROGRESS')
    ORDER BY t.dueDate ASC NULLS LAST, t.createdAt DESC
    """)
List<Task> findAssignedToMember(@Param("memberId") UUID memberId);

@Query("""
    SELECT t FROM Task t
    WHERE t.assigneeId IS NULL
      AND t.status = 'OPEN'
      AND t.projectId IN (
        SELECT pm.projectId FROM ProjectMember pm WHERE pm.memberId = :memberId
      )
    ORDER BY t.priority DESC, t.createdAt DESC
    """)
List<Task> findUnassignedInMemberProjects(@Param("memberId") UUID memberId);
```

Both queries naturally respect tenant isolation:
- **Pro tier**: Hibernate sets `search_path` to the dedicated schema. Only that org's tasks exist in the schema.
- **Starter tier**: Hibernate `@Filter` appends `AND tenant_id = :tenantId` to both queries. Only the current org's tasks are visible.

**Consequences**:
- New repository methods are self-contained queries — no external service dependencies.
- The "My Work" endpoint is fast: two queries + one for time entries, all hitting indexed columns.
- `ProjectAccessService` is **not** called by `MyWorkService` — the query structure provides equivalent authorization.
- If "admin view of another member's work" is needed later, a new endpoint with explicit `ProjectAccessService` checks can be added.
- Frontend receives pre-aggregated data (assigned + unassigned lists) — no client-side merging needed.
