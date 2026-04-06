# Backend Quality Backlog

This backlog turns the backend maintainability review into a sequenced implementation plan with GitHub-ready issue bodies and agent-ready execution guidance.

## Waves

| Wave | Tickets |
| --- | --- |
| 1 | BE-001, BE-002, BE-003, BE-019 |
| 2 | BE-004, BE-005, BE-006 |
| 3 | BE-007, BE-008, BE-009, BE-010 |
| 4 | BE-011, BE-012 |
| 5 | BE-013, BE-014, BE-015 |
| 6 | BE-016, BE-017, BE-018 |
| 7 | BE-020, BE-021, BE-022 |

## Ticket Index

| Ticket | Title | Depends On |
| --- | --- | --- |
| BE-001 | Standardize backend API error responses with centralized `ProblemDetail` | - |
| BE-002 | Reduce duplicated `ProblemDetail` construction across exception classes | BE-001 |
| BE-003 | Replace API-leaking `IllegalArgumentException` with typed domain/API exceptions | BE-001, BE-002 |
| BE-004 | Introduce Spring MVC actor injection to remove direct `RequestScopes` access from controllers | - |
| BE-005 | Refactor org settings endpoints into thin controller + service validation flow | BE-004 |
| BE-006 | Move portal authentication orchestration out of controller | BE-004, BE-001 |
| BE-007 | Remove repository-backed response enrichment from selected controllers | BE-004 |
| BE-008 | Eliminate direct repository injection from non-dev controllers | BE-005, BE-006, BE-007 |
| BE-009 | Move large nested controller DTOs into dedicated request/response classes | - |
| BE-010 | Replace string action parameters with enums and Spring validation | BE-001 |
| BE-011 | Decompose invoice service into focused collaborators | BE-001 |
| BE-012 | Decompose billing run service into lifecycle, selection, and generation workflows | BE-011 |
| BE-013 | Identify and plan the next oversized services for decomposition | BE-011, BE-012 |
| BE-014 | Move reusable business invariants out of controller/service branching | BE-011, BE-012 |
| BE-015 | Centralize reusable validation logic for files, ranges, and action inputs | BE-001 |
| BE-016 | Introduce tenant-aware test fixtures and helpers for backend integration tests | - |
| BE-017 | Define and document backend test taxonomy and coupling rules | - |
| BE-018 | Reduce implementation coupling in selected backend integration tests | BE-016, BE-017 |
| BE-019 | Add HTTP contract tests for standardized backend error responses | BE-001 |
| BE-020 | Enforce backend architecture rules with automated tests | BE-008, BE-004, BE-001 |
| BE-021 | Add guardrails for oversized services and controllers | BE-011, BE-012 |
| BE-022 | Add backend contribution checklist for controller, exception, and test quality | BE-001, BE-004, BE-017 |

## GitHub-Ready Issue Bodies

### BE-001 — Standardize backend API error responses with centralized `ProblemDetail`

**Description**
Consolidate HTTP error shaping in the backend so validation, binding, authorization, not found, conflict, and unexpected errors return a consistent `ProblemDetail` contract. Extend the existing global exception handling instead of allowing framework-default shapes to leak through.

**Scope**
- Add handlers or overrides for request-body validation failures, query/path binding failures, constraint violations, unreadable request bodies, and a safe fallback for unexpected exceptions.
- Ensure the response includes stable fields appropriate for clients, such as `title`, `detail`, `status`, and optional structured validation errors.
- Preserve existing domain exception semantics where already correct.

**Acceptance Criteria**
- Invalid request body returns a consistent `400` response shape.
- Invalid query or path parameter returns the same standard shape.
- Forbidden, not found, conflict, and unprocessable errors remain stable and consistent.
- Unexpected server errors return a sanitized `500` response.
- Add HTTP-level tests covering representative cases.

**Dependencies**
- None

### BE-002 — Reduce duplicated `ProblemDetail` construction across exception classes

**Description**
Several exception classes build nearly identical `ProblemDetail` payloads. Introduce a shared helper or base pattern so new exceptions are easier to add and existing ones stay consistent.

**Scope**
- Extract common `ProblemDetail` creation logic.
- Refactor current API/domain exception classes to use the shared mechanism.
- Keep public semantics unchanged.

**Acceptance Criteria**
- Existing exception types still produce the same status and core response fields.
- Duplicate `ProblemDetail` factory logic is substantially reduced.
- New exception classes can be implemented with minimal boilerplate.

**Dependencies**
- BE-001

### BE-003 — Replace API-leaking `IllegalArgumentException` with typed domain/API exceptions

**Description**
Some services throw `IllegalArgumentException` for cases that can escape through API boundaries. Replace those with typed exceptions so HTTP behavior is intentional and stable.

**Scope**
- Audit service methods where `IllegalArgumentException` can propagate to controllers.
- Replace with domain or API exceptions where the condition represents user-facing input or state errors.
- Leave purely internal programming errors alone.

**Acceptance Criteria**
- Representative flows return intentional `ProblemDetail` responses instead of framework-default errors.
- No user-facing business validation relies on raw `IllegalArgumentException`.

**Dependencies**
- BE-001
- BE-002

### BE-004 — Introduce Spring MVC actor injection to remove direct `RequestScopes` access from controllers

**Description**
Controllers currently fetch actor and tenant/member context directly. Add a framework-level injection mechanism so controllers receive `ActorContext` without knowing how it is resolved.

**Scope**
- Implement an argument resolver or equivalent pattern for `ActorContext`.
- Support current authenticated use cases without changing business behavior.
- Migrate a small pilot set of controllers first.

**Acceptance Criteria**
- Pilot controllers no longer call `RequestScopes` or `ActorContext.fromRequestScopes()` directly.
- Behavior and authorization outcomes remain unchanged.
- Pattern is documented for future controller work.

**Dependencies**
- None

### BE-005 — Refactor org settings endpoints into thin controller + service validation flow

**Description**
The org settings controller contains repeated actor extraction and inline multipart validation. Refactor it into a cleaner HTTP adapter backed by service or validator abstractions.

**Scope**
- Remove repeated unused actor locals.
- Move logo file validation into a dedicated validator or application service.
- Keep endpoint contract unchanged.

**Acceptance Criteria**
- Controller methods are limited to request parsing, service invocation, and response creation.
- File validation logic is not implemented inline in controller methods.
- Existing tests continue to pass or are updated without changing behavior.

**Dependencies**
- BE-004

### BE-006 — Move portal authentication orchestration out of controller

**Description**
The portal auth controller currently resolves orgs, binds tenant context, queries repositories, handles enumeration-safe behavior, and exchanges tokens. Move that orchestration into a dedicated application service.

**Scope**
- Create an application service for request-link and exchange flows.
- Move tenant scoping and repository interactions out of controller methods.
- Preserve enumeration-safe behavior and current response semantics.

**Acceptance Criteria**
- Controller endpoints each delegate to a single application service call.
- Controller no longer binds tenant context directly.
- Error handling becomes simpler and centralized.

**Dependencies**
- BE-004
- BE-001

### BE-007 — Remove repository-backed response enrichment from selected controllers

**Description**
Some controllers perform enrichment like author/name resolution directly. Move that mapping and enrichment into dedicated query assemblers or application services.

**Scope**
- Refactor billing rate and comment endpoints first.
- Extract reusable response assembly components.
- Preserve current response payloads.

**Acceptance Criteria**
- Controllers do not inject repositories for response enrichment.
- Response mapping is reusable and testable outside controllers.
- No contract changes for API consumers.

**Dependencies**
- BE-004

### BE-008 — Eliminate direct repository injection from non-dev controllers

**Description**
Controllers should not orchestrate persistence directly. Audit current controllers and move repository usage into services or query layers.

**Scope**
- Identify all non-dev controllers importing repositories.
- Migrate them incrementally.
- Keep dev-only tooling separate if needed.

**Acceptance Criteria**
- Non-dev controllers no longer inject repositories directly.
- Any remaining exceptions are documented and justified.

**Dependencies**
- BE-005
- BE-006
- BE-007

### BE-009 — Move large nested controller DTOs into dedicated request/response classes

**Description**
Many controllers contain large embedded DTO sections, which makes them harder to navigate. Move the biggest DTO groups into dedicated types.

**Scope**
- Start with the largest controllers first.
- Keep package structure coherent and avoid needless fragmentation.
- Preserve JSON contract.

**Acceptance Criteria**
- Target controllers are materially shorter and easier to scan.
- Serialization and validation behavior remains unchanged.

**Dependencies**
- None

### BE-010 — Replace string action parameters with enums and Spring validation

**Description**
Several endpoints rely on string constants and manual `switch` validation for actions and statuses. Replace these with enums where appropriate.

**Scope**
- Identify endpoints using action strings.
- Introduce enums and conversion/validation.
- Standardize error responses for invalid values.

**Acceptance Criteria**
- Invalid action values return the standard validation error contract.
- Manual action-string validation is reduced or removed.

**Dependencies**
- BE-001

### BE-011 — Decompose invoice service into focused collaborators

**Description**
The invoice service currently mixes creation, mutation, transitions, integration, rendering, validation, events, and query concerns. Split it into coherent use-case services.

**Scope**
- Define a decomposition plan.
- Extract focused collaborators incrementally.
- Preserve public behavior while improving internal structure.

**Acceptance Criteria**
- Dependency count and class size are materially reduced.
- Major responsibilities are isolated behind focused components.
- Existing behavior is preserved through tests.

**Dependencies**
- BE-001

### BE-012 — Decompose billing run service into lifecycle, selection, and generation workflows

**Description**
Billing run logic spans creation, selection, invoice generation, transactions, and eventing. Break it into smaller workflow-oriented services.

**Scope**
- Separate run lifecycle from entry selection.
- Separate invoice generation from post-processing and notifications.
- Keep transactional boundaries explicit.

**Acceptance Criteria**
- Core workflows are implemented in separate focused classes.
- The top-level service becomes orchestration-only or is eliminated.

**Dependencies**
- BE-011

### BE-013 — Identify and plan the next oversized services for decomposition

**Description**
After the first two major decompositions, produce a ranked queue for the next maintainability hotspots.

**Scope**
- Review large services.
- Create a short responsibility map and extraction proposal for each.
- Rank by complexity, risk, and change frequency.

**Acceptance Criteria**
- A prioritized shortlist exists for the next 5 large services.
- Each candidate includes a concrete split proposal.

**Dependencies**
- BE-011
- BE-012

### BE-014 — Move reusable business invariants out of controller/service branching

**Description**
Common state rules and invariant checks are spread across services. Move stable domain rules into better domain seams.

**Scope**
- Identify reusable transition and invariant logic.
- Extract into domain objects, policy classes, or rule components.
- Keep orchestration layers thinner.

**Acceptance Criteria**
- Representative domains have isolated rule logic with focused tests.
- Services become easier to read and reason about.

**Dependencies**
- BE-011
- BE-012

### BE-015 — Centralize reusable validation logic for files, ranges, and action inputs

**Description**
Validation rules like file uploads and date ranges are repeated in multiple places. Consolidate them into reusable validators.

**Scope**
- Identify repeat validation logic.
- Extract validators and standardize error messages.
- Integrate with the central API error contract.

**Acceptance Criteria**
- At least three duplicated validation patterns are centralized.
- Controllers and services stop duplicating identical checks.

**Dependencies**
- BE-001

### BE-016 — Introduce tenant-aware test fixtures and helpers for backend integration tests

**Description**
Many tests manually provision tenants and bind request scope/context. Create shared fixtures so tests describe intent rather than plumbing.

**Scope**
- Add reusable helpers for tenant setup, actor context, and seeded entities.
- Make the helpers easy to compose for service and HTTP tests.

**Acceptance Criteria**
- At least three representative test classes are migrated to the new fixtures.
- Direct `ScopedValue` and repeated setup ceremony is reduced.

**Dependencies**
- None

### BE-017 — Define and document backend test taxonomy and coupling rules

**Description**
Clarify what belongs in HTTP contract tests, service tests, repository tests, and cross-tenant/security tests to reduce future test brittleness.

**Scope**
- Write a short testing guide.
- Define allowed dependency surface per test type.
- Use it when refactoring existing tests.

**Acceptance Criteria**
- A concise backend testing guideline exists.
- New tests can follow a clear pattern without guessing.

**Dependencies**
- None

### BE-018 — Reduce implementation coupling in selected backend integration tests

**Description**
Some integration tests depend heavily on repositories, transaction templates, and request-scope plumbing. Refactor them to rely on fixtures and observable behavior.

**Scope**
- Start with a small set of high-value test classes.
- Seed through supported helpers or public seams where practical.
- Preserve multitenancy and security assertions.

**Acceptance Criteria**
- Selected tests use less direct repository and scope plumbing.
- Tests remain strong on behavioral coverage while becoming easier to refactor safely.

**Dependencies**
- BE-016
- BE-017

### BE-019 — Add HTTP contract tests for standardized backend error responses

**Description**
Once error handling is centralized, add contract tests to prevent accidental changes in API-visible error payloads.

**Scope**
- Cover validation, forbidden, not found, conflict, and unexpected server errors.
- Assert core shape and stable fields.

**Acceptance Criteria**
- Contract tests fail on accidental response-shape drift.
- Coverage includes representative controller entry points.

**Dependencies**
- BE-001

### BE-020 — Enforce backend architecture rules with automated tests

**Description**
Add architecture tests so controller and exception-handling conventions do not regress over time.

**Scope**
- Enforce that controllers do not inject repositories.
- Enforce that controllers do not access raw request-scope plumbing directly.
- Optionally enforce standard exception-handling patterns.

**Acceptance Criteria**
- Build fails when core architectural rules are violated.
- Rules are scoped carefully to avoid excessive false positives.

**Dependencies**
- BE-008
- BE-004
- BE-001

### BE-021 — Add guardrails for oversized services and controllers

**Description**
Prevent new god classes by introducing warning or enforcement thresholds for class size and constructor dependency count.

**Scope**
- Define initial thresholds.
- Add a reporting or enforcement mechanism appropriate for the project.
- Calibrate based on existing hotspots.

**Acceptance Criteria**
- Thresholds are documented.
- CI or local checks can flag new violations.

**Dependencies**
- BE-011
- BE-012

### BE-022 — Add backend contribution checklist for controller, exception, and test quality

**Description**
Capture the preferred patterns introduced by this backlog so future changes follow the same discipline.

**Scope**
- Document controller thinness rules.
- Document error-handling rules.
- Document test-coupling guidance.

**Acceptance Criteria**
- A concise checklist exists and is easy to reference in reviews.

**Dependencies**
- BE-001
- BE-004
- BE-017
