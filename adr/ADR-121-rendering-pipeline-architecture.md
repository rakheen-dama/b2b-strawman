# ADR-121: Rendering Pipeline Architecture

**Status**: Accepted

**Context**:

With document storage moving from Thymeleaf HTML to Tiptap JSON (ADR-120), we need a new rendering pipeline that converts JSON documents to HTML for PDF generation (via OpenHTMLToPDF, which is unchanged). The renderer must handle standard rich text nodes (headings, paragraphs, lists, tables) plus three custom node types: `variable` (dot-path context map lookups), `clauseBlock` (recursive clause rendering), and `loopTable` (data source iteration for table rows).

The existing pipeline uses Thymeleaf with a dedicated StringTemplateResolver and OGNL dialect. This requires active SSTI defense via TemplateSecurityValidator because Thymeleaf/OGNL is a full expression language capable of arbitrary code execution. The new pipeline should eliminate this attack surface by design.

**Options Considered**:

1. **Custom TiptapRenderer (JSON tree walker)** -- A pure Java service that walks the Tiptap JSON tree recursively, emitting HTML elements for each node type. Variables resolved by dot-path lookup on context maps. No template engine library -- just StringBuilder and switch/case on node types.
   - Pros:
     - Zero expression language (no injection surface by design)
     - Simple and auditable: one recursive method handles the entire tree
     - No external template library dependency beyond what the project already uses
     - Full control over output HTML structure and class names
     - Variable resolution is pure map lookup (no evaluation, no parsing)
     - Clause rendering is a recursive tree walk (not string injection into a template)
   - Cons:
     - Must implement HTML emission for every node type manually
     - No existing Java library for Tiptap JSON to HTML conversion
     - Must handle marks (bold, italic, link, underline) correctly across all inline node types
     - Missing or unrecognized node types produce empty output -- renderer must be comprehensive

2. **Keep Thymeleaf with JSON-to-HTML pre-processor** -- Convert Tiptap JSON to Thymeleaf-compatible HTML on the fly, then feed to the existing Thymeleaf engine. Variables become `${key}` expressions; clause blocks become `th:utext` fragments.
   - Pros:
     - Reuses existing Thymeleaf infrastructure with minimal backend changes
     - LenientOGNLEvaluator still handles missing fields gracefully
     - No new dependency or rendering model to learn
   - Cons:
     - SSTI attack surface remains: Thymeleaf/OGNL is still a full expression language
     - JSON to Thymeleaf HTML conversion is a fragile translation layer between two moving targets
     - TemplateSecurityValidator and LenientStandardDialect must be maintained indefinitely
     - Two-step rendering (convert JSON then process with Thymeleaf) adds latency and failure modes
     - OGNL evaluation overhead for operations that are semantically just map lookups

3. **Handlebars.java (logic-less template engine)** -- Convert Tiptap JSON to Handlebars-compatible HTML, then render with Handlebars.java. Variables become `{{key}}` expressions; clause blocks become partials; loop tables become `{{#each}}` blocks.
   - Pros:
     - Handlebars is intentionally logic-less, with no arbitrary code execution by design
     - Established Java library (jknack/handlebars.java) with active maintenance
     - `{{variable}}` syntax is HTML-escaped by default, reducing XSS risk
     - `{{#each}}` handles loop table iteration natively without custom logic
   - Cons:
     - Still requires a JSON to Handlebars template conversion step, introducing the same fragility as Option 2
     - Handlebars helpers can be registered on the engine, creating a potential attack surface if not locked down
     - Clause blocks as partials add indirection and complexity to the rendering path
     - Two-step pipeline (convert then render) versus a single-step tree walk
     - Adds a new dependency not currently in the project

4. **JavaScript/Node.js renderer via Tiptap's generateHTML** -- Use Tiptap's own `generateHTML()` from `@tiptap/html`, running via GraalVM polyglot or a Node.js sidecar, with Java-side variable resolution applied after.
   - Pros:
     - Uses Tiptap's own renderer, guaranteeing format compatibility with the frontend editor
     - Handles all standard node types and extensions automatically
     - Reduces risk of rendering divergence between editor display and PDF output
   - Cons:
     - Adds JVM-to-JS bridge complexity (GraalVM polyglot startup overhead or Node.js process management)
     - Custom nodes (variable, clauseBlock, loopTable) still require Java-side resolution -- JS renderer has no access to the context map
     - Operational complexity: Node.js process lifecycle must be managed alongside the Spring Boot process
     - Latency overhead for every render invocation
     - Breaks the pure-Java backend architecture with no proportionate benefit for the custom node types that drive the design

**Decision**: Option 1 -- Custom TiptapRenderer (JSON tree walker).

**Rationale**:

The primary driver is **security by design**. A JSON tree walker with map-based variable resolution has no expression language, no evaluation engine, and therefore no injection surface. This eliminates `TemplateSecurityValidator`, `LenientStandardDialect`, and `LenientOGNLEvaluator` entirely -- not because a better defense was found, but because the attack surface does not exist. Removing the defense code rather than hardening it is the more durable outcome.

The Tiptap JSON schema is well-defined and relatively small: approximately fifteen node types for standard rich text (paragraph, heading, bulletList, orderedList, listItem, blockquote, codeBlock, horizontalRule, table, tableRow, tableHeader, tableCell, text, hardBreak, doc) plus three custom types (variable, clauseBlock, loopTable). Implementing HTML emission for this set is straightforward string building, not complex enough to justify pulling in a template engine library.

The recursive tree walk handles clause blocks naturally: a clauseBlock node loads the referenced clause's JSON body and renders it with the same method and the same context map. No partials, no template registration, no indirection. Loop tables follow the same pattern: iterate the named collection in the context map, render each item as a table row by calling the same recursive method.

Handlebars.java (Option 3) is the closest alternative but still requires a JSON to Handlebars conversion step, which creates the same fragile intermediate representation that made the Thymeleaf approach problematic. The tree walker eliminates all intermediate formats: JSON in, HTML out, one step. The conversion step is not a minor implementation detail -- it is an ongoing maintenance coupling between the storage format and the rendering model.

The Node.js renderer (Option 4) would be appropriate if custom nodes did not exist or if the Tiptap JSON schema were unstable. Neither is true here. The custom nodes (variable, clauseBlock, loopTable) are the design -- they require Java-side resolution regardless of how standard nodes are rendered. A JS renderer that handles standard nodes but falls back to Java for custom nodes is a split pipeline with no clear benefit over a unified Java walker.

**Consequences**:

- `TiptapRenderer` is a single Java service class (approximately 200--300 LOC) in the `template/` package, with one primary `render(JsonNode, Map<String, Object>)` method that walks the tree recursively
- All Thymeleaf document-rendering code is deleted: `LenientStandardDialect`, `LenientOGNLEvaluator`, `TemplateSecurityValidator`, `ClauseAssembler`, and the Thymeleaf engine setup in `PdfRenderingService`
- `PdfRenderingService` is simplified: call `TiptapRenderer.render()` to produce HTML, then pass to OpenHTMLToPDF unchanged -- the PDF generation stage is unaffected
- Variable resolution degrades gracefully: missing keys or invalid dot-paths produce an empty string (no exception, no placeholder text), consistent with the existing LenientOGNLEvaluator behavior
- Clause rendering is recursive: a clauseBlock node fetches the clause's stored JSON body from the repository and calls `render()` again with the same context map; circular references must be detected via a depth limit or visited-set guard
- HTML output must match existing CSS class names and element structure to avoid PDF styling regressions; the renderer emits the same class attributes that the Thymeleaf pipeline previously produced
- New node types added in future phases require adding one case to the renderer's switch statement -- a localized, low-effort change
- Email templates (Phase 24) retain their separate Thymeleaf engine; the `spring-boot-starter-thymeleaf` dependency remains in `pom.xml` -- only the document-rendering Thymeleaf code is removed
- Marks (bold, italic, underline, link, code) are applied as nested inline elements within text nodes; the renderer wraps text in `<strong>`, `<em>`, `<u>`, `<a>`, or `<code>` elements based on the marks array present on each text node
- Related: [ADR-119](ADR-119-editor-library-selection.md) (editor library selection), [ADR-120](ADR-120-document-storage-format.md) (Tiptap JSON as storage format), [ADR-122](ADR-122-content-migration-strategy.md) (migration of existing HTML documents to Tiptap JSON)
