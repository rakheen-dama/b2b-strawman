# ADR-104: Clause Rendering Strategy

**Status**: Accepted

**Context**:

The document generation pipeline (Phase 12) renders Thymeleaf templates with entity context — project, customer, or invoice data plus org branding — producing HTML that is converted to PDF via OpenHTMLToPDF. `PdfRenderingService` loads a `DocumentTemplate`, finds the appropriate `TemplateContextBuilder`, builds a `Map<String, Object>` context, and renders via Thymeleaf's string template engine. `TemplateSecurityValidator` runs on all template content before rendering to block SSTI vectors (T() expressions, object instantiation, context access, etc.).

Phase 27 introduces clauses: reusable HTML/Thymeleaf text blocks (e.g., payment terms, liability limitations, confidentiality provisions) that compose into documents at generation time. Users select and reorder clauses per document generation. The architect must decide how clause content is injected into the existing template rendering pipeline. The key tension is between simplicity (one rendering pass, one code path) and isolation (individual clause error handling, per-clause positioning).

Clause bodies are authored by org admins in the same Thymeleaf dialect as templates. They may reference entity context variables like `${project.name}`, `${customer.name}`, or `${org.brandColor}`. They are validated by `TemplateSecurityValidator` at save time (when the clause is created/edited) and again at render time (defense in depth).

**Options Considered**:

1. **String concatenation before Thymeleaf rendering (chosen)** -- Assemble all selected clause bodies into a single HTML string, wrap each in a `<div class="clause-block">`, inject the combined string into the template context as a `clauses` variable. Template uses `th:utext="${clauses}"` to render the block. Thymeleaf processes everything in one pass — clauses have access to the same variables as the parent template.
   - Pros:
     - Simple implementation: clause assembly is string concatenation with div wrappers, no template resolver changes.
     - Clauses get full template context for free — `${project.name}`, `${org.brandColor}`, etc. work identically in clauses and templates.
     - Single rendering pass: no performance penalty from multiple Thymeleaf render cycles.
     - Consistent error handling: one clause error fails the whole render, which is correct — partial document rendering would produce misleading legal/financial documents.
     - Works naturally with the per-document clause picker: user selects and reorders clauses as a set, they render in that order.
   - Cons:
     - Raw clause HTML must be trusted — mitigated by `TemplateSecurityValidator` at both save-time and render-time.
     - All clauses rendered even if template does not include `${clauses}` — mitigated by only assembling when clauses are selected.
     - Individual clause rendering errors cannot be isolated — one bad Thymeleaf expression in any clause fails the entire document.

2. **Thymeleaf fragment inclusion** -- Register each clause as a named Thymeleaf template fragment. Template references them via `th:replace` or `th:insert` by clause slug. Each clause rendered as a separate template resolution.
   - Pros:
     - Standard Thymeleaf mechanism — uses built-in fragment resolution rather than string injection.
     - Individual clause errors can be caught separately during fragment resolution.
   - Cons:
     - Requires a dynamic `ITemplateResolver` registration per render call — the clause bodies are database-stored, not classpath resources. This means building a custom resolver that maps slug names to clause body strings for the duration of a single render.
     - Clause names must be unique across the render context. Slug collisions between clauses and any future template fragments would cause silent rendering errors.
     - Significantly more complex: custom resolver lifecycle, per-render state management, fragment name namespace.
     - Fundamentally incompatible with the per-document clause picker model: `th:replace` hardcodes fragment names in the template, so the template author decides which clauses appear — the user at generation time cannot add or remove clauses without editing the template.

3. **Two-pass rendering** -- First pass: render each clause individually with the entity context, producing resolved HTML per clause. Second pass: inject the pre-rendered HTML strings into the template (no Thymeleaf variables in the injected content — just static HTML).
   - Pros:
     - Clause rendering errors isolated per clause — the system can report "clause X failed to render" while still rendering the rest.
     - Pre-rendered HTML is inert in the second pass — no SSTI risk from injected content (already resolved to static HTML).
   - Cons:
     - Two full Thymeleaf render cycles per document generation: N clause renders + 1 template render. For a document with 8 clauses, that is 9 Thymeleaf invocations instead of 1.
     - Clauses cannot use template-level layout variables or conditionals that depend on other clause content or their position in the document.
     - More complex error handling: must decide what to do when one clause fails (skip it? fail the whole document? render a placeholder?). For legal documents, skipping a failed clause silently is worse than failing loudly.
     - Pre-rendered HTML loses Thymeleaf conditional logic — a clause with `th:if` that depends on runtime context resolves in the first pass, before it knows its position in the template.

**Decision**: Option 1 -- String concatenation before Thymeleaf rendering.

**Rationale**:

The simplest approach that satisfies all requirements. Clauses are Thymeleaf fragments that need the same variable context as the parent template — string concatenation gives them this for free without any additional template resolver machinery. The rendering pipeline remains a single `renderThymeleaf()` call in `PdfRenderingService`, with clause assembly happening as a context-building step beforehand.

Option 2 (fragment inclusion) is fundamentally incompatible with the per-document clause picker model. Templates would hardcode fragment names, meaning the template author — not the user at generation time — decides which clauses appear. This defeats the purpose of clause reusability and per-document customization. The dynamic `ITemplateResolver` registration also adds significant complexity for no functional benefit.

Option 3 (two-pass rendering) solves a problem that does not exist in v1: isolated clause error handling. For legal and financial documents, a partially rendered document (some clauses succeeded, one failed) is worse than a clear failure. Users need to know that their engagement letter is complete, not that "7 of 8 clauses rendered successfully." The single-pass approach correctly treats any clause error as a document error.

Security is handled by `TemplateSecurityValidator` running on clause bodies at both save-time (when admin creates/edits a clause) and render-time (defense in depth before Thymeleaf processes the assembled content). This is the same security model already proven for template content.

**Consequences**:

- Positive:
  - Clause assembly is a simple string operation: iterate selected clauses, wrap each body in `<div class="clause-block">`, concatenate. No template resolver changes, no custom Thymeleaf infrastructure.
  - Single rendering path: `PdfRenderingService.renderThymeleaf()` works identically whether a document has 0 or 20 clauses. The clause HTML is just part of the context map.
  - Clauses have full access to entity context variables. A clause referencing `${customer.name}` works the same as the parent template referencing it.
  - Performance: one Thymeleaf render pass per document, regardless of clause count.

- Negative:
  - Clause bodies are raw HTML/Thymeleaf injected via `th:utext` — `TemplateSecurityValidator` is the critical security gate. Any bypass of the validator would allow SSTI through clause content.
  - Individual clause rendering errors cannot be isolated. A bad Thymeleaf expression in any clause fails the entire document. Users must fix the offending clause before regenerating.
  - Clause preview (in the clause editor) requires building a mini-template that wraps just the clause body with `th:utext`, since clauses are not standalone templates.

- Neutral:
  - Template authors use a single `${clauses}` variable to place all clause content. No learning curve beyond knowing the variable name.
  - The `${clauseCount}` variable enables conditional display: `th:if="${clauseCount > 0}"` to show/hide a "Terms and Conditions" heading.
  - Adding per-clause CSS classes (e.g., `clause-block clause-payment-terms`) is straightforward by including the slug in the wrapping div.

- Related: [ADR-058](ADR-058-rendering-context-assembly.md) (context builder pattern), [ADR-106](ADR-106-template-clause-placeholder-strategy.md) (placeholder strategy for clause placement in templates).
