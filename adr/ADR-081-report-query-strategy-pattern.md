# ADR-081: Report Query Strategy Pattern

**Status**: Accepted

**Context**:

Phase 19 introduces a report execution framework where each report type (Timesheet, Invoice Aging, Project Profitability) has fundamentally different query logic — different SQL, different grouping, different summary computation. The framework needs a dispatch mechanism that maps a report slug to the correct query implementation. This mechanism must be extensible (adding a new report type should not require modifying the dispatch code) and testable (each query should be independently testable without the full framework).

The existing codebase has a precedent for this pattern: `PdfRenderingService` collects `List<TemplateContextBuilder>` via constructor injection and dispatches by `TemplateEntityType`. The report framework faces the same dispatch problem but keyed by String slug rather than an enum.

**Options Considered**:

1. **Enum-based switch in service** -- A single `ReportExecutionService` method with a `switch` on the report slug, calling different private methods for each report type.
   - Pros: Simple, all logic in one place, easy to follow
   - Cons: Violates Open/Closed Principle — adding a new report requires modifying the service. Not independently testable. Method grows unboundedly.

2. **Manual Map bean with explicit registration** -- A `@Bean` method that constructs a `Map<String, ReportQuery>` with explicit entries: `Map.of("timesheet", new TimesheetReportQuery(...), ...)`.
   - Pros: Explicit wiring, clear what's registered. Easy to understand.
   - Cons: Adding a new report requires modifying the config class. Manual wiring is error-prone (forgetting to register). Doesn't leverage Spring's component scanning.

3. **Spring @Component auto-collection with slug-based dispatch (chosen)** -- Each `ReportQuery` implementation is a Spring `@Component`. `ReportExecutionService` receives `List<ReportQuery>` via constructor injection and builds a `Map<String, ReportQuery>` keyed by `getSlug()` at startup.
   - Pros: Adding a new report = new `@Component` class only (no config changes). Follows existing `TemplateContextBuilder` pattern. Each query is independently injectable and testable. Spring validates wiring at startup.
   - Cons: Slightly less explicit — must scan the package to see all registered queries. Slug collision detected at runtime, not compile time.

**Decision**: Option 3 -- Spring `@Component` auto-collection with slug-based dispatch.

**Rationale**:

This pattern is already proven in the codebase. `PdfRenderingService` collects `List<TemplateContextBuilder>` and dispatches by entity type — the report framework does the same thing with a String slug. The consistency makes the codebase more predictable for developers who already understand the document template pipeline.

The extensibility benefit is significant for a report framework. Professional services platforms accumulate many report types over time. Each new report type being a single `@Component` class with no framework modifications keeps the change surface minimal and reduces merge conflicts when multiple reports are developed in parallel.

The slug collision risk is mitigated by adding a startup validation in `ReportExecutionService` constructor: if two beans return the same slug, throw an `IllegalStateException` with a clear message. This fails fast during application startup rather than silently shadowing one implementation.

**Backend impact**: `ReportExecutionService` receives `List<ReportQuery>` via constructor injection and builds a `Map<String, ReportQuery>` at startup. Each `ReportQuery` implementation (`TimesheetReportQuery`, `InvoiceAgingReportQuery`, `ProjectProfitabilityReportQuery`) is a `@Component` in the `reporting/` package. Startup validation in the constructor throws `IllegalStateException` on duplicate slugs.

**Consequences**:

- Each new report type requires: (1) a `ReportQuery` `@Component`, (2) a `ReportDefinition` seed entry. No framework changes.
- `ReportExecutionService` constructor must validate no duplicate slugs in the collected list.
- Report queries are fully testable in isolation — inject the query bean directly without the dispatch layer.
- The slug string is the coupling point between the `ReportDefinition` entity (database) and the `ReportQuery` bean (code). If a definition exists without a matching query, the execution endpoint returns a clear error.
