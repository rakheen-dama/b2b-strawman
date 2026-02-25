# ADR-094: Conditional Field Visibility Evaluation Strategy

**Status**: Accepted

**Context**:

DocTeams needs conditional field visibility — some custom fields should only appear when another field has a specific value (e.g., "Trust Account Number" only when "Matter Type" = "Litigation"). Two decisions are required: where visibility conditions are evaluated, and whether hidden field values should be cleared or preserved when a field becomes hidden.

Each custom field definition may carry an optional visibility condition of the form `{ dependsOnSlug, operator, value }`, where `operator` is one of `eq`, `neq`, or `in`. These conditions are stored in the JSONB field definition on `FieldGroup` and are evaluated against the sibling field values on the entity being edited or saved.

**Options Considered**:

### Decision A: Where visibility evaluation happens

1. **Frontend-only evaluation** -- Visibility conditions are evaluated only in the `CustomFieldSection` React component. The backend validates all fields unconditionally regardless of whether any field is hidden.
   - Pros:
     - Simple backend: no visibility logic needed in `CustomFieldValidator`
     - No duplicate evaluation logic to keep in sync
   - Cons:
     - Backend rejects valid submissions: a hidden required field has no value (the user cannot see or fill it), but the backend demands it — causing spurious validation errors on otherwise correct forms
     - API consumers (e.g., integrations, scripts) that submit partial field sets get incorrect validation errors for hidden required fields
     - Inconsistent validation model: frontend hides fields that backend treats as mandatory

2. **Frontend + backend evaluation (chosen)** -- Both the `CustomFieldSection` component and the backend `CustomFieldValidator` evaluate visibility conditions independently. The frontend hides fields reactively as the user changes controlling field values. The backend skips required-field validation for any field whose visibility condition evaluates to false given the submitted field values.
   - Pros:
     - Consistent validation: hidden fields are not validated on either side — a submission with a blank hidden required field is accepted correctly
     - API consumers get correct behaviour without knowing which fields are visible; the backend derives visibility from the submitted values themselves
     - Defense in depth: both layers enforce the same semantics, so a client bug that submits a hidden required field blank does not cause a confusing server error
   - Cons:
     - Evaluation logic exists in two languages (TypeScript and Java) — must be kept in sync when operators or condition structure changes
     - Slightly more complex `CustomFieldValidator`

3. **Backend-only with frontend hint** -- The backend evaluates conditions and returns a `visibleFields` list alongside each entity response. The frontend renders only the fields in that list and does not evaluate conditions itself.
   - Pros:
     - Single source of truth: condition evaluation lives only in the backend
     - No sync risk between languages
   - Cons:
     - Requires an API round-trip on every controlling field change: each keystroke or dropdown selection that could affect visibility must re-fetch the entity (or a dedicated visibility endpoint), making field hiding sluggish
     - Does not work for unsaved entities (new entity forms): there is no server-side entity yet whose response can carry the `visibleFields` hint
     - Increases coupling between frontend rendering and backend response shape; field visibility becomes dependent on network availability

### Decision B: Hidden field value handling

1. **Preserve values silently (chosen)** -- When a field becomes hidden because its controlling field changed value, the stored value in the JSONB column is retained. The hidden field is neither shown nor validated. If the controlling field changes back, the previous value reappears as if unchanged.
   - Pros:
     - No data loss: users who toggle a dropdown back and forth do not lose dependent field values they already entered
     - Undo is free: reverting the controlling field restores the full previous state without any special logic
     - Simple implementation: no clearing logic needed in frontend state management or backend save handlers
     - Document templates can still reference hidden field values — useful for conditional template sections where the template author, not the visibility rule, decides what to render
   - Cons:
     - "Ghost data" exists: the JSONB column contains values for fields that are not currently visible, which can be surprising when inspecting raw data or building exports
     - Hidden values appear in raw JSONB queries; consumers must be aware that not all stored values correspond to currently-visible fields

2. **Clear values on hide** -- When a field becomes hidden (its visibility condition evaluates to false), its value is immediately cleared — set to null or removed from the JSONB map.
   - Pros:
     - Clean data: the JSONB value set exactly matches the visible field set at all times
     - WYSIWYG principle: what you see is what is stored
   - Cons:
     - Data loss: a user who accidentally changes a controlling field loses all values in dependent fields, with no recovery path
     - Complex implementation: clearing must trigger either an immediate save-on-change (adds backend calls on every field interaction) or a special pre-save clearing pass (requires tracking which fields were visible at form-open time vs. save time)
     - Particularly destructive for required dependent fields: the user must re-enter values after any accidental controlling field change

3. **Clear on save, preserve in memory** -- The frontend preserves hidden field values in local React state while the form is open. On save, any field whose visibility condition evaluates to false at save time has its value stripped from the payload before submission.
   - Pros:
     - Undo works during the editing session: reverting the controlling field before saving restores the dependent field values
     - Clean data on save: the persisted JSONB never contains values for currently-hidden fields
   - Cons:
     - Complex state management: the component must maintain a shadow copy of "all values including hidden" separate from "values to submit"
     - Surprising behaviour: a value that was visible in the form disappears silently after saving, with no indication to the user that it was discarded
     - Still loses data across save boundaries: once the entity is saved and the form is re-opened, the cleared value is gone permanently

**Decision**:

- Decision A: Option 2 — Frontend + backend evaluation
- Decision B: Option 1 — Preserve values silently

**Rationale**:

For evaluation location: the backend `CustomFieldValidator` already enforces required-field constraints. Without visibility-awareness in the validator, any hidden required field produces a spurious validation error — the user cannot see the field, cannot fill it in, and receives a confusing rejection. This makes frontend-only evaluation (Option 1) non-functional as designed. Backend-only evaluation (Option 3) is rejected because it makes visibility change latency proportional to network round-trips, breaks entirely for unsaved entities, and introduces an awkward coupling between the API response shape and frontend rendering logic.

The condition structure is deliberately minimal — three operators (`eq`, `neq`, `in`) over a string comparison — so keeping the TypeScript and Java implementations in sync is a low-maintenance burden. The condition schema can be versioned alongside the field definition schema if operators are ever extended.

For value handling: preserving hidden values (Option 1) is the pragmatic choice for a form-heavy workflow tool. Users frequently adjust controlling dropdown values while completing a form; clearing dependent field values on every such change would cause repeated data loss and frustration. The "ghost data" concern is mitigated by the fact that hidden fields are explicitly declared via visibility conditions — any consumer of the raw JSONB is dealing with a known schema, not arbitrary unstructured data. Option 3 (clear on save) is rejected because it produces surprising data loss at save time with no user-visible indication, and its state management complexity is disproportionate to the problem it solves.

**Consequences**:

- Positive:
  - Consistent validation between frontend and backend: hidden fields are invisible to validation on both sides, so no spurious required-field errors reach the user
  - No data loss from visibility changes: reverting a controlling field restores all dependent field values exactly as entered
  - Document templates retain access to hidden field values, enabling template authors to build conditional template sections independently of form visibility rules
  - Simple condition structure (three operators) keeps the dual-language sync burden low and the logic easy to audit

- Negative:
  - Evaluation logic must be maintained in two languages (Java and TypeScript); any change to the condition structure (e.g., adding a new operator) requires coordinated updates in `CustomFieldValidator` and `CustomFieldSection`
  - Hidden field values are present in raw JSONB queries and exports; consumers must be aware that the stored value set is a superset of the currently-visible field set

- Neutral:
  - If the controlling field is in a different group and that group is removed from the entity, the dependent field's visibility condition cannot be evaluated (the `dependsOnSlug` is absent from submitted values) — the default behaviour in this edge case is to treat the field as visible rather than hidden, avoiding unexpected data suppression
  - The "ghost data" present in JSONB is not a security concern: all fields in a group are scoped to the same tenant and the same entity type; there is no cross-tenant or cross-entity leakage

- Related: [ADR-052](ADR-052-custom-fields-jsonb-storage.md) — JSONB storage model that holds field values, including the `applied_field_groups` array and per-field value map that visibility evaluation reads from
