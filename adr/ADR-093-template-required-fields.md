# ADR-093: Template Required Field Storage and Resolution

**Status**: Accepted

**Context**:

Document templates in DocTeams reference custom field values in their Thymeleaf expressions (e.g., `${customer.customFields['tax_number']}`). Currently there is no declaration of what fields a template needs — documents generate with blank placeholders when data is missing. This ADR decides how template required fields are stored and what happens when a referenced field definition is deleted.

**Options Considered**:

1. **Soft references by slug (chosen)** -- Store required fields as a JSONB array of `{entity, slug}` pairs on `DocumentTemplate`. At generation time, look up field values by slug in the rendering context. If a referenced field definition has been deleted, the field renders as blank and a warning is emitted. No hard foreign key relationship.
   - Pros:
     - Resilient to field definition deletion -- templates do not break when a field is removed, they simply produce a blank value (with a generation warning)
     - Simple JSONB storage: no join queries, no foreign key constraints, easy to serialize and deserialize
     - Natural alignment with how Thymeleaf already accesses fields (`customFields['slug']`) -- declaration and usage use the same key
     - Field slugs are already immutable after creation in this system, so slug drift is not a runtime concern
     - Generation validation can check each declared field against the assembled rendering context and surface clear, actionable warnings before emitting the document
   - Cons:
     - Stale slug references accumulate silently if field definitions are deleted; requires a UI indicator for "unknown field" references
     - No database-level referential integrity -- orphaned references are only detectable at generation time or via explicit UI validation
     - Periodic cleanup tooling or admin UX may be needed to surface orphaned references over time

2. **Hard references by field definition ID** -- Store required fields as a JSONB array of field definition UUIDs. At generation time, resolve UUIDs to slugs via `FieldDefinitionRepository`. If a field definition is deleted, block the deletion or cascade-remove the reference from all templates.
   - Pros:
     - Strong referential integrity: no stale references possible if deletions are blocked or cascaded
     - Deletion is explicit -- admins must acknowledge the impact on templates before removing a field
     - UUID-based references are stable even if slug semantics were ever to change
   - Cons:
     - Breaks templates when field definitions are deleted (forced cascade logic) or forces admins to update templates before deletion can proceed
     - UUID-based JSONB storage is opaque and requires an extra `FieldDefinitionRepository` lookup at generation time to resolve slugs
     - Couples template authoring to field definition IDs rather than the slug-based names that appear in the template body -- authoring and storage use different keys, increasing cognitive overhead
     - Cascade-on-delete logic across a JSONB array is complex to implement correctly at the database level and would require application-side traversal of all tenant templates on every field deletion

3. **Template-embedded field extraction (auto-detect)** -- Parse the Thymeleaf template body to extract field references automatically (regex or AST walk for `customFields['...']` patterns). No manual declaration needed; required fields are always inferred from the template source.
   - Pros:
     - Zero configuration: required fields are always in sync with the template body by construction
     - No stale references: removing a `customFields['slug']` expression from the template immediately removes it from the "required" set
     - No additional storage column needed on `DocumentTemplate`
   - Cons:
     - Fragile parsing: Thymeleaf's expression language supports complex, nested, and conditional expressions that a regex or shallow AST walk cannot reliably cover
     - Cannot distinguish required from optional field usage -- a field referenced inside a `th:if` conditional block may be legitimately absent without indicating an error
     - Fields accessed indirectly (e.g., via a helper variable or a included fragment) will be missed entirely
     - Significant implementation complexity with a high risk of false negatives (missing required fields) and false positives (flagging optional conditional fields as required)
     - Parsing must be re-run every time the template body is saved, adding latency to the save path

**Decision**: Soft references by slug (Option 1).

**Rationale**: Thymeleaf templates already access custom fields by slug (`customFields['tax_number']`), so slug-based references create a natural alignment between declaration and usage -- the key in the JSONB array is exactly the key the template expression uses. This eliminates any translation layer at generation time.

Soft references mean templates are resilient: deleting a field definition does not break existing templates. Instead, generation validation checks each declared `{entity, slug}` pair against the assembled rendering context and emits a warning when a value is missing or blank. This gives admins actionable feedback without causing hard failures, and it respects the reality that field deletions in production are rare events that should not cascade into template breakage.

Hard FK references (Option 2) create a brittle coupling that forces admins to either block field deletion or update every template that references the field before deletion can proceed. This is a poor operator experience in a system where templates are long-lived and field definitions may evolve independently. Auto-detection (Option 3) is elegant in concept but unreliable in practice: Thymeleaf's expression language cannot be safely parsed with a regex, conditional field usage cannot be distinguished from unconditional usage, and the approach fails silently on indirect or fragment-based field references.

The `requiredContextFields` JSONB structure on `DocumentTemplate` is: `[{"entity": "customer", "slug": "tax_number"}, {"entity": "project", "slug": "reference_number"}]`. At generation time, the rendering context validation step checks each declared pair against the assembled context map produced by the context builders. Missing or blank values produce per-field warnings in the generation response, allowing the caller to decide whether to proceed or abort.

Field slugs are immutable after creation in this system (an existing invariant), so slug drift -- a slug reference in `requiredContextFields` becoming stale because the slug was renamed -- is not a concern. The only staleness scenario is field definition deletion, which is handled by the "unknown field" UI indicator and generation-time warnings described above.

**Consequences**:

- Positive:
  - Templates survive field definition deletion without breaking -- generation degrades gracefully to blank values with warnings rather than hard errors
  - Simple JSONB storage with no join queries or foreign key constraints needed
  - Generation validation provides clear, per-field warnings for missing data, giving operators actionable feedback
  - Declaration and usage use the same slug key, so template authors can read `requiredContextFields` and immediately understand which template expressions it covers

- Negative:
  - Orphaned slug references may accumulate over time when field definitions are deleted; mitigated by a UI indicator that flags `{entity, slug}` pairs that do not correspond to any known field definition
  - If a field slug were ever renamed (currently prevented by system invariant), all `requiredContextFields` references to the old slug would silently become orphaned -- this invariant must be maintained or this ADR must be revisited

- Neutral:
  - The `requiredContextFields` column is advisory metadata for validation and UI hints; it does not affect the Thymeleaf rendering pipeline itself, which already accesses all `customFields` values by slug regardless of what is declared in `requiredContextFields`

- Related:
  - [ADR-058](ADR-058-rendering-context-assembly.md) -- how context builders assemble the rendering context that `requiredContextFields` is validated against
  - [ADR-052](ADR-052-custom-fields-jsonb-storage.md) -- the JSONB storage pattern for custom field values that this ADR's slug references point into
