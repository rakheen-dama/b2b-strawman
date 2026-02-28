# ADR-116: Recurring Task Implementation

**Status**: Accepted

**Context**:

Phase 30 adds recurring task support so that repeatable work items (monthly bank reconciliations, weekly status reports, quarterly compliance reviews) automatically create the next instance when the current one is completed. This is a common requirement in professional services where many tasks follow a predictable cadence.

The implementation must decide where recurrence metadata lives and how new task instances are created. The existing codebase has two relevant patterns: (1) the Task entity itself (Phase 4, Phase 29), which is a tenant-scoped entity with status transitions, due dates, and project scoping; and (2) ProjectSchedule from Phase 16, which is a separate entity that defines project-level scheduling templates with start/end dates and milestone definitions -- a fundamentally different concept from individual task recurrence.

**Options Considered**:

1. **Fields on Task entity** -- Add `recurrenceRule` (VARCHAR, simplified RRULE), `recurrenceEndDate` (DATE, nullable), and `parentTaskId` (UUID, nullable soft FK) directly to the Task entity. When a recurring task transitions to DONE, the service creates a new Task in the same transaction with the next due date calculated from the recurrence rule.
   - Pros:
     - Recurrence is intrinsically a property of the task -- no conceptual mismatch
     - Simple query for all recurring tasks: `WHERE recurrence_rule IS NOT NULL`
     - No additional entity, repository, service, or migration boilerplate
     - Auto-creation in the same transaction as completion guarantees consistency (no eventual consistency risk)
     - `parentTaskId` enables lineage tracking: all instances point to the root task for easy "show all instances" queries
   - Cons:
     - Adds three nullable columns to the Task table, increasing its width (mitigated: nullable columns have minimal storage impact in PostgreSQL)
     - Task entity grows in responsibility -- it now encodes scheduling semantics alongside task state
     - Complex recurrence rules (e.g., "third Tuesday of the month") would require extending the RRULE parser, though this is explicitly out of scope for v1

2. **Separate RecurrenceRule entity** -- A new `RecurrenceRule` entity with a FK to Task, containing frequency, interval, end date, and last-generated date. A service or scheduled job reads active rules and creates tasks.
   - Pros:
     - Clean separation of concerns: task state and recurrence scheduling are distinct entities
     - RecurrenceRule can be reused for other entities in the future (e.g., recurring invoices, recurring reminders)
     - Easier to add complex scheduling features (skip dates, holiday awareness) without bloating the Task entity
   - Cons:
     - Additional entity, repository, migration, and service boilerplate for what is fundamentally a task property
     - Requires a JOIN to determine if a task is recurring -- every task list query that shows a "recurring" badge needs the join
     - If using a scheduled job (rather than completion-triggered creation), introduces eventual consistency: the next task may not exist immediately after completion
     - Over-engineering for v1 scope, which only supports simple FREQ+INTERVAL rules

3. **Reuse ProjectSchedule pattern (Phase 16)** -- Extend the existing ProjectSchedule entity to include task-level recurrence definitions, treating recurring tasks as schedule items.
   - Pros:
     - Reuses existing infrastructure -- no new entity type
     - Consistent scheduling model across projects and tasks
   - Cons:
     - Conceptual mismatch: ProjectSchedule defines project-level milestones and timelines, not individual task recurrence. Conflating the two creates a confusing abstraction.
     - ProjectSchedule has fields (milestone definitions, Gantt dependencies) irrelevant to task recurrence
     - Would require significant refactoring of ProjectSchedule to accommodate task-level granularity
     - Forces a project-level entity to manage task-level concerns, violating the existing responsibility boundary

4. **Cron-based scheduled job (decoupled from task completion)** -- A background job runs periodically (e.g., every hour), scans for recurring tasks, and creates the next instance based on a schedule, regardless of whether the current task is complete.
   - Pros:
     - Fully decoupled: task completion and next-instance creation are independent
     - Can pre-create tasks ahead of time (e.g., create next month's task on the 1st, even if the current one is not yet complete)
     - Works for time-based recurrence that is not tied to completion (e.g., "every Monday, regardless of status")
   - Cons:
     - Eventual consistency: the next task may not appear for up to an hour after the trigger condition
     - Requires a scheduler infrastructure (Spring `@Scheduled` or similar) with distributed locking in multi-instance deployments
     - Creates tasks even when the previous instance is not done, potentially leading to a backlog of unfinished recurring tasks
     - More complex failure handling: what if the job fails mid-run? Need idempotency guards.
     - Overkill for the requirement, which is completion-triggered recurrence

**Decision**: Option 1 -- Fields on the Task entity.

**Rationale**:

Recurrence is a property of the task itself, not a separate scheduling concept. A recurring task is still a task -- it just happens to generate its successor when completed. Adding three nullable columns (`recurrenceRule`, `recurrenceEndDate`, `parentTaskId`) to the Task entity is the simplest implementation that satisfies the requirement with no additional entities, repositories, or infrastructure.

The synchronous creation model (new task created in the same transaction as the status transition to DONE) provides strong consistency: the user sees the next instance immediately after completing the current one. This avoids the eventual consistency pitfalls of a scheduled job (Option 4) and the boilerplate overhead of a separate entity (Option 2).

The `parentTaskId` field creates a flat lineage structure: all auto-generated instances point to the **root** recurring task, not to the immediately preceding instance. This makes "show all instances of this recurring task" a simple query (`WHERE parent_task_id = :rootId OR id = :rootId`) rather than a recursive traversal. The flat structure is deliberate -- a linked list would require recursive CTEs for lineage queries, which is unnecessary complexity.

Phase 16's ProjectSchedule (Option 3) was rejected because it addresses a fundamentally different concept: project-level milestone scheduling with Gantt dependencies, not individual task recurrence. Forcing task recurrence into ProjectSchedule would create a confusing abstraction that conflates project timelines with task-level automation.

The v1 scope intentionally limits recurrence rules to `FREQ` + `INTERVAL` (a subset of RFC 5545 RRULE). Complex patterns like "third Tuesday of the month" or "skip holidays" are out of scope. If needed in the future, the `recurrenceRule` VARCHAR field can accommodate richer RRULE strings without schema changes.

**Consequences**:

- Task entity gains three nullable columns via Flyway migration: `recurrence_rule`, `recurrence_end_date`, `parent_task_id`
- Existing tasks are unaffected (all three columns default to null)
- `TaskService.updateStatus()` is extended: when status transitions to DONE and `recurrenceRule` is not null, a new Task is created in the same transaction with the next due date
- New task inherits: project, assignee, title, description, priority, recurrence rule, recurrence end date. Status is set to TODO. `parentTaskId` is set to the root task's ID.
- Task deletion does NOT cascade to generated instances -- once created, each task is independent
- Stopping recurrence is done by clearing `recurrenceRule` on the task (or setting `recurrenceEndDate` to a past date)
- UI shows a "recurring" indicator on tasks where `recurrenceRule IS NOT NULL` and a "generated from" link where `parentTaskId IS NOT NULL`
- Related: [ADR-110](ADR-110-task-status-representation.md) (task status transitions -- completion triggers recurrence), Phase 16 ProjectSchedule (different concept, not reused)
