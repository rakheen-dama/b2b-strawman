# ADR-238: Entity Type and Work Type as VARCHAR with Service-Layer Validation

**Status**: Accepted

**Context**:

Phase 63 promotes `entity_type` (on Customer) and `work_type` (on Project) from JSONB custom fields to proper entity columns. Both fields represent categorical values with vertical-specific options. In the accounting-za vertical, `entity_type` options include PTY_LTD, SOLE_PROPRIETOR, CC, TRUST, PARTNERSHIP, and NPC. In the legal-za vertical, the options include INDIVIDUAL, COMPANY, TRUST, CLOSE_CORPORATION, PARTNERSHIP, ESTATE, and GOVERNMENT. Similarly, `work_type` unifies accounting-za's `engagement_type` (MONTHLY_BOOKKEEPING, ANNUAL_TAX_RETURN, etc.) and legal-za's `matter_type` (LITIGATION, CONVEYANCING, COMMERCIAL, etc.) -- completely disjoint value sets.

The question is what type to use for the database column and the Java field. Two other fields being promoted in this phase -- `ProjectPriority` (LOW, MEDIUM, HIGH) and `TaxType` (VAT, GST, SALES_TAX, NONE) -- have universal, fixed value sets that do not vary by vertical. The decision for those is straightforward: Java enums with `@Enumerated(EnumType.STRING)`. But `entity_type` and `work_type` are different -- their valid values depend on the tenant's vertical profile and will expand as new verticals are added.

The codebase already has a precedent for this choice. `Invoice.paymentDestination` is stored as `VARCHAR(50)` with service-layer validation rather than a Java enum, because the valid destinations depend on vertical configuration (trust accounting introduces "TRUST" as a destination alongside "OPERATING"). `Task.type` is also a VARCHAR for similar reasons. Conversely, `Customer.customerType` (INDIVIDUAL, COMPANY, TRUST) and `Customer.lifecycleStatus` (PROSPECT, ONBOARDING, ACTIVE, ...) are Java enums because their values are universal and state-machine-governed.

**Options Considered**:

1. **Java enum containing all vertical values** -- Create a single `EntityTypeValue` enum with every value from every vertical: PTY_LTD, SOLE_PROPRIETOR, CC, TRUST, PARTNERSHIP, NPC, INDIVIDUAL, COMPANY, CLOSE_CORPORATION, ESTATE, GOVERNMENT. Similarly, a `WorkTypeValue` enum with all accounting engagement types and all legal matter types.
   - Pros:
     - Maximum type safety in Java code. Compile-time checking prevents typos.
     - Hibernate handles serialization/deserialization automatically via `@Enumerated(EnumType.STRING)`.
     - IDE autocompletion shows all possible values.
   - Cons:
     - Every new vertical requires a code change and redeployment to add its values. Adding a single new matter type for a future "insurance-za" vertical means modifying `WorkTypeValue.java`, recompiling, and redeploying. This couples vertical configuration to the deployment cycle.
     - The enum becomes a catch-all. A `WorkTypeValue` enum with 20+ values spanning accounting, legal, insurance, and engineering verticals is meaningless -- no single tenant uses more than a subset, and the values have no relationship to each other.
     - `@Enumerated(EnumType.STRING)` throws `IllegalArgumentException` if the database contains a value not in the Java enum. This means a value added directly in the database (migration for a new vertical) will crash existing code until the enum is updated. This is fragile during phased rollouts.
     - No vertical-aware validation. The enum accepts LITIGATION for an accounting tenant and MONTHLY_BOOKKEEPING for a legal tenant. Additional service-layer validation is still required.

2. **VARCHAR with no validation** -- Store as plain VARCHAR and accept any string value. No enum, no validation.
   - Pros:
     - Maximum flexibility. New values can be added via pack file changes alone, no code changes.
     - No deployment coupling. A new vertical's field values are configured, not coded.
     - No crash risk from unknown values.
   - Cons:
     - No protection against typos or invalid values. A client sending `"PTY_LTDD"` (double D) would be silently accepted.
     - No documentation of valid values in the codebase. Developers must check pack files to discover what values are acceptable.
     - Querying and filtering becomes unreliable if values drift.

3. **VARCHAR with service-layer validation per vertical profile** -- Store as `VARCHAR(30)` / `VARCHAR(50)` in the database. In the Java entity, use `String`. Validation happens in the service layer: the service reads the tenant's vertical profile and validates the submitted value against a registry of allowed values per vertical per field.
   - Pros:
     - Decouples value sets from the Java type system. New verticals add their values to a configuration registry (or pack metadata), not to a Java enum. No recompilation for new values.
     - Vertical-aware validation. An accounting-za tenant submitting "LITIGATION" as `entity_type` is rejected. A legal-za tenant submitting "PTY_LTD" is accepted (it is in the legal-za entity type list).
     - Follows the existing `paymentDestination` pattern in the codebase. Consistent with established conventions.
     - Resilient to unknown values in the database. If a future migration adds a new value for a new vertical, existing code does not crash -- it simply does not validate against its registry, which is a graceful degradation.
     - The validation registry can be loaded from the same pack metadata that defines dropdown options, keeping the source of truth in one place.
   - Cons:
     - No compile-time safety. A developer writing `project.setWorkType("LITIGAITON")` (typo) is not caught until runtime.
     - Validation logic must be written and maintained in the service layer. More code than an enum annotation.
     - Discoverability is slightly worse than an enum, though constants classes or validation registries mitigate this.

**Decision**: Option 3 -- VARCHAR with service-layer validation per vertical profile.

**Rationale**:

The decisive factor is the vertical-dependent nature of the value sets. `entity_type` and `work_type` are not universal enumerations -- they are vertical-specific vocabularies. An accounting firm in South Africa deals with PTY_LTD, CC, and NPC. A law firm deals with INDIVIDUAL, COMPANY, ESTATE, and GOVERNMENT. A future construction vertical might add JOINT_VENTURE, CONSORTIUM, and SUBCONTRACTOR. These value sets will grow independently of each other as new verticals are onboarded.

A Java enum that attempts to contain all values from all verticals is a code smell: it creates a compile-time dependency between unrelated vertical domains. Adding a legal-za entity type should not require touching a file that an accounting-za developer also modifies. The VARCHAR approach keeps vertical value sets in configuration (pack metadata or a validation registry), which is the same boundary the custom fields system already established.

The service-layer validation pattern is proven in this codebase. `InvoiceService` validates `paymentDestination` against allowed destinations based on org configuration. The same pattern applies here: `CustomerService.validateEntityType(entityType, verticalProfile)` checks against a registry that maps vertical profiles to allowed entity type values. The registry can be initialized from the same data that the `FieldPackSeeder` uses for dropdown options, ensuring consistency.

The compile-time safety tradeoff is acceptable. Fields like `entity_type` and `work_type` are written infrequently (customer creation, project creation) and the validation runs on every write. A typo in a constant is caught by the first integration test. The codebase already uses this pattern for `Task.type`, `Invoice.paymentDestination`, and several other VARCHAR fields without issues.

`ProjectPriority` (LOW, MEDIUM, HIGH) and `TaxType` (VAT, GST, SALES_TAX, NONE) are different -- they use Java enums because their values are universal across all verticals and are unlikely to change. The boundary is clear: if the value set is fixed and universal, use a Java enum. If the value set is vertical-dependent or likely to expand per-vertical, use VARCHAR with service-layer validation.

**Consequences**:

- `Customer.entityType` is `String` with `@Column(name = "entity_type", length = 30)`. No `@Enumerated`.
- `Project.workType` is `String` with `@Column(name = "work_type", length = 50)`. No `@Enumerated`.
- `Project.priority` is `ProjectPriority` enum (LOW, MEDIUM, HIGH) with `@Enumerated(EnumType.STRING)`. `Invoice.taxType` is `TaxType` enum with `@Enumerated(EnumType.STRING)`. These use enums because their values are universal and fixed. Note: `ProjectPriority` intentionally has 3 values while the existing `TaskPriority` has 4 (LOW, MEDIUM, HIGH, URGENT). Projects operate at a different urgency granularity than tasks — project priority is a planning signal, while task priority includes an URGENT level for immediate operational triage.
- Service-layer validation uses a registry that maps `(verticalProfile, fieldName) -> Set<String>`. The registry is a Spring bean initialized from pack metadata or a static configuration class.
- The database columns use `VARCHAR` -- no `CHECK` constraints, because the valid value set is runtime-configurable per tenant, not schema-enforceable.
- Future verticals add their allowed values to the registry (configuration change) rather than modifying a Java enum (code change). This keeps vertical onboarding as a configuration concern.
- Related: [ADR-237](ADR-237-structural-vs-custom-field-boundary.md) (which fields to promote)
