# ADR-131: Prerequisite Context Granularity — Typed Enum vs. Alternatives

**Status**: Accepted

**Context**:

Phase 33 introduces prerequisite enforcement at multiple action points: lifecycle activation, invoice generation, proposal sending, document generation, and project creation. Each action point may require a different subset of customer fields to be filled. The system needs a mechanism to associate field definitions with the contexts in which they are required. The granularity of this association determines the flexibility of the configuration, the type safety of the codebase, and the complexity of the admin UI.

DocTeams already uses typed enums extensively for domain concepts: `EntityType` (PROJECT, TASK, CUSTOMER, INVOICE), `LifecycleStatus` (PROSPECT, ONBOARDING, ACTIVE, ...), `FieldType` (TEXT, NUMBER, DATE, SELECT, ...). These enums provide compile-time safety and IDE discoverability. However, the prerequisite system serves a cross-cutting concern that touches multiple domains, and there is a question of whether a fixed enum is flexible enough or whether a more dynamic mechanism is needed to accommodate future vertical forks (law firms, accounting firms) that may have industry-specific enforcement points.

**Options Considered**:

1. **Typed enum (`PrerequisiteContext`)** -- A Java enum with a fixed set of values (`LIFECYCLE_ACTIVATION`, `INVOICE_GENERATION`, `PROPOSAL_SEND`, `DOCUMENT_GENERATION`, `PROJECT_CREATION`). Stored as JSONB string array in the database. Each value corresponds to a specific enforcement point in the code.
   - Pros:
     - Compile-time type safety — invalid contexts are caught at build time
     - IDE autocompletion and refactoring support
     - Clear mapping between enum values and enforcement points in the codebase
     - Admin UI can display a bounded dropdown with human-readable labels
     - Easy to reason about — every context has a corresponding check in `PrerequisiteService`
   - Cons:
     - Adding a new context requires a code change (add enum constant + wire enforcement point)
     - Vertical forks that add industry-specific contexts must modify the enum
     - Cannot be configured at runtime by end users

2. **Free-form string tags** -- Field definitions store an array of arbitrary strings as context tags (e.g., `["activation", "invoicing", "fica-compliance"]`). Any string is valid; the system matches tags at enforcement points.
   - Pros:
     - Maximum flexibility — new contexts added without code changes
     - Vertical forks add industry-specific tags without modifying shared code
     - End users can define custom contexts (future extension)
   - Cons:
     - No compile-time safety — typos in tag strings cause silent failures
     - No discoverability — admins must know valid tag names
     - Tag proliferation over time with no governance (e.g., "invoice", "invoicing", "inv-gen" all meaning the same thing)
     - Enforcement points must use string matching, which is error-prone
     - Admin UI needs a tag-input field with no bounded options — harder to use

3. **Hierarchical context tree** -- Contexts are organized in a hierarchy (e.g., `FINANCIAL.INVOICING`, `FINANCIAL.BILLING`, `COMPLIANCE.FICA`, `COMPLIANCE.KYC`). A field required for `FINANCIAL` is automatically required for all sub-contexts.
   - Pros:
     - Supports inheritance — "required for all financial actions" without listing each one
     - Elegant modeling of domain relationships
     - Reduces configuration burden for common patterns
   - Cons:
     - Significantly more complex to implement and reason about
     - Hierarchy must be defined and maintained — who decides the tree structure?
     - Inheritance can cause surprising behavior (adding a parent context silently adds child requirements)
     - JSONB storage and query patterns become more complex
     - Over-engineered for the current 5 enforcement points

4. **Per-entity boolean flags** -- Instead of a context mechanism, add individual boolean columns to `FieldDefinition`: `requiredForActivation`, `requiredForInvoicing`, `requiredForProposals`, etc.
   - Pros:
     - Simple schema — one boolean per context
     - Easy to query: `WHERE required_for_activation = true`
     - No JSONB complexity
   - Cons:
     - Schema change required for each new context (ALTER TABLE ADD COLUMN)
     - Migration needed for every new enforcement point
     - Entity class grows with each boolean
     - Vertical forks must add their own columns, complicating the base schema
     - Does not scale beyond 5-8 contexts without becoming unwieldy

**Decision**: Option 1 -- Typed enum (`PrerequisiteContext`).

**Rationale**:

The typed enum provides the best trade-off between safety and simplicity for DocTeams' current and near-future needs. With 5 well-defined enforcement points, a bounded enum eliminates an entire class of bugs (misspelled context strings, orphaned tags, ambiguous names) while keeping the admin UI clean — a multi-select dropdown with 5 human-readable options is far more usable than a free-form tag input.

The concern about vertical fork flexibility is real but manageable. Adding a new `PrerequisiteContext` value is a single-line code change (add the enum constant) plus wiring the enforcement point. This is appropriate because every new context requires code changes anyway — someone must write the check logic. A new context without corresponding enforcement code is meaningless. The enum ensures that every context has a corresponding implementation.

Free-form tags were rejected because the prerequisite system is fundamentally a code-backed enforcement mechanism, not a user-configurable tagging system. Each context maps to specific service method calls; there is no runtime interpretation of arbitrary strings. Tags would create an illusion of flexibility that the enforcement layer cannot fulfill.

The hierarchical approach was rejected as premature optimization. With 5 contexts, there is no meaningful hierarchy to model. If a future phase requires context grouping (e.g., "all financial contexts"), this can be modeled as a convenience method on the enum (`PrerequisiteContext.financialContexts()`) without changing the storage model.

Per-entity booleans were rejected because they do not scale and they couple the schema to the set of contexts. JSONB storage with a GIN index achieves the same query performance while allowing the set of contexts to grow without schema changes.

**Consequences**:

- `PrerequisiteContext` enum is defined in the `prerequisite/` package with 5 values
- `FieldDefinition.requiredForContexts` stores enum names as a JSONB string array (e.g., `["LIFECYCLE_ACTIVATION", "INVOICE_GENERATION"]`)
- Database queries use JSONB `@>` (contains) operator with GIN index for efficient lookup
- Admin UI renders a multi-select dropdown with human-readable labels mapped from enum values
- Adding a new context requires: (1) add enum constant, (2) wire enforcement in `PrerequisiteService`, (3) add label in frontend constants, (4) update field pack defaults if applicable
- Vertical forks add their contexts to the enum — this is acceptable because enforcement logic must be added regardless
- Related: [ADR-130](ADR-130-prerequisite-enforcement-strategy.md) (enforcement strategy consumes contexts), [ADR-132](ADR-132-engagement-prerequisite-storage.md) (engagement prerequisites are orthogonal to context-based prerequisites)
