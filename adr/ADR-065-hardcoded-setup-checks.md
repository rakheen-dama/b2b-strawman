# ADR-065: Hardcoded Setup Checks vs. Configurable Setup Engine

**Status**: Accepted

**Context**:

Phase 15 introduces setup status aggregation — computing whether a project or customer is "fully set up" by checking multiple dimensions (customer assigned, rate card configured, budget set, team members added, required custom fields filled). The platform already has three admin-configurable engines: custom fields (Phase 11, `FieldDefinition` + `FieldGroup`), compliance checklists (Phase 14, `ChecklistTemplate` → `ChecklistInstance` → `ChecklistItem`), and document templates (Phase 12, `DocumentTemplate` with context builders). Each engine has its own admin UI, seed/pack system, and per-tenant configuration.

The question is whether setup completeness checks should be a fourth configurable engine (admin defines which "setup steps" apply per entity type, orders them, enables/disables them) or hardcoded service methods that know what constitutes a "fully set up" project or customer.

**Options Considered**:

1. **Hardcoded service methods (chosen)** — `ProjectSetupStatusService` contains explicit knowledge: a project needs a customer, rates, budget, team, and filled required fields. These checks are Java methods calling existing repositories.
   - Pros: Zero new entities or tables; zero admin UI needed; zero configuration surface area; trivial to understand and modify; each check is a one-line repository call; total service is ~60 lines; vertical fork developers modify the service directly — a 10-minute change.
   - Cons: Changing which checks apply requires a code deploy; different tenants cannot have different setup definitions; no admin self-service for setup step configuration.

2. **Configurable setup engine** — New `SetupStepDefinition` entity with admin CRUD: step name, entity type, check type (enum: HAS_CUSTOMER, HAS_RATE, HAS_BUDGET, etc.), enabled flag, sort order. A `SetupStepEvaluator` resolves each check type dynamically.
   - Pros: Admins can enable/disable setup steps per tenant; different verticals can have different definitions without code changes; reorderable steps; extensible via new check type enum values.
   - Cons: Requires new database table + migration (violates Phase 15 constraint of no new tables); requires admin UI for configuration; introduces a fourth configurable engine increasing admin cognitive load; check type enum still requires code changes to add new evaluation logic; configuration rarely changes — the definition of "fully set up" is stable across verticals.

3. **Configuration file (YAML/JSON) per vertical** — Define setup steps in a configuration file bundled with the application, not in the database. Different vertical forks ship different config files.
   - Pros: No database tables; fork-friendly (swap the config file); no admin UI needed; structured and declarative.
   - Cons: Still requires an evaluation framework to interpret the config; adds indirection vs. direct service methods; config file must map to code-level checks anyway (e.g., "HAS_BUDGET" → query `ProjectBudgetRepository`); over-engineers what is fundamentally a small, stable set of checks; configuration changes still require a deploy.

**Decision**: Option 1 — hardcoded service methods.

**Rationale**:

The definition of "fully set up" is stable and universal: every professional services firm needs their projects to have a customer, billing rates, a budget, team members, and filled compliance/custom fields. What varies between verticals is not *which* checks apply but *which custom fields and checklists are seeded* — and that variability is already handled by the existing pack systems (field packs from Phase 11, compliance packs from Phase 14). A law firm and an accounting firm both need "required fields filled" — they just have different required fields, configured through the existing `FieldDefinition.required` flag.

Adding a fourth configurable engine would increase admin onboarding complexity (custom fields, checklists, templates, *and now setup steps*) for negligible benefit. The entire `ProjectSetupStatusService` is ~60 lines of straightforward repository calls. When a vertical fork needs a different check (e.g., "Has court calendar entry" for legal), the fork developer adds one method and one repository call — a change that takes minutes and is immediately testable. This is the right level of abstraction for a set of checks that changes at the pace of vertical forks (months/years), not at the pace of tenant configuration (days/weeks).

**Consequences**:

- Positive:
  - No new database tables, migrations, or admin UI — Phase 15 remains a pure aggregation layer
  - Setup checks are immediately readable in the service class — no indirection through configuration
  - Testing is trivial: mock repositories, assert boolean fields on the response record
  - Vertical fork developers modify a single service class with clear, self-documenting methods
  - Admin cognitive load unchanged — no fourth engine to learn and configure
- Negative:
  - Different tenants within the same deployment cannot have different setup step definitions (acceptable — setup definitions are per-vertical, not per-tenant)
  - Adding a new check type requires a code deploy (acceptable — this happens at the pace of feature development, not tenant configuration)
  - If a future vertical needs radically different setup semantics (e.g., 15 steps instead of 5), the service class grows but remains manageable
