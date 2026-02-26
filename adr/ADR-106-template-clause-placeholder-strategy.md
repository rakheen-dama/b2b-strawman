# ADR-106: Template-Clause Placeholder Strategy

**Status**: Accepted

**Context**:

Templates need a way to declare where clause content appears in the rendered document. Per ADR-104, clause bodies are concatenated into a single HTML string and injected into the Thymeleaf context. The template must reference this variable to place clause content in the output. The question is how much control template authors have over clause placement — specifically, whether clauses appear as a single block or whether individual clauses can be placed at specific positions in the template.

The design must work with the per-document clause picker model: at generation time, users select which clauses to include and set their order. This means the set of clauses varies per document — a template cannot assume that a specific clause will always be present.

Template authors in the DocTeams platform range from org admins with basic HTML knowledge to power users who customize Thymeleaf expressions. The placeholder mechanism must be simple enough for the common case (put all clauses at the bottom of the document) while being expressive enough for reasonable customization (conditional clause headings, clause count checks).

**Options Considered**:

1. **Single global placeholder (chosen)** -- One `${clauses}` variable renders all selected clauses as a contiguous block. Template author places `<div th:utext="${clauses}"></div>` where they want all clauses. If the template does not include this placeholder, clauses are appended after the main content as a fallback.
   - Pros:
     - Dead simple for template authors: one variable name to learn, one placement decision to make.
     - Works naturally with the per-document clause picker: user selects and reorders all clauses as one set, they render in that order in one block.
     - Backward compatible: templates created before Phase 27 (without `${clauses}`) still work — clauses are appended at the end.
     - Easy to explain in documentation and template editor hints.
     - Supports conditional display via `${clauseCount}`: `<div th:if="${clauseCount > 0}"><h2>Terms and Conditions</h2><div th:utext="${clauses}"></div></div>`.
   - Cons:
     - No per-clause positioning: cannot put payment terms in Section 3 and liability in Section 7 of the same template.
     - All clauses appear in one contiguous block — no interleaving of clause content with template content.
     - Template author cannot conditionally render based on which specific clauses are selected (only whether any clauses exist).

2. **Named insertion points** -- Template uses `${clause('payment-terms')}` syntax to place specific clauses at specific positions by slug. Template author can position individual clauses anywhere in the document. Unplaced clauses are collected into a default `${clauses}` block.
   - Pros:
     - Fine-grained control over clause placement: payment terms in the billing section, confidentiality in the legal section.
     - Templates can integrate clauses contextually, making them feel like native parts of the document rather than an appended block.
   - Cons:
     - Named references create a hard dependency between templates and clause slugs. Renaming a clause slug (e.g., `payment-terms` to `payment-terms-v2`) breaks every template that references it.
     - Conflicts with the per-document picker model: if a template names `${clause('payment-terms')}` but the user does not include that clause at generation time, the template renders an empty spot (or needs fallback logic like `th:if` guards around every named reference).
     - Template authors must know clause slugs — adds a lookup step to the authoring workflow. The template editor would need a clause slug picker or autocomplete.
     - Significantly more complex rendering pipeline: must parse `${clause('slug')}` expressions, resolve them against the selected clause list, handle missing clauses, and still collect unplaced clauses for the default block.
     - Harder to maintain: the relationship between templates and clauses becomes bidirectional (templates reference clauses, clauses are selected per document), creating a dependency graph that is difficult to reason about.

3. **Both (global + named overrides)** -- Templates can use `${clauses}` for all-in-one rendering OR `${clause('slug')}` for specific placement. Named placements consume from the clause list; remaining clauses go to `${clauses}`. Both mechanisms coexist.
   - Pros:
     - Maximum flexibility: simple templates use `${clauses}`, advanced templates use named placement for key clauses.
     - Gradual adoption: template authors start with `${clauses}` and add named placements as needed.
   - Cons:
     - Complex mental model: template authors must understand the consumption semantics (named placements remove clauses from the global block). "Why did my payment terms clause disappear from the bottom?" — because the template placed it by name in Section 3.
     - Edge cases: what if a named clause is also in the global block? What if the user reorders clauses but the template has named placements that override the order? What if a named clause is not selected by the user?
     - Two rendering paths to maintain and test: global assembly + named extraction with fallback collection.
     - Over-engineered for v1: the additional complexity serves a use case (mixed placement) that no users have requested and that can be added later without breaking changes.
     - The consumption semantics create a non-obvious coupling between the `${clauses}` block and named placements — debugging rendering issues becomes harder.

**Decision**: Option 1 -- Single global placeholder.

**Rationale**:

The clause picker model is designed for per-document customization: users select and reorder clauses as a set at generation time. Named insertion points (Option 2) create a fundamental tension between template-author intent ("put payment terms here in Section 3") and user-generation-time choice ("I don't want payment terms in this document"). If the template has a named slot for a clause the user did not select, the template either renders an empty gap or requires `th:if` guard logic around every named reference. This turns every named placement into a conditional block, negating the simplicity benefit.

Option 3 (both mechanisms) inherits all of Option 2's problems plus adds consumption semantics: named placements "eat" clauses from the global block. This creates a non-obvious interaction where adding a `${clause('payment-terms')}` reference in Section 3 causes that clause to disappear from the `${clauses}` block at the bottom. Template authors would need to understand this consumption model, which is a significant cognitive burden for a v1 feature.

A single `${clauses}` block keeps the model clean: the template says where clauses go, the user decides which clauses and in what order. The `${clauseCount}` variable enables the most common conditional pattern — showing a "Terms and Conditions" heading only when clauses are present. The append-after-content fallback ensures backward compatibility with existing templates.

If demand materializes for per-clause positioning (e.g., a firm needs payment terms in the billing section and liability in the legal section), Option 2 can be added as an enhancement without breaking existing templates — the `${clauses}` global block would continue to work alongside named placements.

**Consequences**:

- Positive:
  - Template authors have one simple variable: `${clauses}`. The template editor can insert this with a single button click.
  - All clauses render in a single contiguous block, ordered by the user's selection at generation time. No ordering conflicts between template and user intent.
  - Backward compatible: templates without `${clauses}` get clauses appended after the main content. No migration needed for existing templates.
  - `${clauseCount}` enables conditional display: `<section th:if="${clauseCount > 0}"><h2>Terms and Conditions</h2><div th:utext="${clauses}"></div></section>`.
  - No dependency between templates and clause slugs. Renaming a clause slug does not break any template.

- Negative:
  - No per-clause positioning capability in v1. All clauses appear in one block, wherever the template places `${clauses}`.
  - Template authors cannot conditionally render content based on which specific clauses are selected — only whether any clauses exist (via `${clauseCount}`).
  - Templates without `${clauses}` get clauses appended at the end, which may produce suboptimal layout. Template editor should warn when a template has no `${clauses}` placeholder but the entity type supports clauses.

- Neutral:
  - The append-after-content fallback uses a `<div class="clause-appendix">` wrapper with default CSS for visual separation. Template authors who want precise control should include `${clauses}` explicitly.
  - CSS styling of the clause block is handled by the wrapping `<div class="clause-block">` per clause (set during assembly in ADR-104). Template authors can target `.clause-block` in their custom CSS for styling.
  - Future enhancement path: add `${clause('slug')}` syntax alongside `${clauses}` without breaking changes. Existing templates continue to work.

- Related: [ADR-104](ADR-104-clause-rendering-strategy.md) (clause assembly via string concatenation), [ADR-105](ADR-105-clause-snapshot-depth.md) (clause metadata persisted on generated documents), [ADR-057](ADR-057-template-storage.md) (template storage in database).
