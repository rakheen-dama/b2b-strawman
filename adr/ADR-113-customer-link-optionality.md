# ADR-113: Customer Link Optionality

**Status**: Accepted

**Context**:

Phase 29 adds a `customerId` column to the Project entity so that projects can be attributed to the customer they serve. This supports a core professional services workflow: firms track which engagements belong to which clients for billing, reporting, and profitability analysis. The `GET /api/customers/{id}/projects` endpoint and the customer detail "Projects" tab depend on this link.

The design question is whether this link should be required (every project must have a customer) or optional (projects can exist without a customer), and how referential integrity should be enforced. Professional services firms frequently have internal projects -- administrative overhead, internal R&D, training, firm management -- that are not attributable to any external customer. These projects still need time tracking, task management, and budgeting, but they have no customer relationship.

**Options Considered**:

1. **Required FK (NOT NULL + foreign key constraint)** -- Every project must reference a valid customer. The column is `customer_id UUID NOT NULL REFERENCES customers(id)`.
   - Pros:
     - Strongest referential integrity: the database guarantees every project has a valid customer
     - Simplifies reporting: no null checks needed when aggregating by customer
     - Forces organizational discipline: every project is attributed from creation
   - Cons:
     - Breaks internal projects: administrative work, R&D, training, and firm management projects have no customer. A "dummy" internal customer would be needed, polluting the customer list and distorting customer-level reports
     - Hard FK creates a cross-entity coupling in the schema-per-tenant migration: the `customers` table must exist before `projects` can reference it, constraining migration order
     - Hard FK with `ON DELETE RESTRICT` prevents customer deletion even when the business relationship has ended -- the user must manually unlink all projects first. With `ON DELETE CASCADE`, deleting a customer would destroy all its projects, which contradicts the delete protection philosophy (ADR-112)
     - Inconsistent with the existing codebase: `Invoice.customerId`, `TimeEntry.projectId`, and `TimeEntry.taskId` are all soft FKs (nullable UUID, application-validated)

2. **Optional soft FK (nullable UUID, application-validated)** -- The column is `customer_id UUID` (nullable, no database foreign key constraint). The service layer validates that the referenced customer exists within the tenant schema when a non-null value is provided. Follows the existing pattern used throughout the codebase.
   - Pros:
     - Internal projects (no customer) are first-class citizens: `customerId = null` is a valid state meaning "internal project"
     - Consistent with the existing soft FK pattern: `Invoice.customerId`, `TimeEntry.projectId`, `Retainer.customerId` all use the same approach
     - No cross-entity DDL coupling: the migration adds a plain UUID column with no REFERENCES clause, avoiding migration ordering constraints
     - Application-level validation catches invalid customer IDs at creation/update time with a clear error message
     - Customer deletion is controlled by application logic (ADR-112 delete protection) rather than database CASCADE/RESTRICT rules, keeping the delete philosophy in one place
   - Cons:
     - No database-level referential integrity: a direct SQL INSERT could write an invalid `customerId` that the application would not catch until the next read
     - Orphaned references are theoretically possible if a customer is deleted through a code path that bypasses the delete protection guard (mitigated by the guard itself and by audit logging)
     - Developers must remember to validate the customer ID in the service layer -- the database will not enforce it

3. **Join table (project_customers)** -- A many-to-many relationship table linking projects to customers. Supports projects shared across multiple customers.
   - Pros:
     - Supports multi-customer projects: a shared audit or joint venture could be attributed to multiple customers
     - Clean separation: the link is its own entity with its own lifecycle (can add metadata like "primary customer" flag)
     - No nullable FK on the project table -- the absence of a row in the join table means "no customer"
   - Cons:
     - Over-engineering for the current requirement: projects serve one customer at a time. Multi-customer attribution is not in the requirements and would complicate billing, profitability, and reporting logic
     - Every query that needs the customer link requires a JOIN instead of a simple column read
     - The join table needs its own migration, its own entity class, and its own repository -- additional boilerplate for a feature that is not needed
     - Inconsistent with the existing pattern: invoices have a single `customerId`, not a join table. Projects should follow the same convention

**Decision**: Option 2 -- Optional soft FK (nullable UUID, application-validated).

**Rationale**:

Internal projects are a fundamental use case for professional services firms. Administrative overhead, internal R&D, staff training, and firm management all require project-level time tracking, task management, and budgeting -- but they are not attributable to any external customer. Making `customerId` required would force firms to create a synthetic "internal" customer entity, which pollutes customer lists, distorts customer-level profitability reports, and creates confusion about what constitutes a real client relationship.

The soft FK pattern is the established convention in this codebase. `Invoice.customerId`, `TimeEntry.projectId`, `TimeEntry.taskId`, and `Retainer.customerId` all use nullable UUID columns with application-level validation. Introducing a hard FK for `Project.customerId` would be the only hard cross-entity FK in the tenant schema, creating an inconsistency that would confuse developers and constrain future migration ordering. The schema-per-tenant architecture (ADR-064) creates each tenant's tables via Flyway migrations -- a hard FK adds an implicit dependency between the `customers` and `projects` table creation order that the soft FK avoids.

The join table (Option 3) solves a problem that does not exist in the current requirements. If multi-customer attribution becomes necessary in the future, a join table can be introduced alongside the existing `customerId` column (the column becomes the "primary customer" while the join table tracks additional attributions). Building the join table now adds complexity with no immediate benefit.

The risk of orphaned references (a deleted customer leaving behind projects with invalid `customerId`) is mitigated by the delete protection rules (ADR-112): customers with linked projects cannot be deleted. The `CustomerLifecycleGuard` is extended with a `CREATE_PROJECT` action check to block project creation for customers in OFFBOARDING or OFFBOARDED status, ensuring the link is only established with active customer relationships.

**Consequences**:

- `Project.customerId` is a nullable UUID column added via Flyway migration with no REFERENCES constraint
- Existing projects default to `customerId = null` (no data migration needed) -- they are implicitly internal projects
- `ProjectService.create()` and `ProjectService.update()` validate non-null `customerId` by loading the customer and checking it exists within the tenant schema
- `CustomerLifecycleGuard` gains a `CREATE_PROJECT` action: blocks project creation for OFFBOARDING/OFFBOARDED customers, consistent with the existing `CREATE_INVOICE` guard
- Customer delete protection (ADR-112) checks for linked projects: `projectRepository.countByCustomerId(id) > 0` blocks deletion
- Existing projects linked to an OFFBOARDING customer remain linked -- the guard only prevents new links, not existing ones
- Profitability reports and customer-level aggregations must handle `customerId = null` (internal projects are excluded from customer reports or shown in a separate "Internal" category)
- Related: [ADR-112](ADR-112-delete-vs-archive-philosophy.md) (customer delete protection when projects are linked), [ADR-017](ADR-017-customer-as-org-child.md) (customer entity design), [ADR-064](ADR-064-dedicated-schema-only.md) (dedicated schema -- no hard cross-entity FKs)
