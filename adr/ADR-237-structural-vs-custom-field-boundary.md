# ADR-237: Structural vs. Custom Field Boundary

**Status**: Accepted

**Context**:

Phase 11 introduced an EAV-style custom fields system: `FieldDefinition` entities define field metadata, field packs seed them per vertical profile, and the actual values live in JSONB `custom_fields` columns on Customer, Project, Task, and Invoice. This was the right choice at launch -- the platform had no vertical domain knowledge, and every field beyond the core schema was speculative.

Over 50+ phases of domain building, many "custom" fields have proven to be structurally necessary. `registration_number` is queried by `ConflictCheckService` for conflict matching. `financial_year_end` is parsed from JSONB by `DeadlineCalculationService` to compute filing deadlines. `address_line1`, `city`, and `country` are checked by `PrerequisiteService` before invoice generation. `entity_type` appears in every vertical's customer pack. These fields are "custom" in name only -- they are universally present, service-layer dependencies, and performance bottlenecks when stored as unindexed JSONB.

The question is: what principle determines when a field should graduate from the custom fields system to a proper entity column? Without a clear boundary, every new field added in future phases will default to "custom" (because that is the path of least resistance), and the same technical debt will accumulate again.

**Options Considered**:

1. **Promote on first service-layer read** -- Any field that is read by service-layer logic (not just rendered in templates or displayed on forms) becomes structural immediately.
   - Pros:
     - Clear, binary criterion: does a `*Service.java` class access this field? If yes, promote.
     - Catches performance-critical fields early. A service that does `customFields.get("financial_year_end")` is a signal that the field needs type safety and indexing.
     - Easy to audit: grep for `customFields.get("` or `getCustomFields()` in service classes.
   - Cons:
     - Too aggressive. A service that reads a field once for a low-frequency admin operation does not justify a schema change. The `registration_number` check in `ConflictCheckService` runs on every customer creation -- that justifies promotion. A hypothetical service that reads `referred_by` once during an annual report does not.
     - Does not account for universality. A vertical-specific field read by a vertical-specific service (e.g., `trust_deed_date` read by `TrustAdministrationService`) should remain custom even though it is accessed in a service.

2. **Promote when used in 2+ verticals** -- A field becomes structural when it appears in pack files for two or more vertical profiles, or in the "common" pack.
   - Pros:
     - Ensures promoted fields are genuinely universal, not vertical-specific.
     - Simple to verify: count pack files containing the slug.
   - Cons:
     - Misses fields that are in one vertical but are structurally critical. `financial_year_end` originated in the accounting-za pack but is used by `DeadlineCalculationService` -- a service that all verticals will eventually need. Cross-vertical presence is a lagging indicator.
     - Does not consider performance. A field present in all verticals but never queried (e.g., `referred_by` appears in both accounting-za and legal-za) does not benefit from promotion.

3. **Promote when required for a core flow** -- A field becomes structural when it is a prerequisite for a core business flow (invoicing, proposal sending, document generation, conflict checking).
   - Pros:
     - Directly tied to business value. If a field blocks invoice generation, it matters enough to be a real column.
     - Aligns with the existing `requiredForContexts` mechanism in `FieldDefinition`.
   - Cons:
     - Too narrow. `estimated_hours` feeds into budget and profitability calculations but is not "required for" any specific prerequisite context. `priority` on projects is used for sorting and filtering but does not gate any flow.
     - Conflates "required for context" (a validation rule) with "should be structural" (a storage decision). A field can be required for invoicing and still work fine as JSONB if it is only null-checked, never indexed or joined.

4. **Scoring rubric combining all factors** -- A field earns points across four dimensions. If it scores above a threshold, it is structural.
   - Dimensions: (a) service-layer read (queried in a `*Service.java`), (b) cross-vertical presence (appears in 2+ packs or the common pack), (c) core flow dependency (is a prerequisite or blocks a business action), (d) query performance need (filtered, sorted, joined, or indexed).
   - Pros:
     - Captures the full nuance. A field that scores highly on all four dimensions (like `registration_number`: service-layer conflict check, present in legal and accounting packs, prerequisite for proposals, needs an index) is unambiguously structural.
     - Prevents both over-promotion (a field that only scores on one dimension stays custom) and under-promotion (a field that scores on three dimensions gets promoted even if it is in only one vertical).
   - Cons:
     - Adds process overhead. Every field decision requires evaluating four criteria and potentially debating scores.
     - The threshold is arbitrary -- where to draw the line between "2 out of 4" and "3 out of 4"?

**Decision**: Option 4 -- Scoring rubric combining all factors, with a simple threshold.

**Rationale**:

No single criterion is sufficient. The fields being promoted in Phase 63 illustrate this clearly:

- `registration_number`: service-layer read (ConflictCheckService), cross-vertical (accounting + legal), core flow (conflict check prerequisite), query need (indexed for matching). 4/4 -- clearly structural.
- `estimated_hours`: service-layer read (budget/profitability calculations), single vertical origin (common-task), not a prerequisite, query need (aggregation). 2/4 -- borderline, but the aggregation use case and universal task presence push it over.
- `tax_year`: read by `DeadlineCalculationService` (a vertical-specific service invoked only for accounting-za tenants), single vertical (accounting-za), not a prerequisite for core flows, no query/index need. 1/4 -- stays custom. The "service-layer read" dimension applies to **universal services** that run for all tenants (e.g., `ConflictCheckService`, `PrerequisiteService`), not vertical-specific services that only execute when a tenant's profile matches. `DeadlineCalculationService` is accounting-za-specific, so this read does not trigger the "strongest signal" override.
- `postal_address`: no service-layer read, cross-vertical (accounting + legal), not a prerequisite, no query need. 1/4 -- stays custom. Correct.

The rubric produces the right answers for every field in this phase. The threshold is **2 or more dimensions**, with the caveat that a field scoring only on "cross-vertical presence" without any of the other three dimensions does not qualify (universality alone is not enough -- `referred_by` is in multiple packs but needs no promotion).

The four dimensions, in evaluation order:

| Dimension | Question | Weight |
|-----------|----------|--------|
| **Service-layer read** | Does a **universal** `*Service.java` class (one that runs for all tenants, not gated by vertical profile) access this field via `getCustomFields().get("slug")`? Vertical-specific services (e.g., `DeadlineCalculationService` for accounting-za only) do not count for the "strongest signal" override. | High -- strongest signal for universal services |
| **Query performance** | Is this field filtered, sorted, joined, or aggregated in SQL or JPQL queries? | High -- JSONB extraction in WHERE clauses is a measurable performance cost |
| **Core flow dependency** | Is this field a prerequisite for invoicing, proposals, document generation, or conflict checks? | Medium -- indicates business criticality |
| **Cross-vertical presence** | Does this field appear in 2+ vertical packs or the common pack? | Low alone, reinforcing with others |

A field that scores on service-layer read OR query performance is promoted regardless of the other dimensions, because those two dimensions have concrete technical consequences (type safety, indexing, query planning). A field that scores only on core flow dependency AND cross-vertical presence is promoted if both are present. A field that scores on only one of the lower dimensions stays custom.

**Consequences**:

- Phase 63 promotes 21 fields that score 2+ on the rubric. Fields like `tax_year`, `postal_address`, `referred_by`, and all trust-specific fields remain custom.
- Future phases adding new fields should evaluate against the rubric before defaulting to the custom fields system. The evaluation takes 30 seconds per field -- not a significant process burden.
- The rubric is documented here for reference. It is not enforced by tooling -- it is a design guideline for architecture decisions.
- Fields that are initially custom can be promoted later if they gain service-layer readers or query needs. The promotion path (add column, update entity/DTO, update service to read from column, remove from pack) is exactly what Phase 63 demonstrates.
- Related: [ADR-238](ADR-238-entity-type-varchar-vs-enum.md) (type choice for promoted fields)
