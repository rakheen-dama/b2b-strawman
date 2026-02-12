# ADR-039: Rate Resolution Hierarchy

**Status**: Accepted

**Context**: Phase 8 introduces billing rate cards -- per-member hourly rates that determine how tracked time is valued for client billing. The existing `TimeEntry` entity (Phase 5, [ADR-021](ADR-021-time-tracking-model.md)) has a user-supplied `rateCents` field that staff fill in manually when logging time. This is error-prone and inconsistent: different members enter different rates for the same client, rates change without historical tracking, and there is no organizational control over billing rates.

Phase 8 replaces manual rate entry with a system-managed `BillingRate` entity and an automatic rate resolution engine. The core design question is: how many levels of rate overrides should the system support? Professional services firms commonly need different rates for different contexts -- a senior developer might bill at a default rate of $200/hr, but a specific client negotiated a $175/hr rate, and a particular fixed-fee project uses a blended $150/hr rate. The resolution engine must determine which rate applies when a time entry is created for a given member, project, and date.

Projects and customers are linked via the `CustomerProject` join table (many-to-many, see [ADR-017](ADR-017-customer-as-org-child.md)). A project can have multiple customers, and a customer can be linked to multiple projects. For rate resolution, when looking up a customer-level rate override, the system uses the first customer linked to the project (ordered by `CustomerProject.createdAt`). This is a deliberate simplification -- the vast majority of projects have exactly one customer. Multi-customer projects that need distinct customer rates should use project-level overrides instead.

**Options Considered**:

1. **Flat rates (member-only, no overrides)** -- Each member has a single billing rate. No project or customer variations.
   - Pros:
     - Simplest data model and resolution logic (one table, one lookup)
     - No ambiguity about which rate applies
     - Easy to understand and administer
   - Cons:
     - Cannot model the most common real-world scenario: different rates for different clients
     - Forces all client negotiations into manual rate entry on time entries (the problem Phase 8 is solving)
     - Members who work on both premium and discount clients cannot have their rates reflect this

2. **Two-level (member default + project override)** -- Members have a default rate, and individual projects can override it.
   - Pros:
     - Covers the most critical use case: project-specific pricing (fixed-fee projects, discounted engagements)
     - Simple resolution: check project override, then fall back to member default
     - No dependency on the customer-project relationship for rate lookups
   - Cons:
     - Cannot model customer-wide rate agreements (e.g., "Client X gets all team members at 10% discount"). Each project for that client must have its own overrides set individually.
     - When a new project is created for a customer with negotiated rates, the rates must be manually re-entered as project overrides
     - Does not scale for firms with many projects per customer

3. **Three-level (member default + customer override + project override)** -- Members have a default rate. Customer-level overrides apply across all projects for that customer. Project-level overrides take highest priority.
   - Pros:
     - Models the three most common pricing scenarios: default team rates, client-negotiated rates, and project-specific pricing
     - Customer overrides automatically apply to new projects for that customer (no re-entry)
     - Project overrides can still fine-tune for specific engagements (e.g., pro-bono, fixed-fee, discounted)
     - Clear precedence chain: project > customer > member default
   - Cons:
     - More complex resolution logic (three lookups in priority order)
     - Requires the customer-project relationship to be established before customer rates apply
     - Effective date ranges add complexity to each level (no-overlap enforcement per scope)

4. **Four-level (member default + customer + project + project+customer compound)** -- All of Option 3, plus a compound override for "member X on project Y for customer Z."
   - Pros:
     - Maximum granularity -- can express any rate variation
     - Handles edge cases like "this member is discounted only for this specific project-customer combination"
   - Cons:
     - Combinatorial explosion: for N members, M projects, and C customers, the compound level can have up to N x M x C rate entries
     - Confusing admin UX -- which override is active? Debugging rate resolution requires understanding four levels
     - The compound level's use case (per-member, per-project, per-customer) is extremely rare in practice
     - CHECK constraint on the `BillingRate` table becomes more complex (must allow both `project_id` and `customer_id` to be non-null)
     - Indexing for no-overlap enforcement requires a four-column composite

**Decision**: Three-level hierarchy with project > customer > member default (Option 3).

**Rationale**: Option 3 captures the pricing patterns that cover the vast majority of professional services billing without the combinatorial complexity of compound overrides:

1. **Real-world coverage**: The three levels map directly to how billing negotiations happen. Organizations set default team rates. Sales teams negotiate client-wide discounts. Project managers adjust rates for specific engagements (fixed-fee, strategic, pro-bono). A fourth level (project+customer compound) adds no practical value that a project override alone doesn't already provide -- if a member needs a special rate on a specific project, the project override handles it regardless of which customer is attached.

2. **Clean data model**: The `BillingRate` entity uses a CHECK constraint (`NOT (project_id IS NOT NULL AND customer_id IS NOT NULL)`) to prevent compound overrides. Each rate row is unambiguously one of three types: member-default (both nulls), customer-override (`customer_id` set), or project-override (`project_id` set). This makes admin UIs straightforward -- three distinct screens, each managing one level.

3. **Effective date ranges**: Each rate has `effective_from` (DATE, required) and `effective_to` (DATE, nullable for "current"). A no-overlap constraint is enforced per scope: for a given `(member_id, project_id, customer_id)` tuple, no two rates can have overlapping date ranges. This is validated in the service layer (not a DB constraint, since range overlap checks are complex in SQL) and prevents ambiguity about which rate is active on any given date.

4. **Customer resolution for multi-customer projects**: When resolving the customer-level rate, the system looks up the project's first linked customer via `CustomerProject` ordered by `created_at ASC`. This covers the common case (one customer per project) without introducing a "primary customer" concept. Projects with multiple customers that need distinct rate treatment should use project-level overrides. This simplification is explicitly documented and can be revisited if a "primary customer" field is added to `CustomerProject` in the future.

5. **Replaces manual rate entry**: The existing `TimeEntry.rateCents` field (user-supplied, nullable integer) is superseded by the automatic rate resolution and snapshot mechanism (see [ADR-040](ADR-040-point-in-time-rate-snapshotting.md)). The `rateCents` field will be deprecated in favor of `billingRateSnapshot` and `costRateSnapshot` fields that are system-resolved, not user-entered.

**Consequences**:
- `BillingRate` entity has `memberId`, `projectId` (nullable), `customerId` (nullable), `currency`, `hourlyRate` (BigDecimal), `effectiveFrom`, `effectiveTo` (nullable)
- CHECK constraint: `NOT (project_id IS NOT NULL AND customer_id IS NOT NULL)` -- no compound overrides
- Rate resolution service method `resolveRate(memberId, projectId, date)` performs three lookups in priority order: project override, customer override (via `CustomerProject`), member default
- Customer lookup uses `CustomerProject` ordered by `created_at ASC` to find the first linked customer -- documented simplification for multi-customer projects
- No-overlap validation for effective date ranges is enforced in `BillingRateService`, not via database constraints
- Three admin UIs: org settings (member defaults), project settings (project overrides), customer detail (customer overrides)
- The existing `TimeEntry.rateCents` field is superseded by automatic rate snapshots (see [ADR-040](ADR-040-point-in-time-rate-snapshotting.md)) and will be retained for backward compatibility but no longer populated by new code
- Audit events (`BILLING_RATE_CREATED`, `BILLING_RATE_UPDATED`, `BILLING_RATE_DELETED`) published via the existing `AuditService` pattern from Phase 6
- Applies identically to both Starter (shared schema with `tenant_id` + `@Filter`) and Pro (dedicated schema) tenancy models
