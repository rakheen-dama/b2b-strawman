# ADR-110: Task Status Representation

**Status**: Accepted

**Context**:

Task status is currently stored as a plain `String` column in the database with values `"OPEN"` and `"IN_PROGRESS"`. Phase 29 adds terminal states (`DONE`, `CANCELLED`) and validated transitions between states. The system needs a mechanism to enforce that only valid status values exist in the database and that transitions between states follow defined rules. Without enforcement, any string could be written to the status column via direct SQL, migrations, or application bugs — leading to tasks in impossible states that break lifecycle guardrails.

The Customer entity already solved this problem: `LifecycleStatus` is a Java enum with a `CustomerLifecycleGuard` that validates transitions, and the database column is constrained. Task status should follow an established pattern rather than inventing a new approach. The same reasoning applies to `Task.priority`, which is also a raw string today (`"LOW"`, `"MEDIUM"`, `"HIGH"`) and gains a new value (`URGENT`).

**Options Considered**:

1. **Java enum + DB CHECK constraint** -- Define `TaskStatus` and `TaskPriority` as Java enum classes with an `allowedTransitions()` method on each status value. Store as `VARCHAR` in PostgreSQL with a `CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'))` constraint. Hibernate maps the enum via `@Enumerated(STRING)`.
   - Pros:
     - Compile-time type safety: controller/service code cannot pass an invalid status string
     - DB-level integrity: even raw SQL or faulty migrations cannot insert an invalid value
     - Transition logic lives on the enum itself (`TaskStatus.OPEN.canTransitionTo(DONE)` returns false), keeping it co-located with the type definition
     - Matches the existing `LifecycleStatus` pattern on Customer and `InvoiceStatus` on Invoice -- consistent codebase
   - Cons:
     - Adding a new status requires a Flyway migration to update the CHECK constraint (and a code change for the enum)
     - Slightly more ceremony than a raw string for what is currently a two-value field

2. **String with application-level validation** -- Keep `status` as a `String` column. Add a `TaskStatusValidator` service that checks values against a whitelist and validates transitions. No DB constraint.
   - Pros:
     - Adding new statuses requires only a code change, no migration
     - Simple column type, no ORM mapping complexity
   - Cons:
     - No compile-time safety: typos like `"IN_PROGRES"` compile and pass code review
     - No DB-level protection: direct SQL inserts, data imports, or migration bugs can write arbitrary strings
     - Transition validation is disconnected from the type, making it easier to forget or bypass
     - Inconsistent with `LifecycleStatus`, `InvoiceStatus`, and `BillingStatus` which are all Java enums

3. **PostgreSQL native enum type (CREATE TYPE)** -- Create a custom PostgreSQL type via `CREATE TYPE task_status AS ENUM ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')`. Map in Hibernate via a custom `UserType`.
   - Pros:
     - Strongest possible DB enforcement: the type system itself rejects invalid values
     - Slightly more storage-efficient than VARCHAR (internally stored as OID)
   - Cons:
     - Adding new enum values requires `ALTER TYPE ... ADD VALUE`, which cannot run inside a transaction in PostgreSQL < 12 (our minimum is PG 16, so this is mitigated, but the DDL still acquires an `ACCESS EXCLUSIVE` lock on `pg_enum`)
     - Requires a custom Hibernate `UserType` or `AttributeConverter` with JDBC-level casting (`::task_status`), adding ORM complexity
     - Schema-per-tenant architecture means the type must be created in every tenant schema -- the `CREATE TYPE` must be in the tenant migration, and `ALTER TYPE` for new values must also run per-schema
     - No other entity in the codebase uses PostgreSQL native enums -- this would be an outlier pattern

**Decision**: Option 1 -- Java enum with CHECK constraint.

**Rationale**:

The codebase has a well-established pattern for lifecycle-managed enums: `LifecycleStatus` (Customer), `InvoiceStatus` (Invoice), `BillingStatus` (TimeEntry), and `DocumentStatus` (Document) are all Java enums stored as `VARCHAR` with application-level transition validation. Task status should follow this same pattern for consistency. Developers already know how to work with this approach, and code reviewers can verify transition rules by looking at a single enum class.

The CHECK constraint adds a database-level safety net that catches bugs the application layer might miss. In a schema-per-tenant architecture (ADR-064), the CHECK constraint is part of the tenant migration and applies identically to every schema. Adding a new status value (unlikely -- lifecycle states are a closed set by design) requires updating both the enum and the migration, which is acceptable because status changes are deliberate architectural decisions, not runtime configuration.

PostgreSQL native enums (Option 3) provide marginally stronger enforcement but introduce ORM complexity and a per-schema `ALTER TYPE` burden that is not justified for a four-value field. The existing codebase has zero native enum types, and introducing one here would create an inconsistency that future developers would need to understand.

**Consequences**:

- `TaskStatus` enum defines `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED` with an `allowedTransitions()` method encoding the valid state machine
- `TaskPriority` enum defines `LOW`, `MEDIUM`, `HIGH`, `URGENT` (no transition validation needed -- priority can change freely)
- Flyway migration adds `CHECK (status IN (...))` and `CHECK (priority IN (...))` constraints to the `tasks` table in every tenant schema
- Existing data is backward compatible: current `"OPEN"` and `"IN_PROGRESS"` string values match the new enum names exactly, so no data migration is needed
- Transition validation is enforced in `TaskService` (application layer) and by the CHECK constraint (database layer) -- defense in depth
- Future status additions (unlikely) require both a code change and a migration, which is intentional: lifecycle states are architectural decisions
- Consistent with `LifecycleStatus`, `InvoiceStatus`, `BillingStatus`, and `DocumentStatus` patterns already in the codebase
- Related: [ADR-019](ADR-019-task-claim-workflow.md) (task claim/release workflow that becomes part of the transition graph), [ADR-064](ADR-064-dedicated-schema-only.md) (dedicated schema -- CHECK constraints apply per tenant schema), [ADR-111](ADR-111-project-completion-semantics.md) (task terminal states feed into project completion guardrails -- all tasks must be DONE or CANCELLED before a project can be completed)
