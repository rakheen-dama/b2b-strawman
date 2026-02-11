# ADR-024: Portal-Ready Seams for Task & Time Data

**Status**: Accepted

**Context**: The platform is building toward a customer portal (see [ADR-020](ADR-020-customer-portal-approach.md)) where customers can see project status and progress. Phase 5 adds time tracking, which generates new data that could be useful in the portal (e.g., "how much work has been done on my project?"). The question is how much portal infrastructure to build now alongside the time tracking feature.

**Options Considered**:

1. **Build portal endpoints now** — Implement `/portal/*` endpoints for task summaries and time totals alongside the staff endpoints.
   - Pros: Portal is immediately usable. No second implementation pass.
   - Cons: Premature — portal auth (`CustomerAuthFilter`, magic links) is not built yet. Building portal endpoints without the auth layer means they're either unsecured or require scaffolding a temporary auth mechanism. Doubles the endpoint surface area and test burden for a feature that has no users yet.

2. **Build a full event system now** — Implement Spring Application Events (`TaskStatusChanged`, `TimeEntryLogged`) with listeners, event store, and replay capability.
   - Pros: Future-proof. Portal can subscribe to events for real-time updates.
   - Cons: Significant infrastructure for zero current consumers. Event shapes may change as portal requirements are discovered. Events without consumers are untestable dead code.

3. **Define minimal seams only** — Document the data shapes and query patterns the portal will need. Ensure the staff-facing services are structured so portal services can reuse them. Do not build portal endpoints, event infrastructure, or dedicated portal DTOs.
   - Pros: Zero overhead now. Informed future implementation — the portal team knows exactly what data is available and how to access it. Services are structured for reuse (extracting a read-only view from an existing service is trivial). No dead code.
   - Cons: Portal implementation requires a full pass later. No real-time notification capability until events are added.

4. **Build events but no portal endpoints** — Emit Spring Application Events on task status changes. No listeners yet — events exist as hooks for future consumers.
   - Pros: Events are lightweight to emit (`applicationEventPublisher.publishEvent()`). Event shapes are defined and versioned.
   - Cons: Events without listeners are untested and can silently break. The shape of the event may not match what the portal actually needs. Premature abstraction.

**Decision**: Define minimal seams only (Option 3) with documented event shapes.

**Rationale**: The customer portal has no users, no timeline, and no finalized requirements. Building portal infrastructure now would be speculative engineering. Instead, Phase 5 ensures the **internal service layer** is portal-ready:

1. **Aggregation queries are in the repository** — The same `SELECT SUM(duration_minutes) ... GROUP BY billable` query that powers the staff dashboard can power the portal summary with a `WHERE` clause that scopes to customer-linked projects.

2. **Task listing is already filterable** — `TaskRepository.findByProjectIdWithFilters()` accepts status, assignee, and priority filters. A portal version would call the same query but project a smaller DTO (no assignee name, no description).

3. **Customer-to-task mapping is trivial** — The `customer_projects` junction table provides the bridge. A future `PortalTaskService` would:
   ```java
   List<UUID> projectIds = customerProjectRepository.findProjectIdsByCustomerId(customerId);
   List<Task> tasks = taskRepository.findByProjectIdIn(projectIds);
   ```

4. **Event shapes are documented** — The Phase 5 architecture section documents `TaskStatusChanged`, `ProjectProgressUpdated`, and `TimeEntryLogged` event shapes. When events are needed, the implementation is: (a) add `applicationEventPublisher.publishEvent()` in `TaskService`, (b) add `@EventListener` in a new `PortalNotificationService`. This is a 1-2 hour change, not an architectural decision.

**What "portal-ready" means concretely**:

| Staff service | Portal reuse path | What changes |
|--------------|-------------------|-------------|
| `TaskService.listTasks(projectId, ...)` | Call with customer's linked projectIds | Add DTO projection (strip internal fields) |
| `TimeEntryRepository` aggregation queries | Wrap in `PortalTimeSummaryService` | Filter to billable only, strip rates |
| `ProjectAccessService` | Replace with `CustomerAccessService` | Check `customer_projects` instead of `project_members` |
| `TaskRepository.findByProjectIdWithFilters()` | Same query, scoped to customer's projects | No change to query |

**Consequences**:
- No `/portal/*` endpoints in Phase 5.
- No event infrastructure in Phase 5.
- No `CustomerAuthFilter` or portal JWT in Phase 5.
- Phase 5 services are structured as reusable building blocks (repository queries, aggregation methods) that don't hardcode staff-specific assumptions.
- Event shapes are documented in the architecture doc — not in code. This avoids dead code while preserving the design intent.
- When the portal is built (Phase 6+), the implementation effort is **additive** (new service + new controller + new filter chain), not **refactoring** (no existing code needs to change).
