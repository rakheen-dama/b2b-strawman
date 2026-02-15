# ADR-058: Rendering Context Assembly Pattern

**Status**: Accepted

**Context**: When a document template is rendered, the Thymeleaf engine needs access to data from the primary entity (project, customer, or invoice) and all related entities (customer, members, org settings, budget, tags, custom fields). The question is how this data is provided to the template engine: should JPA entities be passed directly into the Thymeleaf context, or should a separate service assemble a curated `Map<String, Object>` containing only the data that templates are allowed to access?

The answer has implications for lazy-loading behavior (will Thymeleaf trigger N+1 queries by accessing unmapped relationships?), data security (should templates be able to access any field on any entity?), null safety (what happens when a project has no customer or a customer has no custom fields?), and API stability (if an entity field is renamed, do all templates break?).

The existing Thymeleaf usage in Phase 10 (invoice preview) sets a precedent: `InvoiceService.renderPreview()` creates a `Context` and sets individual variables (`ctx.setVariable("invoice", invoice)`, `ctx.setVariable("groupedLines", grouped)`) — effectively a manual context assembly. The question is whether to formalize and extend this pattern or take a different approach.

**Options Considered**:

1. **Builder-pattern service producing `Map<String, Object>`** — A `TemplateContextBuilder` service takes the primary entity type and ID, loads all related entities through the existing service layer, transforms them into plain maps/DTOs, and returns a `Map<String, Object>` that is passed to Thymeleaf.
   - Pros:
     - Explicit control over what data templates can access. Template authors see a documented, stable variable interface — not the internal JPA entity structure.
     - No lazy-loading surprises. All data is loaded eagerly and intentionally. The builder knows exactly which relationships to traverse and performs optimized queries.
     - Null safety at the builder level. If a project has no customer, the builder sets `customer` to null (or an empty map). Template authors use `th:if="${customer}"` — no `LazyInitializationException`.
     - Data enrichment: the builder can format dates, resolve S3 keys to pre-signed URLs, flatten custom field JSONB into a convenient map, compute derived values.
     - Decouples template variable names from entity field names. If `Customer.email` is renamed to `Customer.contactEmail`, only the builder needs updating — templates continue using `${customer.email}`.
     - Custom field values (Phase 11 JSONB) are naturally flattened: `project.customFields.case_number` instead of `project.getCustomFieldValues().get("case_number")`.
     - Testable in isolation — the builder can be unit-tested with mock services.
   - Cons:
     - More code: a dedicated builder service with per-entity-type logic.
     - Data duplication at render time: entity data is copied into maps. Acceptable for single-document rendering (not batch).
     - New fields on entities don't automatically appear in templates — the builder must be updated explicitly. This is also a pro (intentional control), but adds maintenance cost.

2. **Pass JPA entities directly to Thymeleaf** — Set the JPA entity objects directly as Thymeleaf context variables. Template expressions like `${project.name}` resolve directly to entity getter methods.
   - Pros:
     - Zero extra code — no builder, no mapping. Entity fields are automatically available via getter methods.
     - New fields added to entities are immediately available in templates without any mapping changes.
     - Familiar to developers who have used Thymeleaf with Spring MVC — the default pattern.
   - Cons:
     - Lazy-loading risk: if a template accesses a relationship that wasn't eagerly fetched (e.g., `${project.customer.name}` when customer is lazy), it triggers a query — or throws `LazyInitializationException` if the session is closed. In a `@Transactional(readOnly = true)` method this may work, but the query pattern is unpredictable and depends on template content.
     - No control over data exposure: templates can access any public getter, including `tenantId`, `createdAt`, internal IDs, or other fields that shouldn't appear in client-facing documents.
     - Null pointer chains: `${project.customer.customFields.tax_number}` throws NPE if any link in the chain is null. Thymeleaf has safe navigation (`${project?.customer?.name}`) but it's error-prone for template authors.
     - Entity structure changes break templates silently. If `Customer.email` is renamed, all templates referencing `${customer.email}` produce empty output or errors.
     - Custom field values (JSONB) require Thymeleaf to call `getCustomFieldValues()` and navigate a raw `Map` — not as clean as a pre-flattened `customFields` map.
     - No opportunity for data enrichment (S3 URL resolution, date formatting) at the data layer — template authors must use Thymeleaf utility expressions.

3. **DTO projection layer** — Create dedicated DTO records (e.g., `ProjectTemplateView`, `CustomerTemplateView`) that are populated by JPQL constructor expressions or Spring Data projections. Pass these DTOs to Thymeleaf.
   - Pros:
     - Type-safe: DTOs define the exact shape of data available to templates.
     - No lazy-loading: DTOs are plain records with no JPA proxies.
     - Compile-time validation: if a DTO field is removed, compilation fails.
   - Cons:
     - Rigid: adding a new variable to templates requires creating or modifying a DTO class, updating the JPQL query, and rebuilding. The Map-based approach allows the builder to add variables without new classes.
     - Complex JPQL: constructor expressions for DTOs with 15+ fields from multiple joins are difficult to write and maintain.
     - Custom fields (dynamic keys from JSONB) don't fit into a static DTO. Would need a `Map<String, Object> customFields` field on the DTO anyway — defeating the type-safety benefit.
     - Tags (dynamic list) and members (dynamic list) similarly require collections on the DTO.
     - More classes to maintain than the Map approach, with less flexibility for template authors.

**Decision**: Builder-pattern service producing `Map<String, Object>` (Option 1).

**Rationale**: The builder pattern provides the best balance of safety, flexibility, and maintainability for a template rendering system. Templates are authored by org admins (not developers), so the rendering context must be predictable, null-safe, and well-documented. A builder that explicitly assembles the context ensures that every variable a template can access has been intentionally provided, validated for null safety, and enriched where needed (S3 URLs, formatted dates, flattened custom fields).

The lazy-loading risk of Option 2 is particularly dangerous in a multi-tenant system. A template that works fine for Tenant A (whose projects all have customers) could throw `LazyInitializationException` for Tenant B (whose project has no customer). The builder catches this at the assembly level, not at render time.

The DTO approach (Option 3) would provide type safety but at the cost of rigidity. Document templates are a user-facing feature where the variable set evolves as new data becomes available (e.g., adding budget data to project context, adding tags when Phase 11 ships). Maps evolve more easily than DTOs for this use case. The dynamic nature of custom fields (JSONB with user-defined keys) makes static DTOs impractical anyway.

The existing invoice preview pattern (`InvoiceService.renderPreview()`) already does manual context assembly — the builder formalizes and extends this proven approach.

**Consequences**:
- `TemplateContextBuilder` service with `buildContext(String entityType, UUID entityId, UUID memberId)` method returning `Map<String, Object>`.
- Internal strategy: separate builder methods or classes per entity type (`buildProjectContext`, `buildCustomerContext`, `buildInvoiceContext`).
- All entity data is loaded through existing service layer (tenant-scoped, access-controlled). No direct repository calls in the builder.
- Custom field values flattened into `entity.customFields` map — template expressions like `${project.customFields.case_number}` work naturally.
- S3 logo key resolved to pre-signed URL in the builder, not in templates.
- The variable reference panel in the template editor is generated from the builder's documented output, ensuring the editor and the runtime agree on available variables.
- New entity fields must be explicitly added to the builder to become available in templates. This is documented as a conscious design choice, not an oversight.
- Unit tests for each entity type's context assembly, including null relationship cases (project with no customer, customer with no projects, invoice with no project).
